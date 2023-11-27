package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

case class Version1_2_0Fork(active : Boolean = false) extends OptionalSidechainFork

/**
 * <p>This fork introduces 2 major changes:</p>
 * <ul>
 *  <li>1. Allow Forward Transfer to Smart Contact addresses</li>
 *  <li>2. Allow sending funds with a FT to a Forger Pool address, these funds will be distributed between forgers
 *      at the end of withdrawal epoch.</li>
 * </ul>
 */
object Version1_2_0Fork {
  def get(epochNumber: Int): Version1_2_0Fork = {
    ForkManager.getOptionalSidechainFork[Version1_2_0Fork](epochNumber).getOrElse(DefaultVersion1_2_0Fork)
  }

  val DefaultVersion1_2_0Fork: Version1_2_0Fork = Version1_2_0Fork()
}