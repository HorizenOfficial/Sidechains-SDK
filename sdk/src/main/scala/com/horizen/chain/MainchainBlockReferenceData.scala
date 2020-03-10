package com.horizen.chain

case class MainchainBlockReferenceData(sidechainHeight: Int, private val parent: MainchainBlockReferenceHash) extends LinkedElement[MainchainBlockReferenceHash] {
  override def getParentId: MainchainBlockReferenceHash = parent
}
