package com.horizen.validation

import com.horizen.AbstractHistory
import com.horizen.block.SidechainBlockBase
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction

import scala.util.Try


trait HistoryBlockValidator[
  TX <: Transaction,
  PMOD <: SidechainBlockBase[TX],
  HSTOR <: AbstractHistoryStorage[PMOD, HSTOR],
  HT <: AbstractHistory[TX, PMOD, HSTOR, HT]]
{
  def validate(block: PMOD, history: HT): Try[Unit]
}