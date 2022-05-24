package com.horizen.validation

import com.horizen.block.SidechainBlockBase
import com.horizen.params.NetworkParams
import com.horizen.transaction.Transaction

import scala.util.Try

class SidechainBlockSemanticValidator[TX <: Transaction, PMOD <: SidechainBlockBase[TX]](params: NetworkParams) extends SemanticBlockValidator[PMOD] {
  override def validate(block: PMOD): Try[Unit] = block.semanticValidity(params)
}
