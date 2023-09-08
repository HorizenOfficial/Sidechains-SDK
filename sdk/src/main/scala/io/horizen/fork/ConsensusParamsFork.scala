package io.horizen.fork

import io.horizen.utils.Pair


case class ConsensusParamsFork(
    consensusSlotsInEpoch: Int = 720, // how many block are in epoch => 720 blocks in 1 epoch; epoch size
    consensusSecondsInSlot: Int = 12 // block appears on average every 17 seconds. f=0.7 which means 70% of slots are expected to have at least one leader. so 12/0.7 = 17
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
