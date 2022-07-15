package com.horizen.fork

class BaseConsensusEpochFork (val epochNumber: scConsensusEpochNumber) {}

case class scConsensusEpochNumber(mainnetEpochNumber: Int, regtestEpochNumber: Int, testnetEpochNumber: Int) {}
