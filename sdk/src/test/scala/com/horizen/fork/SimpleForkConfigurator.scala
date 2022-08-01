package com.horizen.fork

class SimpleForkConfigurator extends ForkConfigurator {
  override def getSidechainFork1(): ForkConsensusEpochNumber = {
    ForkConsensusEpochNumber(0, 10, 20)
  }
}