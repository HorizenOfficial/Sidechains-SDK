package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

case class ForgerPoolRewardsFork(active : Boolean = false) extends OptionalSidechainFork

object ForgerPoolRewardsFork {
  def get(epochNumber: Int): ForgerPoolRewardsFork = {
    ForkManager.getOptionalSidechainFork[ForgerPoolRewardsFork](epochNumber).getOrElse(DefaultForgerPoolRewardsFork)
  }

  val DefaultForgerPoolRewardsFork: ForgerPoolRewardsFork = ForgerPoolRewardsFork()
}