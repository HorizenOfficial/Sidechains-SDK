package com.horizen.validation

import com.horizen.account.history.AccountHistory
import com.horizen.{AbstractHistory, SidechainHistory, SidechainTypes}
import com.horizen.block.{SidechainBlock, SidechainBlockBase}
import scorex.core.consensus.{History, SyncInfo}
import scorex.core.transaction.Transaction

import scala.util.Try

trait HistoryBlockValidatorBase[TX <: Transaction, PM <: SidechainBlockBase[TX], SI <: SyncInfo, H <: History[PM, SI, H]]  {
  def validate(block: SidechainBlockBase[TX], history: H): Try[Unit]
}

trait HistoryBlockValidator
{
  def validate(block: SidechainBlockBase[SidechainTypes#SCBT], history: SidechainHistory): Try[Unit]
}


trait HistoryAccountBlockValidator
{
  def validate(block: SidechainBlockBase[SidechainTypes#SCAT], history: AccountHistory): Try[Unit]
}