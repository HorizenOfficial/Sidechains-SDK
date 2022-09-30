package com.horizen.certificatesubmitter.dataproof

import com.horizen.box.WithdrawalRequestBox
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.utils.BytesUtils

case class DataForProofGenerationWithoutKeyRotation(override val referencedEpochNumber: Int,
                                                    override val sidechainId: Array[Byte],
                                                    override val withdrawalRequests: Seq[WithdrawalRequestBox],
                                                    override val endEpochCumCommTreeHash: Array[Byte],
                                                    override val btrFee: Long,
                                                    override val ftMinAmount: Long,
                                                    override val customFields: Seq[Array[Byte]],
                                                    schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])])
  extends DataForProofGeneration (referencedEpochNumber, sidechainId, withdrawalRequests, endEpochCumCommTreeHash, btrFee, ftMinAmount, customFields) {
  override def toString: String = {
    "DataForProofGeneration(" +
      s"referencedEpochNumber = $referencedEpochNumber, " +
      s"sidechainId = $sidechainId, " +
      s"withdrawalRequests = {${withdrawalRequests.mkString(",")}}, " +
      s"endEpochCumCommTreeHash = ${BytesUtils.toHexString(endEpochCumCommTreeHash)}, " +
      s"btrFee = $btrFee, " +
      s"ftMinAmount = $ftMinAmount, " +
      s"customFields = ${customFields.map(BytesUtils.toHexString)}, " + // from this field different fields for 2 circuits
      s"number of schnorrKeyPairs = ${schnorrKeyPairs.size})"
  }
}

