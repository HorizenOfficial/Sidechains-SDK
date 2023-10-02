package io.horizen.fork

import io.horizen.utils.Pair


case class ConsensusParamsFork(
    consensusSlotsInEpoch: Int = 720,
    consensusSecondsInSlot: Int = 12
   ) extends OptionalSidechainFork

object ConsensusParamsFork {
  def get(epochNumber: Int): ConsensusParamsFork = {
    ForkManager.getOptionalSidechainFork[ConsensusParamsFork](epochNumber).getOrElse(DefaultConsensusParamsFork)
  }

  val DefaultConsensusParamsFork: ConsensusParamsFork = ConsensusParamsFork()

  def getMaxPossibleSlotsEver(forks: java.util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]]): Int = {
    //Get the max number of consensus slots per epoch from the App Fork configurator and use it to set the Storage versions to mantain
    var maxConsensusSlotsInEpoch = ConsensusParamsFork.DefaultConsensusParamsFork.consensusSlotsInEpoch
    forks.forEach(fork => {
      if (fork.getValue.isInstanceOf[ConsensusParamsFork] && fork.getValue.asInstanceOf[ConsensusParamsFork].consensusSlotsInEpoch > maxConsensusSlotsInEpoch) {
        maxConsensusSlotsInEpoch = fork.getValue.asInstanceOf[ConsensusParamsFork].consensusSlotsInEpoch
      }
    })
    maxConsensusSlotsInEpoch
  }
}
