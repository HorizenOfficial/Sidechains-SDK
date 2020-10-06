package com.horizen


import com.horizen.block.SidechainBlock
import com.horizen.box.{Box, BoxUnlocker}
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.utils.{ByteArrayWrapper, Utils}
import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.Test
import org.mockito.Mockito
import org.mockito.stubbing.Stubber
import org.scalatest.mockito.MockitoSugar

import scala.collection.JavaConverters._

trait MockitoHelper extends MockitoSugar {
  def doReturn(toBeReturned: Any): Stubber = {
    Mockito.doReturn(toBeReturned, Nil: _*)
  }
}

class ClosedBoxesZendooMerkleTreeTest extends TempDirectoriesCreator with MockitoHelper
{
  def createMockedTransaction(toRemoveIds: Seq[Long], toAddIds: Seq[Long], id: String = ""): SidechainTypes#SCBT = {
    val mockedTransaction: SidechainTypes#SCBT = mock[SidechainTypes#SCBT]

    val unlockers = toRemoveIds
      .view
      .map(long => Utils.doubleSHA256Hash(long.toString.getBytes))
      .map(bytes => new ByteArrayWrapper(bytes))
      .map(baw => new BoxUnlocker[Proposition] {
        override def closedBoxId(): Array[Byte] = baw.data
        override def boxKey(): Proof[Proposition] = null
      })
      .asJava

    doReturn(unlockers).when(mockedTransaction).unlockers()// mockedTransaction.boxIdsToOpen).thenReturn(toRemove)

    val toAdd = toAddIds
      .view
      .map(long => Utils.doubleSHA256Hash(long.toString.getBytes))
      .map(bytes => new ByteArrayWrapper(bytes))
      .map { baw =>
        val mockedBox = mock[Box[Proposition]]
        doReturn(baw.data).when(mockedBox).id()
        mockedBox
      }
      .asJava

    doReturn(toAdd).when(mockedTransaction).newBoxes()
    /*Mockito.when(mockedTransaction.newBoxes())
      .thenReturn(toAdd)*/

    doReturn(id).when(mockedTransaction).id()

    mockedTransaction
  }

  def mockedBlock(txs: Seq[SidechainTypes#SCBT]): SidechainBlock = {
    val mockedBlock: SidechainBlock = mock[SidechainBlock]

    doReturn(txs).when(mockedBlock).transactions

    mockedBlock
  }


  private def checkEmptyTree(closedBoxesZendooMerkleTree: ClosedBoxesZendooMerkleTree): Unit = {
    //check empty transaction on empty tree
    val emptyTransaction = createMockedTransaction(Seq(), Seq(1))
    assertTrue(closedBoxesZendooMerkleTree.validateTransaction(emptyTransaction))
    assertTrue(closedBoxesZendooMerkleTree.validateTransactions(Seq(emptyTransaction)).isEmpty)

    //check one compatible transaction on empty tree
    val firstTransaction = createMockedTransaction(Seq(), Seq(1, 2, 3))
    assertTrue(closedBoxesZendooMerkleTree.validateTransaction(firstTransaction))
    assertTrue(closedBoxesZendooMerkleTree.validateTransactions(Seq(firstTransaction)).isEmpty)

    //check one incompatible transaction on empty tree
    val incompatibleForEmptyTreeTransaction = createMockedTransaction(Seq(1), Seq())
    assertFalse(closedBoxesZendooMerkleTree.validateTransaction(incompatibleForEmptyTreeTransaction))
    assertFalse(closedBoxesZendooMerkleTree.validateTransactions(Seq(incompatibleForEmptyTreeTransaction)).isEmpty)

    //check many transaction on empty tree
    val secondTransaction = createMockedTransaction(Seq(3), Seq(4))
    assertTrue(closedBoxesZendooMerkleTree.validateTransactions(Seq(firstTransaction, secondTransaction)).isEmpty)
    assertFalse(closedBoxesZendooMerkleTree.validateTransactions(Seq(secondTransaction)).isEmpty)

    val blockWithOneTransaction = mockedBlock(Seq(firstTransaction))
    assertTrue(closedBoxesZendooMerkleTree.validateBlock(blockWithOneTransaction).isEmpty)

    try {
      closedBoxesZendooMerkleTree.removeBlock(blockWithOneTransaction)
      assertTrue("remove block on empty tree shall be failed", false)
    }
    catch {
      case ex: IllegalArgumentException => assertTrue(ex.getMessage.contains("Position is empty in UTXO merkle tree"))
      case e: Exception => assertTrue(s"Get unexpected exception: ${e.getMessage}", false)
    }

    val blockWithTwoTransactions = mockedBlock(Seq(firstTransaction, secondTransaction))
    assertTrue(closedBoxesZendooMerkleTree.validateBlock(blockWithTwoTransactions).isEmpty)
  }

  @Test
  def simpleTest(): Unit = {
    val closedBoxesZendooMerkleTree: ClosedBoxesZendooMerkleTree = new ClosedBoxesZendooMerkleTree(
      createNewTempDirPath().getAbsolutePath,
      createNewTempDirPath().getAbsolutePath,
      createNewTempDirPath().getAbsolutePath)

    checkEmptyTree(closedBoxesZendooMerkleTree)

    //create transaction sequence
    val tx_1_1 = createMockedTransaction(Seq(), Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), "1") //1, 2, 3, 4, 5, 6, 7, 8, 9, 10
    val tx_1_2 = createMockedTransaction(Seq(1), Seq(), "2") //2, 3, 4, 5, 6, 7, 8, 9, 10
    val tx_1_3 = createMockedTransaction(Seq(2), Seq(2), "3") //2, 3, 4, 5, 6, 7, 8, 9, 10
    val tx_1_4 = createMockedTransaction(Seq(2, 3, 4, 5, 6, 7, 8, 9, 10), Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), "4") //1, 2, 3, 4, 5, 6, 7, 8, 9, 10
    val tx_1_5 = createMockedTransaction(Seq(2, 4, 6, 8, 10), Seq(0), "5") //0, 1, 3, 5, 7, 9
    val tx_1_6 = createMockedTransaction(Seq(0, 9, 1), Seq(), "6") //3, 5, 7

    //tx1.newBoxes().asScala.map(box => println(closedBoxesZendooMerkleTree.getPositionForBoxId(new ByteArrayWrapper(box.id()))))
    //tx5.unlockers().asScala.map(box => println(closedBoxesZendooMerkleTree.getPositionForBoxId(new ByteArrayWrapper(box.closedBoxId()))))


    val firstBlockTxs = Seq(tx_1_1, tx_1_2, tx_1_3, tx_1_4, tx_1_5, tx_1_6)
    assertTrue(closedBoxesZendooMerkleTree.validateTransactions(firstBlockTxs).isEmpty)

    val firstBlock = mockedBlock(firstBlockTxs)
    assertTrue(closedBoxesZendooMerkleTree.validateBlock(firstBlock).isEmpty)


    //apply block so we will hav occupied positions 3, 5, 7
    closedBoxesZendooMerkleTree.applyBlock(firstBlock)

    //try to simulate empty tree by first removing elements 3,5,7
    val txsWithRemovedOccupiedPositionsForBlock1 = createMockedTransaction(Seq(3, 5, 7), Seq(), "0") +: firstBlockTxs
    assertTrue(closedBoxesZendooMerkleTree.validateTransactions(txsWithRemovedOccupiedPositionsForBlock1).isEmpty)
    assertTrue(closedBoxesZendooMerkleTree.validateBlock(mockedBlock(txsWithRemovedOccupiedPositionsForBlock1)).isEmpty)

    //... but without that pre transaction initial transaction sequence is fail because 3,5,7 is already occupied
    assertFalse(closedBoxesZendooMerkleTree.validateTransactions(firstBlockTxs).isEmpty)

    val tx_2_1 = createMockedTransaction(Seq(7), Seq(1, 32), "7") //1, 3, 5, 32
    val tx_2_2 = createMockedTransaction(Seq(5), Seq(2, 23), "8") //1, 2, 3, 23, 32
    val tx_2_3 = createMockedTransaction(Seq(32, 23), Seq(9, 23), "9") //1, 2, 3, 9, 23
    val tx_2_4 = createMockedTransaction(Seq(2, 9, 23), Seq(2, 4, 6, 7), "9") //1, 2, 3, 4, 6, 7

    val secondBlockTxs = Seq(tx_2_1, tx_2_2, tx_2_3, tx_2_4)
    assertTrue(closedBoxesZendooMerkleTree.validateTransactions(secondBlockTxs).isEmpty)

    val secondBlock = mockedBlock(secondBlockTxs)
    assertTrue(closedBoxesZendooMerkleTree.validateBlock(secondBlock).isEmpty)

    //apply block so we will have occupied positions 1, 2, 3, 4, 6, 7
    closedBoxesZendooMerkleTree.applyBlock(secondBlock)

    //try to simulate empty tree by first removing elements 1, 2, 3, 4, 6, 7
    val txsWithRemovedOccupiedPositionsForBlock2 = createMockedTransaction(Seq(1, 2, 3, 4, 6, 7), Seq(3, 5, 7), "10") +: secondBlockTxs
    assertTrue(closedBoxesZendooMerkleTree.validateTransactions(txsWithRemovedOccupiedPositionsForBlock2).isEmpty)
    assertTrue(closedBoxesZendooMerkleTree.validateBlock(mockedBlock(txsWithRemovedOccupiedPositionsForBlock2)).isEmpty)


    //try to add empty block so we will have the same occupied positions 1, 2, 3, 4, 6, 7
    val thirdBlock = mockedBlock(Seq())
    closedBoxesZendooMerkleTree.applyBlock(thirdBlock)

    //remove all blocks one by one
    closedBoxesZendooMerkleTree.removeBlock(thirdBlock)
    closedBoxesZendooMerkleTree.removeBlock(secondBlock)
    closedBoxesZendooMerkleTree.removeBlock(firstBlock)
    checkEmptyTree(closedBoxesZendooMerkleTree)

    //check re apply blocks and remove all of them at once
    closedBoxesZendooMerkleTree.applyBlock(firstBlock)
    closedBoxesZendooMerkleTree.applyBlock(secondBlock)
    closedBoxesZendooMerkleTree.applyBlock(thirdBlock)
    closedBoxesZendooMerkleTree.removeBlocks(Seq(firstBlock, secondBlock, thirdBlock))
    checkEmptyTree(closedBoxesZendooMerkleTree)
  }
}