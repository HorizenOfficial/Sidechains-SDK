package com.horizen.params

import scorex.util.ModifierId

trait NetworkParamsUtils {
  this: {val params: NetworkParams} =>

  def isGenesisBlock(blockId: ModifierId): Boolean = {
    blockId.equals(params.sidechainGenesisBlockId)
  }
}
