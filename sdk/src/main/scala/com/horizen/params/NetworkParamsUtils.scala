package com.horizen.params

import scorex.core.block.Block
import scorex.util.ModifierId

trait NetworkParamsUtils {
  this: {val params: NetworkParams} =>

  def isGenesisBlock(blockId: ModifierId): Boolean = {
    blockId.equals(params.sidechainGenesisBlockId)
  }

  def isGenesisBlock(blockTimestamp: Block.Timestamp, parentBlockId: ModifierId): Boolean = {
    blockTimestamp == params.sidechainGenesisBlockTimestamp && parentBlockId == params.sidechainGenesisBlockParentId
  }
}
