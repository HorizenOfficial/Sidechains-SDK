package com.horizen.helper

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActor, TestProbe}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.fixtures.{SidechainTypesTestsExtension, TransactionFixture}
import com.horizen.transaction.RegularTransaction
import org.junit.Assert.{assertEquals, assertTrue, assertFalse}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.util.ModifierId

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

class TransactionSubmitProviderImplTest extends JUnitSuite with MockitoSugar with TransactionFixture with SidechainTypesTestsExtension {
  implicit lazy val actorSystem: ActorSystem = ActorSystem("tx-actor-test")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")
  implicit val timeout: Timeout = 5 seconds

  @Test
  def submitTransactionSuccessful(): Unit = {
    val mockedSidechainTransactionActor = TestProbe()
    mockedSidechainTransactionActor.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case BroadcastTransaction(tx) =>
          val promise = Promise[ModifierId]
          val future = promise.future
          sender ! future
          promise.success(ModifierId @@ tx.id())
      }
      TestActor.KeepRunning
    })

    val mockedSidechainTransactionActorRef: ActorRef = mockedSidechainTransactionActor.ref
    val transactionSubmitProvider: TransactionSubmitProviderImpl = new TransactionSubmitProviderImpl(mockedSidechainTransactionActorRef)

    val transaction: RegularTransaction = getRegularTransaction
    val tryRes: Try[Unit] = Try {
      transactionSubmitProvider.submitTransaction(transaction)
    }
    tryRes match {
      case Success(_) => // expected behavior
      case Failure(exception) => fail("Transaction expected to be submitted successfully.", exception)
    }
  }

  @Test
  def submitTransactionFailure(): Unit = {
    // Note: ex type is Exception, that is expected to be transformed to IllegalArgumentException by TransactionSubmitProvider
    val ex = new Exception("invalid tx")
    val mockedSidechainTransactionActor = TestProbe()
    mockedSidechainTransactionActor.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case BroadcastTransaction(tx) =>
          val promise = Promise[ModifierId]
          val future = promise.future
          sender ! future
          promise.failure(ex)
      }
      TestActor.KeepRunning
    })

    val mockedSidechainTransactionActorRef: ActorRef = mockedSidechainTransactionActor.ref
    val transactionSubmitProvider: TransactionSubmitProviderImpl = new TransactionSubmitProviderImpl(mockedSidechainTransactionActorRef)

    val transaction: RegularTransaction = getRegularTransaction
    val tryRes: Try[Unit] = Try {
      transactionSubmitProvider.submitTransaction(transaction)
    }
    tryRes match {
      case Success(_) => fail("Transaction expected to be not submitted.")
      case Failure(exception) =>
        assertTrue("Transaction submission failed but with different exception type.", exception.isInstanceOf[IllegalArgumentException])
        assertEquals("Transaction submission failed but with different exception.", ex.toString, exception.getMessage)
    }
  }

  @Test
  def asyncSubmitTransactionSuccessful(): Unit = {
    val mockedSidechainTransactionActor = TestProbe()
    mockedSidechainTransactionActor.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case BroadcastTransaction(tx) =>
          val promise = Promise[ModifierId]
          val future = promise.future
          sender ! future
          promise.success(ModifierId @@ tx.id())
      }
      TestActor.KeepRunning
    })

    val mockedSidechainTransactionActorRef: ActorRef = mockedSidechainTransactionActor.ref
    val transactionSubmitProvider: TransactionSubmitProviderImpl = new TransactionSubmitProviderImpl(mockedSidechainTransactionActorRef)

    val transaction: RegularTransaction = getRegularTransaction

    var callbackCondition = false;
    var asyncOpCondition = false;

    def callback(res: Boolean, errorOpt: Option[Throwable]): Unit = synchronized {
      var attempts = 10;
      while (!asyncOpCondition && attempts > 0) {
        Thread.sleep(100)
        attempts -= 1
      }
      assertTrue("Callback is not async.", asyncOpCondition)

      assertTrue("Transaction expected to be submitted.", res)
      assertTrue("No errors on success result", errorOpt.isEmpty)
      callbackCondition = true;
    }

    // Start submission operation ...
    transactionSubmitProvider.asyncSubmitTransaction(transaction, callback)
    // ... and be sure that operation is really async
    asyncOpCondition = true

    // wait for callback completion.
    var attempts = 10;
    while (!callbackCondition && attempts > 0) {
      Thread.sleep(100)
      attempts -= 1
    }
    assertTrue("Callback was not executed in time", callbackCondition)
  }

  @Test
  def asyncSubmitTransactionFailure(): Unit = {
    val ex = new Exception("invalid tx")

    val mockedSidechainTransactionActor = TestProbe()
    mockedSidechainTransactionActor.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case BroadcastTransaction(tx) =>
          val promise = Promise[ModifierId]
          val future = promise.future
          sender ! future
          promise.failure(ex)
      }
      TestActor.KeepRunning
    })

    val mockedSidechainTransactionActorRef: ActorRef = mockedSidechainTransactionActor.ref
    val transactionSubmitProvider: TransactionSubmitProviderImpl = new TransactionSubmitProviderImpl(mockedSidechainTransactionActorRef)

    val transaction: RegularTransaction = getRegularTransaction

    var callbackCondition = false;
    var asyncOpCondition = false;

    def callback(res: Boolean, errorOpt: Option[Throwable]): Unit = synchronized {
      var attempts = 10;
      while (!asyncOpCondition && attempts > 0) {
        Thread.sleep(100)
        attempts -= 1
      }
      assertTrue("Callback is not async.", asyncOpCondition)

      assertFalse("Transaction expected to be not submitted.", res)
      assertTrue("Error expected to be present on failure", errorOpt.isDefined)
      assertEquals("Transaction submission failed but with different exception.", ex, errorOpt.get)
      callbackCondition = true;
    }

    // Start submission operation ...
    transactionSubmitProvider.asyncSubmitTransaction(transaction, callback)
    // ... and be sure that operation is really async
    asyncOpCondition = true

    // wait for callback completion.
    var attempts = 10;
    while (!callbackCondition && attempts > 0) {
      Thread.sleep(100)
      attempts -= 1
    }
    assertTrue("Callback was not executed in time", callbackCondition)
  }
}