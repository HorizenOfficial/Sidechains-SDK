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

  def inReachedCertificateSubmissionWindowEnd(block: SidechainBlock, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    if (block.mainchainBlockReferencesData.nonEmpty && inSubmitCertificateWindow(parentEpochInfo, params)) {
      val mcBlocksLeft = certificateSubmissionWindowLength(params) - parentEpochInfo.lastEpochIndex
      // It can be no blocks left if parent reached exactly the end of the window.
      // SC block may have multiple MCBlockRefData entries that reach or even exceed the CertificateSubmissionWindowEnd
      mcBlocksLeft > 0 && block.mainchainBlockReferencesData.size >= mcBlocksLeft
    } else {
      // SC block has no MCBlockRefData or parent is not inside the window at all
      false
    }
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
}
