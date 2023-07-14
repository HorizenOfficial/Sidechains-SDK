package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}


case class ConsensusParamsFork(
    consensusSlotsInEpoch: Int = 720
   ) extends OptionalSidechainFork

object ConsensusParamsFork {
  def get(epochNumber: Int): ConsensusParamsFork = {
    ForkManager.getOptionalSidechainFork[ConsensusParamsFork](epochNumber).getOrElse(DefaultConsensusParamsFork)
  }

  val DefaultConsensusParamsFork: ConsensusParamsFork = ConsensusParamsFork()
}
