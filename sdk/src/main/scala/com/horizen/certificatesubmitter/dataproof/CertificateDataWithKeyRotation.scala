package com.horizen.certificatesubmitter.dataproof

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes.getSchnorrKeysSignaturesList
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.librustsidechains.Library
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.schnorrnative.SchnorrPublicKey
import com.horizen.utils.BytesUtils

import scala.collection.JavaConverters


object CertificateDataWithKeyRotation {
  Library.load()
}
case class CertificateDataWithKeyRotation(override val referencedEpochNumber: Int,
                                          override val sidechainId: Array[Byte],
                                          override val withdrawalRequests: Seq[WithdrawalRequestBox],
                                          override val endEpochCumCommTreeHash: Array[Byte],
                                          override val btrFee: Long,
                                          override val ftMinAmount: Long,
                                          override val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                          schnorrKeysSignaturesListBytes: SchnorrKeysSignaturesListBytes,
                                          previousCertificateOption: Option[WithdrawalEpochCertificate],
                                          genesisKeysRootHash: Array[Byte]
                                                )
  extends CertificateData(referencedEpochNumber, sidechainId, withdrawalRequests, endEpochCumCommTreeHash, btrFee, ftMinAmount, schnorrKeyPairs) {

  override def getCustomFields: Seq[Array[Byte]] = {
    Seq(getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes).
      getUpdatedKeysRootHash(schnorrKeysSignaturesListBytes.schnorrSignersPublicKeysBytesList.size).
      serializeFieldElement()
    )
  }

  override def toString: String = {
    "DataForProofGeneration(" +
      s"referencedEpochNumber = $referencedEpochNumber, " +
      s"sidechainId = ${sidechainId.mkString("Array(", ", ", ")")}, " +
      s"withdrawalRequests = {${withdrawalRequests.mkString(",")}}, " +
      s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
      s"btrFee = $btrFee, " +
      s"ftMinAmount = $ftMinAmount, " +
      s"customFields = ${getCustomFields.map(BytesUtils.toHexString)}, " +
      s"number of schnorrKeyPairs = ${schnorrKeyPairs.size})" +
      s"signers public keys = ${schnorrKeysSignaturesListBytes.schnorrSignersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"masters public keys = ${schnorrKeysSignaturesListBytes.schnorrMastersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"new signers public keys = ${schnorrKeysSignaturesListBytes.newSchnorrSignersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"new masters public keys = ${schnorrKeysSignaturesListBytes.newSchnorrMastersPublicKeysBytesList.map(BytesUtils.toHexString)}), " +
      s"updated signers keys signing key signatures = ${mapFromOptionalSignature(schnorrKeysSignaturesListBytes.updatedSigningKeysSkSignatures)}), " +
      s"updated signers keys master key signatures = ${mapFromOptionalSignature(schnorrKeysSignaturesListBytes.updatedSigningKeysMkSignatures)}), " +
      s"updated master keys signing key signatures = ${mapFromOptionalSignature(schnorrKeysSignaturesListBytes.updatedMasterKeysSkSignatures)}), " +
      s"updated master keys master key signatures = ${mapFromOptionalSignature(schnorrKeysSignaturesListBytes.updatedMasterKeysMkSignatures)}), " +
      s"previous certificate = ${previousCertificateOption.getOrElse("").toString})"
  }

  private def mapFromOptionalSignature(signatures: Seq[Option[Array[Byte]]]): Seq[String] = {
    signatures.map {
      case Some(k) =>
        BytesUtils.toHexString(k)
      case None =>
        "None"
    }
  }
}
