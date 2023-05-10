package io.horizen.fork

class SimpleForkConfigurator extends ForkConfigurator {
  override val getSidechainFork1: ForkConsensusEpochNumber = ForkConsensusEpochNumber(10, 20, 0)
}
