package io.horizen.certificatesubmitter.dataproof

import com.horizen.certnative.BackwardTransfer
import io.horizen.block.WithdrawalEpochCertificate
import io.horizen.certificatesubmitter.keys.SchnorrKeysSignatures
import io.horizen.cryptolibprovider.{CryptoLibProvider, CustomFieldsReservedPositions}
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.proof.SchnorrProof
import io.horizen.proposition.SchnorrProposition
import io.horizen.sc2sc.Sc2ScDataForCertificate
import io.horizen.utils.BytesUtils

import java.util
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
    val keysRootHash: Array[Byte] = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.getSchnorrKeysHash(schnorrKeysSignatures)
    val orderedCustomFields = prepareCustomFields(keysRootHash)
    val customFields = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.getCertificateCustomFields(orderedCustomFields).asScala
    customFields
  }

  private def prepareCustomFields(keyRootHash: Array[Byte]): util.List[Array[Byte]] = {
    val orderedCustomFields = new util.ArrayList[Array[Byte]]()
    orderedCustomFields.add(keyRootHash)

    sc2ScDataForCertificate.foreach(sc2scData => {
      orderedCustomFields.add(sc2scData.messagesTreeRoot)

      sc2scData.previousTopQualityCertificateHash.foreach {
        cert => orderedCustomFields.add(cert)
      }
    })
    orderedCustomFields
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
