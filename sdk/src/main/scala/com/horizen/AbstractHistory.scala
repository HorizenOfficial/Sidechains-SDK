package com.horizen

import com.horizen.block.SidechainBlockBase
import com.horizen.utils.BytesUtils
import scorex.core.consensus.History.ProgressInfo
import scorex.core.transaction.Transaction
import scorex.util.idToBytes

import scala.util.Try


abstract class AbstractHistory[TX <: Transaction, PM <: SidechainBlockBase[TX], HT <: AbstractHistory[TX, PM, HT]]
  extends scorex.core.consensus.History[
  PM,
  SidechainSyncInfo,
  AbstractHistory[TX, PM, HT]]{

  override def append(block: PM): Try[(HT, ProgressInfo[PM])] = {
    // just for test, remove it
    throw new IllegalArgumentException("parent block %s.".format(BytesUtils.toHexString(idToBytes(block.parentId))))
  }
}
