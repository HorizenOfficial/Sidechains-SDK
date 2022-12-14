package com.horizen.certificatesubmitter.strategies

import com.horizen.{AbstractHistory, AbstractState, SidechainSettings, SidechainTypes, Wallet}
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.CertificateDataWithoutKeyRotation
import com.horizen.certnative.BackwardTransfer
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.cryptolibprovider.ThresholdSignatureCircuit
import com.horizen.params.NetworkParams
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction
import sparkz.core.transaction.MemoryPool

import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class WithoutKeyRotationCircuitStrategy[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H] : ClassTag,
  _FPI <: AbstractFeePaymentsInfo,
  _HSTOR <: AbstractHistoryStorage[PM, _FPI, _HSTOR],
  _HIS <: AbstractHistory[TX, H, PM, _FPI, _HSTOR, _HIS],
  _MS <: AbstractState[TX, H, PM, _MS],
  _VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PM, _VL],
  _MP <: MemoryPool[TX, _MP]](settings: SidechainSettings, params: NetworkParams,
                            cryptolibCircuit: ThresholdSignatureCircuit)
  extends CircuitStrategy[TX, H, PM, CertificateDataWithoutKeyRotation](settings, params) {

  type FPI = _FPI
  type HSTOR = _HSTOR
  type HIS = _HIS
  type MS = _MS
  type VL = _VL
  type MP = _MP

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

  override def buildCertificateData(sidechainNodeView: View, status: SignaturesStatus): CertificateDataWithoutKeyRotation = {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val backwardTransfers: Seq[BackwardTransfer] = state.backwardTransfers(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val consensusEpochNumber = lastConsensusEpochNumberForWithdrawalEpochNumber(history, status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(consensusEpochNumber)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId
    val utxoMerkleTreeRoot: Option[Array[Byte]] = getUtxoMerkleTreeRoot(status.referencedEpoch, state)


    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map {
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

  override def getMessageToSign(sidechainNodeView: View, referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val backwardTransfers: Seq[BackwardTransfer] = state.backwardTransfers(referencedWithdrawalEpochNumber)

    val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
    val consensusEpochNumber = lastConsensusEpochNumberForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
    val ftMinAmount: Long = getFtMinAmount(consensusEpochNumber)

    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
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
      backwardTransfers.asJava,
      sidechainId,
      referencedWithdrawalEpochNumber,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      Optional.ofNullable(utxoMerkleTreeRoot.orNull)
    )
  }

  private def getUtxoMerkleTreeRoot(referencedWithdrawalEpochNumber: Int, state: MS): Option[Array[Byte]] = {
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