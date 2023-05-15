package io.horizen.crosschain

import io.horizen.cryptolibprovider.utils.{FieldElementUtils, HashUtils}
import io.horizen.sc2sc.{CrossChainMessageImpl, CrossChainProtocolVersion}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.Assertions.intercept

import scala.util.Using

class CrossChainMessageMerkleTreeTest {
  @Test
  def ifMessageIsNotIncludedInMessagesList_getCrossChainMessageMerklePath_throwsIllegalArgumentException(): Unit = {
    // Arrange
    val msg1 = new CrossChainMessageImpl(CrossChainProtocolVersion.VERSION_1, 1, "senderSidechain1".getBytes, "sender1".getBytes, "receiverSidechain1".getBytes, "receiver1".getBytes, "payload1".getBytes)
    val msg2 = new CrossChainMessageImpl(CrossChainProtocolVersion.VERSION_1, 1, "senderSidechain2".getBytes, "sender2".getBytes, "receiverSidechain2".getBytes, "receiver2".getBytes, "payload2".getBytes)
    val notIncludedMsg = new CrossChainMessageImpl(CrossChainProtocolVersion.VERSION_1, 1, "senderSidechain3".getBytes, "sender3".getBytes, "receiverSidechain3".getBytes, "receiver3".getBytes, "payload3".getBytes)
    val messages = Seq(msg1, msg2)
    val ccMsgMerkleTree = new CrossChainMessageMerkleTree()

    Using.resource(
      ccMsgMerkleTree.initMerkleTree
    ) { tree =>
      // Act
      val exception = intercept[IllegalArgumentException] {
        ccMsgMerkleTree.insertMessagesInMerkleTreeWithIndex(tree, messages, notIncludedMsg)
      }

      // Assert// Assert
      val expectedMessage = "Cannot get merkle path of a message not included in the message list"
      assertEquals(expectedMessage, exception.getMessage)
    }
  }

  @Test
  def verifyMessageMerklePathCorrectness(): Unit = {
    // Arrange
    val msg1 = new CrossChainMessageImpl(CrossChainProtocolVersion.VERSION_1, 1, "senderSidechain1".getBytes, "sender1".getBytes, "receiverSidechain1".getBytes, "receiver1".getBytes, "payload1".getBytes)
    val msg2 = new CrossChainMessageImpl(CrossChainProtocolVersion.VERSION_1, 1, "senderSidechain2".getBytes, "sender2".getBytes, "receiverSidechain2".getBytes, "receiver2".getBytes, "payload2".getBytes)
    val msg3 = new CrossChainMessageImpl(CrossChainProtocolVersion.VERSION_1, 1, "senderSidechain3".getBytes, "sender3".getBytes, "receiverSidechain3".getBytes, "receiver3".getBytes, "payload3".getBytes)
    val messages = Seq(msg1, msg2, msg3)
    val ccMsgMerkleTree = new CrossChainMessageMerkleTree()

    Using.resources(
      ccMsgMerkleTree.initMerkleTree,
      FieldElementUtils.deserializeMany(msg3.bytes)
    ) { (tree, feContainer) =>
      Using.resource(
        HashUtils.fieldElementListHash(feContainer.getFieldElementCollection)
      ) { msg3Fe =>
        val leafIndex = ccMsgMerkleTree.insertMessagesInMerkleTreeWithIndex(tree, messages, msg3)
        Using.resource(
          ccMsgMerkleTree.getCrossChainMessageTreeRootAsFieldElement(tree)
        ) { treeRoot =>
          val msg3MerklePath = ccMsgMerkleTree.getCrossChainMessageMerklePath(tree, leafIndex)
          assertTrue(msg3MerklePath.verify(msg3Fe, treeRoot))
        }
      }
    }
  }
}
