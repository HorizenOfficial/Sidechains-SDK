package io.horizen.utils

import io.horizen.consensus._
import io.horizen.fork.ConsensusParamsFork
import sparkz.core.block.Block

object TimeToEpochUtils {
  def epochInSeconds(consensusSecondsInSlot: Int, consensusSlotsInEpoch: Int): Long =
    Math.multiplyExact(consensusSlotsInEpoch, consensusSecondsInSlot)

  def virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp: Block.Timestamp): Long =
    sidechainGenesisBlockTimestamp - epochInSeconds(ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSecondsInSlot, ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSlotsInEpoch) + ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSecondsInSlot

  def timestampToEpochAndSlot(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): ConsensusEpochAndSlot =
    ConsensusEpochAndSlot(timeStampToEpochNumber(sidechainGenesisBlockTimestamp, timestamp), timeStampToSlotNumber(sidechainGenesisBlockTimestamp, timestamp))

  def timeStampToEpochNumber(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): ConsensusEpochNumber =
    intToConsensusEpochNumber(getEpochIndex(sidechainGenesisBlockTimestamp, timestamp))

  def timeStampToSlotNumber(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp): ConsensusSlotNumber = {
    val (remainingSlotsInFork, _, lastFork) = getConsensusInformationFromTimestamp(sidechainGenesisBlockTimestamp, timestamp, false)
    val slotIndex = (remainingSlotsInFork % epochInSeconds(lastFork._2.consensusSecondsInSlot, lastFork._2.consensusSlotsInEpoch)) / lastFork._2.consensusSecondsInSlot
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
      while (epoch > fork._1 && forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        absoluteSlotNumber = absoluteSlotNumber + (fork._1 - previousFork._1) * previousFork._2.consensusSlotsInEpoch
        forkIndex = forkIndex + 1
        if (forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
          previousFork = fork
          fork = forks(forkIndex)
        }
      }

      val lastFork = ConsensusParamsUtil.getConsensusParamsForkActivation(forkIndex - 1)

      absoluteSlotNumber = absoluteSlotNumber + (epoch - lastFork._1) * lastFork._2.consensusSlotsInEpoch + slot
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
      var accumulatedSecondsPerFork: Long = currentFork._1 * epochInSeconds(previousFork._2.consensusSecondsInSlot, previousFork._2.consensusSlotsInEpoch)

      while (epochNumber > currentFork._1 - 1 && currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        previousAccumulatedSecondsPerFork = accumulatedSecondsPerFork
        currentForkIndex = currentForkIndex + 1
        if (currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
          previousFork = currentFork
          currentFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex)
          accumulatedSecondsPerFork = accumulatedSecondsPerFork + (currentFork._1 - previousFork._1) * epochInSeconds(previousFork._2.consensusSecondsInSlot, previousFork._2.consensusSlotsInEpoch )
        }
      }

      val lastFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex - 1)
      val epochInCurrentFork =  epochNumber - lastFork._1 - 1

      val accumulatedSecondsPerEpoch = previousAccumulatedSecondsPerFork + (epochInCurrentFork+1) * epochInSeconds(lastFork._2.consensusSecondsInSlot, lastFork._2.consensusSlotsInEpoch) + (slotNumber-1)*lastFork._2.consensusSecondsInSlot - epochInSeconds(ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSecondsInSlot, ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSlotsInEpoch)
      virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp) + accumulatedSecondsPerEpoch
    } else {
      val defaultConsensusParamsFork = ConsensusParamsUtil.getConsensusParamsForkActivation.head

      val totalSlots: Int = (epochNumber - 1) * defaultConsensusParamsFork._2.consensusSlotsInEpoch + (slotNumber - 1)
      virtualGenesisBlockTimeStamp(sidechainGenesisBlockTimestamp) + (totalSlots * defaultConsensusParamsFork._2.consensusSecondsInSlot)
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
    val (remainingSlotsInFork, startingForkEpoch, lastFork) = getConsensusInformationFromTimestamp(sidechainGenesisBlockTimestamp, timestamp, true)
    startingForkEpoch + (remainingSlotsInFork / epochInSeconds(lastFork._2.consensusSecondsInSlot, lastFork._2.consensusSlotsInEpoch)).toInt
  }

  private def getConsensusInformationFromTimestamp(sidechainGenesisBlockTimestamp: Block.Timestamp, timestamp: Block.Timestamp, getEpoch: Boolean): (Long, Int, (Int, ConsensusParamsFork)) = {
    require(
      timestamp >= sidechainGenesisBlockTimestamp,
      s"Try to get index epoch for timestamp $timestamp which are less than genesis timestamp ${sidechainGenesisBlockTimestamp}"
    )

    var startingEpoch = 0
    var forkIndex = 0
    val forks = ConsensusParamsUtil.getConsensusParamsForkActivation
    val activationForksTimestamp = ConsensusParamsUtil.getConsensusParamsForkTimestampActivation()
    var fork = forks(forkIndex)
    var forkActivationTimestamp = activationForksTimestamp(forkIndex)
    var refinedTimestamp = timestamp
    if (getEpoch)
      refinedTimestamp = timestamp - fork._2.consensusSecondsInSlot
    while (refinedTimestamp > forkActivationTimestamp && forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
      startingEpoch = fork._1
      forkIndex = forkIndex + 1
      if (forkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        fork = forks(forkIndex)
        forkActivationTimestamp = activationForksTimestamp(forkIndex)
      }
    }
    val lastFork = forks(Math.max(forkIndex - 1,0))
    val lastForkActivationTimestamp = activationForksTimestamp(Math.max(forkIndex - 1,0))
    val timestampMinusSlot = timestamp - lastFork._2.consensusSecondsInSlot

    val timeStampInFork = timestampMinusSlot - lastForkActivationTimestamp + lastFork._2.consensusSecondsInSlot
    if (lastFork._1 == 0) {
      startingEpoch = startingEpoch + 1
    }

    (timeStampInFork, startingEpoch, lastFork)
  }
}
