package io.horizen.consensus
import io.horizen.fork.ConsensusParamsFork
import sparkz.core.block.Block


object ConsensusParamsUtil {
  private var currentConsensusEpoch: Int = 0
  private var consensusParamsForksActivation: Seq[(Int, ConsensusParamsFork)] = Seq()
  private var consensusParamsForkTimestampActivation: Seq[Block.Timestamp] = Seq()

  def setCurrentConsensusEpoch(currentConsensusEpoch: Int): Unit = {
    this.currentConsensusEpoch = currentConsensusEpoch
  }

  def getCurrentConsensusEpoch: Int = {
    this.currentConsensusEpoch
  }

  def setConsensusParamsForkActivation(forkActivationHeights: Seq[(Int, ConsensusParamsFork)]): Unit = {
    this.consensusParamsForksActivation = forkActivationHeights
  }

  def setConsensusParamsForkTimestampActivation(blockTs: Seq[Block.Timestamp]): Unit = {
    this.consensusParamsForkTimestampActivation = blockTs
  }

  def getConsensusParamsForkTimestampActivation(): Seq[Block.Timestamp] = {
    this.consensusParamsForkTimestampActivation
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

  def getConsensusSecondsInSlotsPerEpoch(epochId: Option[Int]): Int = {
    epochId match {
      case Some(epoch) =>
        ConsensusParamsFork.get(epoch).consensusSecondsInSlot
      case None =>
        ConsensusParamsFork.get(this.currentConsensusEpoch).consensusSecondsInSlot
    }
  }

  def numberOfConsensusParamsFork: Int = this.consensusParamsForksActivation.size
}
