package io.horizen.fork

import com.horizen.librustsidechains.Constants
import io.horizen.utils.ZenCoinsUtils

/**
 * Sidechain Fork # 1
 *
 * Fork details:
 *
 *   - In the mainchain we have a limitation on the amount of BTs that we can have inside a certificate. This limitation
 *     is not handled inside the Sidechain SDK and this may lead in the creation of an invalid certificate. For this
 *     reason in the SidechainFork1 we add some restrictions on the amount of BT that can mined inside a withdrawal
 *     epoch. The maximum amount of BT allowed in a certificate is 3999, the idea is that for every MC block reference
 *     that we sync we open up the possibility to mine some WithdrawalBoxes until we reach the limit of 3999 in the last
 *     block of the withdrawal epoch.
 *
 *   - We have the possibility to restrict the forging operation to a predefined set of forgers "closed forging state"
 *     and there is no way to change from this state to an "open forging state" where everyone are allowed to forge.
 *     Inside this SidechainFork1 we give this opportunity by adding a new kind of transaction "OpenStakeTransaction"
 *     that let the current allowed forgers to vote for the forge opening. If the majority of the allowed forgers send
 *     this transactions, the forging operation is then opened to everyone.
 *
 *   - `nonceLength` - changes nonce length used for building VrfMessages from 8 to 32
 *
 *   - `stakePercentageForkApplied` - security improvement for calculating minimum required stake percentage
 *
 *   - ftMinAmount - Sidechain sends minimum FT amount as a parameter in a Certificate sent to Mainchain. Before the
 *     fork, minimum amount for Forward Transfer was 0, so it was possible to create a coin box which value is below the
 *     dust threshold (< 54 satoshi ATM), so the cost of verification is greater than the value itself. After the fork,
 *     ftMinAmount is set to min Dust threshold, and thus it will be impossible to create a FT with value below the dust
 *     threshold (< 54 satoshi ATM)
 *
 *   - coinBoxMinAmount - we should prevent creation of any kind of coin boxes on SC which value is too low, so the cost
 *     of verification is greater than the value itself. We already have such check for WithdrawalRequestBox, but it was
 *     missing for all CoinBoxes. After the fork is applied, all CoinBoxes value will be checked against the dust
 *     threshold limit - 54 satoshis ATM.
 */
class SidechainFork1(epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {
  override val backwardTransferLimitEnabled: Boolean = true
  override val openStakeTransactionEnabled: Boolean = true
  override val nonceLength: Int = Constants.FIELD_ELEMENT_LENGTH()
  override val stakePercentageForkApplied: Boolean = true
  override val ftMinAmount: Long = ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)
  override val coinBoxMinAmount: Long = ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)
}
