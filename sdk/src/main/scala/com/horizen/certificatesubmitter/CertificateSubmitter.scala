package com.horizen.certificatesubmitter


import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.box.WithdrawalRequestBox
import com.horizen.mainchain.api.RpcMainchainApi
import com.horizen.mainchain.{CertificateRequest, CertificateRequestCreator}
import com.horizen.params.NetworkParams
import com.horizen.backwardtransfer.BackwardTransferLoader
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.WithdrawalEpochUtils
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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
  type SubmitMessageType = GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, SubmitResult]
  type CheckMessageType = GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, Unit]

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  val prePreviousEpochPlaceholderForFirstEpoch: Array[Byte] = null
  val missedPrivateKeySignatureBytesPlaceholder:  Array[Byte] = null

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    context.system.eventStream.subscribe(self, SidechainAppEvents.SidechainApplicationStart.getClass)
  }

  lazy val mainchainApi = new RpcMainchainApi(settings)

  protected def submitCertificate(sidechainNodeView: View): SubmitResult = {
    try {
      val history = sidechainNodeView.history
      val withdrawalEpochInfo = sidechainNodeView.state.getWithdrawalEpochInfo
      if (withdrawalEpochInfo.epoch > 0 && WithdrawalEpochUtils.canSubmitCertificate(withdrawalEpochInfo, params)) {
        val withdrawalEpoch = withdrawalEpochInfo.epoch - 1

        for {
          withdrawalRequests <- sidechainNodeView.state.unprocessedWithdrawalRequests(withdrawalEpoch)
          mcEndEpochBlockHash <- lastMainchainBlockHashForWithdrawalEpochNumber(history, withdrawalEpochInfo.epoch)
        } yield {
          val previousMcEndEpochBlockHash =
            lastMainchainBlockHashForWithdrawalEpochNumber(history, withdrawalEpochInfo.epoch - 1).getOrElse(prePreviousEpochPlaceholderForFirstEpoch)

          val proof = generateProof(withdrawalRequests, mcEndEpochBlockHash, previousMcEndEpochBlockHash, sidechainNodeView.vault)

          val certificate: CertificateRequest = CertificateRequestCreator.create(
            withdrawalEpoch,
            mcEndEpochBlockHash,
            previousMcEndEpochBlockHash,
            proof,
            withdrawalRequests,
            params)

          mainchainApi.sendCertificate(certificate)
        }
      }

      SubmitSuccess

    } catch {
      case e: Throwable => SubmitFailed(e)
    }
  }

  private def lastMainchainBlockHashForWithdrawalEpochNumber(history: SidechainHistory, withdrawalEpochNumber: Int): Option[Array[Byte]] = {
    val mcHeight = params.mainchainCreationBlockHeight + withdrawalEpochNumber * params.withdrawalEpochLength - 1
    history.getMainchainBlockReferenceInfoByMainchainBlockHeight(mcHeight).asScala.map(_.getMainchainBlockReferenceHash)
  }


  private def generateProof(withdrawalRequestBoxes: Seq[WithdrawalRequestBox], endWithdrawalEpochBlockHash: Array[Byte], prevEndWithdrawalEpochBlockHash: Array[Byte], sidechainWallet: SidechainWallet): Array[Byte] = {
    val message = BackwardTransferLoader.schnorrFunctions.createBackwardTransferMessage(withdrawalRequestBoxes.asJava, endWithdrawalEpochBlockHash, prevEndWithdrawalEpochBlockHash)
    val publicKeysBytes = params.schnorrPublicKeys.map(_.bytes())

    val schnorrSignatures = params.schnorrPublicKeys.map{publicKey =>
      sidechainWallet.secret(publicKey).map(_.sign(message).bytes).getOrElse(missedPrivateKeySignatureBytesPlaceholder)
    }

    BackwardTransferLoader.schnorrFunctions.createProof(
      withdrawalRequestBoxes.asJava,
      endWithdrawalEpochBlockHash,
      prevEndWithdrawalEpochBlockHash,
      schnorrSignatures.asJava,
      publicKeysBytes.asJava,
      params.backwardTransferThreshold,
      params.provingKeyFilePath)
  }

  protected def trySubmitCertificate: Receive = {
    case SemanticallySuccessfulModifier(_: SidechainBlock) => {
      val submitCertificateFunction: View => SubmitResult = submitCertificate

      val submitMessage: SubmitMessageType =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, SubmitResult](submitCertificateFunction)

      val certificateSubmittingResult = (sidechainNodeViewHolderRef ? submitMessage).asInstanceOf[Future[SubmitResult]]
      certificateSubmittingResult.onComplete{
        case Success(SubmitSuccess) =>
          log.info(s"Backward transfer certificate was successfully created.")

        case Success(SubmitFailed(ex)) =>
          log.info("Creation of backward transfer certificate had been failed." + ex)

        case Failure(ex) =>
          log.info("Creation of backward transfer certificate had been failed. " + ex)
      }
    }
  }

  protected def checkSubmitter: Receive = {
    case SidechainAppEvents.SidechainApplicationStart => {
      val submitterCheckingFunction: View => Unit = checkSubmitterMessage

      val checkMessage: CheckMessageType =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, Unit](submitterCheckingFunction)

      val checkAsFuture = (sidechainNodeViewHolderRef ? checkMessage).asInstanceOf[Future[Unit]]
      checkAsFuture.onComplete{
        case Success(()) =>
          log.info(s"Backward transfer certificate submitter was successfully started.")

        case Failure(ex) =>
          log.info("Backward transfer certificate submitter failed to start due:" + ex)
          throw ex
      }
    }
  }

  private def checkSubmitterMessage(sidechainNodeView: View): Unit = {
    val backwardTransferPublicKeys = params.schnorrPublicKeys

    val actualPoseidonRootHash =
      BackwardTransferLoader.schnorrFunctions.generatePoseidonHash(backwardTransferPublicKeys.map(_.bytes()).asJava, params.backwardTransferThreshold)

    val expectedPoseidonRootHash = getSidechainCreationTransaction(sidechainNodeView.history).getBackwardTransferPoseidonRootHash
    if (actualPoseidonRootHash.deep != expectedPoseidonRootHash.deep) {
      throw new IllegalStateException(s"Incorrect configuration for backward transfer, expected poseidon root hash ${expectedPoseidonRootHash.deep} but actual is ${actualPoseidonRootHash.deep}")
    }

    val wallet = sidechainNodeView.vault
    val actualStoredPrivateKey = backwardTransferPublicKeys.map(pubKey => wallet.secret(pubKey)).size
    if (actualStoredPrivateKey < params.backwardTransferThreshold) {
      throw new IllegalStateException(s"Incorrect configuration for backward transfer, expected private keys size shall be at least ${params.backwardTransferThreshold} but actual is ${actualStoredPrivateKey}")
    }
  }

  private def getSidechainCreationTransaction(history: SidechainHistory): SidechainCreation = {
    val mainchainReference = history
      .getMainchainBlockReferenceByHash(params.genesisMainchainBlockHash)
      .orElseGet(throw new IllegalStateException("No mainchain creation transaction in history"))

    mainchainReference.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs.get(0).asInstanceOf[SidechainCreation]
  }

  override def receive: Receive = {
      trySubmitCertificate orElse
      checkSubmitter orElse {
        case message: Any => log.error("CertificateSubmitter received strange message: " + message)
      }
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
