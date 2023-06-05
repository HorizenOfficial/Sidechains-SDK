package io.horizen.fork

class SimpleForkConfigurator extends ForkConfigurator {
  override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(10, 20, 0)
}
