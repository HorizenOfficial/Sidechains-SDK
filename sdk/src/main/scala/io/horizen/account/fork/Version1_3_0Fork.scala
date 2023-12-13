package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

case class Version1_3_0Fork(active: Boolean = false) extends OptionalSidechainFork

object Version1_3_0Fork {
  def get(epochNumber: Int): Version1_3_0Fork = {
    ForkManager.getOptionalSidechainFork[Version1_3_0Fork](epochNumber).getOrElse(DefaultFork)
  }

  private val DefaultFork: Version1_3_0Fork = Version1_3_0Fork()
}