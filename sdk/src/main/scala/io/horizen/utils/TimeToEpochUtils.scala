package io.horizen.utils

import io.horizen.consensus._
import io.horizen.fork.ConsensusParamsFork
import io.horizen.params.NetworkParams
import sparkz.core.block.Block

object TimeToEpochUtils {
  def epochInSeconds(consensusSecondsInSlot: Int, consensusSlotsInEpoch: Int): Long =
    Math.multiplyExact(consensusSlotsInEpoch, consensusSecondsInSlot)

  def virtualGenesisBlockTimeStamp(params: NetworkParams): Long =
    params.sidechainGenesisBlockTimestamp - epochInSeconds(ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSecondsInSlot, ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSlotsInEpoch) + ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSecondsInSlot

  def timestampToEpochAndSlot(params: NetworkParams, timestamp: Block.Timestamp): ConsensusEpochAndSlot =
    ConsensusEpochAndSlot(timeStampToEpochNumber(params, timestamp), timeStampToSlotNumber(params, timestamp))

  def timeStampToEpochNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusEpochNumber =
    intToConsensusEpochNumber(getEpochIndex(params, timestamp))

  def timeStampToSlotNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusSlotNumber = {
    val (remainingSlotsInFork, _, lastFork) = getConsensusInformationFromTimestamp(params, timestamp, false)
    val slotIndex = (remainingSlotsInFork % epochInSeconds(lastFork._2.consensusSecondsInSlot, lastFork._2.consensusSlotsInEpoch)) / lastFork._2.consensusSecondsInSlot
    intToConsensusSlotNumber(slotIndex.toInt + 1)
  }

  // Slot number starting from genesis block
  def timeStampToAbsoluteSlotNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusAbsoluteSlotNumber = {
    if (ConsensusParamsUtil.numberOfConsensusParamsFork > 1) {
      val epoch = getEpochIndex(params, timestamp)
      val slot = timeStampToSlotNumber(params, timestamp)
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
      val slotNumber = timeStampToEpochNumber(params, timestamp) * ConsensusParamsFork.DefaultConsensusParamsFork.consensusSlotsInEpoch +
        timeStampToSlotNumber(params, timestamp)
      intToConsensusAbsoluteSlotNumber(slotNumber)
    }

  }

    def getTimeStampForEpochAndSlot(
      params: NetworkParams,
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
      virtualGenesisBlockTimeStamp(params) + accumulatedSecondsPerEpoch
    } else {
      val defaultConsensusParamsFork = ConsensusParamsUtil.getConsensusParamsForkActivation.head

      val totalSlots: Int = (epochNumber - 1) * defaultConsensusParamsFork._2.consensusSlotsInEpoch + (slotNumber - 1)
      virtualGenesisBlockTimeStamp(params) + (totalSlots * defaultConsensusParamsFork._2.consensusSecondsInSlot)
    }
  }

  def secondsRemainingInSlot(params: NetworkParams, timestamp: Block.Timestamp): Long = {
    val secondsElapsedInSlot = (timestamp - virtualGenesisBlockTimeStamp(params)) % ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(Option.empty)
    ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(Option.empty) - secondsElapsedInSlot
  }

  private def getEpochIndex(params: NetworkParams, timestamp: Block.Timestamp): Int = {
    require(
      timestamp >= params.sidechainGenesisBlockTimestamp,
      s"Try to get index epoch for timestamp $timestamp which are less than genesis timestamp ${params.sidechainGenesisBlockTimestamp}"
    )
    val (remainingSlotsInFork, startingForkEpoch, lastFork) = getConsensusInformationFromTimestamp(params, timestamp, true)
    startingForkEpoch + (remainingSlotsInFork / epochInSeconds(lastFork._2.consensusSecondsInSlot, lastFork._2.consensusSlotsInEpoch)).toInt
  }

  private def getConsensusInformationFromTimestamp(params: NetworkParams, timestamp: Block.Timestamp, getEpoch: Boolean): (Long, Int, (Int, ConsensusParamsFork)) = {
    require(
      timestamp >= params.sidechainGenesisBlockTimestamp,
      s"Try to get index epoch for timestamp $timestamp which are less than genesis timestamp ${params.sidechainGenesisBlockTimestamp}"
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
