package com.horizen

import com.horizen.librustsidechains.FieldElement
import com.horizen.merkletreenative.BigLazyMerkleTree

import scala.collection.mutable

class CachedMerkleTree(merkleTree: BigLazyMerkleTree,
                       val addCache: mutable.Map[Long, FieldElement] = new mutable.HashMap(),
                       val removeCache: mutable.Map[Long, FieldElement] = new mutable.HashMap()) {

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
