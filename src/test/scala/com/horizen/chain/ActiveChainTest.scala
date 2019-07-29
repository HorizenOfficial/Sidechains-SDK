package com.horizen.chain

import com.horizen.fixtures.SidechainBlockInfoFixture
import org.scalatest.junit.JUnitSuite
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.ModifierId

import scala.collection.mutable.ArrayBuffer


class ActiveChainTest extends JUnitSuite with SidechainBlockInfoFixture {

  @Test
  def creation(): Unit = {
    // Test 1: create an empty ActiveChain
    var chain: ActiveChain = ActiveChain()
    assertEquals("Empty ActiveChain expected to have height 0", 0, chain.height())
    assertTrue("Empty ActiveChain expected to have no tip", chain.tip().isEmpty)
    assertTrue("Empty ActiveChain expected to have no tipInfo", chain.tipInfo().isEmpty)

    assertTrue("Empty ActiveChain expected not to find height of nonexistent modifier", chain.heightOf(getRandomModifier()).isEmpty)
    assertFalse("Empty ActiveChain expected not to contain nonexistent modifier", chain.contains(getRandomModifier()))
    assertTrue("Empty ActiveChain expected not to find nonexistent modifier", chain.getBlockInfo(getRandomModifier()).isEmpty)
    assertTrue("Empty ActiveChain expected not to find modifier for inconsistent height", chain.getBlockInfo(1).isEmpty)
    assertTrue("Empty ActiveChain expected not to find modifier id for inconsistent height", chain.getBlockId(1).isEmpty)
    assertTrue("Empty ActiveChain expected not to change validity for nonexistent modifier", chain.updateSemanticValidity(getRandomModifier(), ModifierSemanticValidity.Valid).isEmpty)
    assertTrue("Empty ActiveChain expected to return empty chain from nonexistent modifier", chain.chainFrom(getRandomModifier()).isEmpty)


    // Test 2: create an ActiveChain with some data
    val chainHeight: Int = 10
    val blockInfoData: ArrayBuffer[(ModifierId, SidechainBlockInfo)] = generateBlockInfoData(count = chainHeight, basicSeed = 12321312L)
    chain = ActiveChain(blockInfoData)

    assertEquals("ActiveChain expected to have different height", chainHeight, chain.height())
    assertEquals("ActiveChain expected to have different tip", blockInfoData.last._1, chain.tip().get)
    assertEquals("ActiveChain expected to have different tipInfo", blockInfoData.last._2.parentId, chain.tipInfo().get.parentId)

    assertTrue("ActiveChain expected not to find height of nonexistent modifier", chain.heightOf(getRandomModifier()).isEmpty)
    assertEquals("ActiveChain expected not to find height of first(genesis) modifier", 1, chain.heightOf(blockInfoData.head._1).get)
    assertEquals("ActiveChain expected not to find height of 3rd modifier", 3, chain.heightOf(blockInfoData(2)._1).get)
    assertEquals("ActiveChain expected not to find height of tip modifier", chainHeight, chain.heightOf(blockInfoData.last._1).get)

    assertFalse("ActiveChain expected not to contain nonexistent modifier", chain.contains(getRandomModifier()))
    assertTrue("ActiveChain expected to contain first(genesis) modifier", chain.contains(blockInfoData.head._1))
    assertTrue("ActiveChain expected to contain 3rd modifier", chain.contains(blockInfoData(2)._1))
    assertTrue("ActiveChain expected to contain tip modifier", chain.contains(blockInfoData.last._1))

    assertTrue("ActiveChain expected not to find nonexistent modifier", chain.getBlockInfo(getRandomModifier()).isEmpty)
    assertTrue("ActiveChain expected to find first(genesis) modifier", chain.getBlockInfo(blockInfoData.head._1).isDefined)
    assertTrue("ActiveChain expected to find 3rd modifier", chain.getBlockInfo(blockInfoData(2)._1).isDefined)
    assertTrue("ActiveChain expected to find tip modifier", chain.getBlockInfo(blockInfoData.last._1).isDefined)

    assertTrue("ActiveChain expected not to find modifier for inconsistent height", chain.getBlockInfo(chainHeight + 1).isEmpty)
    assertTrue("ActiveChain expected to find modifier for height 1", chain.getBlockInfo(1).isDefined)
    assertTrue("ActiveChain expected to find modifier for chain current height", chain.getBlockInfo(chainHeight).isDefined)

    assertTrue("ActiveChain expected not to find modifier id for inconsistent height", chain.getBlockId(chainHeight + 1).isEmpty)
    assertTrue("ActiveChain expected to find modifier id for height 1", chain.getBlockId(1).isDefined)
    assertTrue("ActiveChain expected to find modifier id for chain current height", chain.getBlockId(chainHeight).isDefined)


    assertTrue("ActiveChain expected not to change validity for nonexistent modifier",
      chain.updateSemanticValidity(getRandomModifier(), ModifierSemanticValidity.Valid).isEmpty)
    assertEquals("ActiveChain expected to change validity for tip modifier", ModifierSemanticValidity.Unknown.code,
      chain.updateSemanticValidity(blockInfoData.last._1, ModifierSemanticValidity.Unknown).get.semanticValidity.code)


    assertTrue("ActiveChain expected to return empty chain from nonexistent modifier", chain.chainFrom(getRandomModifier()).isEmpty)

    var chainFrom: Seq[ModifierId] = chain.chainFrom(blockInfoData.head._1)
    assertEquals("ActiveChain chainFrom expected to return chain with different height for first(genesis) modifier",
      blockInfoData.size, chainFrom.size)
    for(i <- chainFrom.indices)
      assertEquals("ActiveChain chainFrom item at index %d is different".format(i), blockInfoData(i)._1, chainFrom(i))


    var startingIndex = 3
    chainFrom = chain.chainFrom(blockInfoData(startingIndex)._1)
    assertEquals("ActiveChain chainFrom expected to return chain with different height for 4th modifier",
      chainHeight - startingIndex, chainFrom.size)
    for(i <- chainFrom.indices)
      assertEquals("ActiveChain chainFrom item at index %d is different".format(i), blockInfoData(i + startingIndex)._1, chainFrom(i))


    chainFrom = chain.chainFrom(blockInfoData.last._1)
    assertEquals("ActiveChain chainFrom expected to return chain with size 1 for tip modifier", 1, chainFrom.size)
    assertEquals("ActiveChain chainFrom item at index 0 is different", blockInfoData.last._1, chainFrom.head)
  }

  @Test
  def tipUpdate(): Unit = {
    // Test 1: update a tip of empty ActiveChain
    var chain: ActiveChain = ActiveChain()
    val (newTip: ModifierId, newTipInfo: SidechainBlockInfo) = generateBlockInfoData(1, basicSeed = 1234L).head

    assertTrue("Tip expected to be added.", chain.updateTip(newTip, newTipInfo).isSuccess)
    assertEquals("Height expected to be changed", 1, chain.height())
    assertEquals("Tip expected to be changed", newTip, chain.tip().get)
    assertEquals("Tip info expected to be changed", newTipInfo, chain.tipInfo().get)
    assertEquals("ChainFrom the beginning should contain just a tip", Seq(newTip), chain.chainFrom(newTip))


    // Test 2: try to update the same tip twice
    assertTrue("Tip expected to be failed to add.", chain.updateTip(newTip, newTipInfo).isFailure)
    assertEquals("Height expected to be changed", 1, chain.height())
    assertEquals("Tip expected to be changed", newTip, chain.tip().get)
    assertEquals("Tip info expected to be changed", newTipInfo, chain.tipInfo().get)
    assertEquals("ChainFrom the beginning should contain just a tip", Seq(newTip), chain.chainFrom(newTip))


    // Test 3: update tip for non-empty ActiveChain
    val (anotherTip: ModifierId, anotherTipInfo: SidechainBlockInfo) = (
      getRandomModifier(111222L),
      SidechainBlockInfo(newTipInfo.height + 1, newTipInfo.score + 1L, newTip, ModifierSemanticValidity.Valid)
    )
    assertTrue("Tip expected to be added.", chain.updateTip(anotherTip, anotherTipInfo).isSuccess)
    assertEquals("Height expected to be changed", 2, chain.height())
    assertEquals("Tip expected to be changed", anotherTip, chain.tip().get)
    assertEquals("Tip info expected to be changed", anotherTipInfo, chain.tipInfo().get)
    assertEquals("ChainFrom the beginning should contain just both tips", Seq(newTip, anotherTip), chain.chainFrom(newTip))


    // Test 4: try to update tip, that is not connected to current one
    val (wrongTip: ModifierId, wrongTipInfo: SidechainBlockInfo) = (
      getRandomModifier(),
      SidechainBlockInfo(anotherTipInfo.height + 1, anotherTipInfo.score + 1L, getRandomModifier(), ModifierSemanticValidity.Valid)
    )
    assertTrue("Tip expected to be failed to add.", chain.updateTip(newTip, newTipInfo).isFailure)
    assertEquals("Height expected to be changed", 2, chain.height())
    assertEquals("Tip expected to be changed", anotherTip, chain.tip().get)
    assertEquals("Tip info expected to be changed", anotherTipInfo, chain.tipInfo().get)
    assertEquals("ChainFrom the beginning should still contain just both tips", Seq(newTip, anotherTip), chain.chainFrom(newTip))

  }
}
