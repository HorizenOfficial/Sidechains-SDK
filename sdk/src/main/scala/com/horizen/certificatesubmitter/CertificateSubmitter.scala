package com.horizen.certificatesubmitter


import java.util.Optional
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import com.horizen._
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.InternalReceivableMessages.{LocallyGeneratedSignature, TryToGenerateCertificate, TryToScheduleCertificateGeneration}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.{CertificateSignatureInfo, CertificateSubmissionStopped, DifferentMessageToSign, InvalidPublicKeyIndex, InvalidSignature, KnownSignature, SignaturesStatus, SubmissionWindowStatus, SubmitterIsOutsideSubmissionWindow, ValidSignature}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.SchnorrSecret
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import com.horizen.websocket.client.MainchainNodeChannel
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * Certificate submitter listens to the State changes and takes care of of certificate signatures managing (generation, storing and broadcasting)
 * If `submitterEnabled` is `true`, it will try to generate and send the Certificate to MC node in case the proper amount of signatures were collected.
 * Must be singleton.
 */
class CertificateSubmitter(settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel)
  (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock
  ](settings, sidechainNodeViewHolderRef, params, mainchainChannel)
{
  type HSTOR = SidechainHistoryStorage
  type VL = SidechainWallet
  type HIS = SidechainHistory
  type MS = SidechainState
  type MP = SidechainMemoryPool
  type PM = SidechainBlock

  override def preStart(): Unit = {
    super.preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
  }

  override def postStop(): Unit = {
    super.postStop()
  }

  // Take withdrawal epoch info for block from the History.
  // Note: We can't rely on `State.getWithdrawalEpochInfo`, because it shows the tip info,
  // but the older block may being applied at the moment.
  override protected def getSubmissionWindowStatus(block: SidechainBlock): Try[SubmissionWindowStatus] = Try {
    def getStatus(sidechainNodeView: View): SubmissionWindowStatus = {
      val withdrawalEpochInfo: WithdrawalEpochInfo = sidechainNodeView.history.blockInfoById(block.id).withdrawalEpochInfo
      SubmissionWindowStatus(withdrawalEpochInfo, WithdrawalEpochUtils.inSubmitCertificateWindow(withdrawalEpochInfo, params))
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getStatus), timeoutDuration).asInstanceOf[SubmissionWindowStatus]
  }

  protected def getMessageToSign(referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    def getMessage(sidechainNodeView: View): Array[Byte] = {
      val history = sidechainNodeView.history
      val state = sidechainNodeView.state

      val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(referencedWithdrawalEpochNumber)

      val btrFee: Long = getBtrFee (referencedWithdrawalEpochNumber)
      val ftMinAmount: Long = getFtMinAmount (referencedWithdrawalEpochNumber)

      val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
      val sidechainId = params.sidechainId

      val utxoMerkleTreeRoot = state.utxoMerkleTreeRoot(referencedWithdrawalEpochNumber).get

      CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned(
        withdrawalRequests.asJava,
        sidechainId,
        referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash,
        btrFee,
        ftMinAmount,
        utxoMerkleTreeRoot)
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getMessage), timeoutDuration).asInstanceOf[Array[Byte]]
  }

  protected def calculateSignatures(messageToSign: Array[Byte]): Try[Seq[CertificateSignatureInfo]] = Try {
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

  protected def tryToGenerateCertificate: Receive = {
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

                log.info(s"Backward transfer certificate request was successfully created for epoch number ${certificateRequest.epochNumber}, with proof ${BytesUtils.toHexString(proofWithQuality.getKey)} with quality ${proofWithQuality.getValue} try to send it to mainchain")

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

  case class DataForProofGeneration(referencedEpochNumber: Int,
                                    sidechainId: Array[Byte],
                                    withdrawalRequests: Seq[WithdrawalRequestBox],
                                    endEpochCumCommTreeHash: Array[Byte],
                                    btrFee: Long,
                                    ftMinAmount: Long,
                                    utxoMerkleTreeRoot: Array[Byte],
                                    schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])])

  private def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGeneration = {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId
    val utxoMerkleTreeRoot = state.utxoMerkleTreeRoot(status.referencedEpoch).get

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
      utxoMerkleTreeRoot,
      signersPublicKeyWithSignatures)
  }

  protected def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    val (signersPublicKeysBytes: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      dataForProofGeneration.schnorrKeyPairs.map{case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)}.unzip

    log.info(s"Start generating proof for ${dataForProofGeneration.referencedEpochNumber} withdrawal epoch number, " +
      s"with parameters: sidechainId LE = ${BytesUtils.toHexString(dataForProofGeneration.sidechainId)}, " +
      s"withdrawalRequests=${dataForProofGeneration.withdrawalRequests.foreach(_.toString)}, " +
      s"endEpochCumCommTreeHash=${BytesUtils.toHexString(dataForProofGeneration.endEpochCumCommTreeHash)}, " +
      s"utxoMerkleTreeRoot=${BytesUtils.toHexString(dataForProofGeneration.utxoMerkleTreeRoot)}, " +
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
      dataForProofGeneration.utxoMerkleTreeRoot,
      signaturesBytes.asJava,
      signersPublicKeysBytes.asJava,
      params.signersThreshold,
      provingFileAbsolutePath,
      true,
      true)
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
