package com.horizen.certificatesubmitter.dataproof

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignatures
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.utils.BytesUtils

import scala.collection.JavaConverters._


case class CertificateDataWithKeyRotation(override val referencedEpochNumber: Int,
                                          override val sidechainId: Array[Byte],
                                          override val withdrawalRequests: Seq[WithdrawalRequestBox],
                                          override val endEpochCumCommTreeHash: Array[Byte],
                                          override val btrFee: Long,
                                          override val ftMinAmount: Long,
                                          override val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                          schnorrKeysSignatures: SchnorrKeysSignatures,
                                          previousCertificateOption: Option[WithdrawalEpochCertificate],
                                          genesisKeysRootHash: Array[Byte]
                                         )
  extends CertificateData(referencedEpochNumber, sidechainId, withdrawalRequests, endEpochCumCommTreeHash, btrFee, ftMinAmount, schnorrKeyPairs) {

  override def getCustomFields: Seq[Array[Byte]] = {
    val validatorKeysUpdatesList = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.getSchnorrKeysSignaturesList(schnorrKeysSignatures)
    val customData = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
      .getCertificateCustomFields(
        Seq(validatorKeysUpdatesList.getUpdatedKeysRootHash.serializeFieldElement()).toList.asJava).iterator().asScala.toSeq
    validatorKeysUpdatesList.getSigningKeys.foreach(_.freePublicKey())
    validatorKeysUpdatesList.getMasterKeys.foreach(_.freePublicKey())
    validatorKeysUpdatesList.getUpdatedSigningKeys.foreach(_.freePublicKey())
    validatorKeysUpdatesList.getUpdatedMasterKeys.foreach(_.freePublicKey())
    validatorKeysUpdatesList.getUpdatedSigningKeysSkSignatures.foreach(_.freeSignature())
    validatorKeysUpdatesList.getUpdatedSigningKeysMkSignatures.foreach(_.freeSignature())
    validatorKeysUpdatesList.getUpdatedMasterKeysSkSignatures.foreach(_.freeSignature())
    validatorKeysUpdatesList.getUpdatedMasterKeysMkSignatures.foreach(_.freeSignature())
    customData
  }

  override def toString: String = {
    "CertificateDataWithoutKeyRotation(" +
      s"referencedEpochNumber = $referencedEpochNumber, " +
      s"sidechainId = ${sidechainId.mkString("Array(", ", ", ")")}, " +
      s"withdrawalRequests = {${withdrawalRequests.mkString(",")}}, " +
      s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
      s"btrFee = $btrFee, " +
      s"ftMinAmount = $ftMinAmount, " +
      s"customFields = ${getCustomFields.map(BytesUtils.toHexString)}, " +
      s"number of schnorrKeyPairs = ${schnorrKeyPairs.size}), " +
      s"schnorr key signatures = ${schnorrKeysSignatures.toString}), " +
      s"previous certificate = ${previousCertificateOption.getOrElse("").toString})"
  }
}
