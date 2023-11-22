package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

case class ForgerPoolRewardsFork(active : Boolean = false) extends OptionalSidechainFork

/**
 * <p>This fork introduces 2 major changes:</p>
 * <ul>
 *  <li>1. Allow Forward Transfer to Smart Contact addresses</li>
 *  <li>2. Allow sending funds with a FT to a Forger Pool address, these funds will be distributed between forgers
 *      at the end of withdrawal epoch.</li>
 * </ul>
 */
object ForgerPoolRewardsFork {
  def get(epochNumber: Int): ForgerPoolRewardsFork = {
    ForkManager.getOptionalSidechainFork[ForgerPoolRewardsFork](epochNumber).getOrElse(DefaultForgerPoolRewardsFork)
  }

  val DefaultForgerPoolRewardsFork: ForgerPoolRewardsFork = ForgerPoolRewardsFork()
}