package com.horizen.storage

import com.horizen.block.SidechainBlock
import com.horizen.utils.ByteArrayWrapper
import scorex.util.{ModifierId, ScorexLogging}

class SidechainHistoryStorage extends ScorexLogging {

  private val bestBlockIdKey: ByteArrayWrapper = new ByteArrayWrapper(Array.fill(32)(-1: Byte))

  def height: Int = ???

  def bestBlockId: ModifierId = ???

  def bestBlock: SidechainBlock = ???

  def blockById(blockId: ModifierId): Option[SidechainBlock] = ???

  def parentBlockId(blockId: ModifierId): Option[ModifierId] = ???

}
