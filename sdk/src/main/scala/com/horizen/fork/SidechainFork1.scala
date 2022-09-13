package com.horizen.fork

import com.horizen.librustsidechains.Constants

/**
 * Sidechain Fork # 1
 * To be applied at Consensus Epoch Number `epochNumber`
 *
 * Fork details:
 *
 * 1. In the mainchain we have a limitation on the amount of BTs that we can have inside a certificate.
 *  This limitation is not handled inside the Sidechain SDK and this may lead in the creation of an invalid certificate.
 *  For this reason in the SidechainFork1 we add some restrictions on the amount of BT that can mined inside a withdrawal epoch.
 *  The maximum amount of BT allowed in a certificate is 3999, the idea is that for every MC block reference that we sync we open up
 *  the possibility to mine some WithdrawalBoxes until we reach the limit of 3999 in the last block of the withdrawal epoch.
 *
 * 2. We have the possibility to restrict the forging operation to a predefined set of forgers "closed forging state" and there is no way to
 *  change from this state to an "open forging state" where everyone are allowed to forge.
 *  Inside this SidechainFork1 we give this opportunity by adding a new kind of transaction "OpenStakeTransaction"
 *  that let the current allowed forgers to vote for the forge opening.
 *  If the majority of the allowed forgers send this transactions, the forging operation is then opened to everyone.
 *
 * 3. `nonceLength` - changes nonce length used for building VrfMessages from 8 to 32
 *
 * 4. `stakePercentageForkApplied` - security improvement for calculating minimum required stake percentage
 *
 * @param epochNumber consensus epoch number at which this fork to be applied
 */
class SidechainFork1(override val epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {

  override def backwardTransferLimitEnabled(): Boolean = true

  override def openStakeTransactionEnabled(): Boolean = true

  override def nonceLength: Int = Constants.FIELD_ELEMENT_LENGTH()

  override def stakePercentageForkApplied: Boolean = true

}
