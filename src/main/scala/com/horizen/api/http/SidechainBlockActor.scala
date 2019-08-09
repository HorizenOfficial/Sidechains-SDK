package com.horizen.api.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.{SidechainHistory, SidechainSettings, SidechainSyncInfo}
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.block.SidechainBlock
import com.horizen.forge.Forger.ReceivableMessages.{TryForgeNextBlock, TrySubmitBlock}
import scorex.core.PersistentNodeViewModifier
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, SemanticallyFailedModification, SyntacticallyFailedModification}
import scorex.util.{ModifierId, ScorexLogging}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


class SidechainBlockActor[PMOD <: PersistentNodeViewModifier, SI <: SidechainSyncInfo, HR <: SidechainHistory : ClassTag]
(settings: SidechainSettings, sidechainNodeViewHolderRef : ActorRef, forgerRef: ActorRef)(implicit ec : ExecutionContext)
  extends Actor with ScorexLogging {

  private var generatedBlockGroups: Map[ModifierId, Seq[ModifierId]] = Map()
  private var generateBlocksPromises: Map[ModifierId, Promise[Try[Seq[ModifierId]]]] = Map()

  private var submitedBlocks: Set[ModifierId] = Set()
  private var submitBlockPromises: Map[ModifierId, Promise[Try[ModifierId]]] = Map()

  lazy val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout / 4
  implicit lazy val timeout: Timeout = Timeout(timeoutDuration)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[SyntacticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[ChangedHistory[HR]])
  }

  def processBlockFailedEvent(sidechainBlock: SidechainBlock, throwable: Throwable): Unit = {
    if(submitedBlocks.contains(sidechainBlock.id)) {
      submitBlockPromises.get(sidechainBlock.id) match {
        case Some(p) => p.success(Failure(throwable))
        case _ =>
      }
      submitedBlocks -= sidechainBlock.id
      submitBlockPromises -= sidechainBlock.id
      generatedBlockGroups -= sidechainBlock.id
    }
  }

  def processHistoryChangedEvent(history: SidechainHistory): Unit = {
    val expectedBlocks = submitedBlocks.toSeq
    for(id <- expectedBlocks) {
      if(history.contains(id)) {
        submitBlockPromises.get(id) match {
          case Some(p) => p.success(Success(id))
          case _ =>
        }
        generateBlocksPromises.get(id) match {
          case Some(p) => p.success(Success(generatedBlockGroups(id)))
          case _ =>
        }
        submitedBlocks -= id
        submitBlockPromises -= id
        generatedBlockGroups -= id
      }
    }
  }
  protected def sidechainNodeViewHolderEvents : Receive = {
    case SemanticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case SyntacticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case ChangedHistory(history: SidechainHistory) =>
      processHistoryChangedEvent(history)

  }

  // Note: It should be used only in regtest
  protected def generateSidechainBlocks : Receive = {
    case GenerateSidechainBlocks(blockCount) =>
      // Try to forge blockCount blocks, collect their ids and wait for
      var generatedIds: Seq[ModifierId] = Seq()
      for(i <- 1 to blockCount) {
        val future = forgerRef ? TryForgeNextBlock
        Await.result(future, timeoutDuration).asInstanceOf[Try[ModifierId]] match {
          case Success(id) =>
            generatedIds = id +: generatedIds
            submitedBlocks += id
            if(i == blockCount) {
              // Create a promise, that will wait for blocks applying result from Node
              val prom = Promise[Try[Seq[ModifierId]]]()
              generatedBlockGroups += (id -> generatedIds)
              generateBlocksPromises += (id -> prom)
              sender() ! prom.future
            }

          case Failure(ex) =>
            sender() ! Future[Try[Seq[ModifierId]]](Failure(ex))
        }
      }
  }

  protected def tryToSubmitBlock: Receive = {
    case SubmitSidechainBlock(blockBytes: Array[Byte]) =>
      val future = forgerRef ? TrySubmitBlock(blockBytes)
      Await.result(future, timeoutDuration).asInstanceOf[Try[ModifierId]] match {
        case Success(id) =>
          // Create a promise, that will wait for block applying result from Node
          val prom = Promise[Try[ModifierId]]()
          submitedBlocks += id
          submitBlockPromises += (id -> prom)
          sender() ! prom.future

        case Failure(ex) =>
          sender() ! Future[Try[ModifierId]](Failure(ex))
      }
  }

  override def receive: Receive = {
    sidechainNodeViewHolderEvents orElse generateSidechainBlocks  orElse tryToSubmitBlock orElse {
      case message: Any => log.error("SidechainBlockActor received strange message: " + message)
    }
  }
}

object SidechainBlockActor {
  object ReceivableMessages{
    case class GenerateSidechainBlocks(blockCount: Int)
    case class SubmitSidechainBlock(blockBytes: Array[Byte])
  }
}

object SidechainBlockActorRef{
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, sidechainForgerRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainBlockActor(settings, sidechainNodeViewHolderRef, sidechainForgerRef))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, sidechainForgerRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, sidechainForgerRef))
}