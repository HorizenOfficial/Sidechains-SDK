package io.horizen.history.validation

import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.history.AbstractHistory
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.Transaction

import scala.util.Try


trait HistoryBlockValidator[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],

  HT <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HT]]
{
  def validate(block: PMOD, history: HT): Try[Unit]
}