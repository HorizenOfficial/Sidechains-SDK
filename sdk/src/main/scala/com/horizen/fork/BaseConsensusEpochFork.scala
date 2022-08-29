package com.horizen.fork

class BaseConsensusEpochFork (val epochNumber: ForkConsensusEpochNumber) {
  val ftMinAmount: Long = 0
  val coinBoxMinAmount: Long = 0
}

case class ForkConsensusEpochNumber(mainnetEpochNumber: Int, regtestEpochNumber: Int, testnetEpochNumber: Int) {}
