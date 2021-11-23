package com.horizen

trait UtxoMerkleTreeView {
  def utxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]]
  // TODO: maybe return MerklePath
  def utxoMerklePath(boxId: Array[Byte]): Option[Array[Byte]]
}
