package io.horizen.certificatesubmitter.dataproof

import com.horizen.certnative.BackwardTransfer
import io.horizen.proof.SchnorrProof
import io.horizen.proposition.SchnorrProposition
import io.horizen.utxo.box.WithdrawalRequestBox
import io.horizen.sc2sc.Sc2ScDataForCertificate

abstract class CertificateData(val referencedEpochNumber: Int,
                               val sidechainId: Array[Byte],
                               val backwardTransfers: Seq[BackwardTransfer],
                               val endEpochCumCommTreeHash: Array[Byte],
                               val sc2ScDataForCertificate: Option[Sc2ScDataForCertificate],
                               val btrFee: Long,
                               val ftMinAmount: Long,
                               val schnorrKeyPairs: Seq[(SchnorrProposition, Option[SchnorrProof])],
                                     ) {
  def getCustomFields: Seq[Array[Byte]]
}


