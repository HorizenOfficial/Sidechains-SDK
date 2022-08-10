package com.horizen.fork

class SidechainFork1(override val epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {
  override def BackwardTransferLimitEnabled(): Boolean = true
}
