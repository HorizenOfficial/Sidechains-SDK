package com.horizen.integration.storage

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.horizen.SidechainTypes
import com.horizen.block.SidechainBlock
import com.horizen.chain.SidechainBlockInfo
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{CompanionsFixture, StoreFixture, SidechainBlockFixture, SidechainBlockInfoFixture}
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.transaction.TransactionSerializer
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import scorex.core.consensus.ModifierSemanticValidity


class SidechainHistoryStorageTest extends JUnitSuite with SidechainBlockFixture with StoreFixture with SidechainBlockInfoFixture with CompanionsFixture {

  val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]] = new JHashMap()
  val sidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val genesisBlock: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion)
  val genesisBlockInfo: SidechainBlockInfo = generateGenesisBlockInfo(
    Some(genesisBlock.mainchainHeaders.head.hash),
    Some(genesisBlock.mainchainBlockReferencesData.head.headerHash),
    ModifierSemanticValidity.Valid,
    Some(genesisBlock.timestamp))

  val params: NetworkParams = MainNetParams(new Array[Byte](32), genesisBlock.id)

  @Test
  def mainWorkflow() : Unit = {
    val historyStorage = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)

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
    var expectedHeight: Int = genesisBlockInfo.height
    var expectedInfo: SidechainBlockInfo = genesisBlockInfo

    assertTrue("HistoryStorage expected to be updated", historyStorage.update(genesisBlock, expectedInfo).isSuccess)
    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(genesisBlock, historyStorage.blockInfoOptionById(genesisBlock.id).get).isSuccess)
    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", genesisBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlockInfo expected", expectedInfo, historyStorage.bestBlockInfo)
    assertEquals("HistoryStorage different bestBlock expected", genesisBlock.id, historyStorage.bestBlock.id)
    assertEquals("HistoryStorage different block expected", genesisBlock.id, historyStorage.blockById(genesisBlock.id).get.id)
    assertEquals("HistoryStorage different block info expected", genesisBlockInfo, historyStorage.blockInfoOptionById(genesisBlock.id).get)
    assertTrue("HistoryStorage genesis block expected to be a part of active chain", historyStorage.isInActiveChain(genesisBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", genesisBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id), historyStorage.activeChainAfter(genesisBlock.id))


    // Add one more block
    val secondBlock: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    expectedHeight += 1
    var lastMainchainBaseInfo = historyStorage.getLastMainchainHeaderBaseInfoInclusion(secondBlock.parentId)
    expectedInfo = generateBlockInfo(secondBlock, expectedInfo, params, lastMainchainBaseInfo.cumulativeCommTreeHash, validity = ModifierSemanticValidity.Unknown)
    assertTrue("HistoryStorage expected to be updated", historyStorage.update(secondBlock, expectedInfo).isSuccess)

    // Before we update best block, active chain related data should not change
    assertEquals("HistoryStorage different height expected", 1, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", genesisBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlock expected", genesisBlock.id, historyStorage.bestBlock.id)
    assertEquals("HistoryStorage different block expected", secondBlock.id, historyStorage.blockById(secondBlock.id).get.id)
    assertFalse("HistoryStorage next block expected NOT to be a part of active chain", historyStorage.isInActiveChain(secondBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", genesisBlock.id, historyStorage.activeChainBlockId(1).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id), historyStorage.activeChainAfter(genesisBlock.id))
    assertEquals("HistoryStorage different semantic validity expected", ModifierSemanticValidity.Unknown, historyStorage.blockInfoOptionById(secondBlock.id).get.semanticValidity)

    // Update best block and validity -> active chain related data should change
    assertTrue("HistoryStorage best block validity expected to be updated", historyStorage.updateSemanticValidity(secondBlock, ModifierSemanticValidity.Valid).isSuccess)
    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(secondBlock, historyStorage.blockInfoOptionById(secondBlock.id).get).isSuccess)
    expectedInfo = changeBlockInfoValidity(expectedInfo, ModifierSemanticValidity.Valid) // was validity = Unknown

    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", secondBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlockInfo expected", expectedInfo, historyStorage.bestBlockInfo)
    assertEquals("HistoryStorage different bestBlock expected", secondBlock.id, historyStorage.bestBlock.id)
    assertTrue("HistoryStorage block expected to be a part of active chain", historyStorage.isInActiveChain(secondBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", secondBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id, secondBlock.id), historyStorage.activeChainAfter(genesisBlock.id))


    // Add one more block
    val thirdBlock: SidechainBlock = generateNextSidechainBlock(secondBlock, sidechainTransactionsCompanion, params)
    expectedHeight += 1
    lastMainchainBaseInfo = historyStorage.getLastMainchainHeaderBaseInfoInclusion(thirdBlock.parentId)
    expectedInfo = generateBlockInfo(thirdBlock, expectedInfo, params, lastMainchainBaseInfo.cumulativeCommTreeHash, validity = ModifierSemanticValidity.Unknown)
    assertTrue("HistoryStorage expected to be updated", historyStorage.update(thirdBlock, expectedInfo).isSuccess)
    assertEquals("HistoryStorage different semantic validity expected", ModifierSemanticValidity.Unknown, historyStorage.blockInfoOptionById(thirdBlock.id).get.semanticValidity)
    // Update best block and validity -> active chain related data should change
    assertTrue("HistoryStorage best block validity expected to be updated", historyStorage.updateSemanticValidity(thirdBlock, ModifierSemanticValidity.Valid).isSuccess)
    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(thirdBlock, historyStorage.blockInfoOptionById(thirdBlock.id).get).isSuccess)
    expectedInfo = changeBlockInfoValidity(expectedInfo, ModifierSemanticValidity.Valid) // was validity = Unknown

    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", thirdBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlockInfo expected", expectedInfo, historyStorage.bestBlockInfo)
    assertEquals("HistoryStorage different bestBlock expected", thirdBlock.id, historyStorage.bestBlock.id)
    assertTrue("HistoryStorage block expected to be a part of active chain", historyStorage.isInActiveChain(thirdBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", thirdBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id, secondBlock.id, thirdBlock.id), historyStorage.activeChainAfter(genesisBlock.id))
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(secondBlock.id, thirdBlock.id), historyStorage.activeChainAfter(secondBlock.id))



    // Add block from another chain after genesis one, which lead to Fork
    val forkBlock: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, basicSeed = 991919L)
    expectedHeight = 2
    lastMainchainBaseInfo = historyStorage.getLastMainchainHeaderBaseInfoInclusion(forkBlock.parentId)
    expectedInfo = generateBlockInfo(forkBlock, genesisBlockInfo, params, lastMainchainBaseInfo.cumulativeCommTreeHash,Some(100L << 32), validity = ModifierSemanticValidity.Unknown)
    assertTrue("HistoryStorage expected to be updated", historyStorage.update(forkBlock, expectedInfo).isSuccess)
    assertEquals("HistoryStorage different semantic validity expected", ModifierSemanticValidity.Unknown, historyStorage.blockInfoOptionById(forkBlock.id).get.semanticValidity)

    // Update best block and validity -> active chain related data should change
    assertTrue("HistoryStorage block semantic validity expected to be updated", historyStorage.updateSemanticValidity(forkBlock, ModifierSemanticValidity.Valid).isSuccess)
    assertTrue("HistoryStorage best block expected to be updated", historyStorage.setAsBestBlock(forkBlock, historyStorage.blockInfoOptionById(forkBlock.id).get).isSuccess)
    expectedInfo = changeBlockInfoValidity(expectedInfo, ModifierSemanticValidity.Valid) // was validity = Unknown

    // Verify changes
    assertEquals("HistoryStorage different height expected", expectedHeight, historyStorage.height)
    assertEquals("HistoryStorage different bestBlockId expected", forkBlock.id, historyStorage.bestBlockId)
    assertEquals("HistoryStorage different bestBlockInfo expected", expectedInfo, historyStorage.bestBlockInfo)
    assertEquals("HistoryStorage different bestBlock expected", forkBlock.id, historyStorage.bestBlock.id)
    assertTrue("HistoryStorage block expected to be a part of active chain", historyStorage.isInActiveChain(forkBlock.id))
    assertFalse("HistoryStorage block expected NOT to be a part of active chain", historyStorage.isInActiveChain(thirdBlock.id))
    assertEquals("HistoryStorage different block expected form active chain", forkBlock.id, historyStorage.activeChainBlockId(expectedHeight).get)
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(genesisBlock.id, forkBlock.id), historyStorage.activeChainAfter(genesisBlock.id))
    assertEquals("HistoryStorage different block chain expected form active chain", Seq(forkBlock.id), historyStorage.activeChainAfter(forkBlock.id))
  }
}
