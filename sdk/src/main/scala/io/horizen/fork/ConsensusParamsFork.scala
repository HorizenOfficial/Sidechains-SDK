package io.horizen.fork


case class ConsensusParamsFork(
    consensusSlotsInEpoch: Int = 720,
    consensusSecondsInSlot: Int = 12
   ) extends OptionalSidechainFork

object ConsensusParamsFork {
  def get(epochNumber: Int): ConsensusParamsFork = {
    ForkManager.getOptionalSidechainFork[ConsensusParamsFork](epochNumber).getOrElse(DefaultConsensusParamsFork)
  }

  val DefaultConsensusParamsFork: ConsensusParamsFork = ConsensusParamsFork()
}
