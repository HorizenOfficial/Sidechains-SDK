package io.horizen.history.validation

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.params.NetworkParams
import com.horizen.transaction.Transaction

import scala.util.Try

class SidechainBlockSemanticValidator[TX <: Transaction, PMOD <: SidechainBlockBase[TX, _<: SidechainBlockHeaderBase]](params: NetworkParams) extends SemanticBlockValidator[PMOD] {
  override def validate(block: PMOD): Try[Unit] = block.semanticValidity(params)
}
