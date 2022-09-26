package com.horizen.certificatesubmitter.submitters

import akka.actor.ActorRef
import com.horizen.SidechainSettings
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.params.NetworkParams
import com.horizen.websocket.client.MainchainNodeChannel

import java.util.Optional
import scala.concurrent.ExecutionContext

class ThresholdSigCircuitSubmitter(settings: SidechainSettings,
                                                  sidechainNodeViewHolderRef: ActorRef,
                                                  params: NetworkParams,
                                                  mainchainChannel: MainchainNodeChannel)
                                                 (implicit ec: ExecutionContext)
  extends CertificateSubmitter(settings, sidechainNodeViewHolderRef, params: NetworkParams, mainchainChannel) {
  @Override
  private def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGenerationWithoutKeyRotation = {
    val history = sidechainNodeView.history
    val state = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId
    val utxoMerkleTreeRoot: Optional[Array[Byte]] = getUtxoMerkleTreeRoot(status.referencedEpoch, state)


    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (pubKey, status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

  }
}
