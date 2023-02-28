package io.horizen.certificatesubmitter.dataproof

import io.horizen.block.WithdrawalEpochCertificate
import io.horizen.certificatesubmitter.keys.SchnorrKeysSignatures
import com.horizen.certnative.BackwardTransfer
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.proof.SchnorrProof
import io.horizen.proposition.SchnorrProposition
import io.horizen.utils.BytesUtils
import io.horizen.utxo.box.WithdrawalRequestBox

import scala.collection.JavaConverters._


case class CertificateDataWithKeyRotation(override val referencedEpochNumber: Int,
                                          override val sidechainId: Array[Byte],
                                          override val backwardTransfers: Seq[BackwardTransfer],
                                          override val endEpochCumCommTreeHash: Array[Byte],
                                          override val btrFee: Long,
                                          override val ftMinAmount: Long,
                                          override val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                          schnorrKeysSignatures: SchnorrKeysSignatures,
                                          previousCertificateOption: Option[WithdrawalEpochCertificate],
                                          genesisKeysRootHash: Array[Byte]
                                         )
  extends CertificateData(referencedEpochNumber, sidechainId, backwardTransfers, endEpochCumCommTreeHash, btrFee, ftMinAmount, schnorrKeyPairs) {

  override def getCustomFields: Seq[Array[Byte]] = {
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
