package com.horizen

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.consensus.{ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.transaction.Transaction
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.core.transaction.state.MinimalState

abstract class AbstractState[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  MS <: AbstractState[TX, H, PM, MS]
] extends MinimalState[PM, MS]
{
  self: MS =>
  def isSwitchingConsensusEpoch(blockTimestamp: Long): Boolean
  def getOrderedForgingStakesInfoSeq: Seq[ForgingStakeInfo]
  def isWithdrawalEpochLastIndex: Boolean
  def getWithdrawalEpochInfo: WithdrawalEpochInfo
  def getWithdrawalEpochNumber: Int = getWithdrawalEpochInfo.epoch
}


