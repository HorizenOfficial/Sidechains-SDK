package com.horizen.certificatesubmitter.dataproof

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.WithdrawalRequestBox
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.utils.BytesUtils


case class DataForProofGenerationWithKeyRotation(override val referencedEpochNumber: Int,
                                                 override val sidechainId: Array[Byte],
                                                 override val withdrawalRequests: Seq[WithdrawalRequestBox],
                                                 override val endEpochCumCommTreeHash: Array[Byte],
                                                 override val btrFee: Long,
                                                 override val ftMinAmount: Long,
                                                 override val customFields: Seq[Array[Byte]],
                                                 override val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                                 schnorrSignersPublicKeysBytesList: Seq[Array[Byte]],
                                                 schnorrMastersPublicKeysBytesList: Seq[Array[Byte]],
                                                 newSchnorrSignersPublicKeysBytesList: Seq[Array[Byte]],
                                                 newSchnorrMastersPublicKeysBytesList: Seq[Array[Byte]],
                                                 val previousCertificateOption: Option[WithdrawalEpochCertificate])
  extends DataForProofGeneration(referencedEpochNumber, sidechainId, withdrawalRequests, endEpochCumCommTreeHash, btrFee, ftMinAmount, customFields, schnorrKeyPairs) {
  override def toString: String = {
    "DataForProofGeneration(" +
      s"referencedEpochNumber = $referencedEpochNumber, " +
      s"sidechainId = $sidechainId, " +
      s"withdrawalRequests = {${withdrawalRequests.mkString(",")}}, " +
      s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
      s"btrFee = $btrFee, " +
      s"ftMinAmount = $ftMinAmount, " +
      s"customFields = ${customFields.map(BytesUtils.toHexString)}, " +
      s"number of schnorrKeyPairs = ${schnorrKeyPairs.size})" +
      s"signers public keys = ${schnorrSignersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"masters public keys = ${schnorrMastersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"new signers public keys = ${newSchnorrSignersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"new masters public keys = ${newSchnorrMastersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"previous certificate = ${previousCertificateOption.getOrElse("").toString})"
  }
}
