package com.horizen.fork

class BaseConsensusEpochFork (val epochNumber: ForkConsensusEpochNumber) {
  def backwardTransferLimitEnabled(): Boolean = false
  def openStakeTransactionEnabled(): Boolean = false
  def nonceLength: Int = 8
  def stakePercentageForkApplied: Boolean = false
}

case class ForkConsensusEpochNumber(mainnetEpochNumber: Int, regtestEpochNumber: Int, testnetEpochNumber: Int) {}
