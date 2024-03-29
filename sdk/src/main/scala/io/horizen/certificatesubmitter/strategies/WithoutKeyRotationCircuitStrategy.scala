package io.horizen.certificatesubmitter.strategies

import io.horizen._
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import io.horizen.certificatesubmitter.dataproof.CertificateDataWithoutKeyRotation
import com.horizen.certnative.BackwardTransfer
import io.horizen.cryptolibprovider.ThresholdSignatureCircuit
import io.horizen.history.AbstractHistory
import io.horizen.params.NetworkParams
import io.horizen.proposition.SchnorrProposition
import io.horizen.transaction.Transaction

import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.{Failure, Success, Try}

class WithoutKeyRotationCircuitStrategy[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  HIS <: AbstractHistory[TX, H, PM, _, _, _],
  MS <: AbstractState[TX, H, PM, MS]](settings: SidechainSettings,
                                      params: NetworkParams,
                                      circuit: ThresholdSignatureCircuit)
  extends CircuitStrategy[TX, H, PM, HIS, MS, CertificateDataWithoutKeyRotation](settings, params) {

  override def generateProof(certificateData: CertificateDataWithoutKeyRotation, provingFileAbsolutePath: String): io.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
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
    circuit.createProof(
      certificateData.backwardTransfers.asJava,
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

  override def buildCertificateData(history: HIS, state: MS, status: SignaturesStatus): CertificateDataWithoutKeyRotation = {
    val backwardTransfers: Seq[BackwardTransfer] = state.backwardTransfers(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val consensusEpochNumber = lastConsensusEpochNumberForWithdrawalEpochNumber(history, state, status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(consensusEpochNumber)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, state, status.referencedEpoch)
    val sidechainId = params.sidechainId
    val utxoMerkleTreeRoot: Option[Array[Byte]] = getUtxoMerkleTreeRoot(state, status.referencedEpoch)


    val signersPublicKeyWithSignatures = status.signersPublicKeys.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (pubKey, status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    CertificateDataWithoutKeyRotation(
      status.referencedEpoch,
      sidechainId,
      backwardTransfers,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      signersPublicKeyWithSignatures,
      utxoMerkleTreeRoot)
  }

  override def getMessageToSignAndPublicKeys(history: HIS, state: MS, referencedWithdrawalEpochNumber: Int): Try[(Array[Byte], Seq[SchnorrProposition])] = Try {
    val backwardTransfers: Seq[BackwardTransfer] = state.backwardTransfers(referencedWithdrawalEpochNumber)

    val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
    val consensusEpochNumber = lastConsensusEpochNumberForWithdrawalEpochNumber(history, state, referencedWithdrawalEpochNumber)
    val ftMinAmount: Long = getFtMinAmount(consensusEpochNumber)

    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, state, referencedWithdrawalEpochNumber)
    val sidechainId = params.sidechainId

    val utxoMerkleTreeRoot: Option[Array[Byte]] = {
      Try {
        getUtxoMerkleTreeRoot(state, referencedWithdrawalEpochNumber)
      } match {
        case Failure(_: IllegalStateException) =>
          throw new Exception("CertificateSubmitter is too late against the State. " +
            s"No utxo merkle tree root for requested epoch $referencedWithdrawalEpochNumber. " +
            s"Current epoch is ${state.getWithdrawalEpochInfo.epoch}")
        case Failure(exception) => log.error("Exception while getting utxoMerkleTreeRoot", exception)
          throw new Exception(exception)
        case Success(value) => value
      }
    }

    val messageToSign = circuit.generateMessageToBeSigned(
      backwardTransfers.asJava,
      sidechainId,
      referencedWithdrawalEpochNumber,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      Optional.ofNullable(utxoMerkleTreeRoot.orNull)
    )

    // For circuit without key rotation, signing keys are always the same
    (messageToSign, params.signersPublicKeys)
  }

  private def getUtxoMerkleTreeRoot(state: MS, referencedWithdrawalEpochNumber: Int): Option[Array[Byte]] = {
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