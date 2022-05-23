package com.horizen.validation

import com.horizen.account.history.AccountHistory
import com.horizen.{SidechainHistory, SidechainTypes}
import com.horizen.block.SidechainBlockBase
import scorex.core.transaction.Transaction

import scala.util.Try

trait HistoryBlockValidatorBase[TX <: Transaction, PM <: SidechainBlockBase[TX], HT]  {
  def validate(block: SidechainBlockBase[TX], history: HT): Try[Unit]
}

trait HistoryBlockValidator extends HistoryBlockValidatorBase[
  SidechainTypes#SCBT,
  SidechainBlockBase[SidechainTypes#SCBT],
  SidechainHistory]
{
  def validate(block: SidechainBlockBase[SidechainTypes#SCBT], history: SidechainHistory): Try[Unit]
}

trait HistoryAccountBlockValidator extends HistoryBlockValidatorBase[
  SidechainTypes#SCAT,
  SidechainBlockBase[SidechainTypes#SCAT],
  AccountHistory]
{
  def validate(block: SidechainBlockBase[SidechainTypes#SCAT], history: AccountHistory): Try[Unit]
}