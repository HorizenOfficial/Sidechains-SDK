package io.horizen.fork

class SimpleForkConfigurator extends ForkConfigurator {
  override def getSidechainFork1(): ForkConsensusEpochNumber = {
    ForkConsensusEpochNumber(10, 20, 0)
  }
}