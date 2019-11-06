package com.horizen.validation

import com.horizen.SidechainHistory
import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils
import scorex.util.idToBytes

import scala.util.{Failure, Success, Try}

class SidechainBlockSemanticValidator(params: NetworkParams) extends SidechainBlockValidator {
  override def validate(block: SidechainBlock, history: SidechainHistory): Try[Unit] = {
    if (block.semanticValidity(params)) {
      Success()
    }
    else {
      Failure(new IllegalArgumentException("Semantic validation failed for block %s".format(BytesUtils.toHexString(idToBytes(block.id)))))
    }
  }
}
