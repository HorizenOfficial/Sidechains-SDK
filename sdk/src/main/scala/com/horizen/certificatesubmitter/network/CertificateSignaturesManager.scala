package com.horizen.certificatesubmitter.network

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.SidechainAppEvents
import com.horizen.certificatesubmitter.CertificateSubmitter.ReceivableMessages.{GetSignaturesStatus, SignatureFromRemote}
import com.horizen.certificatesubmitter.CertificateSubmitter.{BroadcastLocallyGeneratedSignature, CertificateSignatureFromRemoteInfo, CertificateSignatureInfo, DifferentMessageToSign, InvalidPublicKeyIndex, InvalidSignature, KnownSignature, SignatureProcessingStatus, SignaturesStatus, SubmitterIsOutsideSubmissionWindow, ValidSignature}
import com.horizen.certificatesubmitter.network.CertificateSignaturesManager.InternalReceivableMessages.TryToSendGetCertificateSignatures
import com.horizen.params.NetworkParams
import scorex.core.network.NetworkController.ReceivableMessages.{PenalizePeer, RegisterMessageSpecs, SendToNetwork}
import scorex.core.network.NetworkControllerSharedMessages.ReceivableMessages.DataFromPeer
import scorex.core.network.{Broadcast, BroadcastExceptOf, ConnectedPeer, SendToPeer, SendToRandom}
import scorex.core.network.message.Message
import scorex.core.network.peer.PenaltyType
import scorex.core.settings.NetworkSettings
import scorex.util.ScorexLogging
import shapeless.syntax.typeable.typeableOps

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.util.control.Breaks.{break, breakable}

/**
 * Certificate signatures manager is a mediator between `CertificateSubmitter` and P2P network.
 * It listens to the Certificate signatures related messages from the network and broadcasting events from Submitter.
 * Manager also takes care of signatures synchronization between the nodes and reacts on any misbehaving activities.
 * Must be singleton.
 */
class CertificateSignaturesManager(networkControllerRef: ActorRef,
                                   certificateSubmitterRef: ActorRef,
                                   params: NetworkParams,
                                   settings: NetworkSettings)
  (implicit ec: ExecutionContext) extends Actor with ScorexLogging
{

  private implicit val timeout: Timeout = Timeout(settings.syncTimeout.getOrElse(5 seconds))
  private var locallyGeneratedSignatureBroadcastingDelay: FiniteDuration = 5 seconds
  private val getCertificateSignaturesInterval: FiniteDuration = 10 seconds

  // It can be no more Certificate signatures than the public keys for the Threshold Signature Circuit
  private val signaturesLimit = params.signersPublicKeys.size
  private val getCertificateSignaturesSpec = new GetCertificateSignaturesSpec(signaturesLimit)
  private val certificateSignaturesSpec = new CertificateSignaturesSpec(signaturesLimit)

  override def preStart: Unit = {
    super.preStart()

    // subscribe on Application Start event to be sure that Submitter itself was initialized.
    context.system.eventStream.subscribe(self, SidechainAppEvents.SidechainApplicationStart.getClass)
  }

  override def receive: Receive = {
    onSidechainApplicationStart orElse
    tryToSendGetCertificateSignatures orElse
    getCertificateSignatures orElse
    certificateSignatures orElse
    broadcastSignature orElse
    reportStrangeInput
  }

  private def reportStrangeInput: Receive = {
    case nonsense =>
      log.warn(s"Strange input for CertificateSignaturesManager: $nonsense")
  }

  protected def onSidechainApplicationStart: Receive = {
    case SidechainAppEvents.SidechainApplicationStart =>
      // Subscribe on the CertificateSubmitter locally generated signature event
      context.system.eventStream.subscribe(self, classOf[BroadcastLocallyGeneratedSignature])

      // Subscribe on the network CertificateSignature specific messages
      networkControllerRef ! RegisterMessageSpecs(Seq(getCertificateSignaturesSpec, certificateSignaturesSpec), self)

      // Schedule a periodic known signatures synchronization
      context.system.scheduler.schedule(getCertificateSignaturesInterval, getCertificateSignaturesInterval)(self ! TryToSendGetCertificateSignatures)
  }

  private def tryToSendGetCertificateSignatures: Receive = {
    case TryToSendGetCertificateSignatures =>
      Try {
        Await.result(certificateSubmitterRef ? GetSignaturesStatus, timeout.duration).asInstanceOf[Option[SignaturesStatus]] match {
          case Some(status) => // node is in the submission window
            // Collect the list of unknown signatures indexes
            val indexes = (0 until signaturesLimit).filterNot(pubKeyIndex => status.knownSigs.exists(info => info.pubKeyIndex == pubKeyIndex))

            if(indexes.nonEmpty) {
              // Request sigs from the random peer.
              val msg = Message[InvUnknownSignatures](getCertificateSignaturesSpec, Right(InvUnknownSignatures(indexes)), None)
              networkControllerRef ! SendToNetwork(msg, SendToRandom)
            }

          case None => // node is not in the submission window -> do nothing
        }
      } match {
        case Success(_) =>
        case Failure(exception) => log.error("Unexpected behavior on TryToSendGetCertificateSignatures.", exception)
      }
  }

  private def getCertificateSignatures: Receive = {
    case DataFromPeer(spec, unknownSignatures: InvUnknownSignatures@unchecked, peer)
        if spec.messageCode == GetCertificateSignaturesSpec.messageCode && unknownSignatures.cast[InvUnknownSignatures].isDefined =>

      Try {
        Await.result(certificateSubmitterRef ? GetSignaturesStatus, timeout.duration).asInstanceOf[Option[SignaturesStatus]] match {
          case Some(status) =>
            // collect the known signatures from the list of the requested unknown ones.
            val knownSignaturesInfo = status.knownSigs.filter(info => unknownSignatures.indexes.contains(info.pubKeyIndex))
            // send the response only if at least one entry was found
            if(knownSignaturesInfo.nonEmpty) {
              // Send the response back to the peer
              val msgData: KnownSignatures = KnownSignatures(status.messageToSign, knownSignaturesInfo)
              val msg = Message[KnownSignatures](certificateSignaturesSpec, Right(msgData), None)
              networkControllerRef ! SendToNetwork(msg, SendToPeer(peer))
            }
          case None => // node is not in the submission window -> do nothing
        }
      } match {
        case Success(_) =>
        case Failure(exception) => log.error("Unexpected behavior while processing get certificate signatures.", exception)
      }
  }

  private def certificateSignatures: Receive = {
    case DataFromPeer(spec, knownSignatures: KnownSignatures@unchecked, peer)
        if spec.messageCode == CertificateSignaturesSpec.messageCode && knownSignatures.cast[KnownSignatures].isDefined =>

      val signaturesToBroadcast: ArrayBuffer[CertificateSignatureInfo] = ArrayBuffer()
      breakable {
        // Try to apply the signatures one by one and break the loop on critical error.
        for (info <- knownSignatures.signaturesInfo) {
          val remoteSigInfo = CertificateSignatureFromRemoteInfo(info.pubKeyIndex, knownSignatures.messageToSign, info.signature)
          Try {
            Await.result(certificateSubmitterRef ? SignatureFromRemote(remoteSigInfo), timeout.duration).asInstanceOf[SignatureProcessingStatus] match {
              case ValidSignature => signaturesToBroadcast.append(info)
              case KnownSignature | SubmitterIsOutsideSubmissionWindow => // do nothing
              case DifferentMessageToSign => // sender refer to different chain -> do nothing
                break
              case InvalidPublicKeyIndex | InvalidSignature =>
                // Sender provided us with invalid data -> Ban the peer
                penalizeMisbehavingPeer(peer)
                signaturesToBroadcast.clear()
                break
            }
          } match {
            case Success(_) =>
            case Failure(exception) => log.error("Unexpected behavior while processing signatures from remote.", exception)
          }
        }
      }

      // Broadcast new signatures to the known peers except the sender
      if (signaturesToBroadcast.nonEmpty) {
        val msgData: KnownSignatures = KnownSignatures(knownSignatures.messageToSign, signaturesToBroadcast)
        val msg = Message[KnownSignatures](certificateSignaturesSpec, Right(msgData), None)
        networkControllerRef ! SendToNetwork(msg, BroadcastExceptOf(Seq(peer)))
      }
  }

  private def broadcastSignature: Receive = {
    case BroadcastLocallyGeneratedSignature(info: CertificateSignatureFromRemoteInfo) =>
      val knownSignatures: KnownSignatures = KnownSignatures(info.messageToSign, Seq(CertificateSignatureInfo(info.pubKeyIndex, info.signature)))
      val msg = Message[KnownSignatures](certificateSignaturesSpec, Right(knownSignatures), None)

      // Broadcast the signature within a constant delay to ensure the neighbor Nodes reached the Submission Window.
      context.system.scheduler.scheduleOnce(locallyGeneratedSignatureBroadcastingDelay)(networkControllerRef ! SendToNetwork(msg, Broadcast))
  }

  private def penalizeMisbehavingPeer(peer: ConnectedPeer): Unit = {
    networkControllerRef ! PenalizePeer(peer.connectionId.remoteAddress, PenaltyType.MisbehaviorPenalty)
  }

  // Tests only
  private[network] def setLocallyGeneratedSignatureBroadcastingDelay(delay: FiniteDuration): Unit = {
    locallyGeneratedSignatureBroadcastingDelay = delay
  }
}

object CertificateSignaturesManager {
  private[network] object InternalReceivableMessages {
    case object TryToSendGetCertificateSignatures
  }
}

object CertificateSignaturesManagerRef {
  def props(networkControllerRef: ActorRef, certificateSubmitterRef: ActorRef,
            params: NetworkParams, settings: NetworkSettings)(implicit ec: ExecutionContext): Props =
    Props(new CertificateSignaturesManager(networkControllerRef, certificateSubmitterRef, params, settings))

  def apply(networkControllerRef: ActorRef, certificateSubmitterRef: ActorRef,
            params: NetworkParams, settings: NetworkSettings)(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(networkControllerRef, certificateSubmitterRef, params, settings))

  def apply(name: String, networkControllerRef: ActorRef, certificateSubmitterRef: ActorRef,
            params: NetworkParams, settings: NetworkSettings)(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(networkControllerRef, certificateSubmitterRef, params, settings), name)
}
