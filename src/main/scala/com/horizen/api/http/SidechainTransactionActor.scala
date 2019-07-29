package com.horizen.api.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.transaction.Transaction
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{FailedTransaction, SuccessfulTransaction}
import scorex.util.ScorexLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Promise}

class SidechainTransactionActor[T <: Transaction](sidechainNodeViewHolderRef : ActorRef)(implicit ec : ExecutionContext)
  extends Actor with ScorexLogging {

  private var transactionMap : TrieMap[String, Promise[Unit]] = TrieMap()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SuccessfulTransaction[T]])
    context.system.eventStream.subscribe(self, classOf[FailedTransaction[T]])
  }

  protected def broadcastTransaction : Receive = {
    case BroadcastTransaction(transaction) =>
      val promise = Promise[Unit]
      val future = promise.future
      transactionMap(transaction.id()) = promise
      sender() ! future

      sidechainNodeViewHolderRef ! LocallyGeneratedTransaction[Transaction](transaction)
  }

  protected def sidechainNodeViewHolderEvents : Receive = {
    case SuccessfulTransaction(transaction) =>
      transactionMap.remove(transaction.id) match {
        case Some(promise) => promise.success()
        case None =>
      }
    case FailedTransaction(transaction, throwable) =>
      transactionMap.remove(transaction.id) match {
        case Some(promise) => promise.failure(throwable)
        case None =>
      }
  }

  override def receive: Receive = {
    broadcastTransaction orElse
    sidechainNodeViewHolderEvents orElse
    {
      case a : Any => log.error("Strange input: " + a)
    }
  }
}

object SidechainTransactionActor {
  object ReceivableMessages{
    case class BroadcastTransaction[T <: Transaction](transaction : T)
  }
}

object SidechainTransactionRef{
  def props(sidechainNodeViewHolderRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainTransactionActor(sidechainNodeViewHolderRef))

  def apply(sidechainNodeViewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef))
}