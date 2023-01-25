package com.horizen.certificatesubmitter


import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.api.http.client.SecureEnclaveApiClient
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.certificatesubmitter.CertificateSubmitter._
import com.horizen.certificatesubmitter.dataproof.CertificateData
import com.horizen.certificatesubmitter.strategies._
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.cryptolibprovider.utils.{CircuitTypes, FieldElementUtils}
import com.horizen.fork.ForkManager
import com.horizen.mainchain.api.{CertificateRequestCreator, MainchainNodeCertificateApi, SendCertificateRequest}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.SchnorrSecret
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.BytesUtils
import com.horizen.websocket.client.MainchainNodeChannel
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
                           secureEnclaveApiClient: SecureEnclaveApiClient,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeCertificateApi,
                           submissionStrategy: CertificateSubmissionStrategy,
                           keyRotationStrategy: CircuitStrategy[T])
                          (implicit ec: ExecutionContext) extends Actor with Timers with ScorexLogging {

  import CertificateSubmitter.InternalReceivableMessages._
  import CertificateSubmitter.ReceivableMessages._
  import CertificateSubmitter.Timers._

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  private var provingFileAbsolutePath: String = _

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
    loadProvingFilePath()
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
              // Nothing changed -> do nothing
              // Note: applicable only to non-ceasing sidechains
              case Some(status) if status.referencedEpoch == submissionWindowStatus.referencedWithdrawalEpochNumber => // Nothing changes -> do nothing
              case _ => // Case None or Some(status) for newer referencedEpoch
                val referencedWithdrawalEpochNumber = submissionWindowStatus.referencedWithdrawalEpochNumber
                getMessageToSign(referencedWithdrawalEpochNumber) match {
                  case Success(messageToSign) =>
                    signaturesStatus = Some(SignaturesStatus(referencedWithdrawalEpochNumber, messageToSign, ArrayBuffer()))

                    // Try to calculate signatures if signing is enabled
                    if (certificateSigningEnabled) {
                      calculateSignatures(messageToSign, referencedWithdrawalEpochNumber) match {
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
          }
          else {
            if (timers.isTimerActive(CertificateGenerationTimer)) {
              timers.cancel(CertificateGenerationTimer)
              log.info("Cancel the scheduled Certificate generation due to the Submission Window end")
              context.system.eventStream.publish(CertificateSubmissionStopped)
            }
            signaturesStatus = None
          }

        case Failure(exception)
        =>
          log.warn(s"Unexpected behavior on SemanticallySuccessfulModifier($block) while calculating SubmissionWindowStatus.", exception)
      }
  }

  private def getMessageToSign(referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(view => keyRotationStrategy.getMessageToSign(view, referencedWithdrawalEpochNumber)),
      timeoutDuration).asInstanceOf[Try[Array[Byte]]].get
  }

  // Take withdrawal epoch info for block from the History.
  // Note: We can't rely on `State.getWithdrawalEpochInfo`, because it shows the tip info,
  // but the older block may being applied at the moment.
  private def getSubmissionWindowStatus(block: SidechainBlock): Try[SubmissionWindowStatus] = Try {
    def getStatus(sidechainNodeView: View): SubmissionWindowStatus = {
      submissionStrategy.getStatus(sidechainNodeView, block)
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getStatus), timeoutDuration).asInstanceOf[SubmissionWindowStatus]
  }

  private[certificatesubmitter] def getFtMinAmount(consensusEpochNumber: Int): Long = {
    ForkManager.getSidechainConsensusEpochFork(consensusEpochNumber).ftMinAmount
  }

  protected def calculateSignatures(messageToSign: Array[Byte], referencedWithdrawalEpochNumber: Int): Try[Seq[CertificateSignatureInfo]] = Try {
    def getSignersPrivateKeys(sidechainNodeView: View): Seq[CertificateSignatureInfo] = {
      val wallet = sidechainNodeView.vault
      val signersPublicKeys = sidechainNodeView.state.certifiersKeys(referencedWithdrawalEpochNumber - 1) match {
        case Some(actualKeys) => actualKeys.signingKeys
        case None => params.signersPublicKeys
      }
      val privateKeysWithIndexes = signersPublicKeys.map(signerPublicKey => wallet.secret(signerPublicKey)).zipWithIndex.filter(_._1.isDefined).map {
        case (secretOpt, idx) => (secretOpt.get.asInstanceOf[SchnorrSecret], idx)
      }

      val remainingKeys = signersPublicKeys.zipWithIndex.filterNot(key_index => privateKeysWithIndexes.map(_._2).contains(key_index._2))
      (signaturesFromEnclave(messageToSign, remainingKeys)
        ++ privateKeysWithIndexes.map {
        case (secret, pubKeyIndex) => CertificateSignatureInfo(pubKeyIndex, secret.sign(messageToSign))
      })
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getSignersPrivateKeys), timeoutDuration)
      .asInstanceOf[Seq[CertificateSignatureInfo]]
  }

  def signaturesFromEnclave(messageToSign: Array[Byte], indexedPublicKeys: Seq[(SchnorrProposition, Int)]): Seq[CertificateSignatureInfo] = {
    if (!secureEnclaveApiClient.isEnabled) return Seq()

    val signaturesFromEnclaveFuture = secureEnclaveApiClient.listPublicKeys()
      .map(managedKeys => indexedPublicKeys.filter(key_index => managedKeys.contains(key_index._1)))
      .map(_.map(secureEnclaveApiClient.signWithEnclave(messageToSign, _)))
      .map(Future.sequence(_))
      .flatten

    Try(Await.result(signaturesFromEnclaveFuture, timeoutDuration).flatten)
      .getOrElse(Seq())
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

  private def tryToScheduleCertificateGeneration: Receive = {
    // Do nothing if submitter is disabled or submission is in progress (scheduled or generating the proof)
    case TryToScheduleCertificateGeneration if !submitterEnabled ||
      certGenerationState || timers.isTimerActive(CertificateGenerationTimer) => // do nothing

    // In other case check and schedule
    case TryToScheduleCertificateGeneration =>
      signaturesStatus match {
        case Some(status) =>
          if (submissionStrategy.checkQuality(status)) {
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
          if (submissionStrategy.checkQuality(status)) {
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
      if (!submitterEnabled) {
        // If previous value was `false` -> try to schedule cert generation
        // Maybe we have enough signatures at the moment
        self ! TryToScheduleCertificateGeneration
      }
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
  def props(settings: SidechainSettings,
            sidechainNodeViewHolderRef: ActorRef,
            secureEnclaveApiClient: SecureEnclaveApiClient,
            params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props = {
    val submissionStrategy: CertificateSubmissionStrategy = if (params.isNonCeasing) {
      new NonCeasingSidechain(params)
    } else {
      new CeasingSidechain(mainchainChannel, params)
    }
    val keyRotationStrategy = if (params.circuitType.equals(CircuitTypes.NaiveThresholdSignatureCircuit)) {
      new WithoutKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.sigProofThresholdCircuitFunctions)
    } else {
      new WithKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation)
    }
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy))
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox")
  }

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"), name)
}

