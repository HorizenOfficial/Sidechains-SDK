package com.horizen.fork

class BaseConsensusEpochFork (val epochNumber: ForkConsensusEpochNumber) {}

case class ForkConsensusEpochNumber(mainnetEpochNumber: Int, regtestEpochNumber: Int, testnetEpochNumber: Int) {}
