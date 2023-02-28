package com.horizen.history.validation

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.history.AbstractHistory
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction

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