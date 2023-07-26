package io.horizen.utils

import io.horizen.consensus._
import io.horizen.fork.{ConsensusParamsFork, ConsensusParamsForkInfo}
import sparkz.core.block.Block

object TimeToEpochUtils {
  def epochInSeconds(consensusSecondsInSlot: Int, consensusSlotsInEpoch: Int): Long =
    Math.multiplyExact(consensusSlotsInEpoch, consensusSecondsInSlot)

  def virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp: Block.Timestamp): Long =
    sidechainGenesisBlockTimestamp - epochInSeconds(ConsensusParamsUtil.getConsensusParamsForkActivation.head.consensusParamsFork.consensusSecondsInSlot, ConsensusParamsUtil.getConsensusParamsForkActivation.head.consensusParamsFork.consensusSlotsInEpoch) + ConsensusParamsUtil.getConsensusParamsForkActivation.head.consensusParamsFork.consensusSecondsInSlot

  def timestampToEpochAndSlot(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): ConsensusEpochAndSlot =
    ConsensusEpochAndSlot(timeStampToEpochNumber(sidechainGenesisBlockTimestamp, timestamp), timeStampToSlotNumber(sidechainGenesisBlockTimestamp, timestamp))

  def timeStampToEpochNumber(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): ConsensusEpochNumber =
    intToConsensusEpochNumber(getEpochIndex(sidechainGenesisBlockTimestamp, timestamp))

  def timeStampToSlotNumber(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): ConsensusSlotNumber = {
    require(
      timestamp >= sidechainGenesisBlockTimestamp,
      s"Try to get index epoch for timestamp $timestamp which are less than genesis timestamp ${sidechainGenesisBlockTimestamp}"
    )
    val blockConsensusForkInformation = getConsensusInformationFromTimestamp(timestamp, timestamp)
    val slotIndex = (blockConsensusForkInformation.secondsInFork % epochInSeconds(blockConsensusForkInformation.lastConsensusFork.consensusParamsFork.consensusSecondsInSlot, blockConsensusForkInformation.lastConsensusFork.consensusParamsFork.consensusSlotsInEpoch)) / blockConsensusForkInformation.lastConsensusFork.consensusParamsFork.consensusSecondsInSlot
    intToConsensusSlotNumber(slotIndex.toInt + 1)
  }

  // Slot number starting from genesis block
  def timeStampToAbsoluteSlotNumber(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): ConsensusAbsoluteSlotNumber = {
    if (ConsensusParamsUtil.numberOfConsensusParamsFork > 1) {
      val epoch = getEpochIndex(sidechainGenesisBlockTimestamp, timestamp)
      val slot = timeStampToSlotNumber(sidechainGenesisBlockTimestamp, timestamp)
      val forks = ConsensusParamsUtil.getConsensusParamsForkActivation
      var forkIndex = 1
      var fork = forks(forkIndex)
      var previousFork = forks(forkIndex -1)
      var absoluteSlotNumber = 0
      while (epoch > fork.activationEpoch && forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        absoluteSlotNumber = absoluteSlotNumber + (fork.activationEpoch - previousFork.activationEpoch) * previousFork.consensusParamsFork.consensusSlotsInEpoch
        forkIndex = forkIndex + 1
        if (forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
          previousFork = fork
          fork = forks(forkIndex)
        }
      }

      val lastFork = ConsensusParamsUtil.getConsensusParamsForkActivation(forkIndex - 1)

      absoluteSlotNumber = absoluteSlotNumber + (epoch - lastFork.activationEpoch) * lastFork.consensusParamsFork.consensusSlotsInEpoch + slot
      intToConsensusAbsoluteSlotNumber(absoluteSlotNumber)
    } else {
      val slotNumber = timeStampToEpochNumber(sidechainGenesisBlockTimestamp, timestamp) * ConsensusParamsFork.DefaultConsensusParamsFork.consensusSlotsInEpoch +
        timeStampToSlotNumber(sidechainGenesisBlockTimestamp, timestamp)
      intToConsensusAbsoluteSlotNumber(slotNumber)
    }

  }

    def getTimeStampForEpochAndSlot(
      sidechainGenesisBlockTimestamp: Block.Timestamp,
      epochNumber: ConsensusEpochNumber,
      slotNumber: ConsensusSlotNumber
  ): Long = {
    require(slotNumber <= ConsensusParamsFork.get(epochNumber).consensusSlotsInEpoch)
    if (ConsensusParamsUtil.numberOfConsensusParamsFork > 1) {

      var currentForkIndex = 1
      var currentFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex)
      var previousFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex - 1)
      var previousAccumulatedSecondsPerFork: Long = 0
      var accumulatedSecondsPerFork: Long = currentFork.activationEpoch * epochInSeconds(previousFork.consensusParamsFork.consensusSecondsInSlot, previousFork.consensusParamsFork.consensusSlotsInEpoch)

      while (epochNumber > currentFork.activationEpoch - 1 && currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        previousAccumulatedSecondsPerFork = accumulatedSecondsPerFork
        currentForkIndex = currentForkIndex + 1
        if (currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
          previousFork = currentFork
          currentFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex)
          accumulatedSecondsPerFork = accumulatedSecondsPerFork + (currentFork.activationEpoch - previousFork.activationEpoch) * epochInSeconds(previousFork.consensusParamsFork.consensusSecondsInSlot, previousFork.consensusParamsFork.consensusSlotsInEpoch )
        }
      }

      val lastFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex - 1)
      val epochInCurrentFork =  epochNumber - lastFork.activationEpoch - 1

      val accumulatedSecondsPerEpoch = previousAccumulatedSecondsPerFork + (epochInCurrentFork+1) * epochInSeconds(lastFork.consensusParamsFork.consensusSecondsInSlot, lastFork.consensusParamsFork.consensusSlotsInEpoch) + (slotNumber-1)*lastFork.consensusParamsFork.consensusSecondsInSlot - epochInSeconds(ConsensusParamsUtil.getConsensusParamsForkActivation.head.consensusParamsFork.consensusSecondsInSlot, ConsensusParamsUtil.getConsensusParamsForkActivation.head.consensusParamsFork.consensusSlotsInEpoch)
      virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp) + accumulatedSecondsPerEpoch
    } else {
      val defaultConsensusParamsFork = ConsensusParamsUtil.getConsensusParamsForkActivation.head

      val totalSlots: Int = (epochNumber - 1) * defaultConsensusParamsFork.consensusParamsFork.consensusSlotsInEpoch + (slotNumber - 1)
      virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp) + (totalSlots * defaultConsensusParamsFork.consensusParamsFork.consensusSecondsInSlot)
    }
  }

  def secondsRemainingInSlot(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): Long = {
    val secondsElapsedInSlot = (timestamp - virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp)) % ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(Option.empty)
    ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(Option.empty) - secondsElapsedInSlot
  }

  private def getEpochIndex(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): Int = {
    require(
      timestamp >= sidechainGenesisBlockTimestamp,
      s"Try to get index epoch for timestamp $timestamp which are less than genesis timestamp ${sidechainGenesisBlockTimestamp}"
    )
    val refinedTimestamp = timestamp - ConsensusParamsUtil.getConsensusParamsForkActivation.head.consensusParamsFork.consensusSecondsInSlot
    val blockConsensusForkInformation = getConsensusInformationFromTimestamp(timestamp, refinedTimestamp)
    blockConsensusForkInformation.ForkStartingEpoch + (blockConsensusForkInformation.secondsInFork / epochInSeconds(blockConsensusForkInformation.lastConsensusFork.consensusParamsFork.consensusSecondsInSlot, blockConsensusForkInformation.lastConsensusFork.consensusParamsFork.consensusSlotsInEpoch)).toInt
  }

  private def getConsensusInformationFromTimestamp(timestamp: Block.Timestamp, refinedTimestamp: Block.Timestamp): BlockConsensusForkInformation = {
    var startingEpoch = 0
    var forkIndex = 0
    val forks = ConsensusParamsUtil.getConsensusParamsForkActivation
    val activationForksTimestamp = ConsensusParamsUtil.getConsensusParamsForkTimestampActivation()
    var fork: ConsensusParamsForkInfo = forks(forkIndex)
    var forkActivationTimestamp = activationForksTimestamp(forkIndex)
    while (refinedTimestamp > forkActivationTimestamp && forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
      startingEpoch = fork.activationEpoch
      forkIndex = forkIndex + 1
      if (forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        fork = forks(forkIndex)
        forkActivationTimestamp = activationForksTimestamp(forkIndex)
      }
    }
    val lastFork = forks(Math.max(forkIndex - 1,0))
    val lastForkActivationTimestamp = activationForksTimestamp(Math.max(forkIndex - 1,0))
    val timestampMinusSlot = timestamp - lastFork.consensusParamsFork.consensusSecondsInSlot

    val secondsInFork = timestampMinusSlot - lastForkActivationTimestamp + lastFork.consensusParamsFork.consensusSecondsInSlot
    if (lastFork.activationEpoch == 0) {
      startingEpoch = startingEpoch + 1
    }

    BlockConsensusForkInformation(secondsInFork, startingEpoch, lastFork)
  }
}
