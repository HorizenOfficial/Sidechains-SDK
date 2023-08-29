package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

case class ZenDAOFork(active : Boolean = false) extends OptionalSidechainFork

object ZenDAOFork {
  def get(epochNumber: Int): ZenDAOFork = {
    ForkManager.getOptionalSidechainFork[ZenDAOFork](epochNumber).getOrElse(DefaultZenDAOFork)
  }

  val DefaultZenDAOFork: ZenDAOFork = ZenDAOFork()
}