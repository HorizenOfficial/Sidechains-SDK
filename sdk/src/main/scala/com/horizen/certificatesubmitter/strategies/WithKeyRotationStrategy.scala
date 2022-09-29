package com.horizen.certificatesubmitter.strategies

import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.CertificateDataWithKeyRotation
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, SchnorrKeysSignaturesListBytes}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.params.NetworkParams
import com.horizen.{SidechainSettings, SidechainState}

import java.util
import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.Try

class WithKeyRotationStrategy(settings: SidechainSettings, params: NetworkParams) extends KeyRotationStrategy[CertificateDataWithKeyRotation](settings, params) {

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

    val schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      certificateData.schnorrKeysSignaturesListBytes.schnorrSignersPublicKeysBytesList,
      certificateData.schnorrKeysSignaturesListBytes.schnorrMastersPublicKeysBytesList,
      certificateData.schnorrKeysSignaturesListBytes.newSchnorrSignersPublicKeysBytesList,
      certificateData.schnorrKeysSignaturesListBytes.newSchnorrMastersPublicKeysBytesList,
      certificateData.schnorrKeysSignaturesListBytes.updatedSigningKeysSkSignatures,
      certificateData.schnorrKeysSignaturesListBytes.updatedSigningKeysMkSignatures,
      certificateData.schnorrKeysSignaturesListBytes.updatedMasterKeysSkSignatures,
      certificateData.schnorrKeysSignaturesListBytes.updatedMasterKeysMkSignatures
    )
    //create and return proof with quality
    val sidechainCreationVersion: SidechainCreationVersion = params.sidechainCreationVersion
    CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.createProof(
      certificateData.withdrawalRequests.asJava,
      certificateData.sidechainId,
      certificateData.referencedEpochNumber,
      certificateData.endEpochCumCommTreeHash,
      certificateData.btrFee,
      certificateData.ftMinAmount,
      scala.collection.JavaConverters.seqAsJavaList(certificateData.getCustomFields),
      signaturesBytes.asJava,
      schnorrKeysSignaturesListBytes,
      params.signersThreshold,
      certificateData.previousCertificateOption.asJava,
      sidechainCreationVersion.id,
      certificateData.genesisKeysRootHash,
      provingFileAbsolutePath,
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

    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (pubKey, status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    val actualKeysOption: Option[CertifiersKeys] = state.certifiersKeys(status.referencedEpoch)
    val previousCertificateOption: Option[WithdrawalEpochCertificate] = state.certificate(status.referencedEpoch - 1)

//    val keyRotationProofs: Seq[KeyRotationProof] = state.keyRotationProofs(status.referencedEpoch)
    val schnorrSignersPublicKeysBytesList: IndexedSeq[Array[Byte]] = actualKeysOption match {
      case Some(actualKeys) => actualKeys.signingKeys.map(_.bytes())
      case None => IndexedSeq[Array[Byte]]()
    }
    val schnorrMastersPublicKeysBytesList: IndexedSeq[Array[Byte]] = actualKeysOption match {
      case Some(actualKeys) => actualKeys.masterKeys.map(_.bytes())
      case None => IndexedSeq[Array[Byte]]()
    }

    val (newSchnorrSignersPublicKeysBytesList, updatedSigningKeysSkSignatures, updatedSigningKeysMkSignatures) = (for{
      indexOfSigner <- schnorrSignersPublicKeysBytesList.indices
    } yield {
      state.keyRotationProof(status.referencedEpoch, indexOfSigner, keyType = 0) match {
        case Some(keyRotationProof: KeyRotationProof) =>
          (keyRotationProof.newValueOfKey.bytes(), keyRotationProof.signingKeySignature.bytes(), keyRotationProof.masterKeySignature.bytes())
        case _ => (schnorrSignersPublicKeysBytesList(indexOfSigner), null, null)
      }
    }).unzip3

    val (newSchnorrMastersPublicKeysBytesList, updatedMasterKeysSkSignatures, updatedMasterKeysMkSignatures) = (for {
      indexOfSigner <- schnorrMastersPublicKeysBytesList.indices
    } yield {
      state.keyRotationProof(status.referencedEpoch, indexOfSigner, keyType = 1) match {
        case Some(keyRotationProof: KeyRotationProof) =>
          (keyRotationProof.newValueOfKey.bytes(), keyRotationProof.signingKeySignature.bytes(), keyRotationProof.masterKeySignature.bytes())
        case _ => (schnorrMastersPublicKeysBytesList(indexOfSigner), null, null)
      }
    }).unzip3
    

    val schnorrKeysSignaturesListBytes =  SchnorrKeysSignaturesListBytes(
      schnorrSignersPublicKeysBytesList,
      schnorrMastersPublicKeysBytesList,
      newSchnorrSignersPublicKeysBytesList,
      newSchnorrMastersPublicKeysBytesList,
      updatedSigningKeysSkSignatures,
      updatedSigningKeysMkSignatures,
      updatedMasterKeysSkSignatures,
      updatedMasterKeysMkSignatures
    )

    CertificateDataWithKeyRotation(
      status.referencedEpoch,
      sidechainId,
      withdrawalRequests,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      signersPublicKeyWithSignatures,
      schnorrKeysSignaturesListBytes,
      previousCertificateOption,
      CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateKeysRootHash(
        scala.collection.JavaConverters.seqAsJavaList(params.signersPublicKeys.map(sp => sp.bytes())),
        scala.collection.JavaConverters.seqAsJavaList(params.mastersPublicKeys.map(sp => sp.bytes())))
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

    val customFields: Array[Byte] = state.certifiersKeys(referencedWithdrawalEpochNumber) match {
      case Some(keys) => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .generateKeysRootHash(scala.collection.JavaConverters.seqAsJavaList(keys.signingKeys.map(sp => sp.bytes())),
          scala.collection.JavaConverters.seqAsJavaList(keys.masterKeys.map(sp => sp.bytes())))
      case None => Array[Byte]()
    }

    CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
      .generateMessageToBeSigned(withdrawalRequests.asJava, sidechainId, referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash, btrFee, ftMinAmount, util.Arrays.asList(customFields))
  }
}
