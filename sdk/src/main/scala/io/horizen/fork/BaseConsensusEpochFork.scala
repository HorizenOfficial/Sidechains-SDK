package io.horizen.fork

class BaseConsensusEpochFork (val epochNumber: ForkConsensusEpochNumber) {
  def backwardTransferLimitEnabled(): Boolean = false
  def openStakeTransactionEnabled(): Boolean = false
  def ftMinAmount: Long = 0
  def coinBoxMinAmount: Long = 0
  def nonceLength: Int = 8
  def stakePercentageForkApplied: Boolean = false
}

case class ForkConsensusEpochNumber(regtestEpochNumber: Int, testnetEpochNumber: Int, mainnetEpochNumber: Int) {}
