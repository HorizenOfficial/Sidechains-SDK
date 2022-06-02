package com.horizen.validation

import com.horizen.AbstractHistory
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction

import scala.util.Try


trait HistoryBlockValidator[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  HSTOR <: AbstractHistoryStorage[PMOD, HSTOR],
  HT <: AbstractHistory[TX, H, PMOD, HSTOR, HT]]
{
  def validate(block: PMOD, history: HT): Try[Unit]
}