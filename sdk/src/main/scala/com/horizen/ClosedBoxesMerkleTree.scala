package com.horizen

import java.io.File
import java.math.BigInteger

import com.horizen.block.SidechainBlock
import com.horizen.librustsidechains.FieldElement
import com.horizen.merkletreenative.BigLazyMerkleTree
import com.horizen.utils._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Random

//remove ASAP as merkle tree allow to store state
trait TempDirectoriesCreator {
  private val prefix = if (this.getClass.getCanonicalName != null) this.getClass.getCanonicalName else ""
  private val tempDirs: mutable.Set[File] = new mutable.HashSet[File]()


  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      tempDirs.foreach(deleteRecur)
    }
  }
  )

  private def deleteRecur(dir: File): Unit = {
    if (dir == null) return
    val files: Array[File] = dir.listFiles()
    if (files != null)
      files.foreach(deleteRecur)
    dir.delete()
    //println(s"Delete result for delete: ${dir} is ${deleteResult}")
  }

  private def createTempDirPath(): File = new File(System.getProperty("java.io.tmpdir") + File.separator + prefix + "-" + Math.abs(Random.nextInt()))

  private def createTempDir(): File = {
    val dir = createTempDirPath()
    dir.mkdirs()
    dir
  }

  def createNewTempDir(): File = {
    val tempDir = createTempDir()
    tempDirs.add(tempDir)
    tempDir
  }

  def createNewTempDirPath(): File = {
    val tempDir = createTempDirPath()
    tempDirs.add(tempDir)
    tempDir
  }
}

case class TransactionResponse(transactionsCouldBePut: Seq[SidechainTypes#SCBT], transactionsCouldNotBePut: Seq[SidechainTypes#SCBT])


//currently works only with tree
class CachedMerkleTree(
                        merkleTree: BigLazyMerkleTree,
                        val addCache: mutable.Map[Long, FieldElement] = mutable.HashMap()[Long, FieldElement],
                        val removeCache: mutable.Map[Long, FieldElement] = mutable.HashMap()[Long, FieldElement]) {

  def getUpdatedCachedMerkleTree(toRemove: Iterable[FieldElement], toAdd: Iterable[FieldElement]): Option[CachedMerkleTree] = {
    val newCachedTree = this.copy()

    newCachedTree.removeElements(toRemove)
    val addingIsSuccessfully = newCachedTree.addElements(toAdd)
    if (addingIsSuccessfully) {
      Option(newCachedTree)
    }
    else {
      None
    }
  }


  private def addElements(fieldElements: Iterable[FieldElement]): Boolean = {
    fieldElements.view.forall(element => addElement(element))
  }

  private def addElement(fieldElement: FieldElement): Boolean = {
    val position = merkleTree.getPosition(fieldElement)

    val treeContains: Boolean = !merkleTree.isPositionEmpty(position)
    val addCacheContains: Boolean = addCache.contains(position)
    val removeCacheContains: Boolean = removeCache.contains(position)

    (treeContains, addCacheContains, removeCacheContains) match {
      case (false, false, false) =>
        addCache(position) = fieldElement
        true
      case (true, false, true) =>
        removeCache.remove(position)
        true
      case (true, false, false) =>
        false
      case (false, true, false) =>
        false
      case _ => throw new IllegalStateException()
    }
  }

  private def removeElements(fieldElements: Iterable[FieldElement]): Unit = {
    fieldElements.foreach(removeFieldElement)
  }

  private def removeFieldElement(fieldElement: FieldElement): Boolean = {
    val position = merkleTree.getPosition(fieldElement)

    val treeContains: Boolean = !merkleTree.isPositionEmpty(position)
    val addCacheContains: Boolean = addCache.contains(position)
    val removeCacheContains: Boolean = removeCache.contains(position)

    (treeContains, addCacheContains, removeCacheContains) match {
      case (true, false, false) =>
        removeCache(position) = fieldElement
        true
      case (false, true, false) =>
        addCache.remove(position)
        true
      case _ => throw new IllegalStateException()
    }
  }

  private def copy(): CachedMerkleTree = {
    new CachedMerkleTree(merkleTree, mutable.HashMap[Long, FieldElement]() ++ addCache, mutable.HashMap[Long, FieldElement]() ++ removeCache)
  }
}


class CachedClosedBoxesMerkleTree(private var cachedMerkleTree: CachedMerkleTree) {
  private def bytesToFieldElement(bytes: Array[Byte]): FieldElement = {
    new FieldElement(new BigInteger(bytes))
  }


  def addTransaction(transaction: SidechainTypes#SCBT): Boolean = {
    val removedFieldElements = transaction.boxIdsToOpen().asScala.map(bytesToFieldElement(_))
    val addedFieldElements = transaction.newBoxes().asScala.view.map(_.id()).map(bytesToFieldElement)

    val newCachedTreeOpt = cachedMerkleTree.getUpdatedCachedMerkleTree(removedFieldElements, addedFieldElements)
    newCachedTreeOpt match {
      case Some(newCachedTree) =>
        cachedMerkleTree = newCachedTree
        true
      case None =>
        false
    }
  }
}

class ClosedBoxesMerkleTree() extends TempDirectoriesCreator {
  private val merkleTreeHeight = 32
  private val statePath = createNewTempDirPath().getAbsolutePath + File.separator + "state"
  private val dbPath = createNewTempDirPath().getAbsolutePath
  private val cachePath = createNewTempDirPath().getAbsolutePath

  private val merkleTree: BigLazyMerkleTree = BigLazyMerkleTree.init(merkleTreeHeight, statePath, dbPath, cachePath)

  private def bytesToFieldElement(bytes: Array[Byte]): FieldElement = {
    new FieldElement(new BigInteger(bytes))
  }

  //return incompatible transaction
  def validateBlock(block: SidechainBlock): Option[SidechainTypes#SCBT] = {
    val cachedClosedBoxesTree = builtNewCachedClosedBoxMerkleTree()
    block.transactions.foreach(transaction => {
      if (!cachedClosedBoxesTree.addTransaction(transaction)) {
        return Option(transaction)
      }
    })

    None
  }

  def applyBlock(block: SidechainBlock): Unit = {
    block.transactions.foreach{ transaction =>
      val toRemove = transaction.boxIdsToOpen().asScala.view.map(boxId => bytesToFieldElement(boxId.data))
      val toAdd = transaction.newBoxes().asScala.view.map(box => bytesToFieldElement(box.id()))

      updateMerkleTree(toRemove, toAdd)
    }
  }

  def removeBlock(block: SidechainBlock): Unit = {
    block.transactions.foreach{ transaction =>
      val toRemove = transaction.newBoxes().asScala.view.map(box => bytesToFieldElement(box.id()))
      val toAdd = transaction.boxIdsToOpen().asScala.view.map(boxId => bytesToFieldElement(boxId.data))

      updateMerkleTree(toRemove, toAdd)
    }
  }


  private def updateMerkleTree(toRemoveElements: Iterable[FieldElement], toAddElements: Iterable[FieldElement]): Unit = {
    val toRemove: Array[Long] = toRemoveElements.map(merkleTree.getPosition).toArray
    val toAdd: mutable.Buffer[FieldElement] = toAddElements.toBuffer

    //box to add could be placed at the same leaf which are deleted in the same transaction
    merkleTree.removeLeaves(toRemove)

    require(toAdd.map(element => merkleTree.getPosition(element)).forall(merkleTree.isPositionEmpty), "Position is not empty in UTXO merkle tree")
    merkleTree.addLeaves(toAdd.asJava)
  }

  //blocks starts from old to new
  def removeBlocks(blocks: Seq[SidechainBlock]): Unit = {
    blocks.reverse.foreach(removeBlock) //blocks started from the oldest one, removing blocks shall be in reversed order
  }

  def builtNewCachedClosedBoxMerkleTree(): CachedClosedBoxesMerkleTree = new CachedClosedBoxesMerkleTree(new CachedMerkleTree(merkleTree))
}
