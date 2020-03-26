package com.horizen.chain

case class MainchainHeaderMetadata(sidechainHeight: Int, private val parent: MainchainHeaderHash) extends LinkedElement[MainchainHeaderHash] {
  override def getParentId: MainchainHeaderHash = parent
}
