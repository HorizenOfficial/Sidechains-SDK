package com.horizen.helper

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.transaction.Transaction
import scorex.util.ModifierId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class TransactionSubmitProviderImpl[TX <: Transaction ](transactionActor: ActorRef) extends TransactionSubmitProvider[TX] {

  implicit val timeout: Timeout = 20 seconds

  @throws(classOf[IllegalArgumentException])
  override def submitTransaction(tx: TX): Unit = {
    val resFuture = Await.result(transactionActor ? BroadcastTransaction(tx), timeout.duration).asInstanceOf[Future[ModifierId]]
    Await.ready(resFuture, timeout.duration).value.get match {
      case Success(_) =>
      case Failure(exp) => throw new IllegalArgumentException(exp)
    }
  }

  override def asyncSubmitTransaction(tx: TX, callback:(Boolean, Option[Throwable]) => Unit): Unit = {
    val barrier : Future[Unit] = Await.result(
      transactionActor ? BroadcastTransaction(tx), timeout.duration).asInstanceOf[Future[Unit]]
    barrier onComplete{
      case Success(_) =>
        callback(true, None)
      case Failure(exp) =>
        callback(false, Some(exp))
    }
  }
}
