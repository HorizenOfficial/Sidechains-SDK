package io.horizen.fork

class SimpleForkConfigurator extends ForkConfigurator {
  override val getSidechainFork1: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(10, 20, 0)
}
