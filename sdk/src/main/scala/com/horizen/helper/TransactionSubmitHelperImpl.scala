package com.horizen.helper

import java.lang
import java.util.function.Consumer

import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.{Inject, Provider}
import com.horizen.SidechainApp
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}


class TransactionSubmitHelperImpl @Inject()(val appProvider: Provider[SidechainApp]) extends TransactionSubmitHelper {

  implicit val duration: Timeout = 20 seconds

  override def submitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]], callback: Consumer[lang.Boolean]): Unit = {
      val barrier : Future[Unit] = Await.result(
        appProvider.get().getTransactionActorRef() ? BroadcastTransaction(tx), 20 seconds).asInstanceOf[Future[Unit]]

      barrier onComplete{
        case Success(_) =>
          callback.accept(true)
        case Failure(exp) =>
          callback.accept(false)
      }
  }
}