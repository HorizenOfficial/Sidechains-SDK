package com.horizen

import java.io.File

import com.horizen.block.SidechainBlock
import com.horizen.librustsidechains.FieldElement
import com.horizen.merkletreenative.BigLazyMerkleTree
import com.horizen.utils._
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._


trait ClosedBoxesMerkleTree {
  def validateTransaction(transaction: SidechainTypes#SCBT): Boolean
  def findFirstIncompatibleTransaction(transactions: Iterable[SidechainTypes#SCBT]): Option[SidechainTypes#SCBT]
  def validateBlock(block: SidechainBlock): Option[SidechainTypes#SCBT]
  def applyBlock(block: SidechainBlock): Unit
  def removeBlock(block: SidechainBlock)
  def removeBlocks(blocks: Seq[SidechainBlock]): Unit
  def closeTree(): Unit
  def getPositionForBoxId(id: ByteArrayWrapper): Long //remove ASAP
}


class ClosedBoxesZendooMerkleTree(val statePath: String, val dbPath: String, val cachePath: String)
  extends ClosedBoxesMerkleTree
  with ScorexLogging {
  private val merkleTreeHeight = 32
  private val merkleTree: BigLazyMerkleTree = BigLazyMerkleTree.init(merkleTreeHeight, statePath, dbPath, cachePath)
  log.info(s"Create / Load closed box tree by paths: state file: ${statePath}, db directory: ${dbPath}, cache directory: ${cachePath}")

  //current implementation of BigLazyMerkleTree require to use freeLazyMerkleTree() for saving current state at the end of the work
  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      merkleTree.freeLazyMerkleTree()
    }
  })

  //workaround until box id is not Poseidon hash
  private def bytesToFieldElement(bytes: Array[Byte]): FieldElement = {
    val fieldBytes = new Array[Byte](96)
    Array.copy(bytes, 0, fieldBytes, 0, bytes.length)
    Array.copy(bytes, 0, fieldBytes, 32, bytes.length)
    Array.copy(bytes, 0, fieldBytes, 64, 30)
    val fieldElement = FieldElement.deserialize(fieldBytes)

    fieldElement
  }

  private def getTransactionInputFieldElements(transaction: SidechainTypes#SCBT): Set[FieldElement] =
    transaction.boxIdsToOpen().asScala.map(bytesToFieldElement(_)).toSet

  private def getTransactionOutputFieldElements(transaction: SidechainTypes#SCBT): Set[FieldElement] =
    transaction.newBoxes().asScala.view.map(_.id()).map(bytesToFieldElement).toSet

  private def elementsCouldBeAdded(fieldElements: Set[FieldElement]): Boolean =
    fieldElements.map(fieldElement => merkleTree.getPosition(fieldElement)).forall(merkleTree.isPositionEmpty)

  private def elementsCouldBeRemoved(fieldElements: Set[FieldElement]): Boolean =
    fieldElements.map(fieldElement => merkleTree.getPosition(fieldElement)).forall(element => !merkleTree.isPositionEmpty(element))


  def validateTransaction(transaction: SidechainTypes#SCBT): Boolean = {
    val removedFieldElements = getTransactionInputFieldElements(transaction)
    val addedFieldElements = getTransactionOutputFieldElements(transaction)

    elementsCouldBeAdded(addedFieldElements) && elementsCouldBeRemoved(removedFieldElements)
  }

  def findFirstIncompatibleTransaction(transactions: Iterable[SidechainTypes#SCBT]): Option[SidechainTypes#SCBT] = {
    transactions.foldLeft(new CachedMerkleTree(merkleTree)) {
      case (cachedMerkleTree: CachedMerkleTree, transaction: SidechainTypes#SCBT) =>
        val removedFieldElements = getTransactionInputFieldElements(transaction)
        val addedFieldElements = getTransactionOutputFieldElements(transaction)

        //try to get new cached tree with applied field elements
        cachedMerkleTree.tryToGetUpdatedCachedMerkleTree(removedFieldElements, addedFieldElements) match {
          case Some(updatedCachedMerkleTree) => updatedCachedMerkleTree //if it is possible to get get new tree then transaction could be applied
          case None => return Option(transaction) //if it not possible to get new tree then transaction is not compatible, return that incompatible transaction
        }
    }
    None
  }

  //return incompatible transaction
  def validateBlock(block: SidechainBlock): Option[SidechainTypes#SCBT] = {
    findFirstIncompatibleTransaction(block.transactions)
  }

  def applyBlock(block: SidechainBlock): Unit = {
    block.transactions.foreach{ transaction =>
      val toRemove = getTransactionInputFieldElements(transaction)
      val toAdd =  getTransactionOutputFieldElements(transaction)

      updateMerkleTree(toRemove, toAdd)
    }
  }

  def removeBlock(block: SidechainBlock): Unit = {
    //if remove block then transaction order shall be reversed as well as outputs became as remove elements and input as add elements
    block.transactions.reverse.foreach{ transaction =>
      val toAdd = getTransactionInputFieldElements(transaction)
      val toRemove = getTransactionOutputFieldElements(transaction)

      updateMerkleTree(toRemove, toAdd)
    }
  }


  private def updateMerkleTree(toRemoveElements: Iterable[FieldElement], toAddElements: Iterable[FieldElement]): Unit = {
    val toRemovePositions = toRemoveElements.map(merkleTree.getPosition).toArray
    val toAdd = toAddElements.toList.asJava
    val toAddPositions = toAddElements.map(element => merkleTree.getPosition(element))

    log.info(s"update merkle tree positions: ${toRemovePositions.mkString("Remove(", ", ", ")")}, add ${toAddPositions.mkString("Add(", ", ", ")")}")

    //box to add could be placed at the same leaf which are deleted in the same transaction, thus first remove and only then update

    if (toRemovePositions.nonEmpty) {
      require(toRemovePositions.forall(!merkleTree.isPositionEmpty(_)), "Position is empty in UTXO merkle tree")
      merkleTree.removeLeaves(toRemovePositions)
    }

    if (toAddPositions.nonEmpty) {
      require(toAddPositions.forall(merkleTree.isPositionEmpty), "Position is not empty in UTXO merkle tree")
      merkleTree.addLeaves(toAdd)
    }
  }

  //blocks starts from old to new
  def removeBlocks(blocks: Seq[SidechainBlock]): Unit = {
    blocks.reverse.foreach(removeBlock) //blocks started from the oldest one, removing blocks shall be in reversed order
  }

  def getPositionForBoxId(id: ByteArrayWrapper): Long = {
    merkleTree.getPosition(bytesToFieldElement(id))
  }

  def closeTree(): Unit = {
    merkleTree.freeLazyMerkleTree()
  }
}


object ClosedBoxesZendooMerkleTree {
  def newTreeCouldBeCreatedForPaths(statePath: String, dbPath: String, cachePath: String): Boolean = {
    if (statePath.isEmpty || dbPath.isEmpty || cachePath.isEmpty) {
      return false
    }

    val stateFile = new File(statePath).getAbsoluteFile
    val dbFile = new File(dbPath).getAbsoluteFile
    val cacheFile = new File(cachePath).getAbsoluteFile

    (stateFile.exists(), dbFile.exists(), cacheFile.exists()) match {
      case (false, false, false) => true //new tree could be created only if no directories / file is exist
      case _ => false
    }
  }

  def treeCouldBeLoadedForPaths(statePath: String, dbPath: String, cachePath: String): Boolean = {
    if (statePath.isEmpty || dbPath.isEmpty || cachePath.isEmpty) {
      return false
    }

    val stateFile = new File(statePath).getAbsoluteFile
    val dbFile = new File(dbPath).getAbsoluteFile
    val cacheFile = new File(cachePath).getAbsoluteFile

    (stateFile.isFile, dbFile.isDirectory, cacheFile.isDirectory) match {
      case (true, true, true) => true //tree could be loaded only if state file is exist and directories for db and cache is exist
      case _ => false
    }
  }
}