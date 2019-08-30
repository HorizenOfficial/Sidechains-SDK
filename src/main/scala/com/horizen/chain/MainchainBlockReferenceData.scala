package com.horizen.chain

import com.horizen.utils.ByteArrayWrapper

// @TODO add here MainchainBlockReferenceId as tagged type of ByteArrayWrapper


case class MainchainBlockReferenceData(sidechainHeight: Int, parent: ByteArrayWrapper)
  extends ChainData[ByteArrayWrapper] {
  override def getParentId: ByteArrayWrapper = parent
}
