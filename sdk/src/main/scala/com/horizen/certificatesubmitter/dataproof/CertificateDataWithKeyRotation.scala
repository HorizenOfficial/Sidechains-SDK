package com.horizen.certificatesubmitter.dataproof

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignatures
import com.horizen.certnative.BackwardTransfer
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.sc2sc.Sc2ScDataForCertificate
import com.horizen.utils.BytesUtils

import scala.collection.JavaConverters._


case class CertificateDataWithKeyRotation(override val referencedEpochNumber: Int,
                                          override val sidechainId: Array[Byte],
                                          override val backwardTransfers: Seq[BackwardTransfer],
                                          override val endEpochCumCommTreeHash: Array[Byte],
                                          override val sc2ScDataForCertificate: Option[Sc2ScDataForCertificate],
                                          override val btrFee: Long,
                                          override val ftMinAmount: Long,
                                          override val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                          schnorrKeysSignatures: SchnorrKeysSignatures,
                                          previousCertificateOption: Option[WithdrawalEpochCertificate],
                                          genesisKeysRootHash: Array[Byte]
                                         )
  extends CertificateData(referencedEpochNumber, sidechainId, backwardTransfers, endEpochCumCommTreeHash, sc2ScDataForCertificate, btrFee, ftMinAmount, schnorrKeyPairs) {

  override def getCustomFields: Seq[Array[Byte]] = {
    //TODO: use sc2ScDataForCertificate to add custom fields for sc2sc
    val keysRootHash: Array[Byte] = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.getSchnorrKeysHash(schnorrKeysSignatures)
    CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.getCertificateCustomFields(keysRootHash).asScala
  }

  override def toString: String = {
    "CertificateDataWithoutKeyRotation(" +
      s"referencedEpochNumber = $referencedEpochNumber, " +
      s"sidechainId = ${sidechainId.mkString("Array(", ", ", ")")}, " +
      s"withdrawalRequests = {${backwardTransfers.mkString(",")}}, " +
      s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
      s"btrFee = $btrFee, " +
      s"ftMinAmount = $ftMinAmount, " +
      s"customFields = ${getCustomFields.map(BytesUtils.toHexString)}, " +
      s"number of schnorrKeyPairs = ${schnorrKeyPairs.size}), " +
      s"schnorr key signatures = ${schnorrKeysSignatures.toString}), " +
      s"previous certificate = ${previousCertificateOption.getOrElse("").toString})"
  }
}
