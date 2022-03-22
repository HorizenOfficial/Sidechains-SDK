package com.horizen.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.{TestActor, TestProbe}
import akka.util.Timeout
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.SidechainTransactionActorRef
import com.horizen.fixtures.{SidechainTypesTestsExtension, TransactionFixture}
import com.horizen.transaction.RegularTransaction
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{FailedTransaction, SuccessfulTransaction}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import org.junit.Assert._
import scorex.util.ModifierId
import scala.language.postfixOps

import scala.concurrent.duration._

class SidechainTransactionActorTest extends JUnitSuite with MockitoSugar with TransactionFixture with SidechainTypesTestsExtension {
  implicit lazy val actorSystem: ActorSystem = ActorSystem("tx-actor-test")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")
  implicit val timeout: Timeout = 1 second

  @Test
  def submitTransactionSuccessful(): Unit = {
    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((_: ActorRef, msg: Any) => {
      msg match {
        case LocallyGeneratedTransaction(tx) =>
          actorSystem.eventStream.publish(SuccessfulTransaction(tx))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(mockedSidechainNodeViewHolderRef)

    val transaction: RegularTransaction = getRegularTransaction
    val resFuture = Await.result(
      sidechainTransactionActorRef ? BroadcastTransaction(regularTxToScbt(transaction)), timeout.duration).asInstanceOf[Future[ModifierId]]
    Await.ready(resFuture, timeout.duration).value.get match {
      case Success(txId: ModifierId) => assertEquals(transaction.id(), txId)
      case Failure(_) => fail("Transaction expected to be submitted successfully.")
    }
  }

  @Test
  def submitTransactionFailure(): Unit = {
    val ex = new IllegalArgumentException("invalid tx")
    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((_: ActorRef, msg: Any) => {
      msg match {
        case LocallyGeneratedTransaction(tx) =>
          actorSystem.eventStream.publish(FailedTransaction(tx.id, ex, immediateFailure = true))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(mockedSidechainNodeViewHolderRef)

    val transaction: RegularTransaction = getRegularTransaction
    val resFuture = Await.result(
      sidechainTransactionActorRef ? BroadcastTransaction(regularTxToScbt(transaction)), timeout.duration).asInstanceOf[Future[ModifierId]]
    Await.ready(resFuture, timeout.duration).value.get match {
      case Success(_) => fail("Transaction expected to be not submitted.")
      case Failure(exception) => assertEquals("Transaction submission failed but with different exception.", ex, exception)
    }
  }
}
