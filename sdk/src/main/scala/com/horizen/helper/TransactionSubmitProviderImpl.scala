package com.horizen.helper
import akka.actor.ActorRef
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import scorex.util.ModifierId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class TransactionSubmitProviderImpl(var transactionActor: ActorRef) extends TransactionSubmitProvider {

  implicit val timeout: Timeout = 20 seconds

  @throws(classOf[IllegalArgumentException])
  override def submitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]]): Unit = {
    val resFuture = Await.result(transactionActor ? BroadcastTransaction(tx), timeout.duration).asInstanceOf[Future[ModifierId]]
    Await.ready(resFuture, timeout.duration).value.get match {
      case Success(_) =>
      case Failure(exp) => throw new IllegalArgumentException(exp)
    }
  }

  override def asyncSubmitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]], callback:(Boolean, Option[Throwable]) => Unit): Unit = {
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
