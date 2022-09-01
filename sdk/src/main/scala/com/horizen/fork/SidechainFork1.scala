package com.horizen.fork

import com.horizen.utils.ZenCoinsUtils

/**
 * Sidechain Fork # 1
 * To be applied at Consensus Epoch Number `epochNumber`
 *
 * Fork details:
 *
 * 1. ftMinAmount - Sidechain sends minimum FT amount as a parameter in a Certificate sent to Mainchain.
 *  Before the fork, minimum amount for Forward Transfer was 0, so it was possible to create a coin box which
 *  value is below the dust threshold (< 54 satoshi ATM), so the cost of verification is greater than the value itself.
 *  After the fork, ftMinAmount is set to min Dust threshold, and thus it will be impossible to create a FT
 *  with value below the dust threshold (< 54 satoshi ATM)
 *
 * 2. coinBoxMinAmount - we should prevent  creation of any kind of coin boxes on SC which value is too low,
 * so the cost of verification is greater than the value itself. We already have such check for WithdrawalRequestBox,
 * but it was missing for all CoinBoxes.
 * After the fork is applied, all CoinBoxes value will be checked against the dust threshold limit - 54 satoshis ATM.
 *
 */
class SidechainFork1(override val epochNumber: ForkConsensusEpochNumber) extends BaseConsensusEpochFork(epochNumber) {

  override val ftMinAmount: Long = ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)

  override val coinBoxMinAmount: Long = ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)
}
