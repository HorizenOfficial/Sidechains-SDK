package com.horizen.fork

import com.horizen.librustsidechains.Constants

/**
 * Sidechain Fork # 1
 * To be applied at Consensus Epoch Number `epochNumber`
 *
 * Fork details:
 *
 * 1. `nonceLength` - changes nonce length used for building VrfMessages from 8 to 32
 *
 * 2. `stakePercentageForkApplied` - security improvement for calculating minimum required stake percentage
 *
 * @param epochNumber consensus epoch number at which this fork to be applied
 */
class SidechainFork1(override val epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {

  override def nonceLength: Int = Constants.FIELD_ELEMENT_LENGTH()

  override def stakePercentageForkApplied: Boolean = true
}
