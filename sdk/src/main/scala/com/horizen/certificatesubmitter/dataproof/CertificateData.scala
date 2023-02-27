package com.horizen.certificatesubmitter.dataproof

import com.horizen.certnative.BackwardTransfer
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.utxo.box.WithdrawalRequestBox

abstract class CertificateData(val referencedEpochNumber: Int,
                               val sidechainId: Array[Byte],
                               val backwardTransfers: Seq[BackwardTransfer],
                               val endEpochCumCommTreeHash: Array[Byte],
                               val btrFee: Long,
                               val ftMinAmount: Long,
                               val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                     ) {
  def getCustomFields: Seq[Array[Byte]]
}


