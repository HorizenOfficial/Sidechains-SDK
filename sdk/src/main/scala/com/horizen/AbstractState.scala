package com.horizen

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.consensus.{ConsensusEpochInfo, ForgingStakeInfo}
import com.horizen.transaction.Transaction
import com.horizen.utils.WithdrawalEpochInfo
import scorex.util.ModifierId
import sparkz.core.transaction.state.MinimalState

abstract class AbstractState[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  MS <: AbstractState[TX, H, PM, MS]
] extends MinimalState[PM, MS] {
  self: MS =>

  // abstract methods
  def isSwitchingConsensusEpoch(blockTimestamp: Long): Boolean

  def isWithdrawalEpochLastIndex: Boolean

  def getWithdrawalEpochInfo: WithdrawalEpochInfo

  def getCurrentConsensusEpochInfo: (ModifierId, ConsensusEpochInfo)

  //Check if the majority of the allowed forgers opened the stake to everyone
  def isForgingOpen(): Boolean
}


