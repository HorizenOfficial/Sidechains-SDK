package io.horizen.params

import sparkz.util.ModifierId

trait NetworkParamsUtils {
  this: {val params: NetworkParams} =>

  def isGenesisBlock(blockId: ModifierId): Boolean = {
    blockId.equals(params.sidechainGenesisBlockId)
  }
}
