package com.horizen.certificatesubmitter


import java.io.File
import java.util.Optional

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{BitVectorCertificateField, FieldElementCertificateField, MainchainBlockReference, SidechainBlock}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.mainchain.api.{CertificateRequestCreator, MainchainNodeApi, SendCertificateRequest, SendCertificateResponse}
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

import scala.collection.JavaConverters._
import scala.compat.Platform.EOL
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class CertificateSubmitter
  (settings: SidechainSettings,
   sidechainNodeViewHolderRef: ActorRef,
   params: NetworkParams,
   mainchainChannel: MainchainNodeChannel)
  (implicit ec: ExecutionContext)
  extends Actor
  with ScorexLogging
{
  sealed trait SubmitResult

  case object SubmitSuccess
    extends SubmitResult {override def toString: String = "Backward transfer certificate was successfully created."}
  case class SubmitFailed(ex: Throwable)
    extends SubmitResult {override def toString: String = s"Backward transfer certificate creation was failed due to ${ex}"}

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  private var provingFileAbsolutePath: String = _

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    context.system.eventStream.subscribe(self, SidechainAppEvents.SidechainApplicationStart.getClass)
  }

  override def receive: Receive = {
    checkSubmitter orElse trySubmitCertificate orElse {
      case message: Any => log.error("CertificateSubmitter received strange message: " + message)
    }
  }

  protected def checkSubmitter: Receive = {
    case SidechainAppEvents.SidechainApplicationStart => {
      val submitterCheckingFunction =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, Try[Unit]](checkSubmitterMessage)

      val checkAsFuture = (sidechainNodeViewHolderRef ? submitterCheckingFunction).asInstanceOf[Future[Try[Unit]]]
      checkAsFuture.onComplete{
        case Success(Success(_)) =>
          log.info(s"Backward transfer certificate submitter was successfully started.")

        case Success(Failure(ex)) =>
          log.error("Backward transfer certificate submitter failed to start due:" + EOL + ex)
          throw ex

        case Failure(ex) =>
          log.error("Failed to check backward transfer certificate submitter due:" + EOL + ex)
      }
    }
  }

  private def checkSubmitterMessage(sidechainNodeView: View): Try[Unit] = Try {
    val signersPublicKeys = params.signersPublicKeys

    val actualSysDataConstant = params.calculatedSysDataConstant
    val expectedSysDataConstantOpt = getSidechainCreationTransaction(sidechainNodeView.history).getGenSysConstantOpt.asScala

    if(expectedSysDataConstantOpt.isEmpty ||
      actualSysDataConstant.deep != expectedSysDataConstantOpt.get.deep) {
      throw new IllegalStateException("Incorrect configuration for backward transfer, expected SysDataConstant " +
        s"'${BytesUtils.toHexString(expectedSysDataConstantOpt.get)}' but actual is '${BytesUtils.toHexString(actualSysDataConstant)}'")
    } else {
      log.info(s"sysDataConstant in Certificate submitter is: '${BytesUtils.toHexString(actualSysDataConstant)}'")
    }

    val wallet = sidechainNodeView.vault
    val actualStoredPrivateKey = signersPublicKeys.map(pubKey => wallet.secret(pubKey)).size
    if (actualStoredPrivateKey < params.signersThreshold) {
      throw new IllegalStateException(s"Incorrect configuration for backward transfer, expected private keys size shall be at least ${params.signersThreshold} but actual is ${actualStoredPrivateKey}")
    }

    if (params.provingKeyFilePath.equalsIgnoreCase("")) {
      throw new IllegalStateException(s"Proving key file name is not set")
    }

    val provingFile: File = new File(params.provingKeyFilePath)
    if (!provingFile.canRead) {
      throw new IllegalStateException(s"Proving key file at path ${provingFile.getAbsolutePath} is not exist or can't be read")
    }
    else {
      provingFileAbsolutePath = provingFile.getAbsolutePath;
      log.info(s"Found proving key file at location: ${provingFileAbsolutePath}")
    }
  }

  private def getSidechainCreationTransaction(history: SidechainHistory): SidechainCreation = {
    val mainchainReference: MainchainBlockReference = history
      .getMainchainBlockReferenceByHash(params.genesisMainchainBlockHash).asScala
      .getOrElse(throw new IllegalStateException("No mainchain creation transaction in history"))

    mainchainReference.data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs.get(0).asInstanceOf[SidechainCreation]
  }


  protected def trySubmitCertificate: Receive = {
    case SemanticallySuccessfulModifier(_: SidechainBlock) => {
      context.system.eventStream.publish(CertificateSubmitter.StartCertificateSubmission)

      val checkGenerationData =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, Option[DataForProofGeneration]](getDataForProofGeneration)

      // Wait in current thread for proof data
      val checkDataResult: Try[Option[DataForProofGeneration]] = try {
        Success(Await.result(sidechainNodeViewHolderRef ? checkGenerationData, settings.scorexSettings.restApi.timeout)
          .asInstanceOf[Option[DataForProofGeneration]])
      } catch {
        case ex: Throwable => Failure(ex)
      }
      checkDataResult match {
        case Success(Some(dataForProofGeneration)) => {
          log.debug(s"Retrieved data for certificate proof calculation: $dataForProofGeneration")
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
        }

        case Success(None) =>
          log.info("Creation of backward transfer certificate had been skipped")

        case Failure(ex) =>
          log.error("Error in creation of backward transfer certificate:" + ex)
      }
      context.system.eventStream.publish(CertificateSubmitter.StopCertificateSubmission)
    }
  }

  case class DataForProofGeneration(referencedEpochNumber: Int,
                                    sidechainId: Array[Byte],
                                    withdrawalRequests: Seq[WithdrawalRequestBox],
                                    endEpochCumCommTreeHash: Array[Byte],
                                    btrFee: Long,
                                    ftMinAmount: Long,
                                    schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])])

  protected def getDataForProofGeneration(sidechainNodeView: View): Option[DataForProofGeneration] = {
    val state = sidechainNodeView.state

    val withdrawalEpochInfo: WithdrawalEpochInfo = state.getWithdrawalEpochInfo
    if (WithdrawalEpochUtils.inSubmitCertificateWindow(withdrawalEpochInfo, params)) {
      log.info("In submit certificate window, withdrawal epoch info = " + withdrawalEpochInfo.toString)
      val referencedWithdrawalEpochNumber = withdrawalEpochInfo.epoch - 1
      buildDataForProofGeneration(sidechainNodeView, referencedWithdrawalEpochNumber)
    }
    else {
      log.info("Not in submit certificate window, withdrawal epoch info = " + withdrawalEpochInfo.toString)
      None
    }
  }

  private def getCertificateTopQuality(epoch: Int): Try[Long] = Try {
    mainchainChannel.getTopQualityCertificates(BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId))) match {
      case Success(topQualityCertificates)=>
        if (!topQualityCertificates.mempoolCertInfo.quality.isEmpty && topQualityCertificates.mempoolCertInfo.epoch.get == epoch) {
          topQualityCertificates.mempoolCertInfo.quality.get
        } else if (!topQualityCertificates.chainCertInfo.quality.isEmpty && topQualityCertificates.chainCertInfo.epoch.get == epoch) {
          topQualityCertificates.chainCertInfo.quality.get
        } else {
          0
        }
      case Failure(ex) => {
        throw new Exception("Unable to retrieve topQualityCertificates", ex)
      }
    }
  }

  private def buildDataForProofGeneration(sidechainNodeView: View, referencedWithdrawalEpochNumber: Int): Option[DataForProofGeneration] = {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    getCertificateTopQuality(referencedWithdrawalEpochNumber) match {
      case Success(currentCertificateTopQuality) => {

      val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(referencedWithdrawalEpochNumber)

      val btrFee: Long = 0 // No MBTRs support, so no sense to specify btrFee different to zero.
      val ftMinAmount: Long = 0 // Every positive value FT is allowed.
      val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
      val sidechainId = params.sidechainId

      val message = CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned(
        withdrawalRequests.asJava,
        sidechainId,
        referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash,
        btrFee,
        ftMinAmount)

      val sidechainWallet = sidechainNodeView.vault
      val signersPublicKeyWithSignatures: Seq[(SchnorrProposition, Option[SchnorrProof])] =
        params.signersPublicKeys.map{signerPublicKey =>
          val signature = sidechainWallet.secret(signerPublicKey).map(schnorrSecret => schnorrSecret.asInstanceOf[SchnorrSecret].sign(message))
          (signerPublicKey, signature)
        }

      // The quality of data generated must be greater then current top certificate quality.
      // Note: Although quality returned as a result of Snark proof generation, we don't want to waste time for snark creation.
      // Quality is equal to the number of valid schnorr signatures for the Threshold signature proof
      val newCertQuality: Long = signersPublicKeyWithSignatures.flatMap(_._2).size
      if(newCertQuality > currentCertificateTopQuality) {
        Some(
          DataForProofGeneration(referencedWithdrawalEpochNumber, sidechainId, withdrawalRequests,
            endEpochCumCommTreeHash, btrFee, ftMinAmount, signersPublicKeyWithSignatures)
        )
      } else {
        log.info("Node was not able to generate certificate with better quality than the one in the chain: " +
          s"new cert quality is $newCertQuality, but top certificate quality is $currentCertificateTopQuality.")
        None
      }
    }
      case Failure(ex) => {
        log.info("Node was not able to generate certificate: unable to retrieve actual top quality certificates in Mainchain(" + ex.getCause + ")")
        None
      }
    }
  }

  private def lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): Array[Byte] = {
    val mcBlockHash = withdrawalEpochNumber match {
      case -1 => params.parentHashOfGenesisMainchainBlock
      case _  => {
        val mcHeight = params.mainchainCreationBlockHeight + (withdrawalEpochNumber + 1) * params.withdrawalEpochLength - 1
        history.getMainchainBlockReferenceInfoByMainchainBlockHeight(mcHeight).asScala.map(_.getMainchainHeaderHash).getOrElse(throw new IllegalStateException("Information for Mc is missed"))
      }
    }
    log.info(s"Last MC block hash for withdrawal epoch number ${withdrawalEpochNumber} is ${BytesUtils.toHexString(mcBlockHash)}")

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
  case object StartCertificateSubmission
  case object StopCertificateSubmission
}

object CertificateSubmitterRef {

  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainApi: MainchainNodeChannel)
           (implicit ec: ExecutionContext) : Props =
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainApi))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainApi: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainApi))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainApi: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainApi), name)
}
