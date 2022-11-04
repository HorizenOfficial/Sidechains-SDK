package com.horizen.certificatesubmitter

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.cryptolibprovider.{CryptoLibProvider, FieldElementUtils}
import com.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.SchnorrSecret
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter._
import com.horizen.fork.ForkManager
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
abstract class AbstractCertificateSubmitter (settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel)
                          (implicit ec: ExecutionContext) extends Actor with Timers with ScorexLogging {

  import AbstractCertificateSubmitter.InternalReceivableMessages._
  import AbstractCertificateSubmitter.ReceivableMessages._
  import AbstractCertificateSubmitter.Timers._

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  private var provingFileAbsolutePath: String = _

  private[certificatesubmitter] var submitterEnabled: Boolean = settings.withdrawalEpochCertificateSettings.submitterIsEnabled
  private[certificatesubmitter] var certificateSigningEnabled: Boolean = settings.withdrawalEpochCertificateSettings.certificateSigningIsEnabled

  private[certificatesubmitter] var signaturesStatus: Option[SignaturesStatus] = None

  private[certificatesubmitter] var certGenerationState: Boolean = false
  private[certificatesubmitter] val certificateFee = if (settings.withdrawalEpochCertificateSettings.certificateAutomaticFeeComputation) None else Some(settings.withdrawalEpochCertificateSettings.certificateFee)

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
    if(timers.isTimerActive(CertificateGenerationTimer)) {
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

  private[certificatesubmitter] def newBlockArrived: Receive

  // Take withdrawal epoch info for block from the History.
  // Note: We can't rely on `State.getWithdrawalEpochInfo`, because it shows the tip info,
  // but the older block may being applied at the moment.
  private[certificatesubmitter] def getSubmissionWindowStatus(block: SidechainBlock): Try[SubmissionWindowStatus]

  // No MBTRs support, so no sense to specify btrFee different to zero.
  private[certificatesubmitter] def getBtrFee(referencedWithdrawalEpochNumber: Int): Long = 0

  private[certificatesubmitter] def getFtMinAmount(consensusEpochNumber: Int): Long = {
    ForkManager.getSidechainConsensusEpochFork(consensusEpochNumber).ftMinAmount
  }

  private[certificatesubmitter] def getMessageToSign(referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    def getMessage(sidechainNodeView: View): Try[Array[Byte]] = Try {
      val history = sidechainNodeView.history
      val state = sidechainNodeView.state

      val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(referencedWithdrawalEpochNumber)

      val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
      val ftMinAmount: Long = getFtMinAmount(referencedWithdrawalEpochNumber)

      val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
      val sidechainId = params.sidechainId

      val utxoMerkleTreeRoot: Optional[Array[Byte]] = {
        Try {
          getUtxoMerkleTreeRoot(referencedWithdrawalEpochNumber, state)
        } match {
          case Failure(e: IllegalStateException) =>
            throw new Exception("CertificateSubmitter is too late against the State. " +
              s"No utxo merkle tree root for requested epoch $referencedWithdrawalEpochNumber. " +
              s"Current epoch is ${state.getWithdrawalEpochInfo.epoch}")
          case Failure(exception) => log.error("Exception while getting utxoMerkleTreeRoot", exception)
            throw new Exception(exception)
          case Success(value) => value
        }
      }


      CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned(
        withdrawalRequests.asJava,
        sidechainId,
        referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash,
        btrFee,
        ftMinAmount,
        utxoMerkleTreeRoot)
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getMessage), timeoutDuration).asInstanceOf[Try[Array[Byte]]].get
  }

  private[certificatesubmitter] def getUtxoMerkleTreeRoot(referencedWithdrawalEpochNumber: Int, state: SidechainState): Optional[Array[Byte]] = {
    if (params.isCSWEnabled) {
      state.utxoMerkleTreeRoot(referencedWithdrawalEpochNumber) match {
        case x: Some[Array[Byte]] => x.asJava
        case None =>
          log.error("UtxoMerkleTreeRoot is not defined even if CSW is enabled")
          throw new IllegalStateException("UtxoMerkleTreeRoot is not defined")
      }
    }
    else {
      Optional.empty()
    }
  }

  private[certificatesubmitter] def calculateSignatures(messageToSign: Array[Byte]): Try[Seq[CertificateSignatureInfo]] = Try {
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

  private[certificatesubmitter] def locallyGeneratedSignature: Receive = {
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
            context.system.eventStream.publish(AbstractCertificateSubmitter.BroadcastLocallyGeneratedSignature(infoToRemote))
            self ! TryToScheduleCertificateGeneration
          }

        case None => log.error("Locally generated signature was retrieved out of the certificate submission window.")
      }
  }

  private[certificatesubmitter] def signatureFromRemote: Receive = {
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

  private[certificatesubmitter] def tryToScheduleCertificateGeneration: Receive
  private[certificatesubmitter] def tryToGenerateCertificate: Receive

  case class DataForProofGeneration(referencedEpochNumber: Int,
                                    sidechainId: Array[Byte],
                                    withdrawalRequests: Seq[WithdrawalRequestBox],
                                    endEpochCumCommTreeHash: Array[Byte],
                                    btrFee: Long,
                                    ftMinAmount: Long,
                                    utxoMerkleTreeRoot: Optional[Array[Byte]],
                                    schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])]) {

    override def toString: String = {
      val utxoMerkleTreeRootString = if (utxoMerkleTreeRoot.isPresent) BytesUtils.toHexString(utxoMerkleTreeRoot.get()) else "None"
      "DataForProofGeneration(" +
        s"referencedEpochNumber = $referencedEpochNumber, " +
        s"sidechainId = $sidechainId, " +
        s"withdrawalRequests = {${withdrawalRequests.mkString(",")}}, " +
        s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
        s"btrFee = $btrFee, " +
        s"ftMinAmount = $ftMinAmount, " +
        s"utxoMerkleTreeRoot = ${utxoMerkleTreeRootString}, " +
        s"number of schnorrKeyPairs = ${schnorrKeyPairs.size})"
    }
  }

  private[certificatesubmitter] def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGeneration = {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId
    val utxoMerkleTreeRoot: Optional[Array[Byte]] = getUtxoMerkleTreeRoot(status.referencedEpoch, state)


    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map {
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
      utxoMerkleTreeRoot,
      signersPublicKeyWithSignatures)
  }

  private def lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): Array[Byte] = {
    val mcBlockHash = withdrawalEpochNumber match {
      case -1 => params.parentHashOfGenesisMainchainBlock
      case _ => {
        val mcHeight = params.mainchainCreationBlockHeight + (withdrawalEpochNumber + 1) * params.withdrawalEpochLength - 1
        history.getMainchainBlockReferenceInfoByMainchainBlockHeight(mcHeight).asScala.map(_.getMainchainHeaderHash).getOrElse(throw new IllegalStateException("Information for Mc is missed"))
      }
    }
    log.debug(s"Last MC block hash for withdrawal epoch number $withdrawalEpochNumber is ${
      BytesUtils.toHexString(mcBlockHash)
    }")

    val headerInfo = history.mainchainHeaderInfoByHash(mcBlockHash).getOrElse(
      throw new IllegalStateException("Missed MC Cumulative Hash"))

    headerInfo.cumulativeCommTreeHash
  }

  private[certificatesubmitter] def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    val (signersPublicKeysBytes: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      dataForProofGeneration.schnorrKeyPairs.map {
        case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)
      }.unzip

    log.info(s"Start generating proof with parameters: dataForProofGeneration = ${
      dataForProofGeneration
    }, " +
      s"signersThreshold = ${
        params.signersThreshold
      }. " +
      s"It can take a while.")

    //create and return proof with quality
    CryptoLibProvider.sigProofThresholdCircuitFunctions.createProof(
      dataForProofGeneration.withdrawalRequests.asJava,
      dataForProofGeneration.sidechainId,
      dataForProofGeneration.referencedEpochNumber,
      dataForProofGeneration.endEpochCumCommTreeHash,
      dataForProofGeneration.btrFee,
      dataForProofGeneration.ftMinAmount,
      dataForProofGeneration.utxoMerkleTreeRoot,
      signaturesBytes.asJava,
      signersPublicKeysBytes.asJava,
      params.signersThreshold,
      provingFileAbsolutePath,
      true,
      true)
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

object AbstractCertificateSubmitter {
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
  private[certificatesubmitter] case class SubmissionWindowStatus(referencedEpochNumber: Int, isInWindow: Boolean)

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
