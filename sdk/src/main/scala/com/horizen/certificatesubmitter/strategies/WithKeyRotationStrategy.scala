package com.horizen.certificatesubmitter.strategies

import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.CertificateDataWithKeyRotation
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes.getSchnorrKeysSignaturesList
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, SchnorrKeysSignaturesListBytes}
import com.horizen.cryptolibprovider.{CryptoLibProvider, ThresholdSignatureCircuitWithKeyRotation}
import com.horizen.params.NetworkParams
import com.horizen.proposition.SchnorrProposition
import com.horizen.{SidechainSettings, SidechainState}

import java.util
import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.Try

class WithKeyRotationStrategy(settings: SidechainSettings, params: NetworkParams,
                              cryptolibCircuit: ThresholdSignatureCircuitWithKeyRotation
                             ) extends KeyRotationStrategy[CertificateDataWithKeyRotation](settings, params) {

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
      scala.collection.JavaConverters.seqAsJavaList(certificateData.getCustomFields),
      signaturesBytes.asJava,
      certificateData.schnorrKeysSignaturesListBytes,
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


    val schnorrKeysSignaturesListBytes =  getSchnorrKeysSignaturesListBytes(state, status.referencedEpoch)

    val signersPublicKeyWithSignatures = schnorrKeysSignaturesListBytes.schnorrSignersPublicKeysBytesList.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (new SchnorrProposition(pubKey), status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

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
        scala.collection.JavaConverters.seqAsJavaList(params.signersPublicKeys.map(sp => sp.pubKeyBytes())),
        scala.collection.JavaConverters.seqAsJavaList(params.mastersPublicKeys.map(sp => sp.pubKeyBytes())))
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

    val actualKeys = getSchnorrKeysSignaturesList(getSchnorrKeysSignaturesListBytes(state, referencedWithdrawalEpochNumber))
    val customFields = actualKeys.getUpdatedKeysRootHash(actualKeys.getSigningKeys.length).serializeFieldElement()

    CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
      .generateMessageToBeSigned(withdrawalRequests.asJava, sidechainId, referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash, btrFee, ftMinAmount, util.Arrays.asList(customFields))
  }

  private def getSchnorrKeysSignaturesListBytes(state: SidechainState, referencedWithdrawalEpochNumber: Int): SchnorrKeysSignaturesListBytes = {
    val actualKeysOption: Option[CertifiersKeys] = state.certifiersKeys(referencedWithdrawalEpochNumber)
    val schnorrSignersPublicKeysBytesList: IndexedSeq[Array[Byte]] = actualKeysOption match {
      case Some(actualKeys) => actualKeys.signingKeys.map(_.pubKeyBytes())
      case None => IndexedSeq[Array[Byte]]()
    }
    val schnorrMastersPublicKeysBytesList: IndexedSeq[Array[Byte]] = actualKeysOption match {
      case Some(actualKeys) => actualKeys.masterKeys.map(_.pubKeyBytes())
      case None => IndexedSeq[Array[Byte]]()
    }

    val (newSchnorrSignersPublicKeysBytesList, updatedSigningKeysSkSignatures, updatedSigningKeysMkSignatures) = (for{
      indexOfSigner <- schnorrSignersPublicKeysBytesList.indices
    } yield {
      state.keyRotationProof(referencedWithdrawalEpochNumber, indexOfSigner, keyType = 0) match {
        case Some(keyRotationProof: KeyRotationProof) =>
          (keyRotationProof.newValueOfKey.bytes(), Option.apply(keyRotationProof.signingKeySignature.bytes()), Option.apply(keyRotationProof.masterKeySignature.bytes()))
        case _ => (schnorrSignersPublicKeysBytesList(indexOfSigner), Option.empty, Option.empty)
      }
    }).unzip3

    val (newSchnorrMastersPublicKeysBytesList, updatedMasterKeysSkSignatures, updatedMasterKeysMkSignatures) = (for {
      indexOfMaster <- schnorrMastersPublicKeysBytesList.indices
    } yield {
      state.keyRotationProof(referencedWithdrawalEpochNumber, indexOfMaster, keyType = 1) match {
        case Some(keyRotationProof: KeyRotationProof) =>
          (keyRotationProof.newValueOfKey.bytes(), Option.apply(keyRotationProof.signingKeySignature.bytes()), Option.apply(keyRotationProof.masterKeySignature.bytes()))
        case _ => (schnorrMastersPublicKeysBytesList(indexOfMaster), Option.empty,  Option.empty)
      }
    }).unzip3

    SchnorrKeysSignaturesListBytes(
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
      cryptolibCircuit.generateKeysRootHash(
        scala.collection.JavaConverters.seqAsJavaList(params.signersPublicKeys.map(sp => sp.pubKeyBytes())),
        scala.collection.JavaConverters.seqAsJavaList(params.mastersPublicKeys.map(sp => sp.pubKeyBytes())))
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
      case Some(keys) => cryptolibCircuit
        .generateKeysRootHash(scala.collection.JavaConverters.seqAsJavaList(keys.signingKeys.map(sp => sp.bytes())),
          scala.collection.JavaConverters.seqAsJavaList(keys.masterKeys.map(sp => sp.bytes())))
      case None => Array[Byte]()
    }

    cryptolibCircuit
      .generateMessageToBeSigned(withdrawalRequests.asJava, sidechainId, referencedWithdrawalEpochNumber,
        endEpochCumCommTreeHash, btrFee, ftMinAmount, util.Arrays.asList(customFields))
  }
}
