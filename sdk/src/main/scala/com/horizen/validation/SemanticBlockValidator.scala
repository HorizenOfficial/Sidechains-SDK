package com.horizen.validation

import com.horizen.SidechainTypes
import com.horizen.block.{SidechainAccountBlock, SidechainBlock, SidechainBlockBase}
import scorex.core.transaction.Transaction

import scala.util.Try

trait SemanticBlockValidatorBase[TX <: Transaction] {
  //def validate(block: SidechainBlockBase[TX]): Try[Unit]
}

trait SemanticBlockValidator extends SemanticBlockValidatorBase[SidechainTypes#SCBT] {
  def validate(block: SidechainBlock): Try[Unit]
}

trait SemanticAccountBlockValidator extends SemanticBlockValidatorBase[SidechainTypes#SCAT] {
  def validate(block: SidechainAccountBlock): Try[Unit]
}
