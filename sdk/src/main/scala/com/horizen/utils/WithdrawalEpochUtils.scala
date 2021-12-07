package com.horizen.utils

import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams

object WithdrawalEpochUtils {

  def getWithdrawalEpochInfo(block: SidechainBlock, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): WithdrawalEpochInfo = {
    val withdrawalEpoch: Int =
      if(parentEpochInfo.lastEpochIndex == params.withdrawalEpochLength) // Parent block is the last SC Block of withdrawal epoch.
        parentEpochInfo.epoch + 1
      else if(parentEpochInfo.lastEpochIndex + block.mainchainBlockReferencesData.size > params.withdrawalEpochLength) // block mc block references lead to surpassing of the epoch length
        parentEpochInfo.epoch + 1
      else // Continue current withdrawal epoch
        parentEpochInfo.epoch

    val withdrawalEpochIndex: Int =
      if(withdrawalEpoch > parentEpochInfo.epoch) // New withdrawal epoch started
        (parentEpochInfo.lastEpochIndex + block.mainchainBlockReferencesData.size) % params.withdrawalEpochLength // Note: in case of empty MC Block ref list index should be 0.
      else // Continue current withdrawal epoch
        parentEpochInfo.lastEpochIndex + block.mainchainBlockReferencesData.size // Note: in case of empty MC Block ref list index should be the same as for previous SC block.

    WithdrawalEpochInfo(withdrawalEpoch, withdrawalEpochIndex)
  }

  def hasReachedCertificateSubmissionWindowEnd(newEpochInfo: WithdrawalEpochInfo, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    inSubmitCertificateWindow(parentEpochInfo, params) && // parent was in the submission window
      newEpochInfo != parentEpochInfo && // new block should increase epoch index (corner case: parent in the end odf the window)
      newEpochInfo.lastEpochIndex >= certificateSubmissionWindowLength(params) // new block may have multiple ref data so may pass over the window
  }

  def hasReachedCertificateSubmissionWindowEnd(block: SidechainBlock, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    val newEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(block, parentEpochInfo, params)
    hasReachedCertificateSubmissionWindowEnd(newEpochInfo, parentEpochInfo, params)
  }

  def isEpochLastIndex(epochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    epochInfo.lastEpochIndex == params.withdrawalEpochLength
  }

  // Certificate can be sent only when mc block is in a specific position in the Withdrawal epoch
  def inSubmitCertificateWindow(withdrawalEpochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    (withdrawalEpochInfo.epoch > 0) && (withdrawalEpochInfo.lastEpochIndex <= certificateSubmissionWindowLength(params))
  }

  def certificateSubmissionWindowLength(params: NetworkParams): Int = {
    certificateSubmissionWindowLength(params.withdrawalEpochLength)
  }

  def certificateSubmissionWindowLength(withdrawalEpochLength: Int): Int = {
    // MC consensus cert submission window length is 1/5 of the withdrawal epoch length, but at least 2 mc blocks
    Math.max(2, withdrawalEpochLength / 5)
  }

  def ceasedAtMcBlockHeight(withdrawalEpochNumber: Int, params: NetworkParams): Int = {
    params.mainchainCreationBlockHeight + (withdrawalEpochNumber * params.withdrawalEpochLength) + certificateSubmissionWindowLength(params) - 1
  }
}
