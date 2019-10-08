package com.horizen.validation

import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils
import scorex.core.block.BlockValidator
import scorex.util.idToBytes

import scala.util.{Failure, Success, Try}

class SidechainBlockValidator(params: NetworkParams) extends BlockValidator[SidechainBlock] {
  override def validate(block: SidechainBlock): Try[Unit] = {
    if (block.semanticValidity(params)) {
      Success()
    }
    else {
      Failure(new IllegalArgumentException("Semantic validation failed for block %s".format(BytesUtils.toHexString(idToBytes(block.id)))))
    }
  }
}
