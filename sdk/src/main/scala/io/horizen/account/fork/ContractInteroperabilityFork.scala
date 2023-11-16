package io.horizen.account.fork

import io.horizen.fork.{ForkManager, OptionalSidechainFork}

case class ContractInteroperabilityFork(active: Boolean = false) extends OptionalSidechainFork

object ContractInteroperabilityFork {
  def get(epochNumber: Int): ContractInteroperabilityFork = {
    ForkManager.getOptionalSidechainFork[ContractInteroperabilityFork](epochNumber).getOrElse(DefaultFork)
  }

  private val DefaultFork: ContractInteroperabilityFork = ContractInteroperabilityFork()
}