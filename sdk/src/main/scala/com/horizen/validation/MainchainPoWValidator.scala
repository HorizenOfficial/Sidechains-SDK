package com.horizen.validation

import com.horizen.block.{ProofOfWorkVerifier, SidechainBlock}
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.utils.BytesUtils
import scorex.core.block.BlockValidator
import scorex.util.idToBytes

import scala.util.{Failure, Success, Try}

class MainchainPoWValidator(sidechainHistoryStorage: SidechainHistoryStorage, params: NetworkParams) extends BlockValidator[SidechainBlock]{
  override def validate(block: SidechainBlock): Try[Unit] = {
    if(ProofOfWorkVerifier.checkNextWorkRequired(block, sidechainHistoryStorage, params)) {
      Success()
    }
     else {
      Failure (new IllegalArgumentException("Containing MC Blocks PoW difficulty is invalid for block %s".format(BytesUtils.toHexString(idToBytes(block.id)))))
    }
  }
}
