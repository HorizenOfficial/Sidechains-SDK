package com.horizen

import java.math.BigInteger

import com.horizen.block.SidechainBlock
import com.horizen.librustsidechains.FieldElement
import com.horizen.merkletreenative.BigLazyMerkleTree
import com.horizen.utils._

import scala.collection.JavaConverters._


trait ClosedBoxesMerkleTree {
  def validateTransaction(transaction: SidechainTypes#SCBT): Boolean
  def validateTransactions(transactions: Iterable[SidechainTypes#SCBT]): Option[SidechainTypes#SCBT]
  def validateBlock(block: SidechainBlock): Option[SidechainTypes#SCBT]
  def applyBlock(block: SidechainBlock): Unit
  def removeBlock(block: SidechainBlock)
  def removeBlocks(blocks: Seq[SidechainBlock]): Unit
  def getPositionForBoxId(id: ByteArrayWrapper): Long //remove ASAP
}


class ClosedBoxesZendooMerkleTree(val statePath: String, val dbPath: String, val cachePath: String) extends ClosedBoxesMerkleTree {
  private val merkleTreeHeight = 32
  private val merkleTree: BigLazyMerkleTree = BigLazyMerkleTree.init(merkleTreeHeight, statePath, dbPath, cachePath)

  private def bytesToFieldElement(bytes: Array[Byte]): FieldElement = {
    FieldElement.createFromLong(new BigInteger(bytes).longValue())
  }

  private def getRemovedFieldElements(transaction: SidechainTypes#SCBT): Set[FieldElement] = transaction.boxIdsToOpen().asScala.map(bytesToFieldElement(_)).toSet
  private def getAddedFieldElements(transaction: SidechainTypes#SCBT): Set[FieldElement] = transaction.newBoxes().asScala.view.map(_.id()).map(bytesToFieldElement).toSet
  private def elementsCouldBeAdded(fieldElements: Set[FieldElement]): Boolean = fieldElements.map(element => merkleTree.getPosition(element)).forall(merkleTree.isPositionEmpty)
  private def elementsCouldBeRemoved(fieldElements: Set[FieldElement]): Boolean = {
    require(fieldElements.map(element => merkleTree.getPosition(element)).forall(element => !merkleTree.isPositionEmpty(element)))
    true
  }


  def validateTransaction(transaction: SidechainTypes#SCBT): Boolean = {
    val removedFieldElements = getRemovedFieldElements(transaction)
    val addedFieldElements = getAddedFieldElements(transaction)

    elementsCouldBeAdded(addedFieldElements) && elementsCouldBeRemoved(removedFieldElements)
  }

  /*private def getUpdatedCachedMerkleTree(transaction: SidechainTypes#SCBT, cachedMerkleTree: CachedMerkleTree): Option[CachedMerkleTree] = {
    val removedFieldElements = getRemovedFieldElements(transaction)
    val addedFieldElements = getAddedFieldElements(transaction)

    cachedMerkleTree.getUpdatedCachedMerkleTree(removedFieldElements, addedFieldElements)
  }*/


  def validateTransactions(transactions: Iterable[SidechainTypes#SCBT]): Option[SidechainTypes#SCBT] = {
    transactions.foldLeft(new CachedMerkleTree(merkleTree)) {
      case (cachedMerkleTree: CachedMerkleTree, transaction: SidechainTypes#SCBT) =>
        val removedFieldElements = getRemovedFieldElements(transaction)
        val addedFieldElements = getAddedFieldElements(transaction)

        //try to get new cached tree with applied field elements
        cachedMerkleTree.getUpdatedCachedMerkleTree(removedFieldElements, addedFieldElements) match {
          case Some(updatedCachedMerkleTree) => updatedCachedMerkleTree //if it is possible to get get new tree then transaction could be applied
          case None => return Option(transaction) //if it not possible to get new tree then transaction is not compatible, return that incompatible transaction
        }
    }
    None
  }

  //return incompatible transaction
  def validateBlock(block: SidechainBlock): Option[SidechainTypes#SCBT] = {
    validateTransactions(block.transactions)
  }

  def applyBlock(block: SidechainBlock): Unit = {
    block.transactions.foreach{ transaction =>
      val toRemove = getRemovedFieldElements(transaction)
      val toAdd =  getAddedFieldElements(transaction)

      updateMerkleTree(toRemove, toAdd)
    }
  }

  def removeBlock(block: SidechainBlock): Unit = {
    block.transactions.foreach{ transaction =>
      val toRemove = getRemovedFieldElements(transaction)
      val toAdd = getAddedFieldElements(transaction)

      updateMerkleTree(toRemove, toAdd)
    }
  }


  private def updateMerkleTree(toRemoveElements: Iterable[FieldElement], toAddElements: Iterable[FieldElement]): Unit = {
    val toRemovePositions = toRemoveElements.map(merkleTree.getPosition).toArray
    val toAdd = toAddElements.toList.asJava
    val toAddPositions = toAddElements.map(element => merkleTree.getPosition(element))

    //box to add could be placed at the same leaf which are deleted in the same transaction
    merkleTree.removeLeaves(toRemovePositions)

    require(toAddPositions.forall(merkleTree.isPositionEmpty), "Position is not empty in UTXO merkle tree")
    merkleTree.addLeaves(toAdd)
  }

  //blocks starts from old to new
  def removeBlocks(blocks: Seq[SidechainBlock]): Unit = {
    blocks.reverse.foreach(removeBlock) //blocks started from the oldest one, removing blocks shall be in reversed order
  }

  def getPositionForBoxId(id: ByteArrayWrapper): Long = {
    merkleTree.getPosition(bytesToFieldElement(id))
  }
}
