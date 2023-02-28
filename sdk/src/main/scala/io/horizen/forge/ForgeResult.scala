package io.horizen.forge

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.transaction.Transaction

sealed trait ForgeResult

case class ForgeSuccess[PMOD <: SidechainBlockBase[_<: Transaction,_<: SidechainBlockHeaderBase]](block: PMOD) extends ForgeResult {
  override def toString: String = s"Successfully generated block ${block.id} with size ${block.bytes.length}"
}

sealed trait ForgeFailure extends ForgeResult


case class SkipSlot(reason: String = "") extends ForgeFailure {
  override def toString: String = s"Skipped slot for forging" + (if(reason.nonEmpty) s" with reason: $reason" else "")
}

case object NoOwnedForgingStake extends ForgeFailure {
  override def toString: String = s"Can't forge block, no forging stake is present for epoch."
}

case object ForgingStakeListEmpty extends ForgeFailure {
  override def toString: String = s"Can't forge block, ForgerStakes list can't be empty."
}

case class ForgeFailed(ex: Throwable) extends ForgeFailure {
  override def toString: String = s"Failed block generation due $ex"
}
