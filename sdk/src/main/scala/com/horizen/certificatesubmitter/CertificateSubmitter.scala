package com.horizen.certificatesubmitter


import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter._
import com.horizen.cryptolibprovider.{CryptoLibProvider, FieldElementUtils}
import com.horizen.fork.ForkManager
import com.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.SchnorrSecret
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BytesUtils, TimeToEpochUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
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
class CertificateSubmitter(settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel)
                          (implicit ec: ExecutionContext) extends AbstractCertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel)
                           with Timers with ScorexLogging {

  import AbstractCertificateSubmitter.InternalReceivableMessages._
  import AbstractCertificateSubmitter.Timers._

  private[certificatesubmitter] def newBlockArrived: Receive = {
    case SemanticallySuccessfulModifier(block: SidechainBlock) =>
      getSubmissionWindowStatus(block) match {
        case Success(submissionWindowStatus) =>
          if (submissionWindowStatus.isInWindow) {
            signaturesStatus match {
              case Some(_) => // do nothing
              case None =>
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
    def getStatus(sidechainNodeView: View): SubmissionWindowStatus = {
      val withdrawalEpochInfo: WithdrawalEpochInfo = sidechainNodeView.history.blockInfoById(block.id).withdrawalEpochInfo
      SubmissionWindowStatus(withdrawalEpochInfo.epoch - 1, WithdrawalEpochUtils.inSubmitCertificateWindow(withdrawalEpochInfo, params))
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getStatus), timeoutDuration).asInstanceOf[SubmissionWindowStatus]
  }

  private def getCertificateTopQuality(epoch: Int): Try[Long] = {
    mainchainChannel.getTopQualityCertificates(BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId)))
      .map(topQualityCertificates => {
        (topQualityCertificates.mempoolCertInfo, topQualityCertificates.chainCertInfo) match {
          // case we have mempool cert for the given epoch return its quality.
          case (Some(mempoolInfo), _) if mempoolInfo.epoch == epoch => mempoolInfo.quality
          // case the mempool certificate epoch is a newer than submitter epoch thrown an exception
          case (Some(mempoolInfo), _) if mempoolInfo.epoch > epoch =>
            throw ObsoleteWithdrawalEpochException("Requested epoch " + epoch + " is obsolete. Current epoch is " + mempoolInfo.quality)
          // case we have chain cert for the given epoch return its quality.
          case (_, Some(chainInfo)) if chainInfo.epoch == epoch => chainInfo.quality
          // case the chain certificate epoch is a newer than submitter epoch thrown an exception
          case (_, Some(chainInfo)) if chainInfo.epoch > epoch =>
            throw ObsoleteWithdrawalEpochException("Requested epoch " + epoch + " is obsolete. Current epoch is " + chainInfo.quality)
          // no known certs
          case _ => 0
        }
      })
  }

  private def checkQuality(status: SignaturesStatus): Boolean = {
    if (status.knownSigs.size >= params.signersThreshold) {
      getCertificateTopQuality(status.referencedEpoch) match {
        case Success(currentCertificateTopQuality) =>
          if (status.knownSigs.size > currentCertificateTopQuality)
            return true
        case Failure(e) => e match {
          // May happen if there is a bug on MC side or the SDK code is inconsistent to the MC one.
          case ex: WebsocketErrorResponseException =>
            log.error("Mainchain error occurred while processed top quality certificates request(" + ex + ")")
            // So we don't know the result
            // Return true to keep submitter going and prevent SC ceasing
            return true
          // May happen during node synchronization and node behind for one epoch or more
          case ex: ObsoleteWithdrawalEpochException =>
            log.info("Sidechain is behind the Mainchain(" + ex + ")")
            return false
          // May happen if MC and SDK websocket protocol is inconsistent.
          // Should never happen in production.
          case ex: WebsocketInvalidErrorMessageException =>
            log.error("Mainchain error message is inconsistent to SC implementation(" + ex + ")")
            // So we don't know the result
            // Return true to keep submitter going and prevent SC ceasing
            return true
          // Various connection errors
          case other =>
            log.error("Unable to retrieve actual top quality certificates from Mainchain(" + other + ")")
            return false
        }
      }
    }
    false
  }

  private[certificatesubmitter] def tryToScheduleCertificateGeneration: Receive = {
    // Do nothing if submitter is disabled or submission is in progress (scheduled or generating the proof)
    case TryToScheduleCertificateGeneration if !submitterEnabled ||
      certGenerationState || timers.isTimerActive(CertificateGenerationTimer) => // do nothing

    // In other case check and schedule
    case TryToScheduleCertificateGeneration =>
      signaturesStatus match {
        case Some(status) =>
          if (checkQuality(status)) {
            val delay = Random.nextInt(15) + 5 // random delay from 5 to 20 seconds
            log.info(s"Scheduling Certificate generation in $delay seconds")
            timers.startSingleTimer(CertificateGenerationTimer, TryToGenerateCertificate, FiniteDuration(delay, SECONDS))
            context.system.eventStream.publish(CertificateSubmissionStarted)
          }
        case None =>
          log.warn("Trying to schedule certificate generation being outside Certificate submission window.")
      }
  }

  private[certificatesubmitter] def tryToGenerateCertificate: Receive = {
    case TryToGenerateCertificate => Try {
      signaturesStatus match {
        case Some(status) =>
          // Check quality again, in case better Certificate appeared.
          if (checkQuality(status)) {
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
          } else {
            context.system.eventStream.publish(CertificateSubmissionStopped)
          }
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
}


object CertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props =
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel)).withMailbox("akka.actor.deployment.submitter-prio-mailbox")

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"), name)
}
