package com.horizen.certificatesubmitter


import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.certificatesubmitter.CertificateSubmitter._
import com.horizen.certificatesubmitter.dataproof.{CertificateData, CertificateDataWithoutKeyRotation}
import com.horizen.certificatesubmitter.strategies.{KeyRotationStrategy, WithKeyRotationStrategy, WithoutKeyRotationStrategy}
import com.horizen.cryptolibprovider.utils.{FieldElementUtils, TypeOfCircuit}
import com.horizen.certificatesubmitter.dataproof.DataForProofGeneration
import com.horizen.certificatesubmitter.submitters.{ThresholdSigCircuitSubmitter, ThresholdSigCircuitSubmitterWithKeyRotation}
import com.horizen.cryptolibprovider.{CryptoLibProvider, FieldElementUtils}
import com.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.secret.SchnorrSecret
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import com.horizen.websocket.client.{MainchainNodeChannel, WebsocketErrorResponseException, WebsocketInvalidErrorMessageException}
import scorex.util.ScorexLogging
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier

import java.io.File
import java.util
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
class CertificateSubmitter[T <: CertificateData](settings: SidechainSettings,
                                                 sidechainNodeViewHolderRef: ActorRef,
                                                 params: NetworkParams,
                                                 mainchainChannel: MainchainNodeChannel,
                                                 keyRotationStrategy: KeyRotationStrategy[T])
                                                (implicit ec: ExecutionContext) extends Actor with Timers with ScorexLogging {

  import CertificateSubmitter.InternalReceivableMessages._
  import CertificateSubmitter.ReceivableMessages._
  import CertificateSubmitter.Timers._

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  private[certificatesubmitter] var submitterEnabled: Boolean = settings.withdrawalEpochCertificateSettings.submitterIsEnabled
  private[certificatesubmitter] var certificateSigningEnabled: Boolean = settings.withdrawalEpochCertificateSettings.certificateSigningIsEnabled

  private[certificatesubmitter] var signaturesStatus: Option[SignaturesStatus] = None

  private[certificatesubmitter] var certGenerationState: Boolean = false
  private val certificateFee = if (settings.withdrawalEpochCertificateSettings.certificateAutomaticFeeComputation) None else Some(settings.withdrawalEpochCertificateSettings.certificateFee)

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    context.system.eventStream.subscribe(self, SidechainAppEvents.SidechainApplicationStart.getClass)

    context.system.eventStream.subscribe(self, CertificateSubmissionStarted.getClass)
    context.system.eventStream.subscribe(self, CertificateSubmissionStopped.getClass)

    context.become(initialization)
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.error("CertificateSubmitter was restarted because of: ", reason)
    // Switch to the working cycle, otherwise Submitter will stuck on initialization phase.
//    loadProvingFilePath()
    context.become(workingCycle)
  }

  override def postStop(): Unit = {
    log.debug("Certificate Submitter actor is stopping...")
    super.postStop()
    if (timers.isTimerActive(CertificateGenerationTimer)) {
      context.system.eventStream.publish(CertificateSubmissionStopped)
    }
  }

  override def receive: Receive = reportStrangeInput

  private def reportStrangeInput: Receive = {
    case nonsense =>
      log.warn(s"Strange input for CertificateSubmitter: $nonsense")
  }

  private[certificatesubmitter] def initialization: Receive = {
    checkSubmitter orElse reportStrangeInput
  }

  private[certificatesubmitter] def workingCycle: Receive = {
    onCertificateSubmissionEvent orElse
      newBlockArrived orElse
      locallyGeneratedSignature orElse
      signatureFromRemote orElse
      tryToScheduleCertificateGeneration orElse
      tryToGenerateCertificate orElse
      getCertGenerationState orElse
      getSignaturesStatus orElse
      submitterStatus orElse
      signerStatus orElse
      reportStrangeInput
  }

  protected def checkSubmitter: Receive = {
    case SidechainAppEvents.SidechainApplicationStart =>
      val checkAsFuture = (sidechainNodeViewHolderRef ? GetDataFromCurrentView(checkSubmitterMessage)).asInstanceOf[Future[Try[Unit]]]
      checkAsFuture.onComplete {
        case Success(Success(_)) =>
          log.info(s"Backward transfer certificate submitter was successfully started.")
          context.become(workingCycle)

        case Success(Failure(ex)) =>
          log.error("Backward transfer certificate submitter failed to start due:" + EOL + ex)
          context.stop(self)

        case Failure(ex) =>
          log.error("Failed to check backward transfer certificate submitter due:" + EOL + ex)
          context.stop(self)
      }
  }

  private def checkSubmitterMessage(sidechainNodeView: View): Try[Unit] = Try {
    val actualSysDataConstant = params.calculatedSysDataConstant
    val expectedSysDataConstantOpt = getSidechainCreationTransaction(sidechainNodeView.history).getGenSysConstantOpt.asScala

    if (expectedSysDataConstantOpt.isEmpty || actualSysDataConstant.deep != expectedSysDataConstantOpt.get.deep) {
      throw new IllegalStateException("Incorrect configuration for backward transfer, expected SysDataConstant " +
        s"'${BytesUtils.toHexString(expectedSysDataConstantOpt.getOrElse(Array.emptyByteArray))}' but actual is '${BytesUtils.toHexString(actualSysDataConstant)}'")
    }

    loadProvingFilePath()
  }

  private def loadProvingFilePath(): Unit = {
    if (params.certProvingKeyFilePath.isEmpty) {
      throw new IllegalStateException(s"Proving key file name is not set")
    }

    val provingFile: File = new File(params.certProvingKeyFilePath)
    if (!provingFile.canRead) {
      throw new IllegalStateException(s"Proving key file at path ${provingFile.getAbsolutePath} is not exist or can't be read")
    }
    else {
      provingFileAbsolutePath = provingFile.getAbsolutePath
      log.debug(s"Found proving key file at location: $provingFileAbsolutePath")
    }
  }
  private def getSidechainCreationTransaction(history: SidechainHistory): SidechainCreation = {
    val mainchainReference: MainchainBlockReference = history
      .getMainchainBlockReferenceByHash(params.genesisMainchainBlockHash).asScala
      .getOrElse(throw new IllegalStateException("No mainchain creation transaction in history"))

    mainchainReference.data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs.get(0).asInstanceOf[SidechainCreation]
  }

  private def onCertificateSubmissionEvent: Receive = {
    case CertificateSubmissionStarted =>
      certGenerationState = true
      log.debug(s"Certificate generation is started.")

    case CertificateSubmissionStopped =>
      certGenerationState = false
      log.debug(s"Certificate generation is finished.")
  }

  private def getCertGenerationState: Receive = {
    case GetCertificateGenerationState =>
      sender() ! certGenerationState
  }

  private def getSignaturesStatus: Receive = {
    case GetSignaturesStatus =>
      sender() ! signaturesStatus
  }

  private def newBlockArrived: Receive = {
    case SemanticallySuccessfulModifier(block: SidechainBlock) =>
      getSubmissionWindowStatus(block) match {
        case Success(submissionWindowStatus) =>
          if (submissionWindowStatus.isInWindow) {
            signaturesStatus match {
              case Some(_) => // do nothing
              case None =>
                val referencedWithdrawalEpochNumber = submissionWindowStatus.withdrawalEpochInfo.epoch - 1

                val messageToSignTry = Try {

                  Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(view => keyRotationStrategy.getMessageToSign(view, referencedWithdrawalEpochNumber)),
                    timeoutDuration).asInstanceOf[Try[Array[Byte]]].get
                }

                messageToSignTry match {
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
  private def getSubmissionWindowStatus(block: SidechainBlock): Try[SubmissionWindowStatus] = Try {
    def getStatus(sidechainNodeView: View): SubmissionWindowStatus = {
      val withdrawalEpochInfo: WithdrawalEpochInfo = sidechainNodeView.history.blockInfoById(block.id).withdrawalEpochInfo
      SubmissionWindowStatus(withdrawalEpochInfo, WithdrawalEpochUtils.inSubmitCertificateWindow(withdrawalEpochInfo, params))
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getStatus), timeoutDuration).asInstanceOf[SubmissionWindowStatus]
  }

  private def calculateSignatures(messageToSign: Array[Byte]): Try[Seq[CertificateSignatureInfo]] = Try {
    def getSignersPrivateKeys(sidechainNodeView: View): Seq[(SchnorrSecret, Int)] = {
      val wallet = sidechainNodeView.vault
      params.signersPublicKeys.map(signerPublicKey => wallet.secret(signerPublicKey)).zipWithIndex.filter(_._1.isDefined).map {
        case (secretOpt, idx) => (secretOpt.get.asInstanceOf[SchnorrSecret], idx)
      }
    }

    val privateKeysWithIndexes = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getSignersPrivateKeys), timeoutDuration)
      .asInstanceOf[Seq[(SchnorrSecret, Int)]]

    privateKeysWithIndexes.map {
      case (secret, pubKeyIndex) => CertificateSignatureInfo(pubKeyIndex, secret.sign(messageToSign))
    }
  }

  private def locallyGeneratedSignature: Receive = {
    case LocallyGeneratedSignature(info: CertificateSignatureInfo) =>
      signaturesStatus match {
        case Some(status) =>
          log.debug(s"Locally generated Certificate signature for pub key index ${
            info.pubKeyIndex
          } retrieved.")
          if (status.knownSigs.exists(item => item.pubKeyIndex == info.pubKeyIndex))
            log.error("Locally generated signature already presents")
          else {
            status.knownSigs.append(info)
            val infoToRemote = CertificateSignatureFromRemoteInfo(info.pubKeyIndex, status.messageToSign, info.signature)
            context.system.eventStream.publish(CertificateSubmitter.BroadcastLocallyGeneratedSignature(infoToRemote))
            self ! TryToScheduleCertificateGeneration
          }

        case None => log.error("Locally generated signature was retrieved out of the certificate submission window.")
      }
  }

  private def signatureFromRemote: Receive = {
    case SignatureFromRemote(remoteSigInfo: CertificateSignatureFromRemoteInfo) =>
      signaturesStatus match {
        case Some(status) =>
          log.debug(s"Certificate signature for pub key index ${
            remoteSigInfo.pubKeyIndex
          } retrieved from remote.")
          if (!util.Arrays.equals(status.messageToSign, remoteSigInfo.messageToSign)) {
            sender() ! DifferentMessageToSign
          } else if (remoteSigInfo.pubKeyIndex < 0 || remoteSigInfo.pubKeyIndex >= params.signersPublicKeys.size) {
            sender() ! InvalidPublicKeyIndex
          } else if (!remoteSigInfo.signature.isValid(params.signersPublicKeys(remoteSigInfo.pubKeyIndex), remoteSigInfo.messageToSign)) {
            sender() ! InvalidSignature
          } else if (!status.knownSigs.exists(item => item.pubKeyIndex == remoteSigInfo.pubKeyIndex)) {
            status.knownSigs.append(CertificateSignatureInfo(remoteSigInfo.pubKeyIndex, remoteSigInfo.signature))
            sender() ! ValidSignature
            self ! TryToScheduleCertificateGeneration
          } else {
            // Remote info has valid message to sign but the Signature record for the PubKey is known
            sender() ! KnownSignature
          }
        case None =>
          sender() ! SubmitterIsOutsideSubmissionWindow
      }
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

  private def tryToScheduleCertificateGeneration: Receive = {
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

  private def tryToGenerateCertificate: Receive = {
    case TryToGenerateCertificate => Try {
      signaturesStatus match {
        case Some(status) =>
          // Check quality again, in case better Certificate appeared.
          if (checkQuality(status)) {
            def getProofGenerationData(sidechainNodeView: View): CertificateData = keyRotationStrategy.buildCertificateData(sidechainNodeView, status)

            val dataForProofGeneration = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getProofGenerationData), timeoutDuration)
              .asInstanceOf[T]
            log.debug(s"Retrieved data for certificate proof calculation: $dataForProofGeneration")

            // Run the time consuming part of proof generation and certificate submission in a background
            // to unlock the Actor message queue for another requests.
            new Thread(new Runnable() {
              override def run(): Unit = {
                var proofWithQuality: com.horizen.utils.Pair[Array[Byte], java.lang.Long] = null
                try {
                  proofWithQuality = keyRotationStrategy.generateProof(dataForProofGeneration, provingFileAbsolutePath)
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
                  dataForProofGeneration.getCustomFields,
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

  def submitterStatus: Receive = {
    case EnableSubmitter =>
      submitterEnabled = true
    case DisableSubmitter =>
      submitterEnabled = false
    case IsSubmitterEnabled =>
      sender() ! submitterEnabled
  }

  def signerStatus: Receive = {
    case EnableCertificateSigner =>
      // Next signing attempt will be at the beginning of the next submission window.
      certificateSigningEnabled = true
    case DisableCertificateSigner =>
      certificateSigningEnabled = false
    case IsCertificateSigningEnabled =>
      sender() ! certificateSigningEnabled
  }
}

object CertificateSubmitter {
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

object CertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props = {
    val keyRotationStrategy = if (params.typeOfCircuit == 0) {
      new WithoutKeyRotationStrategy(settings, params)
    } else {
      new WithKeyRotationStrategy(settings, params)
    }
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel, keyRotationStrategy))
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox")
  }

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"), name)
}

