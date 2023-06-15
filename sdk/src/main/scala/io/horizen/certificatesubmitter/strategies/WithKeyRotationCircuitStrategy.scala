package io.horizen.certificatesubmitter.strategies

import com.horizen.certnative.BackwardTransfer
import io.horizen._
import io.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase, WithdrawalEpochCertificate}
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import io.horizen.certificatesubmitter.dataproof.CertificateDataWithKeyRotation
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, SchnorrKeysSignatures}
import io.horizen.cryptolibprovider.ThresholdSignatureCircuitWithKeyRotation
import io.horizen.history.AbstractHistory
import io.horizen.params.NetworkParams
import io.horizen.proposition.SchnorrProposition
import io.horizen.sc2sc.{Sc2ScConfigurator, Sc2ScDataForCertificate, Sc2ScUtils}
import io.horizen.transaction.Transaction

import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.Try

class WithKeyRotationCircuitStrategy[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  HIS <: AbstractHistory[TX, H, PM, _, _, _],
  MS <: AbstractState[TX, H, PM, MS]](settings: SidechainSettings,
                                      sc2scConfig: Sc2ScConfigurator,
                                      params: NetworkParams,
                                      circuit: ThresholdSignatureCircuitWithKeyRotation)
  extends CircuitStrategy[TX, H, PM, HIS, MS, CertificateDataWithKeyRotation](settings, sc2scConfig, params) with Sc2ScUtils[TX, H, PM, MS, HIS] {

  override def generateProof(certificateData: CertificateDataWithKeyRotation, provingFileAbsolutePath: String): io.horizen.utils.Pair[Array[Byte], java.lang.Long] = {

    val (_: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      certificateData.schnorrKeyPairs.map {
        case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)
      }.unzip

    log.info(s"Start generating proof with parameters: certificateData = $certificateData, " +
      s"signersThreshold = ${
        params.signersThreshold
      }. " +
      s"It can take a while.")

    //create and return proof with quality
    val sidechainCreationVersion: SidechainCreationVersion = params.sidechainCreationVersion
    circuit.createProof(
      certificateData.backwardTransfers.asJava,
      certificateData.sidechainId,
      certificateData.referencedEpochNumber,
      certificateData.endEpochCumCommTreeHash,
      certificateData.btrFee,
      certificateData.ftMinAmount,
      signaturesBytes.asJava,
      certificateData.schnorrKeysSignatures,
      params.signersThreshold,
      certificateData.previousCertificateOption.asJava,
      sidechainCreationVersion.id,
      certificateData.genesisKeysRootHash,
      certificateData.getCustomFields.asJava,
      provingFileAbsolutePath,
      true,
      true
    )
  }

  override def buildCertificateData(history: HIS, state: MS, status: SignaturesStatus): CertificateDataWithKeyRotation = {

    val backwardTransfers: Seq[BackwardTransfer] = state.backwardTransfers(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, state, status.referencedEpoch)
    val sidechainId = params.sidechainId

    val previousCertificateOption: Option[WithdrawalEpochCertificate] = state.certificate(status.referencedEpoch - 1)

    val schnorrKeysSignatures = getSchnorrKeysSignatures(state, status.referencedEpoch)

    val sc2ScDataForCertificate: Option[Sc2ScDataForCertificate] =
      sc2scConfig.canSendMessages match {
        case true => Some(getDataForCertificateCreation(status.referencedEpoch, state, history, params))
        case false => None
      }

    val signersPublicKeyWithSignatures = schnorrKeysSignatures.schnorrSigners.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (new SchnorrProposition(pubKey.pubKeyBytes()), status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    CertificateDataWithKeyRotation(
      status.referencedEpoch,
      sidechainId,
      backwardTransfers,
      endEpochCumCommTreeHash,
      sc2ScDataForCertificate,
      btrFee,
      ftMinAmount,
      signersPublicKeyWithSignatures,
      schnorrKeysSignatures,
      previousCertificateOption,
      circuit.generateKeysRootHash(
        params.signersPublicKeys.map(_.pubKeyBytes()).toList.asJava,
        params.mastersPublicKeys.map(_.pubKeyBytes()).toList.asJava)
    )
  }

  override def getMessageToSignAndPublicKeys(history: HIS, state: MS, referencedWithdrawalEpochNumber: Int): Try[(Array[Byte], Seq[SchnorrProposition])] = Try {
    val backwardTransfers: Seq[BackwardTransfer] = state.backwardTransfers(referencedWithdrawalEpochNumber)

    val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
    val ftMinAmount: Long = getFtMinAmount(referencedWithdrawalEpochNumber)

    val endEpochCumCommTreeHash: Array[Byte] = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, state, referencedWithdrawalEpochNumber)
    val sidechainId = params.sidechainId

    val keysAndSignatures: SchnorrKeysSignatures = getSchnorrKeysSignatures(state, referencedWithdrawalEpochNumber)
    val keysRootHash: Array[Byte] = circuit.getSchnorrKeysHash(keysAndSignatures)

    val sc2ScDataForCertificate: Option[Sc2ScDataForCertificate] =  if (sc2scConfig.canSendMessages) {
      Some(getDataForCertificateCreation(referencedWithdrawalEpochNumber, state, history, params))
    } else {
      None
    }

    val previousCertificateBytes: Array[Byte] = sc2ScDataForCertificate match {
      case Some(sc2scData) => sc2scData.previousTopQualityCertificateHash match {
        case Some(cert) => cert
        case None => Array.emptyByteArray
      }
      case None => Array.emptyByteArray
    }

    val messageTreeRootHash = sc2ScDataForCertificate match {
      case Some(sc2scData) => sc2scData.messagesTreeRoot
      case None => Array.emptyByteArray
    }

    val message = circuit.generateMessageToBeSigned(backwardTransfers.asJava, sidechainId, referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash, btrFee, ftMinAmount, Seq(keysRootHash, messageTreeRootHash, previousCertificateBytes).asJava)

    // For circuit with key rotation signing keys can be changed
    (message, keysAndSignatures.schnorrSigners)
  }

  private def getSchnorrKeysSignatures(state: MS, referencedWithdrawalEpochNumber: Int): SchnorrKeysSignatures = {
    val prevCertifierKeys: CertifiersKeys = state.certifiersKeys(referencedWithdrawalEpochNumber - 1)
      .getOrElse(throw new RuntimeException(s"Certifiers keys for previous withdrawal epoch are not present"))
    val newCertifierKeys: CertifiersKeys = state.certifiersKeys(referencedWithdrawalEpochNumber)
      .getOrElse(throw new RuntimeException(s"Certifiers keys for current withdrawal epoch are not present"))
    val (updatedSigningKeysSkSignatures, updatedSigningKeysMkSignatures) = (for (i <- newCertifierKeys.signingKeys.indices) yield {
      if (prevCertifierKeys.signingKeys(i) != newCertifierKeys.signingKeys(i)) {
        state.keyRotationProof(referencedWithdrawalEpochNumber, i, keyType = 0) match {
          case Some(keyRotationProof: KeyRotationProof) =>
            (Option.apply(keyRotationProof.signingKeySignature), Option.apply(keyRotationProof.masterKeySignature))
          case _ =>
            throw new RuntimeException(s"Key rotation proof of signing key is not present for certifier with index $i")
        }
      } else {
        (Option.empty, Option.empty)
      }
    }).unzip

    val (updatedMasterKeysSkSignatures, updatedMasterKeysMkSignatures) = (for (i <- newCertifierKeys.masterKeys.indices) yield {
      if (prevCertifierKeys.masterKeys(i) != newCertifierKeys.masterKeys(i)) {
        state.keyRotationProof(referencedWithdrawalEpochNumber, i, keyType = 1) match {
          case Some(keyRotationProof: KeyRotationProof) =>
            (Option.apply(keyRotationProof.signingKeySignature), Option.apply(keyRotationProof.masterKeySignature))
          case _ =>
            throw new RuntimeException(s"Key rotation proof of master key is not present for certifier with index $i")
        }
      } else {
        (Option.empty, Option.empty)
      }
    }).unzip

    SchnorrKeysSignatures(
      prevCertifierKeys.signingKeys,
      prevCertifierKeys.masterKeys,
      newCertifierKeys.signingKeys,
      newCertifierKeys.masterKeys,
      updatedSigningKeysSkSignatures,
      updatedSigningKeysMkSignatures,
      updatedMasterKeysSkSignatures,
      updatedMasterKeysMkSignatures
    )
  }
}
