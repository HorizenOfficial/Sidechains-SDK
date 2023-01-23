package com.horizen.certificatesubmitter.strategies
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.strategies.NonCeasingSidechain.NON_CEASING_SUBMISSION_DELAY
import com.horizen.params.NetworkParams
import com.horizen.utils.WithdrawalEpochInfo
import com.horizen.{AbstractHistory, AbstractState}
import scorex.util.ModifierId

class NonCeasingSidechain(params: NetworkParams) extends CertificateSubmissionStrategy {

  override def getStatus[
    H <: AbstractHistory[_, _, _, _, _, _],
    S <: AbstractState[_, _, _, _]
  ](history: H, state: S, id: ModifierId): SubmissionWindowStatus = {
    // Take withdrawal epoch info for block from the History.
    // Note: We can't rely on `State.getWithdrawalEpochInfo`, because it shows the tip info,
    // but the older block may being applied at the moment.
    val withdrawalEpochInfo: WithdrawalEpochInfo = history.blockInfoById(id).withdrawalEpochInfo

    // Withdrawal epoch for which NEXT certificate needs to be applied
    val nextCertReferencedEpochNumber = state.lastCertificateReferencedEpoch.getOrElse(-1) + 1

    val certSubmissionEpoch = nextCertReferencedEpochNumber + 1

    if (certSubmissionEpoch < withdrawalEpochInfo.epoch) {
      // Block belongs to the epoch after the next certificate submission epoch
      // It means that sidechain skips submission for some time
      SubmissionWindowStatus(nextCertReferencedEpochNumber, isInWindow = true)
    } else if (certSubmissionEpoch == withdrawalEpochInfo.epoch && withdrawalEpochInfo.lastEpochIndex >= NON_CEASING_SUBMISSION_DELAY) {
      // Block belongs to the SAME epoch as the next certificate must be submitted
      // Moreover, the submission delay passed.
      SubmissionWindowStatus(nextCertReferencedEpochNumber, isInWindow = true)
    } else {
      // Block belongs to the SAME epoch as the next certificate must be submitted
      // But the submission delay is not passed
      SubmissionWindowStatus(nextCertReferencedEpochNumber, isInWindow = false)
    }
  }

  override def checkQuality(status: SignaturesStatus): Boolean = {
    // No need to check quality against other potential lower quality certificates for non-ceasing case.
    // Only one certificate per epoch is allowed.
    status.knownSigs.size >= params.signersThreshold
  }
}

object NonCeasingSidechain{
  // Delay for non-ceasing sidechain is the same as for ceasing sidechain
  // Measured in MC blocks. See `WithdrawalEpochUtils.inSubmitCertificateWindow(...)`
  private val NON_CEASING_SUBMISSION_DELAY: Int = 1
}
