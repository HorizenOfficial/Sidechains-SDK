package io.horizen.consensus
import io.horizen.fork.{ConsensusParamsFork, ConsensusParamsForkInfo}
import io.horizen.utils.TimeToEpochUtils
import sparkz.core.block.Block


object ConsensusParamsUtil {
  private var consensusParamsForksActivation: Seq[ConsensusParamsForkInfo] = Seq()
  private var consensusParamsForkTimestampActivation: Seq[Block.Timestamp] = Seq()

  def setConsensusParamsForkActivation(forkActivationHeights: Seq[ConsensusParamsForkInfo]): Unit = {
    this.consensusParamsForksActivation = forkActivationHeights
  }

  def setConsensusParamsForkTimestampActivation(blockTs: Seq[Block.Timestamp]): Unit = {
    this.consensusParamsForkTimestampActivation = blockTs
  }

  def getConsensusParamsForkTimestampActivation(): Seq[Block.Timestamp] = {
    this.consensusParamsForkTimestampActivation
  }

  def getConsensusParamsForkActivation: Seq[ConsensusParamsForkInfo] =
    this.consensusParamsForksActivation

  def getConsensusSlotsPerEpoch(epochId: Int): Int = {
    ConsensusParamsFork.get(epochId).consensusSlotsInEpoch
  }

  def getConsensusSlotsPerEpoch(genesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): Int = {
      ConsensusParamsFork.get(TimeToEpochUtils.timeStampToEpochNumber(genesisBlockTimestamp, timestamp)).consensusSlotsInEpoch
  }

  def getConsensusSecondsInSlotsPerEpoch(epochId: Int): Int = {
    ConsensusParamsFork.get(epochId).consensusSecondsInSlot
  }

  def getConsensusSecondsInSlotsPerEpoch(genesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): Int = {
    ConsensusParamsFork.get(TimeToEpochUtils.timeStampToEpochNumber(genesisBlockTimestamp, timestamp)).consensusSecondsInSlot
  }

  def numberOfConsensusParamsFork: Int = this.consensusParamsForksActivation.size
}
