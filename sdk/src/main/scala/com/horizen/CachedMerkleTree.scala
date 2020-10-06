package com.horizen

import com.horizen.librustsidechains.FieldElement
import com.horizen.merkletreenative.BigLazyMerkleTree
import scorex.util.ScorexLogging

import scala.collection.mutable

class CachedMerkleTree(merkleTree: BigLazyMerkleTree,
                       val addCache: mutable.Map[Long, FieldElement] = new mutable.HashMap(),
                       val removeCache: mutable.Map[Long, FieldElement] = new mutable.HashMap()) extends ScorexLogging {

  def getUpdatedCachedMerkleTree(toRemove: Iterable[FieldElement], toAdd: Iterable[FieldElement]): Option[CachedMerkleTree] = {
    val newCachedTree = this.copy()

    if (newCachedTree.removeElements(toRemove) && newCachedTree.addElements(toAdd)) {
      Option(newCachedTree)
    }
    else {
      None
    }
  }


  private def addElements(fieldElements: Iterable[FieldElement]): Boolean = {
    fieldElements.forall(element => addElement(element))
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
      case _ => false
    }
  }

  private def removeElements(fieldElements: Iterable[FieldElement]): Boolean = {
    fieldElements.forall(element => removeFieldElement(element))
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
      case _ => false
    }
  }

  private def copy(): CachedMerkleTree = {
    new CachedMerkleTree(merkleTree, mutable.HashMap[Long, FieldElement]() ++ addCache, mutable.HashMap[Long, FieldElement]() ++ removeCache)
  }
}
