package com.horizen.integration.storage

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.horizen.SidechainTypes
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{IODBStoreFixture, SidechainBlockFixture, SidechainBlockInfoFixture}
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.storage.{IODBStoreAdapter, SidechainHistoryStorage}
import com.horizen.transaction.TransactionSerializer
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.ModifierId


class SidechainHistoryStorageTest extends JUnitSuite with SidechainBlockFixture with IODBStoreFixture with SidechainBlockInfoFixture {

  val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]] = new JHashMap()
  val sidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers)
  val genesisBlock: SidechainBlock = generateGenesisBlock(sidechainTransactionsCompanion)

  class HistoryTestParams extends MainNetParams {
    override val sidechainGenesisBlockId: ModifierId = genesisBlock.id
  }
  val params: NetworkParams = new HistoryTestParams()

  @Test
  def mainWorkflow() : Unit = {
    val historyStorage = new SidechainHistoryStorage(new IODBStoreAdapter(getStore()), sidechainTransactionsCompanion, params)

    // Check that historyStorage is empty
    assertEquals("HistoryStorage expected to be empty", 0, historyStorage.height)
    var exceptionThrown = false
    try {
      historyStorage.bestBlock
    } catch {
      case e : IllegalArgumentException => exceptionThrown = true
    }
    assertTrue("HistoryStorage expected to be empty, no best block", exceptionThrown)

    // Add genesis block
    var expectedHeight: Int = 1
    var expectedScore: Long = 1L
    assertTrue("HistoryStorage expected to be updated", historyStorage.update(genesisBlock, expectedScore).isSuccess)
    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(genesisBlock, historyStorage.blockInfoById(genesisBlock.id).get).isSuccess)
    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", genesisBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlock expected", genesisBlock.id, historyStorage.bestBlock.id)
    assertEquals("HistoryStorage different block expected", genesisBlock.id, historyStorage.blockById(genesisBlock.id).get.id)
    assertEquals("HistoryStorage different block info expected", genesisBlock.parentId, historyStorage.blockInfoById(genesisBlock.id).get.parentId)
    assertTrue("HistoryStorage genesis block expected to be a part of active chain", historyStorage.isInActiveChain(genesisBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", genesisBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id), historyStorage.activeChainAfter(genesisBlock.id))


    // Add one more block
    val secondBlock: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    expectedHeight += 1
    expectedScore += 1
    assertTrue("HistoryStorage expected to be updated", historyStorage.update(secondBlock, expectedScore).isSuccess)

    // Before we update best block, active chain related data should not change
    assertEquals("HistoryStorage different height expected", 1, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", genesisBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlock expected", genesisBlock.id, historyStorage.bestBlock.id)
    assertEquals("HistoryStorage different block expected", secondBlock.id, historyStorage.blockById(secondBlock.id).get.id)
    assertFalse("HistoryStorage next block expected NOT to be a part of active chain", historyStorage.isInActiveChain(secondBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", genesisBlock.id, historyStorage.activeChainBlockId(1).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id), historyStorage.activeChainAfter(genesisBlock.id))

    // Update best block -> active chain related data should change
    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(secondBlock, historyStorage.blockInfoById(secondBlock.id).get).isSuccess)
    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", secondBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlock expected", secondBlock.id, historyStorage.bestBlock.id)
    assertTrue("HistoryStorage block expected to be a part of active chain", historyStorage.isInActiveChain(secondBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", secondBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id, secondBlock.id), historyStorage.activeChainAfter(genesisBlock.id))


    // Add one more block
    val thirdBlock: SidechainBlock = generateNextSidechainBlock(secondBlock, sidechainTransactionsCompanion, params)
    expectedHeight += 1
    expectedScore += 1
    assertTrue("HistoryStorage expected to be updated", historyStorage.update(thirdBlock, expectedScore).isSuccess)
    // Update best block -> active chain related data should change
    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(thirdBlock, historyStorage.blockInfoById(thirdBlock.id).get).isSuccess)

    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", thirdBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlock expected", thirdBlock.id, historyStorage.bestBlock.id)
    assertTrue("HistoryStorage block expected to be a part of active chain", historyStorage.isInActiveChain(thirdBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", thirdBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id, secondBlock.id, thirdBlock.id), historyStorage.activeChainAfter(genesisBlock.id))
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(secondBlock.id, thirdBlock.id), historyStorage.activeChainAfter(secondBlock.id))



    // Add block from another chain after genesis one, which lead to Fork
    val forkBlock: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, basicSeed = 991919L)
    expectedHeight = 2
    expectedScore = 100L
    assertTrue("HistoryStorage expected to be updated", historyStorage.update(forkBlock, expectedScore).isSuccess)
    // Update best block -> active chain related data should change
    // change semantic validity of active chain block
    // @TODO semantic validity shall not be updated after setting block as best block, add check
    assertEquals("HistoryStorage different semantic validity expected", ModifierSemanticValidity.Unknown, historyStorage.blockInfoById(forkBlock.id).get.semanticValidity)
    assertTrue("HistoryStorage block semantic validity expected to be updated", historyStorage.updateSemanticValidity(forkBlock, ModifierSemanticValidity.Valid).isSuccess)
    assertEquals("HistoryStorage different semantic validity expected", ModifierSemanticValidity.Valid, historyStorage.blockInfoById(forkBlock.id).get.semanticValidity)

    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(forkBlock, historyStorage.blockInfoById(forkBlock.id).get).isSuccess)

    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", forkBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlock expected", forkBlock.id, historyStorage.bestBlock.id)
    assertTrue("HistoryStorage block expected to be a part of active chain", historyStorage.isInActiveChain(forkBlock.id))
    assertFalse("HistoryStorage block expected NOT to be a part of active chain", historyStorage.isInActiveChain(thirdBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", forkBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id, forkBlock.id), historyStorage.activeChainAfter(genesisBlock.id))
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(forkBlock.id), historyStorage.activeChainAfter(forkBlock.id))




    // change semantic validity of not an active chain block
    assertEquals("HistoryStorage different semantic validity expected", ModifierSemanticValidity.Unknown, historyStorage.blockInfoById(secondBlock.id).get.semanticValidity)
    assertTrue("HistoryStorage block semantic validity expected to be updated", historyStorage.updateSemanticValidity(secondBlock, ModifierSemanticValidity.Invalid).isSuccess)
    assertEquals("HistoryStorage different semantic validity expected", ModifierSemanticValidity.Invalid, historyStorage.blockInfoById(secondBlock.id).get.semanticValidity)
  }
}
