package com.horizen.fork

class SidechainFork1(override val epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {
  override def backwardTransferLimitEnabled(): Boolean = true
  override def openStakeTransactionEnabled(): Boolean = true
}
