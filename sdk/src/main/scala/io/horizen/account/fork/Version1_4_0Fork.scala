package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

case class Version1_4_0Fork(active: Boolean = false) extends OptionalSidechainFork

/**
 * <p>This fork introduces the following major changes:</p>
 * <ul>
 *  <li>1. It enables max cap for mainchain forger reward distribution based on mainchain coinbase. </li>
 * </ul>
 */
object Version1_4_0Fork {
  def get(epochNumber: Int): Version1_4_0Fork = {
    ForkManager.getOptionalSidechainFork[Version1_4_0Fork](epochNumber).getOrElse(DefaultFork)
  }

  private val DefaultFork: Version1_4_0Fork = Version1_4_0Fork()
}
