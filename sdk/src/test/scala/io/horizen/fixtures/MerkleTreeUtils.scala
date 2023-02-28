package com.horizen.fixtures

import com.horizen.utils.MerkleTree
import scala.collection.JavaConverters._

object MerkleTreeUtils {

  //returns count of leves on the lowest level of full binary tree
  def getFullSize(leavesCount: Int): Int = {
    var i = 1
    while (i < leavesCount)
      i *= 2
    i
  }

  //returns height of binary tree for specified count of leaves
  def getTreeHeight(leavesCount: Int): Int = {
    var size = getFullSize(leavesCount)
    var treeHeight = 1
    while (size > 1) {
      treeHeight += 1
      size /= 2
    }

    treeHeight
  }

  def getPaddingCount(treeHeight: Int, level: Int): Int = {
    var currentLevel = level
    var paddingCount = 1
    while (currentLevel < treeHeight) {
      currentLevel += 1
      paddingCount *= 2
    }
    paddingCount
  }

  def getFullMerkleTree(sidechainHashes: Seq[Array[Byte]]): MerkleTree = {
    var merkleTreeLeaves = sidechainHashes

    val fullMerkleTreeSize = MerkleTreeUtils.getFullSize(merkleTreeLeaves.size)
    val fullMerkleTreeHeight = MerkleTreeUtils.getTreeHeight(merkleTreeLeaves.size)

    while (merkleTreeLeaves.size < fullMerkleTreeSize) {
      var currentLevel = fullMerkleTreeHeight
      var paddingCount = 0
      var currentLevelLeavesCount = merkleTreeLeaves.size
      while (currentLevel > 1 && paddingCount == 0) {
        if (currentLevelLeavesCount %2 == 1) {
          paddingCount = MerkleTreeUtils.getPaddingCount(fullMerkleTreeHeight, currentLevel)
        } else {
          currentLevel -= 1
          currentLevelLeavesCount /= 2
        }
      }
      merkleTreeLeaves = merkleTreeLeaves ++ merkleTreeLeaves.slice(merkleTreeLeaves.size - paddingCount, merkleTreeLeaves.size)
    }

    MerkleTree.createMerkleTree(merkleTreeLeaves.asJava)
  }
}
