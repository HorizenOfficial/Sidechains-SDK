package io.horizen.certificatesubmitter.strategies
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter.{ObsoleteWithdrawalEpochException, SignaturesStatus}
import io.horizen.params.NetworkParams
import io.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import io.horizen.websocket.client.{MainchainNodeChannel, WebsocketErrorResponseException, WebsocketInvalidErrorMessageException}
import io.horizen.AbstractState
import io.horizen.history.AbstractHistory
import sparkz.util.{ModifierId, SparkzLogging}
import sparkz.core.NodeViewHolder.CurrentView

import scala.util.{Failure, Success, Try}

class CeasingSidechain(mainchainChannel: MainchainNodeChannel, params: NetworkParams)
  extends CertificateSubmissionStrategy with SparkzLogging {

  override def getStatus[
    H <: AbstractHistory[_, _, _, _, _, _],
    S <: AbstractState[_, _, _, _]
  ](history: H, state: S, id: ModifierId): SubmissionWindowStatus = {
    // Take withdrawal epoch info for block from the History.
    // Note: We can't rely on `State.getWithdrawalEpochInfo`, because it shows the tip info,
    // but the older block may being applied at the moment.
    val withdrawalEpochInfo: WithdrawalEpochInfo = history.blockInfoById(id).withdrawalEpochInfo
    val referencedWithdrawalEpochNumber: Int = withdrawalEpochInfo.epoch - 1

    SubmissionWindowStatus(referencedWithdrawalEpochNumber, WithdrawalEpochUtils.inSubmitCertificateWindow(withdrawalEpochInfo, params))
  }

  override def checkQuality(status: SignaturesStatus): Boolean = {
    if (status.knownSigs.size >= params.signersThreshold) {
      getCertificateTopQuality(status.referencedEpoch) match {
        case Success(currentCertificateTopQuality) =>
          if (status.knownSigs.size > currentCertificateTopQuality)
            return true
        case Failure(e) => e match {
          // May happen if there is a bug on MC side or the SDK code is inconsistent to the MC one.
          case ex: WebsocketErrorResponseException =>
            log.error("Mainchain error occurred while processed top quality certificates request(" + ex + ")")
            // So we don't know the result
            // Return true to keep submitter going and prevent SC ceasing
            return true
          // May happen during node synchronization and node behind for one epoch or more
          case ex: ObsoleteWithdrawalEpochException =>
            log.info("Sidechain is behind the Mainchain(" + ex + ")")
            return false
          // May happen if MC and SDK websocket protocol is inconsistent.
          // Should never happen in production.
          case ex: WebsocketInvalidErrorMessageException =>
            log.error("Mainchain error message is inconsistent to SC implementation(" + ex + ")")
            // So we don't know the result
            // Return true to keep submitter going and prevent SC ceasing
            return true
          // Various connection errors
          case other =>
            log.error("Unable to retrieve actual top quality certificates from Mainchain(" + other + ")")
            return false
        }
      }
    }
    false
  }

  private def getCertificateTopQuality(epoch: Int): Try[Long] = {
    mainchainChannel.getTopQualityCertificates(BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId)))
      .map(topQualityCertificates => {
        (topQualityCertificates.mempoolCertInfo, topQualityCertificates.chainCertInfo) match {
          // case we have mempool cert for the given epoch return its quality.
          case (Some(mempoolInfo), _) if mempoolInfo.epoch == epoch => mempoolInfo.quality
          // case the mempool certificate epoch is a newer than submitter epoch thrown an exception
          case (Some(mempoolInfo), _) if mempoolInfo.epoch > epoch =>
            throw ObsoleteWithdrawalEpochException("Requested epoch " + epoch + " is obsolete. Current epoch is " + mempoolInfo.epoch)
          // case we have chain cert for the given epoch return its quality.
          case (_, Some(chainInfo)) if chainInfo.epoch == epoch => chainInfo.quality
          // case the chain certificate epoch is a newer than submitter epoch thrown an exception
          case (_, Some(chainInfo)) if chainInfo.epoch > epoch =>
            throw ObsoleteWithdrawalEpochException("Requested epoch " + epoch + " is obsolete. Current epoch is " + chainInfo.epoch)
          // no known certs
          case _ => 0
        }
      })
  }
}
