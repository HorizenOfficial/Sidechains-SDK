package com.horizen.certificatesubmitter.strategies
import com.horizen.block.SidechainBlock
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.params.NetworkParams
import com.horizen.utils.WithdrawalEpochInfo

class NonCeasingSidechain() extends CertificateSubmissionStrategy {
  private val nonCeasingSubmissionDelay = 1 // TBD length: at the moment like for ceasing sidechains

  override def getStatus(sidechainNodeView: View, block: SidechainBlock): SubmissionWindowStatus = {
    // Take withdrawal epoch info for block from the History.
    // Note: We can't rely on `State.getWithdrawalEpochInfo`, because it shows the tip info,
    // but the older block may being applied at the moment.
    val withdrawalEpochInfo: WithdrawalEpochInfo = sidechainNodeView.history.blockInfoById(block.id).withdrawalEpochInfo

    // Withdrawal epoch for which NEXT certificate needs to be applied
    val nextCertReferencedEpochNumber = sidechainNodeView.state.lastCertificateReferencedEpoch().getOrElse(-1) + 1

    val certSubmissionEpoch = nextCertReferencedEpochNumber + 1

    if (certSubmissionEpoch < withdrawalEpochInfo.epoch) {
      // Block belongs to the epoch after the next certificate submission epoch
      // It means that sidechain skips submission for some time
      SubmissionWindowStatus(nextCertReferencedEpochNumber, isInWindow = true)
    } else if (certSubmissionEpoch == withdrawalEpochInfo.epoch && withdrawalEpochInfo.lastEpochIndex >= nonCeasingSubmissionDelay) {
      // Block belongs to the SAME epoch as the next certificate must be submitted
      // Moreover, the submission delay passed.
      SubmissionWindowStatus(nextCertReferencedEpochNumber, isInWindow = true)
    } else {
      // Block belongs to the SAME epoch as the next certificate must be submitted
      // But the submission delay is not passed
      SubmissionWindowStatus(nextCertReferencedEpochNumber, isInWindow = false)
    }
  }

  // No need to check quality for non-ceasing case.
  // Only one certificate per epoch is allowed.
  override def checkQuality(status: SignaturesStatus): Boolean = true
}
