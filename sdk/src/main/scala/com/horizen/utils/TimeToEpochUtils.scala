package com.horizen.utils

import com.horizen.consensus._
import com.horizen.params.NetworkParams
import sparkz.core.block.Block

object TimeToEpochUtils {
  def epochInSeconds(params: NetworkParams): Long =
    Math.multiplyExact(params.consensusSlotsInEpoch, params.consensusSecondsInSlot)

  def virtualGenesisBlockTimeStamp(params: NetworkParams): Long =
    params.sidechainGenesisBlockTimestamp - epochInSeconds(params) + params.consensusSecondsInSlot

  def timestampToEpochAndSlot(params: NetworkParams, timestamp: Block.Timestamp): ConsensusEpochAndSlot =
    ConsensusEpochAndSlot(timeStampToEpochNumber(params, timestamp), timeStampToSlotNumber(params, timestamp))

  def timeStampToEpochNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusEpochNumber =
    intToConsensusEpochNumber(getEpochIndex(params, timestamp) + 1)

  def timeStampToSlotNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusSlotNumber = {
    val secondsFromEpochStart =
      timestamp - (getEpochIndex(params, timestamp) * epochInSeconds(params)) - virtualGenesisBlockTimeStamp(params)
    // integer division here
    val slotIndex = secondsFromEpochStart / params.consensusSecondsInSlot
    intToConsensusSlotNumber(slotIndex.toInt + 1)
  }

  // Slot number starting from genesis block
  def timeStampToAbsoluteSlotNumber(params: NetworkParams, timestamp: Block.Timestamp): ConsensusAbsoluteSlotNumber = {
    val slotNumber = timeStampToEpochNumber(params, timestamp) * params.consensusSlotsInEpoch +
      timeStampToSlotNumber(params, timestamp)
    intToConsensusAbsoluteSlotNumber(slotNumber)
  }

  def getTimeStampForEpochAndSlot(
      params: NetworkParams,
      epochNumber: ConsensusEpochNumber,
      slotNumber: ConsensusSlotNumber
  ): Long = {
    require(slotNumber <= params.consensusSlotsInEpoch)

    val totalSlots: Int = (epochNumber - 1) * params.consensusSlotsInEpoch + (slotNumber - 1)
    virtualGenesisBlockTimeStamp(params) + (totalSlots * params.consensusSecondsInSlot)
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

    // !!!integer division here!!!
    val epochIndex: Long = (timestamp - virtualGenesisBlockTimeStamp(params)) / epochInSeconds(params)
    epochIndex.toInt
  }
}
