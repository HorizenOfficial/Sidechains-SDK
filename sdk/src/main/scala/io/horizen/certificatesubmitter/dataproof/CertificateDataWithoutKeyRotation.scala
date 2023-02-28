package io.horizen.certificatesubmitter.dataproof

import com.horizen.certnative.BackwardTransfer
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.utils.BytesUtils

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.compat.java8.OptionConverters._

case class CertificateDataWithoutKeyRotation(override val referencedEpochNumber: Int,
                                             override val sidechainId: Array[Byte],
                                             override val backwardTransfers: Seq[BackwardTransfer],
                                             override val endEpochCumCommTreeHash: Array[Byte],
                                             override val btrFee: Long,
                                             override val ftMinAmount: Long,
                                             override val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                             utxoMerkleTreeRoot: Option[Array[Byte]])
  extends CertificateData (referencedEpochNumber, sidechainId, backwardTransfers, endEpochCumCommTreeHash, btrFee, ftMinAmount, schnorrKeyPairs) {

  override def getCustomFields: Seq[Array[Byte]] = {
    CryptoLibProvider.sigProofThresholdCircuitFunctions.getCertificateCustomFields(utxoMerkleTreeRoot.asJava).toSeq
  }

  override def toString: String = {
    "CertificateDataWithKeyRotation(" +
      s"referencedEpochNumber = $referencedEpochNumber, " +
      s"sidechainId = $sidechainId, " +
      s"withdrawalRequests = {${backwardTransfers.mkString(",")}}, " +
      s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
      s"btrFee = $btrFee, " +
      s"ftMinAmount = $ftMinAmount, " +
      s"utxoMerkleTreeRoot = ${utxoMerkleTreeRoot.map(BytesUtils.toHexString)}, " +
      s"number of schnorrKeyPairs = ${schnorrKeyPairs.size})"
  }
}
