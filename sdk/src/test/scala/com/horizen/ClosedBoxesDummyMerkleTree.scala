package com.horizen
import com.horizen.block.SidechainBlock
import com.horizen.utils.ByteArrayWrapper


class ClosedBoxesDummyMerkleTree extends ClosedBoxesMerkleTree
{
  var positionCounter = 0

  override def validateTransaction(transaction: SidechainTypes#SCBT): Boolean = true

  override def validateTransactions(transactions: Iterable[SidechainTypes#SCBT]): Option[SidechainTypes#SCBT] = None

  override def validateBlock(block: SidechainBlock): Option[SidechainTypes#SCBT] = None

  override def applyBlock(block: SidechainBlock): Unit = ()

  override def removeBlock(block: SidechainBlock): Unit = ()

  override def removeBlocks(blocks: Seq[SidechainBlock]): Unit = ()

  override def getPositionForBoxId(id: ByteArrayWrapper): Long = {
    positionCounter += 1
    positionCounter
  }
}