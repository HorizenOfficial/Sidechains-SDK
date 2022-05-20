package com.horizen.validation

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.block.{SidechainBlock, SidechainBlockBase}
import scorex.core.transaction.Transaction

import scala.util.Try

trait SemanticBlockValidatorBase[TX <: Transaction] {
  def validate(block: SidechainBlockBase[TX]): Try[Unit]
}

trait SemanticBlockValidator extends SemanticBlockValidatorBase[SidechainTypes#SCBT] {
  override def validate(block: SidechainBlockBase[SidechainTypes#SCBT]): Try[Unit]
}

trait SemanticAccountBlockValidator extends SemanticBlockValidatorBase[SidechainTypes#SCAT] {
  def validate(block: SidechainBlockBase[SidechainTypes#SCAT]): Try[Unit]
}
