package io.horizen.certificatesubmitter.strategies
import io.horizen.AbstractState
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import io.horizen.certificatesubmitter.strategies.NonCeasingSidechain.NON_CEASING_SUBMISSION_DELAY
import io.horizen.history.AbstractHistory
import io.horizen.params.NetworkParams
import io.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import io.horizen.websocket.client.MainchainNodeChannel
import sparkz.util.{ModifierId, SparkzLogging}

import scala.util.{Failure, Success}

class NonCeasingSidechain(mainchainChannel: MainchainNodeChannel, params: NetworkParams) extends CertificateSubmissionStrategy with SparkzLogging {

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
    if (status.knownSigs.size >= params.signersThreshold && !isCertificatePresent(status.referencedEpoch))
      return true
    false
  }

  private def isCertificatePresent(epoch: Int): Boolean = {
    mainchainChannel.getTopQualityCertificates(BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId))) match {
      case Success(topQualityCertificates) =>
        (topQualityCertificates.mempoolCertInfo, topQualityCertificates.chainCertInfo) match {
          case (Some(mcInfo), _) if mcInfo.epoch == epoch && mcInfo.certHash != null =>
              log.info(s"Submission not needed. Certificate already present in epoch " + epoch)
              return true
          case (Some(mcInfo), _) if mcInfo.epoch > epoch && mcInfo.certHash != null =>
              log.info(s"Requested epoch " + epoch + " is obsolete. Current epoch is " + mcInfo.epoch)
              return true
          case (_, Some(ccInfo)) if ccInfo.epoch == epoch && ccInfo.certHash != null =>
              log.info(s"Submission not needed. Certificate already present in epoch " + epoch)
              return true
          case (_, Some(ccInfo)) if ccInfo.epoch > epoch && ccInfo.certHash != null =>
              log.info(s"Requested epoch " + epoch + " is obsolete. Current epoch is " + ccInfo.epoch)
              return true
          case _ =>
        }
      case Failure(_) =>
        log.info("Check for top quality certificates before sending it failed. Trying to send the new certificate anyway.")
    }
    false
  }
}

object NonCeasingSidechain{
  // Delay for non-ceasing sidechain is the same as for ceasing sidechain
  // Measured in MC blocks. See `WithdrawalEpochUtils.inSubmitCertificateWindow(...)`
  private val NON_CEASING_SUBMISSION_DELAY: Int = 1
}
