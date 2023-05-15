package io.horizen.crosschain

import com.horizen.librustsidechains.{Constants, FieldElement}
import com.horizen.merkletreenative.{InMemoryAppendOnlyMerkleTree, MerklePath}
import io.horizen.cryptolibprovider.utils.{FieldElementUtils, HashUtils}
import io.horizen.sc2sc.CrossChainMessage

import scala.util.Using

sealed class CrossChainMessageMerkleTree {

  def initMerkleTree: InMemoryAppendOnlyMerkleTree =
    InMemoryAppendOnlyMerkleTree.init(Constants.MSG_MT_HEIGHT, 1L << Constants.MSG_MT_HEIGHT)

  def getCrossChainMessageTreeRootAsFieldElement(tree: InMemoryAppendOnlyMerkleTree): FieldElement = {
    tree.finalizeTreeInPlace
    tree.root
  }

  def getCrossChainMessageTreeRoot(tree: InMemoryAppendOnlyMerkleTree): Array[Byte] = {
    Using.resource(
      getCrossChainMessageTreeRootAsFieldElement(tree)
    ) {root =>
      root.serializeFieldElement
    }
  }

  def insertMessagesInMerkleTreeWithIndex(tree: InMemoryAppendOnlyMerkleTree, messages: Seq[CrossChainMessage], leafMsg: CrossChainMessage): Int = {
    var msgIndexInsertion = -1
    messages.zipWithIndex.foreach(indexedSeq => {
      val (msg, index) = indexedSeq
      insertMessageInMerkleTree(tree, msg)

      if (msg == leafMsg) {
        msgIndexInsertion = index
      }
    })

    if (msgIndexInsertion < 0) {
      throw new IllegalArgumentException("Cannot get merkle path of a message not included in the message list");
    }
    msgIndexInsertion
  }

  private def insertMessageInMerkleTree(msgTree: InMemoryAppendOnlyMerkleTree, currMsg: CrossChainMessage): Unit = {
    Using.resource(
      FieldElementUtils.deserializeMany(currMsg.bytes)
    ) {fieldElementsContainer =>
      Using.resource(
        HashUtils.fieldElementListHash(fieldElementsContainer.getFieldElementCollection)
      ) { cumulatedFieldElement =>
        msgTree.append(cumulatedFieldElement)
      }
    }
  }

  def appendMessagesToMerkleTree(msgTree: InMemoryAppendOnlyMerkleTree, messages: Seq[CrossChainMessage]): Unit =
    messages.foreach(msg => insertMessageInMerkleTree(msgTree, msg))

  def getCrossChainMessageMerklePath(tree: InMemoryAppendOnlyMerkleTree, leafIndex: Int): MerklePath = {
    tree.finalizeTreeInPlace
    tree.getMerklePath(leafIndex)
  }
}
