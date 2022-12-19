package com.horizen.certificatesubmitter.strategies

import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.{CertificateData, CertificateDataWithoutKeyRotation}
import com.horizen.cryptolibprovider.{CryptoLibProvider, ThresholdSignatureCircuit}
import com.horizen.params.NetworkParams
import com.horizen.{SidechainSettings, SidechainState}

import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.{Failure, Success, Try}

class WithoutKeyRotationCircuitStrategy(settings: SidechainSettings, params: NetworkParams,
                                        cryptolibCircuit: ThresholdSignatureCircuit)
  extends CircuitStrategy[CertificateDataWithoutKeyRotation](settings, params) {
  override def generateProof(certificateData: CertificateDataWithoutKeyRotation, provingFileAbsolutePath: String): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    val (signersPublicKeysBytes: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      certificateData.schnorrKeyPairs.map {
        case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)
      }.unzip

    log.info(s"Start generating proof with parameters: certificateData = ${
      certificateData
    }, " +
      s"signersThreshold = ${
        params.signersThreshold
      }. " +
      s"It can take a while.")

    //create and return proof with quality
    cryptolibCircuit.createProof(
      certificateData.withdrawalRequests.asJava,
      certificateData.sidechainId,
      certificateData.referencedEpochNumber,
      certificateData.endEpochCumCommTreeHash,
      certificateData.btrFee,
      certificateData.ftMinAmount,
      certificateData.utxoMerkleTreeRoot.asJava,
      signaturesBytes.asJava,
      signersPublicKeysBytes.asJava,
      params.signersThreshold,
      provingFileAbsolutePath,
      true,
      true)
  }

  override def buildCertificateData(sidechainNodeView: View, status: SignaturesStatus): CertificateDataWithoutKeyRotation = {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val consensusEpochNumber = lastConsensusEpochNumberForWithdrawalEpochNumber(history, state, status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(consensusEpochNumber)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, state, status.referencedEpoch)
    val sidechainId = params.sidechainId
    val utxoMerkleTreeRoot: Option[Array[Byte]] = getUtxoMerkleTreeRoot(status.referencedEpoch, state)


    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (pubKey, status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    CertificateDataWithoutKeyRotation(
      status.referencedEpoch,
      sidechainId,
      withdrawalRequests,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      signersPublicKeyWithSignatures,
      utxoMerkleTreeRoot)
  }

  override def getMessageToSign(sidechainNodeView: View, referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(referencedWithdrawalEpochNumber)

    val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
    val consensusEpochNumber = lastConsensusEpochNumberForWithdrawalEpochNumber(history, state, referencedWithdrawalEpochNumber)
    val ftMinAmount: Long = getFtMinAmount(consensusEpochNumber)

    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, state, referencedWithdrawalEpochNumber)
    val sidechainId = params.sidechainId

    val utxoMerkleTreeRoot: Option[Array[Byte]] = {
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

    cryptolibCircuit.generateMessageToBeSigned(
      withdrawalRequests.asJava,
      sidechainId,
      referencedWithdrawalEpochNumber,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      Optional.ofNullable(utxoMerkleTreeRoot.orNull)
    )
  }

  private def getUtxoMerkleTreeRoot(referencedWithdrawalEpochNumber: Int, state: SidechainState): Option[Array[Byte]] = {
    if (params.isCSWEnabled) {
      state.utxoMerkleTreeRoot(referencedWithdrawalEpochNumber) match {
        case x: Some[Array[Byte]] => x
        case None =>
          log.error("UtxoMerkleTreeRoot is not defined even if CSW is enabled")
          throw new IllegalStateException("UtxoMerkleTreeRoot is not defined")
      }
    }
    else {
      Option.empty[Array[Byte]]
    }
  }
}