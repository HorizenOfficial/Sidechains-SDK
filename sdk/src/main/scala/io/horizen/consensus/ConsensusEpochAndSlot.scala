package io.horizen.consensus


case class ConsensusEpochAndSlot(epochNumber: ConsensusEpochNumber, slotNumber: ConsensusSlotNumber) extends Comparable[ConsensusEpochAndSlot] {
  override def compareTo(other: ConsensusEpochAndSlot): Int = {
    Integer.compare(epochNumber, other.epochNumber) match {
      case 0 => Integer.compare(slotNumber, other.slotNumber)
      case compareResult => compareResult
    }
  }

  def <(other: ConsensusEpochAndSlot): Boolean = {
    this.compareTo(other) == -1
  }

  def >(other: ConsensusEpochAndSlot): Boolean = {
    this.compareTo(other) == 1
  }

  def <=(other: ConsensusEpochAndSlot): Boolean = {
    this.compareTo(other) match {
      case -1 => true
      case  0 => true
      case  _ => false
    }
  }

  def >=(other: ConsensusEpochAndSlot): Boolean = {
    this.compareTo(other) match {
      case 1 => true
      case 0 => true
      case _ => false
    }
  }

  override def toString: String = {
    s"Epoch: ${epochNumber}, Slot ${slotNumber}"
  }
}
