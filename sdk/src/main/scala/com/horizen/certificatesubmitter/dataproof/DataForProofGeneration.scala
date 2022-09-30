package com.horizen.certificatesubmitter.dataproof

import com.horizen.box.WithdrawalRequestBox

abstract class DataForProofGeneration(val referencedEpochNumber: Int,
                                      val sidechainId: Array[Byte],
                                      val withdrawalRequests: Seq[WithdrawalRequestBox],
                                      val endEpochCumCommTreeHash: Array[Byte],
                                      val btrFee: Long,
                                      val ftMinAmount: Long,
                                      val customFields: Seq[Array[Byte]])


