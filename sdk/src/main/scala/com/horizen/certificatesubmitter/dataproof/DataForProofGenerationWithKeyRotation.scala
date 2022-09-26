package com.horizen.certificatesubmitter.dataproof

import com.horizen.box.WithdrawalRequestBox
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.utils.BytesUtils


case class DataForProofGenerationWithKeyRotation(referencedEpochNumber: Int,
                                                 sidechainId: Array[Byte],
                                                 withdrawalRequests: Seq[WithdrawalRequestBox],
                                                 endEpochCumCommTreeHash: Array[Byte],
                                                 btrFee: Long,
                                                 ftMinAmount: Long,
                                                 customFields: Seq[Array[Byte]],
                                                 schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])])
  extends DataForProofGeneration (referencedEpochNumber, sidechainId, withdrawalRequests, endEpochCumCommTreeHash, btrFee, ftMinAmount, customFields, schnorrKeyPairs) {
  override def toString: String = {
    "DataForProofGeneration(" +
      s"referencedEpochNumber = $referencedEpochNumber, " +
      s"sidechainId = $sidechainId, " +
      s"withdrawalRequests = {${withdrawalRequests.mkString(",")}}, " +
      s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
      s"btrFee = $btrFee, " +
      s"ftMinAmount = $ftMinAmount, " +
      s"utxoMerkleTreeRoot = ${customFields.map(BytesUtils.toHexString)}, " + // from this field different fields for 2 circuits
      s"number of schnorrKeyPairs = ${schnorrKeyPairs.size})"
  }
}
