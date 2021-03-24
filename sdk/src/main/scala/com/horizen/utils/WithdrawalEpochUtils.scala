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

  //Certificate could be sent only then block in specific position in Withdrawal epoch
  def inSubmitCertificateWindow(withdrawalEpochInfo: WithdrawalEpochInfo, params: NetworkParams): Boolean = {
    (withdrawalEpochInfo.epoch > 0) && (withdrawalEpochInfo.lastEpochIndex <= params.withdrawalEpochLength / 5)
  }
}
