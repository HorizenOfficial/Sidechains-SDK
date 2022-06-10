package com.horizen.api.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import com.horizen.SidechainTypes
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{FailedTransaction, SuccessfulTransaction}
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Promise}

class SidechainTransactionActor[T <: SidechainTypes#SCBT](sidechainNodeViewHolderRef: ActorRef)(implicit ec: ExecutionContext)
  extends Actor with ScorexLogging {

  private val transactionMap : TrieMap[String, Promise[ModifierId]] = TrieMap()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SuccessfulTransaction[T]])
    context.system.eventStream.subscribe(self, classOf[FailedTransaction])
  }

  override def postStop(): Unit = {
    log.debug("SidechainTransaction actor is stopping...")
    super.postStop()
  }

  protected def broadcastTransaction: Receive = {
    case BroadcastTransaction(transaction) =>
      val promise = Promise[ModifierId]
      val future = promise.future
      transactionMap(transaction.id) = promise
      sender() ! future
      sidechainNodeViewHolderRef ! LocallyGeneratedTransaction[SidechainTypes#SCBT](transaction)
  }

  protected def sidechainNodeViewHolderEvents: Receive = {
    case SuccessfulTransaction(transaction) =>
      transactionMap.remove(transaction.id) match {
        case Some(promise) => promise.success(transaction.id)
        case None =>
      }
    case FailedTransaction(transactionId, throwable, _) =>
      transactionMap.remove(transactionId) match {
        case Some(promise) => promise.failure(throwable)
        case None =>
      }
  }

  override def receive: Receive = {
    broadcastTransaction orElse
    sidechainNodeViewHolderEvents orElse {
      case message: Any => log.error("SidechainTransactionActor received strange message: " + message)
    }
  }
}

object SidechainTransactionActor {

  object ReceivableMessages {

    case class BroadcastTransaction[T <: SidechainTypes#SCBT](transaction: T)

  }

}

object SidechainTransactionActorRef {
  def props(sidechainNodeViewHolderRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainTransactionActor(sidechainNodeViewHolderRef))

  def apply(sidechainNodeViewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef))
}
