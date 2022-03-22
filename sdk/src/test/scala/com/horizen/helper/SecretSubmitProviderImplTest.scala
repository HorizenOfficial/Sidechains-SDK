package com.horizen.helper

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActor, TestProbe}
import akka.util.Timeout
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.LocallyGeneratedSecret
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.fixtures.{SecretFixture, SidechainTypesTestsExtension, TransactionFixture}
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.RegularTransaction
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SuccessfulTransaction
import scorex.util.ModifierId

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

class SecretSubmitProviderImplTest extends JUnitSuite with MockitoSugar with SecretFixture with SidechainTypesTestsExtension {
  implicit lazy val actorSystem: ActorSystem = ActorSystem("tx-actor-test")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")
  implicit val timeout: Timeout = 5 seconds

  @Test
  def submitTransactionSuccessful(): Unit = {
    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case LocallyGeneratedSecret(_) =>
          sender ! Success(Unit)
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val secretSubmitProvider: SecretSubmitProviderImpl = new SecretSubmitProviderImpl(mockedSidechainNodeViewHolderRef)

    val secret: PrivateKey25519 = getPrivateKey25519("123".getBytes())
    val tryRes: Try[Unit] = Try {
      secretSubmitProvider.submitSecret(secret)
    }
    tryRes match {
      case Success(_) => // expected behavior
      case Failure(exception) => fail("Secret expected to be submitted successfully.", exception)
    }
  }

  @Test
  def submitTransactionFailure(): Unit = {
    // Note: ex type is Exception, that is expected to be transformed to IllegalArgumentException by SecretSubmitProvider
    val ex = new Exception("invalid tx")
    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case LocallyGeneratedSecret(_) =>
          sender ! Failure(ex)
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val secretSubmitProvider: SecretSubmitProviderImpl = new SecretSubmitProviderImpl(mockedSidechainNodeViewHolderRef)

    val secret: PrivateKey25519 = getPrivateKey25519("123".getBytes())
    val tryRes: Try[Unit] = Try {
      secretSubmitProvider.submitSecret(secret)
    }
    tryRes match {
      case Success(_) => fail("Secret expected to be not submitted.")
      case Failure(exception) =>
        assertTrue("Secret submission failed but with different exception type.", exception.isInstanceOf[IllegalArgumentException])
        assertEquals("Secret submission failed but with different exception.", ex.toString, exception.getMessage)
    }
  }
}
