package io.horizen.crosschain

import io.horizen.cryptolibprovider.utils.{FieldElementUtils, HashUtils}
import io.horizen.sc2sc.{CrossChainMessage, CrossChainProtocolVersion}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.Assertions.intercept

import scala.util.Using

class CrossChainMessageMerkleTreeTest {
  @Test
  def ifMessageIsNotIncludedInMessagesList_getCrossChainMessageMerklePath_throwsIllegalArgumentException(): Unit = {
    // Arrange
    val msg1 = new CrossChainMessage(CrossChainProtocolVersion.VERSION_1, 1, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes)
    val msg2 = new CrossChainMessage(CrossChainProtocolVersion.VERSION_1, 1, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes)
    val notIncludedMsg = new CrossChainMessage(CrossChainProtocolVersion.VERSION_1, 1, "dfb5f051c7d132499c16a8ff8572a8f7".getBytes, "dfb5f051c7d132499c16a8ff8572a8f7".getBytes, "ac35757b0e5e8516f77ea05b889140ae".getBytes, "dfb5f051c7d132499c16a8ff8572a8f7".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes)
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
    val msg1 = new CrossChainMessage(CrossChainProtocolVersion.VERSION_1, 1, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "ac35757b0e5e8516f77ea05b889140ae".getBytes)
    val msg2 = new CrossChainMessage(CrossChainProtocolVersion.VERSION_1, 1, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "ac35757b0e5e8516f77ea05b889140ae".getBytes)
    val msg3 = new CrossChainMessage(CrossChainProtocolVersion.VERSION_1, 1, "ac35757b0e5e8516f77ea05b889140ae".getBytes, "f3281225c13d6e6c79befd1781daaaf5".getBytes, "dfb5f051c7d132499c16a8ff8572a8f7".getBytes, "bbc4442c5620d5681f942ba8ff15ef4c".getBytes, "ac35757b0e5e8516f77ea05b889140ae".getBytes)
    val messages = Seq(msg1, msg2, msg3)
    val ccMsgMerkleTree = new CrossChainMessageMerkleTree()

    Using.resources(
      ccMsgMerkleTree.initMerkleTree,
      FieldElementUtils.deserializeMany(msg3.bytes)
    ) { (tree, feContainer) =>
      Using.resource(
        HashUtils.fieldElementsListHash(feContainer.getFieldElementCollection)
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
