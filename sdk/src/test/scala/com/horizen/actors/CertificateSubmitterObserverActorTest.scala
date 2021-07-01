package com.horizen.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.certificatesubmitter.CertificateSubmitterObserver.GetProofGenerationState
import com.horizen.fixtures.{SidechainTypesTestsExtension, TransactionFixture}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}
import org.junit.Assert._
import scala.language.postfixOps
import com.horizen.certificatesubmitter.{CertificateSubmitter, CertificateSubmitterObserverRef}
import scala.concurrent.duration._

class CertificateSubmitterObserverActorTest extends JUnitSuite with MockitoSugar with TransactionFixture with SidechainTypesTestsExtension {
  implicit lazy val actorSystem: ActorSystem = ActorSystem("tx-actor-test")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")
  implicit val timeout: Timeout = 1 second

  @Test
  def submitStartProofGeneration(): Unit = {
    val certSubmitterObserverRef: ActorRef = CertificateSubmitterObserverRef()

    val future1 = certSubmitterObserverRef ? GetProofGenerationState
    val result1 = Await.result(future1, timeout.duration).asInstanceOf[Try[Boolean]]
    result1 match {
      case Success(state) =>
        assertFalse("Certificate proof generation state expected to be inactive at startup.", state)
      case Failure(e) =>
        fail("Certificate proof generation state expected to be received.")
    }

    actorSystem.eventStream.publish(CertificateSubmitter.StartCertificateSubmission)

    val future2 = certSubmitterObserverRef ? GetProofGenerationState
    val result2 = Await.result(future2, timeout.duration).asInstanceOf[Try[Boolean]]
    result2 match {
      case Success(state) =>
        assertTrue("Certificate proof generation state expected to be active.", state)
      case Failure(e) =>
        fail("Certificate proof generation state expected to be received.")
    }

    actorSystem.eventStream.publish(CertificateSubmitter.StopCertificateSubmission)

    val future3 = certSubmitterObserverRef ? GetProofGenerationState
    val result3 = Await.result(future3, timeout.duration).asInstanceOf[Try[Boolean]]
    result3 match {
      case Success(state) =>
        assertFalse("Certificate proof generation state expected to be inactive.", state)
      case Failure(e) =>
        fail("Certificate proof generation state expected to be received.")
    }
  }
}
