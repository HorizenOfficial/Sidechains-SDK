package com.horizen.validation

import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils
import scorex.util.idToBytes

import scala.util.{Failure, Success, Try}

class SidechainBlockSemanticValidator(params: NetworkParams) extends SemanticBlockValidator {
  override def validate(block: SidechainBlock): Try[Unit] = block.semanticValidity(params)
}
