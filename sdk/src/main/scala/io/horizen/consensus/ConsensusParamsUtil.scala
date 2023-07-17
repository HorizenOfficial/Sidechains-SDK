package io.horizen.consensus
import io.horizen.account.fork.ConsensusParamsFork


object ConsensusParamsUtil {
  private var currentConsensusEpoch: Int = 0
  private var consensusParamsForksActivation: Seq[(Int, ConsensusParamsFork)] = Seq()

  def setCurrentConsensusEpoch(currentConsensusEpoch: Int): Unit = {
    this.currentConsensusEpoch = currentConsensusEpoch
  }

  def getCurrentConsensusEpoch: Int = {
    this.currentConsensusEpoch
  }

  def setConsensusParamsForkActivation(forkActivationHeights: Seq[(Int, ConsensusParamsFork)]): Unit = {
    this.consensusParamsForksActivation = forkActivationHeights
  }

  def getConsensusParamsForkActivation: Seq[(Int, ConsensusParamsFork)] =
    this.consensusParamsForksActivation

  def getConsensusSlotsPerEpoch(epochId: Option[Int]): Int = {
    epochId match {
      case Some(epoch) =>
        ConsensusParamsFork.get(epoch).consensusSlotsInEpoch
      case None =>
        ConsensusParamsFork.get(this.currentConsensusEpoch).consensusSlotsInEpoch
    }
  }

  def numberOfConsensusParamsFork: Int = this.consensusParamsForksActivation.size
}
