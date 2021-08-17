package com.horizen.certificatesubmitter


import java.io.File
import java.util.Optional
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.{CertificateSignatureFromRemoteInfo, CertificateSignatureInfo, CertificateSubmissionStarted, CertificateSubmissionStopped, DifferentMessageToSign, InvalidSignature, SignaturesStatus, SubmissionWindowStatus, SubmitterIsOutsideSubmissionWindow, ValidSignature}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.librustsidechains.FieldElement
import com.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.SchnorrSecret
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import com.horizen.websocket.MainchainNodeChannel
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import scorex.util.ScorexLogging

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.compat.Platform.EOL
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class CertificateSubmitter(settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel)
  (implicit ec: ExecutionContext) extends Actor with Timers with ScorexLogging
{
  import CertificateSubmitter.InternalReceivableMessages._
  import CertificateSubmitter.ReceivableMessages._
  import CertificateSubmitter.Timers._

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  private var provingFileAbsolutePath: String = _

  private val submitterEnabled: Boolean = settings.withdrawalEpochCertificateSettings.submitterIsEnabled // todo: make updatable via API
  private val certificateSigningEnabled: Boolean = true // todo: update conf file, update API to change the actual value

  private var signaturesStatus: Option[SignaturesStatus] = None

  private var certGenerationState: Boolean = false

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    context.system.eventStream.subscribe(self, SidechainAppEvents.SidechainApplicationStart.getClass)

    context.system.eventStream.subscribe(self, CertificateSubmissionStarted.getClass)
    context.system.eventStream.subscribe(self, CertificateSubmissionStopped.getClass)

    context.become(initialization)
  }

  override def aroundPostStop(): Unit = {
    if(timers.isTimerActive(CertificateGenerationTimer)) {
      context.system.eventStream.publish(CertificateSubmissionStopped)
    }
    super.aroundPostStop()
  }

  override def receive: Receive = reportStrangeInput

  private def reportStrangeInput: Receive = {
    case nonsense =>
      log.warn(s"Strange input for CertificateSubmitter: $nonsense")
  }

  private def initialization: Receive = {
    checkSubmitter orElse reportStrangeInput
  }

  private def workingCycle: Receive = {
    onCertificateSubmissionEvent orElse
    newBlockArrived orElse
    locallyGeneratedSignature orElse
    signatureFromRemote orElse
    tryToScheduleCertificateGeneration orElse
    tryToGenerateCertificate orElse
    getCertGenerationState orElse
    reportStrangeInput
  }

  protected def checkSubmitter: Receive = {
    case SidechainAppEvents.SidechainApplicationStart =>
      val checkAsFuture = (sidechainNodeViewHolderRef ? GetDataFromCurrentView(checkSubmitterMessage)).asInstanceOf[Future[Try[Unit]]]
      checkAsFuture.onComplete{
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

    if(expectedSysDataConstantOpt.isEmpty || actualSysDataConstant.deep != expectedSysDataConstantOpt.get.deep) {
      throw new IllegalStateException("Incorrect configuration for backward transfer, expected SysDataConstant " +
        s"'${BytesUtils.toHexString(expectedSysDataConstantOpt.getOrElse(Array.emptyByteArray))}' but actual is '${BytesUtils.toHexString(actualSysDataConstant)}'")
    }

    if (params.provingKeyFilePath.isEmpty) {
      throw new IllegalStateException(s"Proving key file name is not set")
    }

    val provingFile: File = new File(params.provingKeyFilePath)
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

  private def newBlockArrived: Receive = {
    case SemanticallySuccessfulModifier(_: SidechainBlock) =>
      val submissionWindowStatus: SubmissionWindowStatus = getSubmissionWindowStatus
      if(submissionWindowStatus.isInWindow) {
        signaturesStatus match {
          case Some(_) => // do nothing
          case None =>
            val referencedWithdrawalEpochNumber = submissionWindowStatus.withdrawalEpochInfo.epoch - 1
            val messageToSign = getMessageToSign(referencedWithdrawalEpochNumber)
            signaturesStatus = Some(SignaturesStatus(referencedWithdrawalEpochNumber, messageToSign, ArrayBuffer()))

            // Try to calculate signatures if at least signing is enabled
            // TODO: maybe we should consider only `certificateSigningEnabled` flag
            if(submitterEnabled || certificateSigningEnabled)
              calculateSignatures(messageToSign).foreach(sigInfo => self ! LocallyGeneratedSignature(sigInfo))
        }
      } else {
        if(timers.isTimerActive(CertificateGenerationTimer))
          timers.cancel(CertificateGenerationTimer)
        signaturesStatus = None
      }
  }

  private def getSubmissionWindowStatus: SubmissionWindowStatus = {
    def getStatus(sidechainNodeView: View): SubmissionWindowStatus = {
      val withdrawalEpochInfo: WithdrawalEpochInfo = sidechainNodeView.state.getWithdrawalEpochInfo
      SubmissionWindowStatus(withdrawalEpochInfo, WithdrawalEpochUtils.inSubmitCertificateWindow(withdrawalEpochInfo, params))
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getStatus), timeoutDuration).asInstanceOf[SubmissionWindowStatus]
  }

  // No MBTRs support, so no sense to specify btrFee different to zero.
  private def getBtrFee(referencedWithdrawalEpochNumber: Int): Long = 0

  // Every positive value FT is allowed.
  private def getFtMinAmount(referencedWithdrawalEpochNumber: Int): Long = 0

  private def getMessageToSign(referencedWithdrawalEpochNumber: Int): Array[Byte] = {
    def getMessage(sidechainNodeView: View): Array[Byte] = {
      val history = sidechainNodeView.history
      val state = sidechainNodeView.state

      val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests (referencedWithdrawalEpochNumber)

      val btrFee: Long = getBtrFee (referencedWithdrawalEpochNumber)
      val ftMinAmount: Long = getFtMinAmount (referencedWithdrawalEpochNumber)

      val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber (history, referencedWithdrawalEpochNumber)
      val sidechainId = params.sidechainId

      CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned (
      withdrawalRequests.asJava,
      sidechainId,
      referencedWithdrawalEpochNumber,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount)
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getMessage), timeoutDuration).asInstanceOf[Array[Byte]]
  }

  private def calculateSignatures(messageToSign: Array[Byte]): Seq[CertificateSignatureInfo] = {
    def getSignersPrivateKeys(sidechainNodeView: View): Seq[(SchnorrSecret, Int)] = {
      val wallet = sidechainNodeView.vault
      params.signersPublicKeys.map(signerPublicKey => wallet.secret(signerPublicKey)).zipWithIndex.filter(_._1.isDefined).map {
        case (secretOpt, idx) => (secretOpt.get.asInstanceOf[SchnorrSecret], idx)
      }
    }

    val privateKeysWithIndexes = Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getSignersPrivateKeys), timeoutDuration)
      .asInstanceOf[Seq[(SchnorrSecret, Int)]]

    privateKeysWithIndexes.map{
      case(secret, pubKeyIndex) => CertificateSignatureInfo(pubKeyIndex, secret.sign(messageToSign))
    }
  }

  private def locallyGeneratedSignature: Receive = {
    case LocallyGeneratedSignature(info: CertificateSignatureInfo) =>
      signaturesStatus match {
        case Some(status) =>
          log.debug(s"Locally generated Certificate signature for pub key index ${info.pubKeyIndex} retrieved.")
          if(status.knownSigs.exists(item => item.pubKeyIndex == info.pubKeyIndex))
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
          log.debug(s"Certificate signature for pub key index ${remoteSigInfo.pubKeyIndex} retrieved from remote.")
          if(!util.Arrays.equals(status.messageToSign, remoteSigInfo.messageToSign)) {
            sender() ! DifferentMessageToSign
          } else if(params.signersPublicKeys.size <= remoteSigInfo.pubKeyIndex ||
              !remoteSigInfo.signature.isValid(params.signersPublicKeys(remoteSigInfo.pubKeyIndex), remoteSigInfo.messageToSign)) {
            sender() ! InvalidSignature
          } else {
            if(!status.knownSigs.exists(item => item.pubKeyIndex == remoteSigInfo.pubKeyIndex)) {
              status.knownSigs.append(CertificateSignatureInfo(remoteSigInfo.pubKeyIndex, remoteSigInfo.signature))
              self ! TryToScheduleCertificateGeneration
            }
            sender() ! ValidSignature
          }
        case None =>
            sender() ! SubmitterIsOutsideSubmissionWindow
      }
  }

  private def getCertificateTopQuality(epoch: Int): Try[Long] = Try {
    mainchainChannel.getTopQualityCertificates(BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId))) match {
      case Success(topQualityCertificates) =>
        if (topQualityCertificates.mempoolCertInfo.quality.isDefined && topQualityCertificates.mempoolCertInfo.epoch.get == epoch) {
          topQualityCertificates.mempoolCertInfo.quality.get
        } else if (topQualityCertificates.chainCertInfo.quality.isDefined && topQualityCertificates.chainCertInfo.epoch.get == epoch) {
          topQualityCertificates.chainCertInfo.quality.get
        } else {
          0
        }
      case Failure(ex) => throw new Exception("Unable to retrieve topQualityCertificates", ex)
    }
  }

  private def checkQuality(status: SignaturesStatus): Boolean = {
    if (status.knownSigs.size >= params.signersThreshold) {
      getCertificateTopQuality(status.referencedEpoch) match {
        case Success(currentCertificateTopQuality) =>
          if (status.knownSigs.size > currentCertificateTopQuality)
            return true

        case Failure(ex) =>
          log.error("Unable to retrieve actual top quality certificates from Mainchain(" + ex.getCause + ")")
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
          if(checkQuality(status)) {
            val delay = Random.nextInt(15) + 5 // random delay from 5 to 15 seconds
            log.info(s"Scheduling Certificate generation in $delay seconds")
            timers.startSingleTimer(CertificateGenerationTimer, TryToGenerateCertificate, FiniteDuration(delay, SECONDS))
            context.system.eventStream.publish(CertificateSubmissionStarted)
          }
        case None =>
          log.error("Trying to schedule certificate generation being outside Certificate submission window.")
      }
  }

  private def tryToGenerateCertificate: Receive = {
    case TryToGenerateCertificate =>
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
                val proofWithQuality = generateProof(dataForProofGeneration)
                val certificateRequest: SendCertificateRequest = CertificateRequestCreator.create(
                  params.sidechainId,
                  dataForProofGeneration.referencedEpochNumber,
                  dataForProofGeneration.endEpochCumCommTreeHash,
                  proofWithQuality.getKey,
                  proofWithQuality.getValue,
                  dataForProofGeneration.withdrawalRequests,
                  dataForProofGeneration.ftMinAmount,
                  dataForProofGeneration.btrFee)

                log.info(s"Backward transfer certificate request was successfully created for epoch number ${certificateRequest.epochNumber}, with proof ${BytesUtils.toHexString(proofWithQuality.getKey)} with quality ${proofWithQuality.getValue} try to send it to mainchain")

                mainchainChannel.sendCertificate(certificateRequest) match {
                  case Success(certificate) =>
                    log.info(s"Backward transfer certificate response had been received. Cert hash = " + BytesUtils.toHexString(certificate.certificateId))

                  case Failure(ex) =>
                    log.error("Creation of backward transfer certificate had been failed. " + ex)
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
  }

  case class DataForProofGeneration(referencedEpochNumber: Int,
                                    sidechainId: Array[Byte],
                                    withdrawalRequests: Seq[WithdrawalRequestBox],
                                    endEpochCumCommTreeHash: Array[Byte],
                                    btrFee: Long,
                                    ftMinAmount: Long,
                                    schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])])

  private def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGeneration = {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId

    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map{
      case (pubKey, pubKeyIndex) =>
        (pubKey, status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    DataForProofGeneration(
      status.referencedEpoch,
      sidechainId,
      withdrawalRequests,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      signersPublicKeyWithSignatures)
  }

  private def lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): Array[Byte] = {
    val mcBlockHash = withdrawalEpochNumber match {
      case -1 => params.parentHashOfGenesisMainchainBlock
      case _  => {
        val mcHeight = params.mainchainCreationBlockHeight + (withdrawalEpochNumber + 1) * params.withdrawalEpochLength - 1
        history.getMainchainBlockReferenceInfoByMainchainBlockHeight(mcHeight).asScala.map(_.getMainchainHeaderHash).getOrElse(throw new IllegalStateException("Information for Mc is missed"))
      }
    }
    log.debug(s"Last MC block hash for withdrawal epoch number $withdrawalEpochNumber is ${BytesUtils.toHexString(mcBlockHash)}")

    val headerInfo = history.mainchainHeaderInfoByHash(mcBlockHash).getOrElse(throw new IllegalStateException("Missed MC Cumulative Hash"))

    headerInfo.cumulativeCommTreeHash
  }

  private def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    val (signersPublicKeysBytes: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      dataForProofGeneration.schnorrKeyPairs.map{case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)}.unzip

    log.info(s"Start generating proof for ${dataForProofGeneration.referencedEpochNumber} withdrawal epoch number, " +
      s"with parameters: sidechainId LE = ${BytesUtils.toHexString(dataForProofGeneration.sidechainId)}, " +
      s"withdrawalRequests=${dataForProofGeneration.withdrawalRequests.foreach(_.toString)}, " +
      s"endEpochCumCommTreeHash=${BytesUtils.toHexString(dataForProofGeneration.endEpochCumCommTreeHash)}, " +
      s"signersThreshold=${params.signersThreshold}. " +
      s"It can take a while.")

    //create and return proof with quality
    CryptoLibProvider.sigProofThresholdCircuitFunctions.createProof(
      dataForProofGeneration.withdrawalRequests.asJava,
      dataForProofGeneration.sidechainId,
      dataForProofGeneration.referencedEpochNumber,
      dataForProofGeneration.endEpochCumCommTreeHash,
      dataForProofGeneration.btrFee,
      dataForProofGeneration.ftMinAmount,
      signaturesBytes.asJava,
      signersPublicKeysBytes.asJava,
      params.signersThreshold,
      provingFileAbsolutePath,
      true,
      true)
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
  case object DifferentMessageToSign extends SignatureProcessingStatus
  case object InvalidSignature extends SignatureProcessingStatus
  case object SubmitterIsOutsideSubmissionWindow extends SignatureProcessingStatus

  // Data
  private case class SubmissionWindowStatus(withdrawalEpochInfo: WithdrawalEpochInfo, isInWindow: Boolean)

  case class SignaturesStatus(referencedEpoch: Int, messageToSign: Array[Byte], knownSigs: ArrayBuffer[CertificateSignatureInfo])

  case class CertificateSignatureInfo(pubKeyIndex: Int, signature: SchnorrProof)

  case class CertificateSignatureFromRemoteInfo(pubKeyIndex: Int, messageToSign: Array[Byte], signature: SchnorrProof) {
    require(pubKeyIndex >= 0, "pubKeyIndex can't be negative value.")
    require(messageToSign.length == FieldElement.FIELD_ELEMENT_LENGTH, "messageToSign has invalid length")
  }

  // Internal interface
  private object Timers {
    object CertificateGenerationTimer
  }

  private object InternalReceivableMessages {
    case class LocallyGeneratedSignature(info: CertificateSignatureInfo)
    case object TryToScheduleCertificateGeneration
    case object TryToGenerateCertificate

  }

  // Public interface
  object ReceivableMessages {
    case class SignatureFromRemote(remoteSigInfo: CertificateSignatureFromRemoteInfo)
    case object GetCertificateGenerationState
  }
}

object CertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext) : Props =
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel), name)
}
