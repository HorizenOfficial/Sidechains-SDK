package com.horizen.certificatesubmitter.strategies

import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.CertificateDataWithKeyRotation
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, SchnorrKeysSignatures}
import com.horizen.cryptolibprovider.{CryptoLibProvider, ThresholdSignatureCircuitWithKeyRotation}
import com.horizen.params.NetworkParams
import com.horizen.proposition.SchnorrProposition
import com.horizen.{SidechainSettings, SidechainState}

import java.util
import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.Try

class WithKeyRotationCircuitStrategy(settings: SidechainSettings, params: NetworkParams,
                                     cryptolibCircuit: ThresholdSignatureCircuitWithKeyRotation
                                    ) extends CircuitStrategy[CertificateDataWithKeyRotation](settings, params) {

  override def generateProof(certificateData: CertificateDataWithKeyRotation, provingFileAbsolutePath: String): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {

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
    cryptolibCircuit.createProof(
      certificateData.withdrawalRequests.asJava,
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
      provingFileAbsolutePath,
      true,
      true
    )
  }

  override def buildCertificateData(sidechainNodeView: View, status: SignaturesStatus): CertificateDataWithKeyRotation = {
    val history = sidechainNodeView.history
    val state: SidechainState = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId

    val previousCertificateOption: Option[WithdrawalEpochCertificate] = state.certificate(status.referencedEpoch - 1)


    val schnorrKeysSignatures = getSchnorrKeysSignaturesListBytes(state, status.referencedEpoch)

    val signersPublicKeyWithSignatures = schnorrKeysSignatures.schnorrSigners.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (new SchnorrProposition(pubKey.pubKeyBytes()), status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    CertificateDataWithKeyRotation(
      status.referencedEpoch,
      sidechainId,
      withdrawalRequests,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      signersPublicKeyWithSignatures,
      schnorrKeysSignatures,
      previousCertificateOption,
      CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateKeysRootHash(
        params.signersPublicKeys.map(_.pubKeyBytes()).toList.asJava,
        params.mastersPublicKeys.map(_.pubKeyBytes()).toList.asJava)
    )
  }

  override def getMessageToSign(sidechainNodeView: View, referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(referencedWithdrawalEpochNumber)

    val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
    val ftMinAmount: Long = getFtMinAmount(referencedWithdrawalEpochNumber)

    val endEpochCumCommTreeHash: Array[Byte] = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
    val sidechainId = params.sidechainId

    val keysRootHash: Array[Byte] = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
      .getSchnorrKeysHash(getSchnorrKeysSignaturesListBytes(state, referencedWithdrawalEpochNumber))

    val message = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
      .generateMessageToBeSigned(withdrawalRequests.asJava, sidechainId, referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash, btrFee, ftMinAmount, keysRootHash)
    message
  }

  private def getSchnorrKeysSignaturesListBytes(state: SidechainState, referencedWithdrawalEpochNumber: Int): SchnorrKeysSignatures = {
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
