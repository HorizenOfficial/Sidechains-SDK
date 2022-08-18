package com.horizen.fork

import com.horizen.librustsidechains.Constants

class SidechainFork1(override val epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {

  override def nonceLength: Int = Constants.FIELD_ELEMENT_LENGTH()

  override def stakePercentageForkApplied: Boolean = true
}
