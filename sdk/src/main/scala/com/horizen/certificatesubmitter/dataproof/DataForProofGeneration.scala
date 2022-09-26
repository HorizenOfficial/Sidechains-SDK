package com.horizen.certificatesubmitter.dataproof

import com.horizen.box.WithdrawalRequestBox
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition

abstract class DataForProofGeneration(referencedEpochNumber: Int,
                                      sidechainId: Array[Byte],
                                      withdrawalRequests: Seq[WithdrawalRequestBox],
                                      endEpochCumCommTreeHash: Array[Byte],
                                      btrFee: Long,
                                      ftMinAmount: Long,
                                      customFields: Seq[Array[Byte]],
                                      schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])])


