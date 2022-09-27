package com.horizen.certificatesubmitter.strategies

import akka.pattern.ask
import com.horizen.SidechainSettings
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.DataForProofGenerationWithKeyRotation
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.params.NetworkParams
import com.horizen.websocket.server.WebSocketServerRef.sidechainNodeViewHolderRef
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class WithKeyRotationStrategy(settings: SidechainSettings, params: NetworkParams) extends KeyRotationStrategy(settings, params) {
  protected def generateProof(dataForProofGeneration: DataForProofGenerationWithKeyRotation): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    val (signersPublicKeysBytes: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      dataForProofGeneration.schnorrKeyPairs.map {
        case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)
      }.unzip

    log.info(s"Start generating proof with parameters: dataForProofGeneration = $dataForProofGeneration, " +
      s"signersThreshold = ${
        params.signersThreshold
      }. " +
      s"It can take a while.")

    //create and return proof with quality
    CryptoLibProvider.sigProofThresholdCircuitFunctions.createProof(schnorrSignatureBytesList = signaturesBytes.asJava, schnorrPublicKeysBytesList = signersPublicKeysBytes.asJava, threshold = params.signersThreshold, provingKeyPath = provingFileAbsolutePath, checkProvingKey = true, zk = true)
  }

  def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGenerationWithKeyRotation = {
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
  }

  def getMessageToSign(referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
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


      CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned(withdrawalRequests.asJava, sidechainId, referencedWithdrawalEpochNumber, endEpochCumCommTreeHash, btrFee, ftMinAmount, utxoMerkleTreeRoot)
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getMessage), settings.sparkzSettings.restApi.timeout).asInstanceOf[Try[Array[Byte]]].get
  }
}
