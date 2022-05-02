package com.horizen.certificatesubmitter.network

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestActor, TestActorRef, TestProbe}
import akka.util.Timeout
import com.horizen.certificatesubmitter.CertificateSubmitter.ReceivableMessages.{GetSignaturesStatus, SignatureFromRemote}
import com.horizen.certificatesubmitter.CertificateSubmitter.{BroadcastLocallyGeneratedSignature, CertificateSignatureFromRemoteInfo, CertificateSignatureInfo, DifferentMessageToSign, InvalidPublicKeyIndex, InvalidSignature, KnownSignature, SignatureProcessingStatus, SignaturesStatus, SubmitterIsOutsideSubmissionWindow, ValidSignature}
import com.horizen.certificatesubmitter.network.CertificateSignaturesManager.InternalReceivableMessages.TryToSendGetCertificateSignatures
import com.horizen.fixtures.FieldElementFixture
import com.horizen.SidechainAppEvents
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.secret.SchnorrKeyGenerator
import org.junit.{Assert, Test}
import org.junit.Assert._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.network.{Broadcast, BroadcastExceptOf, ConnectedPeer, ConnectionDirection, ConnectionId, SendToPeer, SendToRandom}
import scorex.core.network.NetworkController.ReceivableMessages.{PenalizePeer, RegisterMessageSpecs, SendToNetwork}
import scorex.core.network.NetworkControllerSharedMessages.ReceivableMessages.DataFromPeer
import scorex.core.network.peer.PenaltyType
import scorex.core.settings.NetworkSettings

import java.net.InetSocketAddress
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.concurrent.duration._

class CertificateSignaturesManagerTest extends JUnitSuite with MockitoSugar {

  implicit lazy val actorSystem: ActorSystem = ActorSystem("submitter-manager-actor-test")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")
  implicit val timeout: Timeout = 100 milliseconds

  val pubKeysNumber = 5
  val networkSettings: NetworkSettings = getMockedSettings(5 seconds)
  val params: NetworkParams = getParams(pubKeysNumber)

  private def getMockedSettings(timeoutDuration: FiniteDuration): NetworkSettings = {
    val mockedSettings: NetworkSettings = mock[NetworkSettings]
    Mockito.when(mockedSettings.syncTimeout).thenReturn(Some(timeoutDuration))

    mockedSettings
  }

  private def getParams(publicKeysNumber: Int): NetworkParams = {
    val publicKeys = (0 until publicKeysNumber).map {
      idx => SchnorrKeyGenerator.getInstance().generateSecret(s"seed$idx".getBytes()).publicImage()
    }
    RegTestParams(signersPublicKeys = publicKeys)
  }

  @Test
  def initialization(): Unit = {
    val networkController = TestProbe()
    val networkControllerRef: ActorRef = networkController.ref

    val submitter = TestProbe()
    val submitterRef: ActorRef = submitter.ref

    val certificateSignaturesManagerRef: TestActorRef[CertificateSignaturesManager] = TestActorRef(
      Props(new CertificateSignaturesManager(networkControllerRef, submitterRef, params, networkSettings)))

    // Send initialization event
    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    // Expect for specs registration.
    networkController.expectMsgClass(timeout.duration, classOf[RegisterMessageSpecs])
  }

  @Test
  def getCertificateSignatures(): Unit = {
    val networkController = TestProbe()
    val networkControllerRef: ActorRef = networkController.ref

    val submitter = TestProbe()
    val submitterRef: ActorRef = submitter.ref

    val certificateSignaturesManagerRef: TestActorRef[CertificateSignaturesManager] = TestActorRef(
      Props(new CertificateSignaturesManager(networkControllerRef, submitterRef, params, networkSettings)))


    var statusOpt: Option[SignaturesStatus] = None

    submitter.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetSignaturesStatus =>
          sender ! statusOpt
        case _ =>
          Assert.fail("Unexpected message retrieved.")
      }
      TestActor.KeepRunning
    })

    val peer: ConnectedPeer = mock[ConnectedPeer]
    val getCertificateSignaturesSpec = new GetCertificateSignaturesSpec(pubKeysNumber)
    val invData: InvUnknownSignatures = InvUnknownSignatures(Seq(1, 2, 4))


    // Test 1: getCertificateSignaturesSpec when outside the submission window -> do nothing
    certificateSignaturesManagerRef ! DataFromPeer(getCertificateSignaturesSpec, invData, peer)
    // Expect no answer to be send back to the peer
    networkController.expectNoMessage(timeout.duration)


    // Test 2: getCertificateSignaturesSpec when inside the submission window, but has NO known signatures
    val referencedEpoch: Int = 10
    val messageToSign: Array[Byte]= FieldElementFixture.generateFieldElement()
    val noKnownSigs: ArrayBuffer[CertificateSignatureInfo] = ArrayBuffer()
    statusOpt = Some(SignaturesStatus(referencedEpoch, messageToSign, noKnownSigs))

    certificateSignaturesManagerRef ! DataFromPeer(getCertificateSignaturesSpec, invData, peer)
    // Expect no answer to be send back to the peer
    networkController.expectNoMessage(timeout.duration)


    // Test3: getCertificateSignaturesSpec when inside the submission window and has known signatures
    val keyGenerator = SchnorrKeyGenerator.getInstance()
    val secret = keyGenerator.generateSecret("seed1".getBytes())

    val knownSigs: ArrayBuffer[CertificateSignatureInfo] = ArrayBuffer(invData.indexes.map(idx => CertificateSignatureInfo(idx, secret.sign(messageToSign))) : _*)
    statusOpt = Some(SignaturesStatus(referencedEpoch, messageToSign, knownSigs))

    certificateSignaturesManagerRef ! DataFromPeer(getCertificateSignaturesSpec, invData, peer)
    // Expect an answer to be send back to the peer
    val msg: SendToNetwork = networkController.expectMsgClass(timeout.duration, classOf[SendToNetwork])

    msg.sendingStrategy match {
      case SendToPeer(chosenPeer) =>
        assertEquals("Different peer expected.", peer, chosenPeer)
      case other =>
        Assert.fail(s"SendToPeer strategy expected, but found ${other.getClass}")
    }

    assertTrue(s"CertificateSignaturesSpec message spec expected, but found ${msg.message.spec.getClass}",
      msg.message.spec.isInstanceOf[CertificateSignaturesSpec])

    msg.message.input match {
      case Right(knownSignatures: KnownSignatures) =>
        assertArrayEquals("Invalid message to sign.", messageToSign, knownSignatures.messageToSign)
        assertEquals("Different known signatures entries found.", invData.indexes.size, knownSignatures.signaturesInfo.size)
        assertEquals("Different signatures indexes found.", invData.indexes, knownSignatures.signaturesInfo.map(_.pubKeyIndex))
      case _ =>
        Assert.fail("Invalid message data")
    }
  }

  @Test
  def broadcastSignature(): Unit = {
    val networkController = TestProbe()
    val networkControllerRef: ActorRef = networkController.ref

    val submitter = TestProbe()
    val submitterRef: ActorRef = submitter.ref

    val certificateSignaturesManagerRef: TestActorRef[CertificateSignaturesManager] = TestActorRef(
      Props(new CertificateSignaturesManager(networkControllerRef, submitterRef, params, networkSettings)))

    // decrease the delay to speedup the test
    certificateSignaturesManagerRef.underlyingActor.setLocallyGeneratedSignatureBroadcastingDelay(timeout.duration / 2)

    // Send initialization event
    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    // Expect for specs registration.
    networkController.expectMsgClass(timeout.duration, classOf[RegisterMessageSpecs])


    // Test: broadcast locally generated signature
    val pubKeyIndex: Int = 3
    val messageToSign = FieldElementFixture.generateFieldElement()
    val keyGenerator = SchnorrKeyGenerator.getInstance()
    val secret = keyGenerator.generateSecret("seed1".getBytes())
    val signature = secret.sign(messageToSign)
    val info: CertificateSignatureFromRemoteInfo = CertificateSignatureFromRemoteInfo(pubKeyIndex, messageToSign, signature)

    actorSystem.eventStream.publish(BroadcastLocallyGeneratedSignature(info))

    // Expect an answer to be send back to the peer
    val msg: SendToNetwork = networkController.expectMsgClass(timeout.duration, classOf[SendToNetwork])

    msg.sendingStrategy match {
      case Broadcast => // fine -> do nothing
      case other =>
        Assert.fail(s"Broadcast strategy expected, but found ${other.getClass}")
    }

    assertTrue(s"CertificateSignaturesSpec message spec expected, but found ${msg.message.spec.getClass}",
      msg.message.spec.isInstanceOf[CertificateSignaturesSpec])

    msg.message.input match {
      case Right(knownSignatures: KnownSignatures) =>
        assertArrayEquals("Invalid message to sign.", messageToSign, knownSignatures.messageToSign)
        assertEquals("Different known signatures entries found.", 1, knownSignatures.signaturesInfo.size)
        assertEquals("Different signature index found.", pubKeyIndex, knownSignatures.signaturesInfo.head.pubKeyIndex)
        assertEquals("Different signature found.", signature, knownSignatures.signaturesInfo.head.signature)
      case _ =>
        Assert.fail("Invalid message data")
    }
  }

  @Test
  def certificateSignatures(): Unit = {
    val networkController = TestProbe()
    val networkControllerRef: ActorRef = networkController.ref

    val submitter = TestProbe()
    val submitterRef: ActorRef = submitter.ref

    var signatureProcessingStatusRes: SignatureProcessingStatus = SubmitterIsOutsideSubmissionWindow

    submitter.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case SignatureFromRemote(_) =>
          sender ! signatureProcessingStatusRes
        case msg =>
          Assert.fail(s"Unexpected message retrieved: $msg")
      }
      TestActor.KeepRunning
    })

    val certificateSignaturesManagerRef: TestActorRef[CertificateSignaturesManager] = TestActorRef(
      Props(new CertificateSignaturesManager(networkControllerRef, submitterRef, params, networkSettings)))

    val peer: ConnectedPeer = mock[ConnectedPeer]
    val certificateSignaturesSpec = new CertificateSignaturesSpec(pubKeysNumber)
    val messageToSign: Array[Byte] = FieldElementFixture.generateFieldElement()
    val keyGenerator = SchnorrKeyGenerator.getInstance()
    val secret = keyGenerator.generateSecret("seed1".getBytes())
    val signaturesInfo = (0 until pubKeysNumber).map(idx => CertificateSignatureInfo(idx, secret.sign(messageToSign)))
    val knownSignatures = KnownSignatures(messageToSign, signaturesInfo)


    // Test 1: CertificateSignaturesSpec arrives when Submitter is outside the Submission window -> no actions expected
    signatureProcessingStatusRes = SubmitterIsOutsideSubmissionWindow
    certificateSignaturesManagerRef ! DataFromPeer(certificateSignaturesSpec, knownSignatures, peer)
    // Expect no actions
    networkController.expectNoMessage(timeout.duration)


    // Test 2: CertificateSignaturesSpec arrives with all known signatures -> no actions expected
    signatureProcessingStatusRes = KnownSignature
    certificateSignaturesManagerRef ! DataFromPeer(certificateSignaturesSpec, knownSignatures, peer)
    // Expect no actions
    networkController.expectNoMessage(timeout.duration)


    // Test 3: CertificateSignaturesSpec arrives message to sign from another chain -> no actions expected
    signatureProcessingStatusRes = DifferentMessageToSign
    certificateSignaturesManagerRef ! DataFromPeer(certificateSignaturesSpec, knownSignatures, peer)
    // Expect no actions
    networkController.expectNoMessage(timeout.duration)


    // Test 4: CertificateSignaturesSpec arrives with invalid pubic key index -> peer penalizing expected
    Mockito.when(peer.connectionId).thenAnswer(_ => {
      ConnectionId(new InetSocketAddress(0), mock[InetSocketAddress], mock[ConnectionDirection])
    })

    signatureProcessingStatusRes = InvalidPublicKeyIndex
    certificateSignaturesManagerRef ! DataFromPeer(certificateSignaturesSpec, knownSignatures, peer)
    // Expect for penalty message
    var penalizeMsg: PenalizePeer = networkController.expectMsgClass(timeout.duration, classOf[PenalizePeer])
    assertEquals("Different remote address expected.", peer.connectionId.remoteAddress, penalizeMsg.address)
    assertEquals("Different penalty type expected.", PenaltyType.MisbehaviorPenalty, penalizeMsg.penaltyType)
    // no other messages
    networkController.expectNoMessage(timeout.duration)


    // Test 5: CertificateSignaturesSpec arrives with invalid signature -> peer penalizing expected
    signatureProcessingStatusRes = InvalidSignature
    certificateSignaturesManagerRef ! DataFromPeer(certificateSignaturesSpec, knownSignatures, peer)
    // Expect for penalty message
    penalizeMsg = networkController.expectMsgClass(timeout.duration, classOf[PenalizePeer])
    assertEquals("Different remote address expected.", peer.connectionId.remoteAddress, penalizeMsg.address)
    assertEquals("Different penalty type expected.", PenaltyType.MisbehaviorPenalty, penalizeMsg.penaltyType)
    // no other messages
    networkController.expectNoMessage(timeout.duration)


    // Test 6: CertificateSignaturesSpec arrives with 2 new signatures -> signature broadcasting event with 2 sigs expected
    val newSignaturesNumber = 2
    var count: Int = 0
    submitter.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case SignatureFromRemote(_) =>
          count += 1
          if(count <= newSignaturesNumber)
            sender ! ValidSignature
          else
            sender ! KnownSignature
        case _ =>
          Assert.fail("Unexpected message retrieved.")
      }
      TestActor.KeepRunning
    })

    certificateSignaturesManagerRef ! DataFromPeer(certificateSignaturesSpec, knownSignatures, peer)
    // Expect an answer to be send back to the peer
    val msg: SendToNetwork = networkController.expectMsgClass(timeout.duration, classOf[SendToNetwork])

    // Check broadcast to all except the peer
    msg.sendingStrategy match {
      case BroadcastExceptOf(peers) =>
        assertEquals("Different exception list found.", peers, Seq(peer))
      case other =>
        Assert.fail(s"Broadcast strategy expected, but found ${other.getClass}")
    }

    assertTrue(s"CertificateSignaturesSpec message spec expected, but found ${msg.message.spec.getClass}",
      msg.message.spec.isInstanceOf[CertificateSignaturesSpec])

    msg.message.input match {
      case Right(knownSignatures: KnownSignatures) =>
        assertArrayEquals("Invalid message to sign.", messageToSign, knownSignatures.messageToSign)
        assertEquals("Different known signatures entries found.", newSignaturesNumber, knownSignatures.signaturesInfo.size)
        assertEquals("Different signatures indexes found.", signaturesInfo.take(newSignaturesNumber), knownSignatures.signaturesInfo)
      case _ =>
        Assert.fail("Invalid message data")
    }
  }

  @Test
  def tryToSendGetCertificateSignatures(): Unit = {
    val networkController = TestProbe()
    val networkControllerRef: ActorRef = networkController.ref

    val submitter = TestProbe()
    val submitterRef: ActorRef = submitter.ref

    val certificateSignaturesManagerRef: TestActorRef[CertificateSignaturesManager] = TestActorRef(
      Props(new CertificateSignaturesManager(networkControllerRef, submitterRef, params, networkSettings)))


    var statusOpt: Option[SignaturesStatus] = None

    submitter.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetSignaturesStatus =>
          sender ! statusOpt
        case _ =>
          Assert.fail("Unexpected message retrieved.")
      }
      TestActor.KeepRunning
    })

    // Decrease sync interval to speed up the test.

    // Send initialization event
    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    // Expect for specs registration.
    networkController.expectMsgClass(timeout.duration, classOf[RegisterMessageSpecs])

    // Test 1: Try to send signatures info attempt must be skipped because Submitter is outside the submission window.
    certificateSignaturesManagerRef ! TryToSendGetCertificateSignatures
    networkController.expectNoMessage(timeout.duration)


    // Test 2: Try to send signatures info attempt must succeed if Submitter is inside the submission window.
    val referencedEpoch: Int = 10
    val messageToSign: Array[Byte]= FieldElementFixture.generateFieldElement()
    val keyGenerator = SchnorrKeyGenerator.getInstance()
    val secret = keyGenerator.generateSecret("seed1".getBytes())
    var knownIndexes = Seq(0, 2, 4)
    var knownSigs: ArrayBuffer[CertificateSignatureInfo] = ArrayBuffer(knownIndexes.map(idx => CertificateSignatureInfo(idx, secret.sign(messageToSign))) : _*)

    statusOpt = Some(SignaturesStatus(referencedEpoch, messageToSign, knownSigs))

    certificateSignaturesManagerRef ! TryToSendGetCertificateSignatures

    // Expect network broadcast event
    val msg: SendToNetwork = networkController.expectMsgClass(timeout.duration, classOf[SendToNetwork])

    msg.sendingStrategy match {
      case SendToRandom => // fine -> do nothing
      case other =>
        Assert.fail(s"SendToRandom strategy expected, but found ${other.getClass}")
    }

    assertTrue(s"GetCertificateSignaturesSpec message spec expected, but found ${msg.message.spec.getClass}",
      msg.message.spec.isInstanceOf[GetCertificateSignaturesSpec])

    msg.message.input match {
      case Right(invData: InvUnknownSignatures) =>
        val expectedUnknownIndexes = (0 until pubKeysNumber).filterNot(idx => knownIndexes.contains(idx))
        assertEquals("Different unknown signatures indexes found.", expectedUnknownIndexes, invData.indexes)
      case _ =>
        Assert.fail("Invalid message data")
    }


    // Test 3: Try to send signatures info attempt must be skipped if all the signatures are already known.
    knownIndexes = 0 until pubKeysNumber
    knownSigs = ArrayBuffer(knownIndexes.map(idx => CertificateSignatureInfo(idx, secret.sign(messageToSign))) : _*)

    statusOpt = Some(SignaturesStatus(referencedEpoch, messageToSign, knownSigs))

    certificateSignaturesManagerRef ! TryToSendGetCertificateSignatures
    networkController.expectNoMessage(timeout.duration)
  }
}
