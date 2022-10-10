package com.horizen.api.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.forge.AbstractForger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.{SidechainSettings, SidechainSyncInfo}
import sparkz.core.PersistentNodeViewModifier
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedState, SemanticallyFailedModification, SyntacticallyFailedModification}
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.consensus.HistoryReader
import sparkz.core.transaction.state.StateReader

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


class SidechainBlockActor[PMOD <: PersistentNodeViewModifier : ClassTag, SI <: SidechainSyncInfo, HR <: HistoryReader[PMOD,SI] : ClassTag, SR <: StateReader : ClassTag]
(settings: SidechainSettings, forgerRef: ActorRef)(implicit ec: ExecutionContext)
  extends Actor with ScorexLogging {

  private val submitBlockPromises: TrieMap[ModifierId, Promise[Try[ModifierId]]] = TrieMap()

  lazy val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit lazy val timeout: Timeout = Timeout(timeoutDuration)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[SyntacticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[ChangedState[SR]])
  }

  override def postStop(): Unit = {
    log.debug("SidechainBlock Actor is stopping...")
    super.postStop()
  }

  def processBlockFailedEvent(sidechainBlock: PMOD, throwable: Throwable): Unit = {

    if (submitBlockPromises.contains(sidechainBlock.id) ) {
      submitBlockPromises.get(sidechainBlock.id) match {
        case Some(p) =>
          p.failure(throwable)
          submitBlockPromises -= sidechainBlock.id
        case _ =>
      }
    }
  }

  def processStateChangedEvent(sr: SR): Unit = {
    val expectedBlocks = submitBlockPromises.keys
    for (id <- expectedBlocks) {
      if (sr.version == id) {
        submitBlockPromises.get(id) match {
          case Some(p) =>
            p.success(Success(id))
            submitBlockPromises -= id
          case _ =>
        }
      }
    }
  }

  protected def processSidechainNodeViewHolderEvents: Receive = {
    case SemanticallyFailedModification(sidechainBlock: PMOD, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case SyntacticallyFailedModification(sidechainBlock: PMOD, throwable) =>
      processBlockFailedEvent(sidechainBlock, throwable)

    case ChangedState(sr: SR) =>
      processStateChangedEvent(sr)

  }

  protected def processTryForgeNextBlockForEpochAndSlotMessage: Receive = {
    case messageToForger @ TryForgeNextBlockForEpochAndSlot(_, _, _) =>
      val blockCreationFuture = forgerRef ? messageToForger
      val blockCreationResult = Await.result(blockCreationFuture, timeoutDuration).asInstanceOf[Try[ModifierId]]
      blockCreationResult match {
        case Success(blockId) =>
          // Create a promise, that will wait for block applying result from Node
          val prom = Promise[Try[ModifierId]]()
          submitBlockPromises += (blockId -> prom)
          sender() ! prom.future
        case failRes @ Failure(ex) =>
          log.debug("Could not forge", ex)
          sender() ! Future(failRes)
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
  def props[PMOD <: PersistentNodeViewModifier : ClassTag, SI <: SidechainSyncInfo, HR <: HistoryReader[PMOD, SI] : ClassTag, SR <: StateReader : ClassTag](settings: SidechainSettings, sidechainForgerRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainBlockActor[PMOD, SI, HR, SR](settings, sidechainForgerRef))

  def apply[PMOD <: PersistentNodeViewModifier : ClassTag, SI <: SidechainSyncInfo, HR <: HistoryReader[PMOD, SI] : ClassTag, SR <: StateReader : ClassTag](name: String, settings: SidechainSettings, sidechainForgerRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props[PMOD, SI, HR, SR](settings, sidechainForgerRef), name)

  def apply[PMOD <: PersistentNodeViewModifier : ClassTag, SI <: SidechainSyncInfo, HR <: HistoryReader[PMOD, SI]: ClassTag, SR <: StateReader : ClassTag](settings: SidechainSettings, sidechainForgerRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props[PMOD, SI, HR, SR](settings, sidechainForgerRef))
}

