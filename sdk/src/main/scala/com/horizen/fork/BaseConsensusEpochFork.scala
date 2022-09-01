package com.horizen.fork

class BaseConsensusEpochFork (val epochNumber: ForkConsensusEpochNumber) {
  def backwardTransferLimitEnabled(): Boolean = false
  def openStakeTransactionEnabled(): Boolean = false
  val ftMinAmount: Long = 0
  val coinBoxMinAmount: Long = 0
}

case class ForkConsensusEpochNumber(mainnetEpochNumber: Int, regtestEpochNumber: Int, testnetEpochNumber: Int) {}
