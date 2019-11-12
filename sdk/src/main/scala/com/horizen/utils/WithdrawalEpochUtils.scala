package com.horizen.utils

import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams

object WithdrawalEpochUtils {

  def getWithdrawalEpochInfo(block: SidechainBlock, parentEpochInfo: WithdrawalEpochInfo, params: NetworkParams): WithdrawalEpochInfo = {
    val withdrawalEpoch: Int =
      if(parentEpochInfo.index == params.withdrawalEpochLength) // Parent block is the last SC Block of withdrawal epoch.
        parentEpochInfo.epoch + 1
      else // Continue current withdrawal epoch
        parentEpochInfo.epoch

    val withdrawalEpochIndex: Int =
      if(withdrawalEpoch > parentEpochInfo.epoch) // New withdrawal epoch started
        block.mainchainBlocks.size // Note: in case of empty MC Block ref list index should be 0.
      else // Continue current withdrawal epoch
        parentEpochInfo.index + block.mainchainBlocks.size // Note: in case of empty MC Block ref list index should be the same as for previous SC block.

    WithdrawalEpochInfo(withdrawalEpoch, withdrawalEpochIndex)
  }
}
