package io.horizen.history.validation

import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.params.NetworkParams
import io.horizen.transaction.Transaction

import scala.util.Try

class SidechainBlockSemanticValidator[TX <: Transaction, PMOD <: SidechainBlockBase[TX, _<: SidechainBlockHeaderBase]](params: NetworkParams) extends SemanticBlockValidator[PMOD] {
  override def validate(block: PMOD): Try[Unit] = block.semanticValidity(params)
}
