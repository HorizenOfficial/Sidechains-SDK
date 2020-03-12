package com.horizen.api.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.block.SidechainBlock
import com.horizen.forge.Forger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.forge.Forger.SendMessages._
import com.horizen.{SidechainHistory, SidechainSettings, SidechainSyncInfo}
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import scorex.core.PersistentNodeViewModifier
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, SemanticallyFailedModification, SyntacticallyFailedModification}
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class SkipSlotExceptionMessage extends RuntimeException

class SidechainBlockActor[PMOD <: PersistentNodeViewModifier, SI <: SidechainSyncInfo, HR <: SidechainHistory : ClassTag]
(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, forgerRef: ActorRef)(implicit ec: ExecutionContext)
  extends Actor with ScorexLogging {

  private var generatedBlockGroups: TrieMap[ModifierId, Seq[ModifierId]] = TrieMap()
  private var generatedBlocksPromises: TrieMap[ModifierId, Promise[Try[Seq[ModifierId]]]] = TrieMap()

  private var submitBlockPromises: TrieMap[ModifierId, Promise[Try[ModifierId]]] = TrieMap()

  lazy val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
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

  protected def processSidechainNodeViewHolderEvents: Receive = {
    case SemanticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case SyntacticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case ChangedHistory(history: SidechainHistory) =>
      processHistoryChangedEvent(history)

  }

  protected def processTryForgeNextBlockForEpochAndSlotMessage: Receive = {
    case messageToForger @ TryForgeNextBlockForEpochAndSlot(epochNumber, slotNumber) =>
    {
      val future = forgerRef ? messageToForger
      val result = Await.result(future, timeoutDuration).asInstanceOf[ForgeResult]
      result match {
        case ForgeSuccess(block) => {
          // Create a promise, that will wait for block applying result from Node
          val prom = Promise[Try[ModifierId]]()
          submitBlockPromises += (block.id -> prom)

          //and only then apply new block
          sidechainNodeViewHolderRef ! LocallyGeneratedModifier[SidechainBlock](block)

          sender() ! prom.future
        }
        case SkipSlot => sender() ! Future(Failure(new SkipSlotExceptionMessage()))

        case ForgeFailed(exception) => sender() ! Future(exception)
      }
    }
  }

  override def receive: Receive = {
    processSidechainNodeViewHolderEvents orElse processTryForgeNextBlockForEpochAndSlotMessage orElse {
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