package com.horizen.certificatesubmitter


import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock, WithdrawalEpochCertificate, WithdrawalEpochCertificateSerializer}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter._
import com.horizen.cryptolibprovider.{CryptoLibProvider, FieldElementUtils}
import com.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.SchnorrSecret
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import com.horizen.websocket.client.{MainchainNodeChannel, WebsocketErrorResponseException, WebsocketInvalidErrorMessageException}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import scorex.util.ScorexLogging

import java.io.File
import java.util
import java.util.Optional
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.compat.Platform.EOL
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}

/**
 * Certificate submitter listens to the State changes and takes care of of certificate signatures managing (generation, storing and broadcasting)
 * If `submitterEnabled` is `true`, it will try to generate and send the Certificate to MC node in case the proper amount of signatures were collected.
 * Must be singleton.
 */
class NonCeasingCertificateSubmitter(settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel)
                          (implicit ec: ExecutionContext) extends AbstractCertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel)
                          with Timers with ScorexLogging {

  import AbstractCertificateSubmitter.InternalReceivableMessages._
  import AbstractCertificateSubmitter.ReceivableMessages._
  import AbstractCertificateSubmitter.Timers._

  private[certificatesubmitter] def newBlockArrived: Receive = {
    case SemanticallySuccessfulModifier(block: SidechainBlock) =>
      getSubmissionWindowStatus(block) match {
        case Success(submissionWindowStatus) =>
          if (submissionWindowStatus.isInWindow) {
            signaturesStatus match {
              case Some(status) if (status.referencedEpoch == submissionWindowStatus.referencedEpochNumber) => // Nothing changes
              case _ =>
                val referencedWithdrawalEpochNumber = submissionWindowStatus.referencedEpochNumber
                getMessageToSign(referencedWithdrawalEpochNumber) match {
                  case Success(messageToSign) =>
                    signaturesStatus = Some(SignaturesStatus(referencedWithdrawalEpochNumber, messageToSign, ArrayBuffer()))

                    // Try to calculate signatures if signing is enabled
                    if (certificateSigningEnabled) {
                      calculateSignatures(messageToSign) match {
                        case Success(signaturesInfo) =>
                          signaturesInfo.foreach(sigInfo => {
                            self ! LocallyGeneratedSignature(sigInfo)
                          })
                        case Failure(exception) =>
                          log.warn(s"Unexpected behavior on SemanticallySuccessfulModifier($block) while calculating signatures.", exception)
                          signaturesStatus = None // keep signaturesStatus undefined as before
                      }
                    }
                  case Failure(exception) =>
                    log.warn(s"Unexpected behavior on SemanticallySuccessfulModifier($block) while calculating message to sign.", exception)
                    signaturesStatus = None // keep signaturesStatus undefined as before
                }
            }
          } else {
            if (timers.isTimerActive(CertificateGenerationTimer)) {
              timers.cancel(CertificateGenerationTimer)
              log.info("Cancel the scheduled Certificate generation due to the Submission Window end")
              context.system.eventStream.publish(CertificateSubmissionStopped)
            }
            signaturesStatus = None
          }

        case Failure(exception) =>
          log.warn(s"Unexpected behavior on SemanticallySuccessfulModifier($block) while calculating SubmissionWindowStatus.", exception)
      }
  }

  // Take withdrawal epoch info for block from the History.
  // Note: We can't rely on `State.getWithdrawalEpochInfo`, because it shows the tip info,
  // but the older block may being applied at the moment.
  private[certificatesubmitter] def getSubmissionWindowStatus(block: SidechainBlock): Try[SubmissionWindowStatus] = Try {
    val nonCeasingSubmissionDelay = 1 // TBD length

    def getLastTopQualityCertificate(sidechainNodeView: View, referencedWithrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
      var withdrawalEpoch = referencedWithrawalEpoch
      var certificateOpt: Option[WithdrawalEpochCertificate] = sidechainNodeView.state.certificate(withdrawalEpoch)

      while (certificateOpt.isEmpty && withdrawalEpoch > 0) {
        withdrawalEpoch = withdrawalEpoch - 1
        certificateOpt = sidechainNodeView.state.certificate(withdrawalEpoch)
      }

      certificateOpt
    }

    def getStatus(sidechainNodeView: View): SubmissionWindowStatus = {
      val withdrawalEpochInfo: WithdrawalEpochInfo = sidechainNodeView.history.blockInfoById(block.id).withdrawalEpochInfo
      val lastCertificateOpt = getLastTopQualityCertificate(sidechainNodeView, withdrawalEpochInfo.epoch)
      // TODO Make record to state - lastTopQualityCertificate
      val referencedEpochNumber = lastCertificateOpt.map(_.epochNumber).getOrElse(-1) + 1 // Withdrawal epoch for which certificate needs to be applied

      if (referencedEpochNumber + 1 < withdrawalEpochInfo.epoch) {
        // Submission certificate for the epoch before previous
        SubmissionWindowStatus(referencedEpochNumber, true)
      } else if (referencedEpochNumber + 1 == withdrawalEpochInfo.epoch && withdrawalEpochInfo.lastEpochIndex >= nonCeasingSubmissionDelay) {
        // Submission certificate for the previous epoch
        SubmissionWindowStatus(referencedEpochNumber, true)
      } else {
        // No need to submit certificate
        SubmissionWindowStatus(referencedEpochNumber, false)
      }
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getStatus), timeoutDuration).asInstanceOf[SubmissionWindowStatus]
  }



  private[certificatesubmitter] def tryToGenerateCertificate: Receive = {
    case TryToGenerateCertificate => Try {
      signaturesStatus match {
        case Some(status) =>
          // Check quality again, in case better Certificate appeared.
            def getProofGenerationData(sidechainNodeView: View): DataForProofGeneration = buildDataForProofGeneration(sidechainNodeView, status)

            val dataForProofGeneration = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getProofGenerationData), timeoutDuration)
              .asInstanceOf[DataForProofGeneration]
            log.debug(s"Retrieved data for certificate proof calculation: $dataForProofGeneration")

            // Run the time consuming part of proof generation and certificate submission in a background
            // to unlock the Actor message queue for another requests.
            new Thread(new Runnable() {
              override def run(): Unit = {
                var proofWithQuality: com.horizen.utils.Pair[Array[Byte], java.lang.Long] = null
                try {
                  proofWithQuality = generateProof(dataForProofGeneration)
                } catch {
                  case e: Exception =>
                    log.error("Proof creation failed.", e)
                    context.system.eventStream.publish(CertificateSubmissionStopped)
                    return
                }
                val certificateRequest: SendCertificateRequest = CertificateRequestCreator.create(
                  params.sidechainId,
                  dataForProofGeneration.referencedEpochNumber,
                  dataForProofGeneration.endEpochCumCommTreeHash,
                  proofWithQuality.getKey,
                  proofWithQuality.getValue,
                  dataForProofGeneration.withdrawalRequests,
                  dataForProofGeneration.ftMinAmount,
                  dataForProofGeneration.btrFee,
                  dataForProofGeneration.utxoMerkleTreeRoot,
                  certificateFee,
                  params)

                log.info(s"Backward transfer certificate request was successfully created for epoch number ${
                  certificateRequest.epochNumber
                }, with proof ${
                  BytesUtils.toHexString(proofWithQuality.getKey)
                } with quality ${
                  proofWithQuality.getValue
                } try to send it to mainchain")

                mainchainChannel.sendCertificate(certificateRequest) match {
                  case Success(certificate) =>
                    log.info(s"Backward transfer certificate response had been received. Cert hash = " + BytesUtils.toHexString(certificate.certificateId))

                  case Failure(ex) =>
                    log.error("Creation of backward transfer certificate had been failed.", ex)
                }
                context.system.eventStream.publish(CertificateSubmissionStopped)
              }
            }).start()
        case None => // Can occur while during the random delay the Node went out of the Window.
          log.debug("Can't generate Certificate because of being outside the Certificate submission window.")
          context.system.eventStream.publish(CertificateSubmissionStopped)
      }
    } match {
      case Success(_) =>
      case Failure(exception) =>
        log.error("Certificate creation failed.", exception)
        context.system.eventStream.publish(CertificateSubmissionStopped)
    }
  }

  private[certificatesubmitter] def tryToScheduleCertificateGeneration: Receive = {
    // Do nothing if submitter is disabled or submission is in progress (scheduled or generating the proof)
    case TryToScheduleCertificateGeneration if !submitterEnabled ||
      certGenerationState || timers.isTimerActive(CertificateGenerationTimer) => // do nothing

    // In other case check and schedule
    case TryToScheduleCertificateGeneration =>
      signaturesStatus match {
        case Some(status) => {
            val delay = Random.nextInt(15) + 5 // random delay from 5 to 20 seconds
            log.info(s"Scheduling Certificate generation in $delay seconds")
            timers.startSingleTimer(CertificateGenerationTimer, TryToGenerateCertificate, FiniteDuration(delay, SECONDS))
            context.system.eventStream.publish(CertificateSubmissionStarted)
          }
        case None =>
          log.warn("Trying to schedule certificate generation being outside Certificate submission window.")
      }
  }
}

object NonCeasingCertificateSubmitter {
  // Events:
  sealed trait SubmitterEvent

  // Certificate submission status events
  sealed trait CertificateSubmissionEvent extends SubmitterEvent

  case object CertificateSubmissionStarted extends CertificateSubmissionEvent

  case object CertificateSubmissionStopped extends CertificateSubmissionEvent

  // Certificate signature broadcasting events
  case class BroadcastLocallyGeneratedSignature(info: CertificateSignatureFromRemoteInfo) extends SubmitterEvent


  // Response for SignatureFromRemote message
  sealed trait SignatureProcessingStatus

  case object ValidSignature extends SignatureProcessingStatus

  case object KnownSignature extends SignatureProcessingStatus

  case object DifferentMessageToSign extends SignatureProcessingStatus

  case object InvalidPublicKeyIndex extends SignatureProcessingStatus

  case object InvalidSignature extends SignatureProcessingStatus

  case object SubmitterIsOutsideSubmissionWindow extends SignatureProcessingStatus

  // Data
  private case class SubmissionWindowStatus(withdrawalEpochInfo: WithdrawalEpochInfo, isInWindow: Boolean)

  case class SignaturesStatus(referencedEpoch: Int, messageToSign: Array[Byte], knownSigs: ArrayBuffer[CertificateSignatureInfo])

  case class CertificateSignatureInfo(pubKeyIndex: Int, signature: SchnorrProof)

  case class CertificateSignatureFromRemoteInfo(pubKeyIndex: Int, messageToSign: Array[Byte], signature: SchnorrProof) {
    require(pubKeyIndex >= 0, "pubKeyIndex can't be negative value.")
    require(messageToSign.length == FieldElementUtils.fieldElementLength(), "messageToSign has invalid length")
  }

  case class ObsoleteWithdrawalEpochException(message: String = "", cause: Option[Throwable] = None)
    extends RuntimeException(message, cause.orNull)

  // Internal interface
  private[certificatesubmitter] object Timers {
    object CertificateGenerationTimer
  }

  private[certificatesubmitter] object InternalReceivableMessages {
    case class LocallyGeneratedSignature(info: CertificateSignatureInfo)

    case object TryToScheduleCertificateGeneration

    case object TryToGenerateCertificate

  }

  // Public interface
  object ReceivableMessages {
    case class SignatureFromRemote(remoteSigInfo: CertificateSignatureFromRemoteInfo)

    case object GetCertificateGenerationState

    case object GetSignaturesStatus

    // messages to set/check submitter
    case object EnableSubmitter

    case object DisableSubmitter

    case object IsSubmitterEnabled

    // messages to set/check certificate signer
    case object EnableCertificateSigner

    case object DisableCertificateSigner

    case object IsCertificateSigningEnabled
  }
}

object NonCeasingCertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props =
    Props(new NonCeasingCertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel)).withMailbox("akka.actor.deployment.submitter-prio-mailbox")

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"), name)
}
