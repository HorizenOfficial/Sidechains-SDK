package com.horizen.certificatesubmitter.strategies

import akka.pattern.ask
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.{DataForProofGeneration, DataForProofGenerationWithKeyRotation}
import com.horizen.certificatesubmitter.keys.ActualKeys.getMerkleRootOfPublicKeys
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.params.NetworkParams
import com.horizen.websocket.server.WebSocketServerRef.sidechainNodeViewHolderRef
import com.horizen.{SidechainSettings, SidechainState}
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class WithKeyRotationStrategy(settings: SidechainSettings, params: NetworkParams) extends KeyRotationStrategy(settings, params) {

  override def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {

    log.info(s"Start generating proof with parameters: dataForProofGeneration = ${
      dataForProofGeneration
    }, " +
      s"signersThreshold = ${
        params.signersThreshold
      }. " +
      s"It can take a while.")

    //create and return proof with quality
    CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.createProof(
      dataForProofGeneration.withdrawalRequests.asJava,
      dataForProofGeneration.sidechainId,
      dataForProofGeneration.referencedEpochNumber,
      dataForProofGeneration.endEpochCumCommTreeHash,
      dataForProofGeneration.btrFee,
      dataForProofGeneration.ftMinAmount,
      dataForProofGeneration.customFields
    )
  }

  override def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGenerationWithKeyRotation = {
    val history = sidechainNodeView.history
    val state: SidechainState = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId

    val customFields: Array[Byte] = getActualKeysMerkleRoot(status.referencedEpoch, state)

    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (pubKey, status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    val actualKeysOption = state.actualKeys(status.referencedEpoch)

    DataForProofGenerationWithKeyRotation(
      status.referencedEpoch,
      sidechainId,
      withdrawalRequests,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      Seq(customFields),
      signersPublicKeyWithSignatures,
      actualKeysOption,
      state.get)
  }

  override def getMessageToSign(referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    def getMessage(sidechainNodeView: View): Try[Array[Byte]] = Try {
      val history = sidechainNodeView.history
      val state = sidechainNodeView.state

      val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(referencedWithdrawalEpochNumber)

      val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
      val ftMinAmount: Long = getFtMinAmount(referencedWithdrawalEpochNumber)

      val endEpochCumCommTreeHash: Array[Byte] = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
      val sidechainId = params.sidechainId

      val customFields: Array[Byte] = getActualKeysMerkleRoot(referencedWithdrawalEpochNumber, state)

      CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .generateMessageToBeSigned(withdrawalRequests.asJava, sidechainId, referencedWithdrawalEpochNumber,
          endEpochCumCommTreeHash, btrFee, ftMinAmount, Seq(customFields))
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getMessage), settings.sparkzSettings.restApi.timeout).asInstanceOf[Try[Array[Byte]]].get
  }

  private def getActualKeysMerkleRoot(referencedWithdrawalEpochNumber: Int, state: SidechainState): Array[Byte] = {
    Try {
      state.actualKeys(referencedWithdrawalEpochNumber).map(getMerkleRootOfPublicKeys)
    } match {
      case Failure(e: IllegalStateException) =>
        throw new Exception("CertificateSubmitter is too late against the State. " +
          s"No utxo merkle tree root for requested epoch $referencedWithdrawalEpochNumber. " +
          s"Current epoch is ${state.getWithdrawalEpochInfo.epoch}")
      case Failure(exception) => log.error("Exception while getting utxoMerkleTreeRoot", exception)
        throw new Exception(exception)
      case Success(byteArrayOption) => byteArrayOption match {
        case Some(byteArray) => byteArray
        case None => Array[Byte]()
      }
    }
  }
}
