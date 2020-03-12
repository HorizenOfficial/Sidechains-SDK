package com.horizen.consensus

import com.horizen.params.NetworkParams
import scorex.core.block.Block


case class EpochAndSlot(epochNumber: ConsensusEpochNumber, slotNumber: ConsensusSlotNumber) extends Comparable[EpochAndSlot] {
  override def compareTo(other: EpochAndSlot): Int = {
    Integer.compare(epochNumber, other.epochNumber) match {
      case 0 => Integer.compare(slotNumber, other.slotNumber)
      case compareResult => compareResult
    }
  }

  def <(other: EpochAndSlot): Boolean = {
    this.compareTo(other) == -1
  }

  def >(other: EpochAndSlot): Boolean = {
    this.compareTo(other) == 1
  }

  override def toString: String = {
    s"Epoch: ${epochNumber}, Slot ${slotNumber}"
  }
}

trait TimeToEpochSlotConverter {
  this: {val params: NetworkParams} =>

  val epochInSeconds: Long = Math.multiplyExact(params.consensusSlotsInEpoch, params.consensusSecondsInSlot) // will throw exception in case of overflow
  val virtualGenesisBlockTimeStamp: Long = params.sidechainGenesisBlockTimestamp - epochInSeconds + params.consensusSecondsInSlot
  require(virtualGenesisBlockTimeStamp > 0)

  def timestampToEpochAndSlot(timestamp: Block.Timestamp): EpochAndSlot = {
    EpochAndSlot(timeStampToEpochNumber(timestamp), timeStampToSlotNumber(timestamp))
  }

  def timeStampToEpochNumber(timestamp: Block.Timestamp): ConsensusEpochNumber = intToConsensusEpochNumber(getEpochIndex(timestamp) + 1)

  def timeStampToSlotNumber(timestamp: Block.Timestamp): ConsensusSlotNumber = {
      val secondsFromEpochStart = timestamp - (getEpochIndex(timestamp) * epochInSeconds) - virtualGenesisBlockTimeStamp
      val slotIndex = secondsFromEpochStart / params.consensusSecondsInSlot //integer division here

      intToConsensusSlotNumber(slotIndex.toInt + 1)
  }

  //Slot number starting from genesis block
  def timeStampToAbsoluteSlotNumber(timestamp: Block.Timestamp): ConsensusAbsoluteSlotNumber = {
    val slotNumber = timeStampToEpochNumber(timestamp) * params.consensusSlotsInEpoch + timeStampToSlotNumber(timestamp)
    intToConsensusAbsoluteSlotNumber(slotNumber)
  }

  def getTimeStampForEpochAndSlot(epochNumber: ConsensusEpochNumber, slotNumber: ConsensusSlotNumber): Long = {
    require(slotNumber <= params.consensusSlotsInEpoch)

    val totalSlots: Int = (epochNumber - 1) * params.consensusSlotsInEpoch + (slotNumber - 1)
    virtualGenesisBlockTimeStamp + (totalSlots * params.consensusSecondsInSlot)
  }

  private def getEpochIndex(timestamp: Block.Timestamp): Int = {
    require(timestamp >= params.sidechainGenesisBlockTimestamp,
      s"Try to get index epoch for timestamp ${timestamp} which are less than genesis timestamp ${params.sidechainGenesisBlockTimestamp}")

    val epochIndex: Long = (timestamp - virtualGenesisBlockTimeStamp) / epochInSeconds // !!!integer division here!!!
    epochIndex.toInt
  }
}
