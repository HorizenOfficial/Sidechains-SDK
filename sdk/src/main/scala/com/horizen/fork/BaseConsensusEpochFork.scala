package com.horizen.fork

class BaseConsensusEpochFork (val epochNumber: ForkConsensusEpochNumber) {
  def backwardTransferLimitEnabled(): Boolean = false
  def openStakeTransactionEnabled(): Boolean = false
}

case class ForkConsensusEpochNumber(mainnetEpochNumber: Int, regtestEpochNumber: Int, testnetEpochNumber: Int) {}
