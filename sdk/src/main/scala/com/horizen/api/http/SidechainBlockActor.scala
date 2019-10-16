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

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


class SidechainBlockActor[PMOD <: PersistentNodeViewModifier, SI <: SidechainSyncInfo, HR <: SidechainHistory : ClassTag]
(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, forgerRef: ActorRef)(implicit ec: ExecutionContext)
  extends Actor with ScorexLogging {

  private var generatedBlockGroups: TrieMap[ModifierId, Seq[ModifierId]] = TrieMap()
  private var generatedBlocksPromises: TrieMap[ModifierId, Promise[Try[Seq[ModifierId]]]] = TrieMap()

  private var submitBlockPromises: TrieMap[ModifierId, Promise[Try[ModifierId]]] = TrieMap()

  lazy val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout / 4
  implicit lazy val timeout: Timeout = Timeout(timeoutDuration)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[SyntacticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[ChangedHistory[HR]])
  }

  def processBlockFailedEvent(sidechainBlock: SidechainBlock, throwable: Throwable): Unit = {
    if (submitBlockPromises.contains(sidechainBlock.id) || generatedBlocksPromises.contains(sidechainBlock.id)) {
      submitBlockPromises.get(sidechainBlock.id) match {
        case Some(p) =>
          p.failure(throwable)
          submitBlockPromises -= sidechainBlock.id
        case _ =>
      }

      generatedBlocksPromises.get(sidechainBlock.id) match {
        case Some(p) =>
          p.failure(throwable)
          generatedBlockGroups -= sidechainBlock.id
          generatedBlocksPromises -= sidechainBlock.id
        case _ =>
      }
    }
  }

  def processHistoryChangedEvent(history: SidechainHistory): Unit = {
    val expectedBlocks = submitBlockPromises.keys ++ generatedBlocksPromises.keys
    for (id <- expectedBlocks) {
      if (history.contains(id)) {
        submitBlockPromises.get(id) match {
          case Some(p) =>
            p.success(Success(id))
            submitBlockPromises -= id
          case _ =>
        }
        generatedBlocksPromises.get(id) match {
          case Some(p) =>
            p.success(Success(generatedBlockGroups(id)))
            generatedBlockGroups -= id
            generatedBlocksPromises -= id
          case _ =>
        }
      }
    }
  }

  protected def sidechainNodeViewHolderEvents: Receive = {
    case SemanticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case SyntacticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case ChangedHistory(history: SidechainHistory) =>
      processHistoryChangedEvent(history)

  }

  // Note: It should be used only in regtest
  protected def generateSidechainBlocks: Receive = {
    case GenerateSidechainBlocks(blockCount) =>
      // Try to forge blockCount blocks, collect their ids and wait for
      var generatedIds: Seq[ModifierId] = Seq()
      for (i <- 1 to blockCount) {
        val future = forgerRef ? TryForgeNextBlock
        Await.result(future, timeoutDuration).asInstanceOf[Try[ModifierId]] match {
          case Success(id) =>
            generatedIds = id +: generatedIds
            if (i == blockCount) {
              // Create a promise, that will wait for blocks applying result from Node
              val prom = Promise[Try[Seq[ModifierId]]]()
              generatedBlockGroups += (id -> generatedIds)
              generatedBlocksPromises += (id -> prom)
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
          submitBlockPromises += (id -> prom)
          sender() ! prom.future

        case Failure(ex) =>
          sender() ! Future[Try[ModifierId]](Failure(ex))
      }
  }

  override def receive: Receive = {
    sidechainNodeViewHolderEvents orElse generateSidechainBlocks orElse tryToSubmitBlock orElse {
      case message: Any => log.error("SidechainBlockActor received strange message: " + message)
    }
  }
}

object SidechainBlockActor {

  object ReceivableMessages {

    case class GenerateSidechainBlocks(blockCount: Int)

    case class SubmitSidechainBlock(blockBytes: Array[Byte])

  }

}

object SidechainBlockActorRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, sidechainForgerRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainBlockActor(settings, sidechainNodeViewHolderRef, sidechainForgerRef))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, sidechainForgerRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, sidechainForgerRef))
}