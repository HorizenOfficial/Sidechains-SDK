package com.horizen

import scorex.core.NodeViewHolder.ReceivableMessages.{GetNodeViewChanges}
import scorex.core.PersistentNodeViewModifier
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.ChangedHistory
import scorex.util.ScorexLogging
import scala.concurrent.ExecutionContext
import scala.util.Success
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scorex.core.consensus.{HistoryReader, SyncInfo}
import scala.reflect.ClassTag


class SidechainNodeViewReindexer
[SI <: SyncInfo,
  PMOD <: PersistentNodeViewModifier,
  HR <: HistoryReader[PMOD, SI] : ClassTag]
(viewHolderRef: ActorRef) (implicit ec: ExecutionContext) extends Actor with ScorexLogging {

  protected var historyHeight : Int = 0
  protected var reindexStatus : Int = 0

  override def preStart(): Unit = {
    //subscribe for all the node view holder events involving history
    context.system.eventStream.subscribe(self, classOf[ChangedHistory[HR]])
    viewHolderRef ! GetNodeViewChanges(history = true, state = false, vault = false, mempool = false)
  }

  //Actor's event handling
  override def receive: Receive = {
    processReindexEvents orElse
    processViewHolderEvents
  }

  protected def processViewHolderEvents: Receive = {
    case ChangedHistory(history: SidechainHistory) =>
      this.historyHeight = history.getCurrentHeight
      this.reindexStatus = history.reindexStatus
  }

  protected def processReindexEvents: Receive = {
    case SidechainNodeViewReindexer.ReceivableMessages.StartReindex() => startReindex()
    case SidechainNodeViewReindexer.ReceivableMessages.StatusReindex() => statusReindex()
  }

  protected def startReindex(): Unit = {
    if (reindexStatus == SidechainHistory.ReindexNotInProgress) {
        viewHolderRef ! SidechainNodeViewHolder.ReceivableMessages.ReindexStep(true)
        sender() ! Success(Option.empty)
    }else {
      sender() ! Success(Some(reindexStatus))
    }
  }


  protected def statusReindex(): Unit = {
    sender() ! Success(reindexStatus)
  }
}

object SidechainNodeViewReindexer {

  object ReceivableMessages {
    case class StartReindex()
    case class StatusReindex()
  }

}

object SidechainNodeViewReindexerRef {
  def props(viewHolderRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainNodeViewReindexer(viewHolderRef))

  def apply(name: String, viewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(viewHolderRef), name)

  def apply(sviewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sviewHolderRef))
}