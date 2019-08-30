package com.horizen.chain

case class MainchainBlockReferenceData(sidechainHeight: Int, private val parent: MainchainBlockReferenceId) extends ChainData[MainchainBlockReferenceId] {
  override def getParentId: MainchainBlockReferenceId = parent
}
