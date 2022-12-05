package com.horizen.utils

import com.horizen.block.{MainchainBlockReferenceData, SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.params.NetworkParams
import com.horizen.transaction.Transaction

object WithdrawalEpochUtils {

  def getWithdrawalEpochInfo[TX <: Transaction](
      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
      parentEpochInfo: WithdrawalEpochInfo,
      params: NetworkParams
  ): WithdrawalEpochInfo = {
    val withdrawalEpoch: Int =
      if (parentEpochInfo.lastEpochIndex == params.withdrawalEpochLength)
        // Parent block is the last SC Block of withdrawal epoch.
        parentEpochInfo.epoch + 1
      else if (parentEpochInfo.lastEpochIndex + mainchainBlockReferencesData.size > params.withdrawalEpochLength)
        // block mc block references lead to surpassing of the epoch length
        parentEpochInfo.epoch + 1
      else
        // Continue current withdrawal epoch
        parentEpochInfo.epoch

    val withdrawalEpochIndex: Int =
      if (withdrawalEpoch > parentEpochInfo.epoch)
        // New withdrawal epoch started
        // Note: in case of empty MC Block ref list index should be 0.
        (parentEpochInfo.lastEpochIndex + mainchainBlockReferencesData.size) % params.withdrawalEpochLength
      else
        // Continue current withdrawal epoch
        // Note: in case of empty MC Block ref list index should be the same as for previous SC block.
        parentEpochInfo.lastEpochIndex + mainchainBlockReferencesData.size

    WithdrawalEpochInfo(withdrawalEpoch, withdrawalEpochIndex)
  }

  def getWithdrawalEpochInfo[TX <: Transaction](
      block: SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase],
      parentEpochInfo: WithdrawalEpochInfo,
      params: NetworkParams
  ): WithdrawalEpochInfo = getWithdrawalEpochInfo(block.mainchainBlockReferencesData, parentEpochInfo, params)

  def hasReachedCertificateSubmissionWindowEnd(
      newEpochInfo: WithdrawalEpochInfo,
      parentEpochInfo: WithdrawalEpochInfo,
      params: NetworkParams
  ): Boolean = {
    // no submission window for the first epoch
    newEpochInfo.epoch > 0 && (
      // parent was in the middle of the submission window
      parentEpochInfo.lastEpochIndex < certificateSubmissionWindowLength(params) ||
        // or parent was in the end of the withdrawal epoch
        isEpochLastIndex(parentEpochInfo, params)
    ) &&
    // new block may have multiple ref data so may pass over the window
    newEpochInfo.lastEpochIndex >= certificateSubmissionWindowLength(params)
  }

  def hasReachedCertificateSubmissionWindowEnd[TX <: Transaction](
      block: SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase],
      parentEpochInfo: WithdrawalEpochInfo,
      params: NetworkParams
  ): Boolean = {
    val newEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo[TX](block, parentEpochInfo, params)
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

  // MC consensus cert submission window length is 1/5 of the withdrawal epoch length, but at least 2 mc blocks
  def certificateSubmissionWindowLength(withdrawalEpochLength: Int): Int = Math.max(2, withdrawalEpochLength / 5)

  def ceasedAtMcBlockHeight(withdrawalEpochNumber: Int, params: NetworkParams): Int =
    params.mainchainCreationBlockHeight + (withdrawalEpochNumber * params.withdrawalEpochLength) +
      certificateSubmissionWindowLength(params) - 1
}
