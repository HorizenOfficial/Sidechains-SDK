package com.horizen

trait UtxoMerkleTreeView {
  def utxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]]

  def utxoMerklePath(boxId: Array[Byte]): Option[Array[Byte]]
}
