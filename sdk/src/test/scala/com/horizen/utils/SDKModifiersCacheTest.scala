package com.horizen.utils

import com.horizen.SidechainHistory
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{FieldElementFixture, SidechainBlockFixture}
import com.horizen.fixtures.SidechainBlockFixture.getDefaultTransactionsCompanion
import com.horizen.params.{NetworkParams, RegTestParams}
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

class SDKModifiersCacheTest extends JUnitSuite
  with SidechainBlockFixture{
  type HIS = SidechainHistory
  val cacheSize = 10
  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val genesisBlock: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion)
  val params: NetworkParams = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())

  @Test
  def sdkModifiersCacheClean(): Unit = {
    val modifiersCache = new SDKModifiersCache[SidechainBlock, HIS](cacheSize)
    val blocksNumber = 200
    val blocks = generateSidechainBlockSeq(blocksNumber, sidechainTransactionsCompanion, params, Some(genesisBlock.id))

    blocks.foreach(block => modifiersCache.put(block.id, block))

    assertEquals("Cache size is differ", blocksNumber, modifiersCache.size)

    val cleared = modifiersCache.cleanOverfull()

    assertEquals("Cache expected to be fully cleared", (blocksNumber - cacheSize), cleared.length)
    assertEquals("Cache size is differ", cacheSize, modifiersCache.size)
  }

  @Test
  def sdkModifiersCacheRemove(): Unit = {
    val modifiersCache = new SDKModifiersCache[SidechainBlock, HIS](cacheSize)
    val blocksNumber = 200
    var blocks = generateSidechainBlockSeq(blocksNumber, sidechainTransactionsCompanion, params, Some(genesisBlock.id))

    blocks.foreach(block => modifiersCache.put(block.id, block))
    var expectedChacheSize = blocksNumber
    assertEquals("Cache size is differ", expectedChacheSize, modifiersCache.size)

    blocks = generateSidechainBlockSeq(blocksNumber, sidechainTransactionsCompanion, params, Some(blocks.last.id))
    blocks.foreach(block => modifiersCache.put(block.id, block))
    expectedChacheSize += blocksNumber
    assertEquals("Cache size is differ", expectedChacheSize, modifiersCache.size)

    blocks.take(10).foreach(block => modifiersCache.remove(block.id))
    expectedChacheSize -= 10
    assertEquals("Cache size is differ", expectedChacheSize, modifiersCache.size)

    val cleared = modifiersCache.cleanOverfull()

    assertEquals("Cache expected to be fully cleared", (expectedChacheSize - cacheSize), cleared.length)
    expectedChacheSize = cacheSize
    assertEquals("Cache size is differ", expectedChacheSize, modifiersCache.size)
  }
}
