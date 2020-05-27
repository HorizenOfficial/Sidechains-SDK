package com.horizen.certificatesubmitter


import java.io.File
import java.util.Optional

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.mainchain.api.{CertificateRequestCreator, RpcMainchainNodeApi, SendCertificateRequest, SendCertificateResponse}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.SchnorrSecret
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.compat.Platform.EOL
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class CertificateSubmitter
  (settings: SidechainSettings,
   sidechainNodeViewHolderRef: ActorRef,
   params: NetworkParams)
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

  private val  genSysConstant = BytesUtils.fromHexString("caa59704047dcf4e6ac18fafe798915e8ca6584e9a6a90fab006e5fb872c3d4925457ed94bd052ca824494e452fe3cc420fababe75f7a6bda2e4d2d92dff8b25d58e2e5a5df57a02307953679970d35986e14ea50499240974cc208e01e70000")

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

    val actualSysDataConstant =
      CryptoLibProvider.sigProofThresholdCircuitFunctions.generateSysDataConstant(signersPublicKeys.map(_.bytes()).asJava, params.signersThreshold)

    val expectedSysDataConstant = genSysConstant//getSidechainCreationTransaction(sidechainNodeView.history).getBackwardTransferPoseidonRootHash
    if (actualSysDataConstant.deep != expectedSysDataConstant.deep) {
      throw new IllegalStateException(s"Incorrect configuration for backward transfer, expected SysDataConstant ${expectedSysDataConstant.deep} but actual is ${actualSysDataConstant.deep}")
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
      val checkGenerationData =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, Option[DataForProofGeneration]](getDataForProofGeneration)

      val certificateSubmittingResult = (sidechainNodeViewHolderRef ? checkGenerationData).asInstanceOf[Future[Option[DataForProofGeneration]]]
      certificateSubmittingResult.onComplete{
        case Success(Some(dataForProofGeneration)) => {
          val proofWithQuality = generateProof(dataForProofGeneration)
          val certificateRequest: SendCertificateRequest = CertificateRequestCreator.create(
            dataForProofGeneration.processedEpochNumber,
            dataForProofGeneration.endWithdrawalEpochBlockHash,
            proofWithQuality.getKey,
            proofWithQuality.getValue,
            dataForProofGeneration.withdrawalRequests,
            params)

          try {
            log.info(s"Backward transfer certificate request was successfully created fo epoch number ${certificateRequest.epochNumber}, try to send it to mainchain")

            val response: SendCertificateResponse = mainchainApi.sendCertificate(certificateRequest)

            log.info(s"Backward transfer certificate response had been received. Cert hash = " + BytesUtils.toHexString(response.certificateId))
          } catch {
            case ex: Throwable => log.error("Creation of backward transfer certificate had been failed. " + ex)
          }
        }

        case Success(None) =>
          log.info("Creation of backward transfer certificate had been skipped")

        case Failure(ex) =>
          log.error("Creation of backward transfer certificate had been failed. " + ex)
      }
    }
  }

  lazy val mainchainApi = new RpcMainchainNodeApi(settings)

  case class DataForProofGeneration(processedEpochNumber: Int,
                                    withdrawalRequests: Seq[WithdrawalRequestBox],
                                    endWithdrawalEpochBlockHash: Array[Byte],
                                    prevEndWithdrawalEpochBlockHash: Array[Byte],
                                    schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])])

  protected def getDataForProofGeneration(sidechainNodeView: View): Option[DataForProofGeneration] = {
    val state = sidechainNodeView.state

    val withdrawalEpochInfo: WithdrawalEpochInfo = state.getWithdrawalEpochInfo
    if (WithdrawalEpochUtils.canSubmitCertificate(withdrawalEpochInfo, params)) {
      log.info("Can submit certificate, withdrawal epoch info = " + withdrawalEpochInfo.toString)
      val processedWithdrawalEpochNumber = withdrawalEpochInfo.epoch - 1
      state.getUnprocessedWithdrawalRequests(processedWithdrawalEpochNumber)
        .map(unprocessedWithdrawalRequests => buildDataForProofGeneration(sidechainNodeView, processedWithdrawalEpochNumber, unprocessedWithdrawalRequests))
    }
    else {
      log.info("Can't submit certificate, withdrawal epoch info = " + withdrawalEpochInfo.toString)
      None
    }
  }

  private def buildDataForProofGeneration(sidechainNodeView: View, processedWithdrawalEpochNumber: Int, unprocessedWithdrawalRequests: Seq[WithdrawalRequestBox]): DataForProofGeneration = {
    val history = sidechainNodeView.history

    val endEpochBlockHash = lastMainchainBlockHashForWithdrawalEpochNumber(history, processedWithdrawalEpochNumber)
    val previousEndEpochBlockHash = lastMainchainBlockHashForWithdrawalEpochNumber(history, processedWithdrawalEpochNumber - 1)

    val message = CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned(unprocessedWithdrawalRequests.asJava, endEpochBlockHash, previousEndEpochBlockHash)

    val sidechainWallet = sidechainNodeView.vault
    val signersPublicKeyWithSignatures: Seq[(SchnorrProposition, Option[SchnorrProof])] =
      params.signersPublicKeys.map{signerPublicKey =>
        val signature = sidechainWallet.secret(signerPublicKey).map(schnorrSecret => schnorrSecret.asInstanceOf[SchnorrSecret].sign(message))
        (signerPublicKey, signature)
      }

    DataForProofGeneration(processedWithdrawalEpochNumber, unprocessedWithdrawalRequests, endEpochBlockHash, previousEndEpochBlockHash, signersPublicKeyWithSignatures)
  }

  private def lastMainchainBlockHashForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): Array[Byte] = {
    log.info(s"Request last MC block hash for withdrawal epoch number ${withdrawalEpochNumber}")

    withdrawalEpochNumber match {
      case -1 => params.parentHashOfGenesisMainchainBlock
      case _  => {
        val mcHeight = params.mainchainCreationBlockHeight + (withdrawalEpochNumber + 1) * params.withdrawalEpochLength - 1
        history.getMainchainBlockReferenceInfoByMainchainBlockHeight(mcHeight).asScala.map(_.getMainchainHeaderHash).getOrElse(throw new IllegalStateException("Information for Mc is missed"))
      }
    }
  }

  private def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    val (signersPublicKeysBytes: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      dataForProofGeneration.schnorrKeyPairs.map{case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)}.unzip

    log.info(s"Start generating proof for ${dataForProofGeneration.processedEpochNumber} withdrawal epoch number, it can take some time")
    //create and return proof with quality
    CryptoLibProvider.sigProofThresholdCircuitFunctions.createProof(
      dataForProofGeneration.withdrawalRequests.asJava,
      dataForProofGeneration.endWithdrawalEpochBlockHash,
      dataForProofGeneration.prevEndWithdrawalEpochBlockHash,
      signersPublicKeysBytes.asJava,
      signaturesBytes.asJava,
      params.signersThreshold,
      provingFileAbsolutePath)
  }
}

object CertificateSubmitterRef {

  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit ec: ExecutionContext) : Props =
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params), name)
}
