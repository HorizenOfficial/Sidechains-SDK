package io.horizen.utils

import io.horizen.account.fork.ConsensusParamsFork
import io.horizen.consensus._
import io.horizen.params.NetworkParams
import sparkz.core.block.Block

object TimeToEpochUtils {
  def epochInSeconds(params: NetworkParams, consensusSlotsInEpoch: Int): Long =
    Math.multiplyExact(consensusSlotsInEpoch, params.consensusSecondsInSlot)

  def virtualGenesisBlockTimeStamp(params: NetworkParams): Long =
    params.sidechainGenesisBlockTimestamp - epochInSeconds(params, ConsensusParamsUtil.getConsensusParamsForkActivation.head._2.consensusSlotsInEpoch) + params.consensusSecondsInSlot

  def timestampToEpochAndSlot(params: NetworkParams, timestamp: Block.Timestamp): ConsensusEpochAndSlot =
    ConsensusEpochAndSlot(timeStampToEpochNumber(params, timestamp), timeStampToSlotNumber(params, timestamp))

  def timeStampToEpochNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusEpochNumber =
    intToConsensusEpochNumber(getEpochIndex(params, timestamp) + 1)

  def timeStampToSlotNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusSlotNumber = {
    if (ConsensusParamsUtil.numberOfConsensusParamsFork > 1) {
      val (_, remainingSecondsInCurrentFork, forkIndex, _)= remainingSecondsAndForkIndexInActiveConsensusParameterFork(params, timestamp)
      val remainingSecondsInEpoch = remainingSecondsInCurrentFork % epochInSeconds(params, ConsensusParamsUtil.getConsensusParamsForkActivation(forkIndex - 1)._2.consensusSlotsInEpoch )
      // integer division here
      intToConsensusSlotNumber(remainingSecondsInEpoch.toInt / params.consensusSecondsInSlot + 1)
    } else {
      val defaultConsensusParamsFork = ConsensusParamsUtil.getConsensusParamsForkActivation.head
      val secondsFromEpochStart =
        timestamp - (getEpochIndex(params, timestamp) * epochInSeconds(params, defaultConsensusParamsFork._2.consensusSlotsInEpoch)) - virtualGenesisBlockTimeStamp(params)
      // integer division here
      val slotIndex = secondsFromEpochStart / params.consensusSecondsInSlot
      intToConsensusSlotNumber(slotIndex.toInt + 1)
    }
  }

  // Slot number starting from genesis block
  def timeStampToAbsoluteSlotNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusAbsoluteSlotNumber = {
    if (ConsensusParamsUtil.numberOfConsensusParamsFork > 1) {
      val (_, _, _, absoluteSlotNumber)= remainingSecondsAndForkIndexInActiveConsensusParameterFork(params, timestamp)
      absoluteSlotNumber
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
      var accumulatedSecondsPerFork: Long = currentFork._1 * epochInSeconds(params, previousFork._2.consensusSlotsInEpoch)

      while (epochNumber > currentFork._1 && currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        previousAccumulatedSecondsPerFork = accumulatedSecondsPerFork
        currentForkIndex = currentForkIndex + 1
        if (currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
          previousFork = currentFork
          currentFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex)
          accumulatedSecondsPerFork = accumulatedSecondsPerFork + (currentFork._1 - previousFork._1) * epochInSeconds(params, previousFork._2.consensusSlotsInEpoch )
        }
      }

      val lastFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex - 1)
      val epochInCurrentFork =  epochNumber - lastFork._1 - 1

      val accumulatedSecondsPerEpoch = previousAccumulatedSecondsPerFork + epochInCurrentFork * epochInSeconds(params, lastFork._2.consensusSlotsInEpoch) + slotNumber*params.consensusSecondsInSlot
      virtualGenesisBlockTimeStamp(params) + accumulatedSecondsPerEpoch - 1
    } else {
      val defaultConsensusParamsFork = ConsensusParamsUtil.getConsensusParamsForkActivation.head

      val totalSlots: Int = (epochNumber - 1) * defaultConsensusParamsFork._2.consensusSlotsInEpoch + (slotNumber - 1)
      virtualGenesisBlockTimeStamp(params) + (totalSlots * params.consensusSecondsInSlot)
    }
  }

  def secondsRemainingInSlot(params: NetworkParams, timestamp: Block.Timestamp): Long = {
    val secondsElapsedInSlot = (timestamp - virtualGenesisBlockTimeStamp(params)) % params.consensusSecondsInSlot
    params.consensusSecondsInSlot - secondsElapsedInSlot
  }

  private def getEpochIndex(params: NetworkParams, timestamp: Block.Timestamp): Int = {
    require(
      timestamp >= params.sidechainGenesisBlockTimestamp,
      s"Try to get index epoch for timestamp $timestamp which are less than genesis timestamp ${params.sidechainGenesisBlockTimestamp}"
    )

    if (ConsensusParamsUtil.numberOfConsensusParamsFork > 1) {
      val (_, remainingSecondsInCurrentFork, forkIndex, _) = remainingSecondsAndForkIndexInActiveConsensusParameterFork(params, timestamp)
      val currentFork = ConsensusParamsUtil.getConsensusParamsForkActivation(forkIndex - 1)
      // !!!integer division here!!!
      val epochIndex: Long = currentFork._1 + (remainingSecondsInCurrentFork / epochInSeconds(params, currentFork._2.consensusSlotsInEpoch))
      epochIndex.toInt
    } else {
      // !!!integer division here!!!
      val defaultConsensusParamsFork = ConsensusParamsUtil.getConsensusParamsForkActivation.head
      val epochIndex: Long = (timestamp - virtualGenesisBlockTimeStamp(params)) / epochInSeconds(params, defaultConsensusParamsFork._2.consensusSlotsInEpoch)
      epochIndex.toInt
    }
  }

  private def remainingSecondsAndForkIndexInActiveConsensusParameterFork(params: NetworkParams, timestamp: Block.Timestamp): (Long, Long, Int, ConsensusAbsoluteSlotNumber) = {
    val timestampFromGenesis = timestamp - virtualGenesisBlockTimeStamp(params)
    var currentForkIndex = 1
    var previousFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex - 1)
    var currentFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex)
    var accumulatedSecondsPerFork: Long = currentFork._1 * epochInSeconds(params, previousFork._2.consensusSlotsInEpoch)
    var previousAccumulatedSecondsPerFork: Long = 0
    var slotNumber: Int = ConsensusParamsFork.DefaultConsensusParamsFork.consensusSlotsInEpoch + 1

    while (timestampFromGenesis > accumulatedSecondsPerFork && currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
      previousAccumulatedSecondsPerFork = accumulatedSecondsPerFork
      slotNumber = slotNumber + (currentFork._1 - previousFork._1) * previousFork._2.consensusSlotsInEpoch
      currentForkIndex = currentForkIndex + 1
      if (currentForkIndex < ConsensusParamsUtil.numberOfConsensusParamsFork) {
        previousFork = currentFork
        currentFork = ConsensusParamsUtil.getConsensusParamsForkActivation(currentForkIndex)
        accumulatedSecondsPerFork = accumulatedSecondsPerFork + (currentFork._1 - previousFork._1) * epochInSeconds(params, previousFork._2.consensusSlotsInEpoch )
      }
    }
    val secondsInCurrentFork = timestampFromGenesis - previousAccumulatedSecondsPerFork
    slotNumber = (slotNumber + (secondsInCurrentFork / params.consensusSecondsInSlot)).toInt
    (previousAccumulatedSecondsPerFork, secondsInCurrentFork, currentForkIndex, intToConsensusAbsoluteSlotNumber(slotNumber))
  }
}
