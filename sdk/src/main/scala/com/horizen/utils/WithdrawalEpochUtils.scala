package com.horizen.utils

import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams

object WithdrawalEpochUtils {

  def getWithdrawalEpochInfo(mainchainBlockReferenceSize: Int, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): WithdrawalEpochInfo = {
    val withdrawalEpoch: Int =
      if(parentEpochInfo.lastEpochIndex == params.withdrawalEpochLength) // Parent block is the last SC Block of withdrawal epoch.
        parentEpochInfo.epoch + 1
      else if(parentEpochInfo.lastEpochIndex + mainchainBlockReferenceSize > params.withdrawalEpochLength) // block mc block references lead to surpassing of the epoch length
        parentEpochInfo.epoch + 1
      else // Continue current withdrawal epoch
        parentEpochInfo.epoch

    val withdrawalEpochIndex: Int =
      if(withdrawalEpoch > parentEpochInfo.epoch) // New withdrawal epoch started
        (parentEpochInfo.lastEpochIndex + mainchainBlockReferenceSize) % params.withdrawalEpochLength // Note: in case of empty MC Block ref list index should be 0.
      else // Continue current withdrawal epoch
        parentEpochInfo.lastEpochIndex + mainchainBlockReferenceSize // Note: in case of empty MC Block ref list index should be the same as for previous SC block.

    WithdrawalEpochInfo(withdrawalEpoch, withdrawalEpochIndex)
  }

  def hasReachedCertificateSubmissionWindowEnd(newEpochInfo: WithdrawalEpochInfo, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    newEpochInfo.epoch > 0 && // no submission window for the fist epoch
      (parentEpochInfo.lastEpochIndex < certificateSubmissionWindowLength(params) || // parent was in the middle of the submission window
        isEpochLastIndex(parentEpochInfo, params)) &&  // or parent was in the end of the withdrawal epoch
      newEpochInfo.lastEpochIndex >= certificateSubmissionWindowLength(params) // new block may have multiple ref data so may pass over the window
  }

  def hasReachedCertificateSubmissionWindowEnd(block: SidechainBlock, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    val newEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, parentEpochInfo, params)
    hasReachedCertificateSubmissionWindowEnd(newEpochInfo, parentEpochInfo, params)
  }

  def isEpochLastIndex(epochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    epochInfo.lastEpochIndex == params.withdrawalEpochLength
  }

  // Certificate can be sent only when mc block is in a specific position in the Withdrawal epoch
  // Has sense only for ceasing sidechains.
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
