package com.horizen.chain

import java.io.{PrintWriter, StringWriter}

import com.horizen.fixtures.{SidechainBlockInfoFixture, VrfGenerator, FieldElementFixture}
import com.horizen.utils.WithdrawalEpochInfo
import org.junit.Assert.{assertEquals, assertFalse, assertNotEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.ModifierId

import scala.collection.breakOut
import scala.util.Try

class ActiveChainTest extends JUnitSuite with SidechainBlockInfoFixture {
  var testSeed: Long = 92830932726517L
  val genesisBlockMainchainHeight = 42

  private def assertFunctionResultsAreEqualsForGivenInput[T, S](fun1: T => S, fun2: T => S, input: Seq[T], message: String = ""): Unit = {
    input.foreach { in =>
      val res1 = fun1(in)
      val res2 = fun2(in)
      assertEquals(s"Failed for ${in} " + message, res1, res2)
    }
  }

  private def addNewBestBlockIsSuccessful(chain: ActiveChain,
                                          id: ModifierId,
                                          data: SidechainBlockInfo,
                                          mainchainParent: Option[MainchainHeaderHash]): Unit = {
    val adding = Try {chain.setBestBlock(id, data, mainchainParent)}
    val errorMessage = if (adding.isSuccess) {
      ""
    }
    else {
      val ex = adding.failed.get
      val sw = new StringWriter
      ex.printStackTrace(new PrintWriter(sw))
      sw.toString
    }
    assertTrue(s"Failed to add element to an ActiveChain due ${errorMessage}", adding.isSuccess)
  }

  private def addNewBestBlockShallBeFailed(chain: ActiveChain,
                                           id: ModifierId,
                                           data: SidechainBlockInfo,
                                           mainchainParent: Option[MainchainHeaderHash]): Unit = {
    val adding = Try {chain.setBestBlock(id, data, mainchainParent)}
    assertTrue(s"Element expected to not be added to the ActiveChain", adding.isFailure)
  }

  private def checkEmptyActiveChain(chain: ActiveChain): Unit = {
    assertEquals("Empty ActiveChain expected to have height 0", 0, chain.height)
    assertTrue("Empty ActiveChain expected to have no tip", chain.bestId.isEmpty)
    assertTrue("Empty ActiveChain expected to have no tipInfo", chain.bestScBlockInfo.isEmpty)

    assertTrue("Empty ActiveChain expected not to find height of nonexistent modifier", chain.heightById(getRandomModifier()).isEmpty)
    assertFalse("Empty ActiveChain expected not to contain nonexistent modifier", chain.contains(getRandomModifier()))
    assertTrue("Empty ActiveChain expected not to find nonexistent modifier", chain.blockInfoById(getRandomModifier()).isEmpty)
    assertTrue("Empty ActiveChain expected not to find modifier for inconsistent height", chain.blockInfoByHeight(1).isEmpty)
    assertTrue("Empty ActiveChain expected not to find modifier id for inconsistent height", chain.idByHeight(0).isEmpty)
    assertTrue("Empty ActiveChain expected not to find modifier id for inconsistent height", chain.idByHeight(1).isEmpty)
    assertTrue("Empty ActiveChain expected to return empty chain from nonexistent modifier", chain.chainAfter(getRandomModifier()).isEmpty)

    val randomMainchainHash: MainchainHeaderHash = generateMainchainHeaderHash(111L)
    assertTrue("Empty ActiveChain expected not to find height of nonexistent mainchain header", chain.mcHeadersHeightByMcHash(randomMainchainHash).isEmpty)
    assertTrue("Empty ActiveChain expected not to find height of nonexistent mainchain ref data", chain.mcRefDataHeightByMcHash(randomMainchainHash).isEmpty)
    assertTrue("Empty ActiveChain expected not to find nonexistent mainchain header", chain.mcHeaderMetadataByMcHash(randomMainchainHash).isEmpty)
    assertTrue("Empty ActiveChain expected not to find nonexistent mainchain ref data", chain.mcReferenceDataMetadataByMcHash(randomMainchainHash).isEmpty)

    assertTrue("Empty ActiveChain expected not to find sidechain block height of nonexistent mainchain header", chain.heightByMcHeader(randomMainchainHash).isEmpty)
    assertTrue("Empty ActiveChain expected not to find sidechain block height of nonexistent mainchain ref data", chain.heightByMcReferenceData(randomMainchainHash).isEmpty)
    assertTrue("Empty ActiveChain expected not to find sidechain block id of nonexistent mainchain header", chain.idByMcHeader(randomMainchainHash).isEmpty)
    assertTrue("Empty ActiveChain expected not to find sidechain block id of nonexistent mainchain ref data", chain.idByMcReferenceData(randomMainchainHash).isEmpty)
  }

  private def checkElementIsPresent(chain: ActiveChain,
                                    id: ModifierId,
                                    data: SidechainBlockInfo,
                                    height: Int,
                                    mainchainInitialHeight: Int,
                                    allMainchainReferences: Seq[MainchainHeaderHash] // TODO: in general we need to pass MainchainHeaders and RefData info separately
                                   ): Unit = {
    assertTrue("Chain from shall not be empty for added element", chain.chainAfter(id).nonEmpty)
    assertTrue("Element shall be present in chain", chain.contains(id))
    assertEquals("Data shall be reachable by height", data, chain.blockInfoByHeight(height).get)
    assertEquals("Data shall be reachable by id", data, chain.blockInfoById(id).get)
    assertEquals("Height of added element shall be defined", height, chain.heightById(id).get)
    assertEquals("Id of added element shall reachable by height", id, chain.idByHeight(height).get)

    data.mainchainHeaderHashes.zipWithIndex.foreach {
      case (headerHash, index) =>
        val mcHeight = mainchainInitialHeight + index + genesisBlockMainchainHeight

        assertEquals("Sidechain height shall be defined for added mainchain", height, chain.heightByMcHeader(headerHash).get)
        assertEquals("Height of mainchain shall be correctly defined", mcHeight, chain.mcHeadersHeightByMcHash(headerHash).get)
        assertEquals("Sidechain id shall be found my mainchainId", id, chain.idByMcHeader(headerHash).get)
        assertEquals("Mainchain header hash shall be correctly get by height", headerHash, chain.mcHashByMcHeight(mcHeight).get)

        val parentIndex = mcHeight - genesisBlockMainchainHeight
        if (parentIndex > 0) {
          assertEquals("Mainchain data by mainchain id shall be as expected", allMainchainReferences(parentIndex - 1), chain.mcHeaderMetadataByMcHash(headerHash).get.getParentId)
        }
    }

    data.mainchainReferenceDataHeaderHashes.zipWithIndex.foreach {
      case (refDataHeaderHash, index) =>
        val mcHeight = mainchainInitialHeight + index + genesisBlockMainchainHeight

        assertEquals("Sidechain height shall be defined for added mainchain", height, chain.heightByMcReferenceData(refDataHeaderHash).get)
        assertEquals("Height of mainchain shall be correctly defined", mcHeight, chain.mcRefDataHeightByMcHash(refDataHeaderHash).get)
        assertEquals("Sidechain id shall be found my mainchainId", id, chain.idByMcReferenceData(refDataHeaderHash).get)
        assertEquals("Mainchain ref data header hash shall be correctly get by height", refDataHeaderHash, chain.mcHashByMcHeight(mcHeight).get)

        val parentIndex = mcHeight - genesisBlockMainchainHeight
        if (parentIndex > 0) {
          assertEquals("Mainchain data by mainchain id shall be as expected", allMainchainReferences(parentIndex - 1), chain.mcReferenceDataMetadataByMcHash(refDataHeaderHash).get.getParentId)
        }
    }
  }

  private def checkElementIsNotPresent(chain: ActiveChain,
                                       id: ModifierId,
                                       data: SidechainBlockInfo,
                                       height: Int): Unit = {
    assertFalse("Element expected not be present", chain.contains(id))

    val referencesInActualDataIsEmpty = chain.blockInfoByHeight(height).exists(data => data.mainchainHeaderHashes.isEmpty)

    // Do not check data if expected and actual data no have main chain references due false positive
    if (!(data.mainchainHeaderHashes.isEmpty && referencesInActualDataIsEmpty)) {
      assertNotEquals("Data shall not be found", Some(data), chain.blockInfoByHeight(height))
    }
  }

  private def checkElementIsBest(chain: ActiveChain,
                                 id: ModifierId,
                                 data: SidechainBlockInfo,
                                 height: Int): Unit = {
    assertEquals("Data shall be best", data, chain.bestScBlockInfo.get)
    assertEquals("Id shall be best", id, chain.bestId.get)
    assertEquals("Height of activechain shall be as expected", height, chain.height)
  }

  private def checkElementIsNotBest(chain: ActiveChain,
                                    id: ModifierId,
                                    data: SidechainBlockInfo,
                                    height: Int): Unit = {
    assertNotEquals("Data shall be best", data, chain.bestScBlockInfo.get)
    assertNotEquals("Id shall be best", id, chain.bestId.get)
    assertNotEquals("Height of activechain shall be as expected", height, chain.height)
  }

  @Test
  def checkEmptyChain(): Unit = {
    val chain = ActiveChain(genesisBlockMainchainHeight)
    checkEmptyActiveChain(chain)
  }

  @Test
  def checkFilledByDataChain(): Unit = {
    setSeed(testSeed)
    val chainHeight: Int = 10
    val generatedData = generateDataSequenceWithGenesisBlock(chainHeight)
    val initialParent = generatedData.flatMap(_._3).head
    val allAtOnceChain = ActiveChain(generatedData.map{case (id, sbInfo, _) => (id, sbInfo)}(breakOut), initialParent, genesisBlockMainchainHeight)

    val filledByBestBlock = ActiveChain(genesisBlockMainchainHeight)
    generatedData.foldLeft(Option(initialParent)) {
      case (parent, (id, info, mainchainParent)) =>
        val addRes = Try{filledByBestBlock.setBestBlock(id, info, mainchainParent)}
        if (addRes.isFailure) println(addRes.failed)
        assertTrue(addRes.isSuccess)
        info.mainchainHeaderHashes.lastOption.orElse(parent)
    }

    val usedIds = generatedData.map(_._1)
    val mainchainIds = generatedData.flatMap(_._3)
    val usedHeights = 1 to chainHeight

    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.contains, filledByBestBlock.contains, usedIds, "contains is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.heightById, filledByBestBlock.heightById, usedIds, "height is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.heightByMcHeader, filledByBestBlock.heightByMcHeader, mainchainIds, "sidechain height by mainchain header has is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.mcHeadersHeightByMcHash, filledByBestBlock.mcHeadersHeightByMcHash, mainchainIds, "mainchain header height is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.heightByMcReferenceData, filledByBestBlock.heightByMcReferenceData, mainchainIds, "sidechain height by mainchain ref data is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.idByMcHeader, filledByBestBlock.idByMcHeader, mainchainIds, "sidechain id by mainchain header is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.idByMcReferenceData, filledByBestBlock.idByMcReferenceData, mainchainIds, "sidechain id by mainchain ref data is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.mcRefDataHeightByMcHash, filledByBestBlock.mcRefDataHeightByMcHash, mainchainIds, "mainchain ref data height is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.blockInfoById, filledByBestBlock.blockInfoById, usedIds, "data is different")
    assertFunctionResultsAreEqualsForGivenInput(allAtOnceChain.blockInfoByHeight, filledByBestBlock.blockInfoByHeight, usedHeights)

    val mainchainHeaders = generatedData.map(_._2).flatMap(_.mainchainHeaderHashes)
    assertEquals("All mainchain headers are expected to be added", mainchainHeaders.size + genesisBlockMainchainHeight - 1, allAtOnceChain.heightOfMcHeaders)
    assertEquals("All mainchain headers are expected to be added", mainchainHeaders.size + genesisBlockMainchainHeight - 1, filledByBestBlock.heightOfMcHeaders)
    val mainchainReferenceDataHashes = generatedData.map(_._2).flatMap(_.mainchainReferenceDataHeaderHashes)
    assertEquals("All mainchain ref data headers are expected to be added", mainchainReferenceDataHashes.size + genesisBlockMainchainHeight - 1, allAtOnceChain.heightOfMcReferencesData)
    assertEquals("All mainchain ref data headers are expected to be added", mainchainReferenceDataHashes.size + genesisBlockMainchainHeight - 1, filledByBestBlock.heightOfMcReferencesData)

    assertEquals("All sidechain references are expected to be added", usedIds.size, filledByBestBlock.height)
  }

  @Test
  def verifyAddedData(): Unit = {
    setSeed(testSeed)

    val chainHeight: Int = 10
    val data = generateDataSequenceWithGenesisBlock(chainHeight)
    val parent = data.flatMap(_._3).head
    val blockInfoData = data.map(b => (b._1, b._2))
    val chain = ActiveChain(data.map{case (id, sbInfo, _) => (id, sbInfo)}(breakOut), parent, genesisBlockMainchainHeight)

    // Construct the Set of tuples "Mainchain Header Hash" -> "Containing sidechain block id"
    val mainchainHeaderHashToModifierId = {
      for {
        (modifierId, sidechainBlock) <- blockInfoData
        mainchainHeaderHash <- sidechainBlock.mainchainHeaderHashes
      } yield (mainchainHeaderHash, modifierId)
    }.toSet
    // Verify that all Mainchain Headers Hashes lead to correct Sidechain Block Id inside ActiveChain
    val mainchainBlockByHeaderHashToBlockId =
      mainchainHeaderHashToModifierId.map{case(mainchainHeaderHash, _) => (mainchainHeaderHash, chain.idByMcHeader(mainchainHeaderHash).get)}
    val headersDiff = mainchainHeaderHashToModifierId.diff(mainchainBlockByHeaderHashToBlockId)
    assertTrue(s"Sidechain block id by mainchainHeaderHash is failed: ${headersDiff}", headersDiff.isEmpty)


    // Construct the Set of tuples "Mainchain Ref Data Header Hash" -> "Containing sidechain block id"
    val mainchainRefDataHeaderHashToModifierId = {
      for {
        (modifierId, sidechainBlock) <- blockInfoData
        mainchainRefDataHeaderHash <- sidechainBlock.mainchainReferenceDataHeaderHashes
      } yield (mainchainRefDataHeaderHash, modifierId)
    }.toSet
    // Verify that all Mainchain Ref Data Header Hashes lead to correct Sidechain Block Id inside ActiveChain
    val mainchainBlockByRefDataHeaderHashToBlockId =
      mainchainRefDataHeaderHashToModifierId.map{case(mainchainRefDataHeaderHash, _) => (mainchainRefDataHeaderHash, chain.idByMcReferenceData(mainchainRefDataHeaderHash).get)}
    val dataDiff = mainchainRefDataHeaderHashToModifierId.diff(mainchainBlockByRefDataHeaderHashToBlockId)
    assertTrue(s"Sidechain block id by mainchainRefData HeaderHash is failed: ${dataDiff}", dataDiff.isEmpty)


    // Verify that for all Mainchain Headers Hashes lead to correct MC Header height inside ActiveChain
    val mainchainHeaderHashToMainchainHeadersHeight =
      blockInfoData.flatMap(_._2.mainchainHeaderHashes).zipWithIndex.map{case(hash, index) => (hash, index + genesisBlockMainchainHeight)} //height starts from 1

    val failedHeaderHeights =
      mainchainHeaderHashToMainchainHeadersHeight
        .map{case(hash, height) => (hash, height, chain.mcHeadersHeightByMcHash(hash).get)}
        .filter{case(_, expectedHeight, realHeight) => expectedHeight != realHeight}

    val headerHeightAssertMessage: String = if (failedHeaderHeights.nonEmpty) {
      s"Mainchain calculation had been failed, i.e. for hash ${failedHeaderHeights.head._1} expected ${failedHeaderHeights.head._2}, but got ${failedHeaderHeights.head._3}"
    } else {""}

    assertTrue(headerHeightAssertMessage, failedHeaderHeights.isEmpty)

    // Verify that for all Mainchain Ref Data Headers Hashes lead to correct MC Ref Data height inside ActiveChain
    val mainchainRefDataHeaderHashToMainchainRefDataHeight =
      blockInfoData.flatMap(_._2.mainchainReferenceDataHeaderHashes).zipWithIndex.map{case(hash, index) => (hash, index + genesisBlockMainchainHeight)} //height starts from 1

    val failedRefDataHeights =
      mainchainRefDataHeaderHashToMainchainRefDataHeight
        .map{case(hash, height) => (hash, height, chain.mcRefDataHeightByMcHash(hash).get)}
        .filter{case(_, expectedHeight, realHeight) => expectedHeight != realHeight}

    val refDataHeightAssertMessage: String = if (failedRefDataHeights.nonEmpty) {
      s"Mainchain calculation had been failed, i.e. for hash ${failedRefDataHeights.head._1} expected ${failedRefDataHeights.head._2}, but got ${failedRefDataHeights.head._3}"
    } else {""}

    assertTrue(refDataHeightAssertMessage, failedRefDataHeights.isEmpty)


    assertEquals("ActiveChain expected to have different height", chainHeight, chain.height)
    assertEquals("ActiveChain expected to have different tip", blockInfoData.last._1, chain.bestId.get)
    assertEquals("ActiveChain expected to have different tipInfo", blockInfoData.last._2.parentId, chain.bestScBlockInfo.get.parentId)

    assertTrue("ActiveChain expected not to find height of nonexistent modifier", chain.heightById(getRandomModifier()).isEmpty)
    assertEquals("ActiveChain expected not to find height of first(genesis) modifier", 1, chain.heightById(blockInfoData.head._1).get)
    assertEquals("ActiveChain expected not to find height of 3rd modifier", 3, chain.heightById(blockInfoData(2)._1).get)
    assertEquals("ActiveChain expected not to find height of tip modifier", chainHeight, chain.heightById(blockInfoData.last._1).get)

    assertFalse("ActiveChain expected not to contain nonexistent modifier", chain.contains(getRandomModifier()))
    assertTrue("ActiveChain expected to contain first(genesis) modifier", chain.contains(blockInfoData.head._1))
    assertTrue("ActiveChain expected to contain 3rd modifier", chain.contains(blockInfoData(2)._1))
    assertTrue("ActiveChain expected to contain tip modifier", chain.contains(blockInfoData.last._1))

    assertTrue("ActiveChain expected not to find nonexistent modifier", chain.blockInfoById(getRandomModifier()).isEmpty)
    assertTrue("ActiveChain expected to find first(genesis) modifier", chain.blockInfoById(blockInfoData.head._1).isDefined)
    assertTrue("ActiveChain expected to find 3rd modifier", chain.blockInfoById(blockInfoData(2)._1).isDefined)
    assertTrue("ActiveChain expected to find tip modifier", chain.blockInfoById(blockInfoData.last._1).isDefined)

    assertTrue("ActiveChain expected not to find modifier for inconsistent height", chain.blockInfoByHeight(chainHeight + 1).isEmpty)
    assertTrue("ActiveChain expected to find modifier for height 1", chain.blockInfoByHeight(1).isDefined)
    assertTrue("ActiveChain expected to find modifier for chain current height", chain.blockInfoByHeight(chainHeight).isDefined)

    assertTrue("ActiveChain expected not to find modifier id for inconsistent height", chain.idByHeight(0).isEmpty)
    assertTrue("ActiveChain expected not to find modifier id for inconsistent height", chain.idByHeight(chainHeight + 1).isEmpty)
    assertTrue("ActiveChain expected to find modifier id for height 1", chain.idByHeight(1).isDefined)
    assertTrue("ActiveChain expected to find modifier id for chain current height", chain.idByHeight(chainHeight).isDefined)

    assertTrue("ActiveChain expected to return empty chain from nonexistent modifier", chain.chainAfter(getRandomModifier()).isEmpty)

    var chainAfter: Seq[ModifierId] = chain.chainAfter(blockInfoData.head._1)
    assertEquals("ActiveChain chainAfter expected to return chain with different height for first(genesis) modifier",
      blockInfoData.size, chainAfter.size)
    for(i <- chainAfter.indices)
      assertEquals("ActiveChain chainAfter item at index %d is different".format(i), blockInfoData(i)._1, chainAfter(i))


    val startingIndex = 3
    chainAfter = chain.chainAfter(blockInfoData(startingIndex)._1)
    assertEquals("ActiveChain chainAfter expected to return chain with different height for 4th modifier",
      chainHeight - startingIndex, chainAfter.size)
    for(i <- chainAfter.indices)
      assertEquals("ActiveChain chainAfter item at index %d is different".format(i), blockInfoData(i + startingIndex)._1, chainAfter(i))


    chainAfter = chain.chainAfter(blockInfoData.last._1)
    assertEquals("ActiveChain chainAfter expected to return chain with size 1 for tip modifier", 1, chainAfter.size)
    assertEquals("ActiveChain chainAfter item at index 0 is different", blockInfoData.last._1, chainAfter.head)
  }



  @Test
  def tipUpdate(): Unit = {
    setSeed(testSeed)

    // Update a tip of empty ActiveChain
    val chain: ActiveChain = ActiveChain(genesisBlockMainchainHeight)
    val mainchainData: Seq[MainchainHeaderHash] = Seq()

    val (firstId: ModifierId, firstData: SidechainBlockInfo, firstMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(getRandomModifier(), Seq(generateMainchainBlockReference()))
    addNewBestBlockIsSuccessful(chain, firstId, firstData, firstMainchainParent)
    val mainchainDataAfterFirst = mainchainData ++ firstData.mainchainHeaderHashes

    checkElementIsPresent(chain, firstId, firstData, 1, 0, mainchainDataAfterFirst)
    checkElementIsBest(chain, firstId, firstData, 1)
    assertEquals("ChainFrom the beginning should contain just a tip", Seq(firstId), chain.chainAfter(firstId))

    // Try to add the same element second time
    addNewBestBlockShallBeFailed(chain, firstId, firstData, firstMainchainParent)
    checkElementIsBest(chain, firstId, firstData, 1)
    assertEquals("ChainFrom the beginning should contain just a tip", Seq(firstId), chain.chainAfter(firstId))

    // Add second element
    val (secondId: ModifierId, secondData: SidechainBlockInfo, secondMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(firstId)
    addNewBestBlockIsSuccessful(chain, secondId, secondData, secondMainchainParent)
    val mainchainDataAfterSecond = mainchainDataAfterFirst ++ secondData.mainchainHeaderHashes

    checkElementIsPresent(chain, secondId, secondData, 2, mainchainDataAfterFirst.size, mainchainDataAfterSecond)
    checkElementIsBest(chain, secondId, secondData, 2)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId), chain.chainAfter(firstId))
    checkElementIsNotBest(chain, firstId, firstData, 1)

    // Add third element
    val (thirdId: ModifierId, thirdData: SidechainBlockInfo, thirdMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(secondId)
    addNewBestBlockIsSuccessful(chain, thirdId, thirdData, thirdMainchainParent)
    val mainchainDataAfterThird = mainchainDataAfterSecond ++ thirdData.mainchainHeaderHashes

    checkElementIsPresent(chain, thirdId, thirdData, 3, mainchainDataAfterSecond.size, mainchainDataAfterThird)
    checkElementIsBest(chain, thirdId, thirdData, 3)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId, thirdId), chain.chainAfter(firstId))
    checkElementIsNotBest(chain, secondId, secondData, 2)

    // Add fourth element
    val (fourthId: ModifierId, fourthData: SidechainBlockInfo, fourthMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(thirdId)
    addNewBestBlockIsSuccessful(chain, fourthId, fourthData, fourthMainchainParent)
    val mainchainDataAfterFourth = mainchainDataAfterThird ++ fourthData.mainchainHeaderHashes

    checkElementIsPresent(chain, fourthId, fourthData, 4, mainchainDataAfterThird.size, mainchainDataAfterFourth)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId, thirdId, fourthId), chain.chainAfter(firstId))

    //replace last element
    val (otherFourthId: ModifierId, otherFourthData: SidechainBlockInfo, otherFourthMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(thirdId)
    addNewBestBlockIsSuccessful(chain, otherFourthId, otherFourthData, otherFourthMainchainParent)
    val mainchainDataAfterOtherFourth = mainchainDataAfterThird ++ otherFourthData.mainchainHeaderHashes

    checkElementIsPresent(chain, otherFourthId, otherFourthData, 4, mainchainDataAfterThird.size, mainchainDataAfterOtherFourth)
    checkElementIsNotPresent(chain, fourthId, fourthData, 4)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId, thirdId, otherFourthId), chain.chainAfter(firstId))


    // do fork on the second element and add element
    val (otherThirdId: ModifierId, otherThirdData: SidechainBlockInfo, otherThirdMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(secondId)
    addNewBestBlockIsSuccessful(chain, otherThirdId, otherThirdData, otherThirdMainchainParent)
    val mainchainDataAfterOtherThird = mainchainDataAfterSecond ++ otherThirdData.mainchainHeaderHashes

    checkElementIsPresent(chain, otherThirdId, otherThirdData, 3, mainchainDataAfterSecond.size, mainchainDataAfterOtherThird)
    checkElementIsBest(chain, otherThirdId, otherThirdData, 3)
    checkElementIsNotPresent(chain, otherFourthId, otherFourthData, 4)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId, otherThirdId), chain.chainAfter(firstId))

    val (afterThirdId: ModifierId, afterThirdData: SidechainBlockInfo, afterThirdMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(otherThirdId)
    addNewBestBlockIsSuccessful(chain, afterThirdId, afterThirdData, afterThirdMainchainParent)
    val mainchainDataAfterAfterThird = mainchainDataAfterOtherThird ++ afterThirdData.mainchainHeaderHashes

    checkElementIsPresent(chain, afterThirdId, afterThirdData, 4, mainchainDataAfterOtherThird.size, mainchainDataAfterAfterThird)
    checkElementIsBest(chain, afterThirdId, afterThirdData, 4)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId, otherThirdId, afterThirdId), chain.chainAfter(firstId))

    // try to add unconnected element
    val (unconnectedId: ModifierId, unconnectedData: SidechainBlockInfo, unconnectedMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(getRandomModifier())
    addNewBestBlockShallBeFailed(chain, unconnectedId, unconnectedData, unconnectedMainchainParent)
    checkElementIsPresent(chain, afterThirdId, afterThirdData, 4, mainchainDataAfterOtherThird.size, mainchainDataAfterAfterThird)
    checkElementIsBest(chain, afterThirdId, afterThirdData, 4)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId, otherThirdId, afterThirdId), chain.chainAfter(firstId))
  }

  @Test
  def genesisBlockWithoutMainchainReferences(): Unit = {
    setSeed(testSeed)

    val chain: ActiveChain = ActiveChain(genesisBlockMainchainHeight)

    // Add first element with no mainchain references
    val (firstId: ModifierId, firstData: SidechainBlockInfo, mainchainParent: Option[MainchainHeaderHash]) = getNewDataForParentNoMainchainReferences(getRandomModifier())

    addNewBestBlockShallBeFailed(chain, firstId, firstData, mainchainParent)
  }

  @Test
  def genesisBlockWithoutMainchainReferencesAndParent(): Unit = {
    setSeed(testSeed)

    val chain: ActiveChain = ActiveChain(genesisBlockMainchainHeight)

    // Add first element with no mainchain references and no parent
    val (firstId: ModifierId, firstData: SidechainBlockInfo, _: Option[MainchainHeaderHash]) = getNewDataForParentNoMainchainReferences(getRandomModifier())
    addNewBestBlockShallBeFailed(chain, firstId, firstData, None)
  }

  @Test
  def addBlockWithNoMainchainReferences(): Unit = {
    setSeed(testSeed + 1)

    // Update a tip of empty ActiveChain
    val chain: ActiveChain = ActiveChain(genesisBlockMainchainHeight)
    val mainchainData: Seq[MainchainHeaderHash] = Seq()

    val (firstId: ModifierId, firstData: SidechainBlockInfo, mainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(getRandomModifier(), Seq(generateMainchainBlockReference()))
    addNewBestBlockIsSuccessful(chain, firstId, firstData, mainchainParent)
    val mainchainDataAfterFirst = mainchainData ++ firstData.mainchainHeaderHashes

    checkElementIsPresent(chain, firstId, firstData, 1, 0, mainchainDataAfterFirst)
    checkElementIsBest(chain, firstId, firstData, 1)
    assertEquals("ChainFrom the beginning should contain just a tip", Seq(firstId), chain.chainAfter(firstId))

    // Add second element
    val (secondId: ModifierId, secondData: SidechainBlockInfo, secondMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParent(firstId, Seq(generateMainchainBlockReference()))
    addNewBestBlockIsSuccessful(chain, secondId, secondData, secondMainchainParent)
    checkElementIsBest(chain, secondId, secondData, 2)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId), chain.chainAfter(firstId))
    val mainchainDataAfterSecond = mainchainDataAfterFirst ++ secondData.mainchainHeaderHashes

    // Add third element
    val (thirdId: ModifierId, thirdData: SidechainBlockInfo, thirdMainchainParent: Option[MainchainHeaderHash]) = getNewDataForParentNoMainchainReferences(secondId)
    addNewBestBlockIsSuccessful(chain, thirdId, thirdData, thirdMainchainParent)
    val mainchainDataAfterThird = mainchainDataAfterSecond ++ thirdData.mainchainHeaderHashes
    checkElementIsPresent(chain, thirdId, thirdData, 3, mainchainDataAfterSecond.size, mainchainDataAfterThird)
    checkElementIsBest(chain, thirdId, thirdData, 3)
    assertEquals("ChainFrom the beginning should contain all ids", Seq(firstId, secondId, thirdId), chain.chainAfter(firstId))
  }

  @Test
  def addBlockWithMainchainRefDataAfterMainchainHeaders(): Unit = {
    setSeed(testSeed+3)

    // Create empty ActiveChain
    val chain: ActiveChain = ActiveChain(genesisBlockMainchainHeight)

    // Add genesis block info
    val blockId0: ModifierId = getRandomModifier()
    val blockId1: ModifierId = getRandomModifier()
    val mcHash0: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcHash1: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    //val BloclCommTreeHash1: FieldElement = FieldElement.deserialize(generateBytes(PoseidonHash.HASH_LENGTH))
    val mcCumulativeHash1: Array[Byte] = FieldElementFixture.generateFieldElement()
    val blockInfo1 = getBlockInfo(blockId0, Seq(MainchainHeaderBaseInfo(mcHash1, mcCumulativeHash1)), Seq(mcHash1), 1)

    addNewBestBlockIsSuccessful(chain, blockId1, blockInfo1, Some(mcHash0))
    assertEquals("Different height of MainchainHeaders expected.", genesisBlockMainchainHeight, chain.heightOfMcHeaders)
    assertEquals("Different height of MainchainReferenceData expected.", genesisBlockMainchainHeight, chain.heightOfMcReferencesData)
    assertEquals("Different MainchainHeader hash by mc headers height expected.", Some(mcHash1), chain.mcHashByMcHeight(chain.heightOfMcHeaders))
    assertEquals("Different MainchainHeader hash by mc ref data height expected.", Some(mcHash1), chain.mcHashByMcHeight(chain.heightOfMcReferencesData))

    // Add block info with MainchainHeaders only
    val blockId2: ModifierId = getRandomModifier()
    val mcHash2: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcCumulativeHash2: Array[Byte] = FieldElementFixture.generateFieldElement()
    val mcHash3: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcCumulativeHash3: Array[Byte] = FieldElementFixture.generateFieldElement()
    val blockInfo2 = getBlockInfo(blockId1, Seq(MainchainHeaderBaseInfo(mcHash2, mcCumulativeHash2), MainchainHeaderBaseInfo(mcHash3, mcCumulativeHash3)), Seq(), 2)

    addNewBestBlockIsSuccessful(chain, blockId2, blockInfo2, Some(mcHash1))
    assertEquals("Different height of MainchainHeaders expected.", genesisBlockMainchainHeight + 2, chain.heightOfMcHeaders)
    assertEquals("Different height of MainchainReferenceData expected.", genesisBlockMainchainHeight, chain.heightOfMcReferencesData)
    assertEquals("Different MainchainHeader hash by mc headers height expected.", Some(mcHash3), chain.mcHashByMcHeight(chain.heightOfMcHeaders))
    assertEquals("Different MainchainHeader hash by mc ref data height expected.", Some(mcHash1), chain.mcHashByMcHeight(chain.heightOfMcReferencesData))


    // Try to add info with inconsistent MainchainRefData
    // MainchainRefData headers hashes must be equal to the previously added MainchainHeader hashes on the same height
    val inconsistentBlockId: ModifierId = getRandomModifier()
    val inconsistentMcHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val inconsistentBlockInfo = getBlockInfo(blockId2, Seq(), Seq(mcHash2, inconsistentMcHash), 3)
    addNewBestBlockShallBeFailed(chain, inconsistentBlockId, inconsistentBlockInfo, None)


    // Add block info with MainchainRefData only
    val blockId3: ModifierId = getRandomModifier()
    val blockInfo3 = getBlockInfo(blockId2, Seq(), Seq(mcHash2, mcHash3), 3)

    addNewBestBlockIsSuccessful(chain, blockId3, blockInfo3, None)
    assertEquals("Different height of MainchainHeaders expected.", genesisBlockMainchainHeight + 2, chain.heightOfMcHeaders)
    assertEquals("Different height of MainchainReferenceData expected.", genesisBlockMainchainHeight + 2, chain.heightOfMcReferencesData)
    assertEquals("Different MainchainHeader hash by mc headers height expected.", Some(mcHash3), chain.mcHashByMcHeight(chain.heightOfMcHeaders))
    assertEquals("Different MainchainHeader hash by mc ref data height expected.", Some(mcHash3), chain.mcHashByMcHeight(chain.heightOfMcReferencesData))


    // Add block with 3 MainchainHeader and 2 corresponding MainchainRefData
    val blockId4: ModifierId = getRandomModifier()
    val mcHash4: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcCumulativeHash4: Array[Byte] = FieldElementFixture.generateFieldElement()
    val mcHash5: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcCumulativeHash5: Array[Byte] = FieldElementFixture.generateFieldElement()
    val mcHash6: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcCumulativeHash6: Array[Byte] = FieldElementFixture.generateFieldElement()
    val blockInfo4 = getBlockInfo(blockId3, Seq(MainchainHeaderBaseInfo(mcHash4, mcCumulativeHash4), MainchainHeaderBaseInfo(mcHash5, mcCumulativeHash5), MainchainHeaderBaseInfo(mcHash6, mcCumulativeHash6)), Seq(mcHash4, mcHash5), 4)

    addNewBestBlockIsSuccessful(chain, blockId4, blockInfo4, Some(mcHash3))
    assertEquals("Different height of MainchainHeaders expected.", genesisBlockMainchainHeight + 5, chain.heightOfMcHeaders)
    assertEquals("Different height of MainchainReferenceData expected.", genesisBlockMainchainHeight + 4, chain.heightOfMcReferencesData)
    assertEquals("Different MainchainHeader hash by mc headers height expected.", Some(mcHash6), chain.mcHashByMcHeight(chain.heightOfMcHeaders))
    assertEquals("Different MainchainHeader hash by mc ref data height expected.", Some(mcHash5), chain.mcHashByMcHeight(chain.heightOfMcReferencesData))


    // Add block with 1 MainchainHeader and 2 MainchainRefData (1 for previous headers and 1 for current one)
    val blockId5: ModifierId = getRandomModifier()
    val mcHash7: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcCumulativeHash7: Array[Byte] = FieldElementFixture.generateFieldElement()
    val blockInfo5 = getBlockInfo(blockId4, Seq(MainchainHeaderBaseInfo(mcHash7, mcCumulativeHash7)), Seq(mcHash6, mcHash7), 5)

    addNewBestBlockIsSuccessful(chain, blockId5, blockInfo5, Some(mcHash6))
    assertEquals("Different height of MainchainHeaders expected.", genesisBlockMainchainHeight + 6, chain.heightOfMcHeaders)
    assertEquals("Different height of MainchainReferenceData expected.", genesisBlockMainchainHeight + 6, chain.heightOfMcReferencesData)
    assertEquals("Different MainchainHeader hash by mc headers height expected.", Some(mcHash7), chain.mcHashByMcHeight(chain.heightOfMcHeaders))
    assertEquals("Different MainchainHeader hash by mc ref data height expected.", Some(mcHash7), chain.mcHashByMcHeight(chain.heightOfMcReferencesData))


    // Try to add info with consistent MainchainHeaders, but with inconsistent MainchainRefData
    // MainchainRefData headers hashes must be equal to the MainchainHeader hashes on the same height
    val inconsistentBlockId2: ModifierId = getRandomModifier()
    val mcHash8: MainchainHeaderHash = byteArrayToMainchainHeaderHash(generateBytes())
    val mcCumulativeHash8: Array[Byte] = FieldElementFixture.generateFieldElement()
    val inconsistentBlockInfo2 = getBlockInfo(blockId5, Seq(MainchainHeaderBaseInfo(mcHash8, mcCumulativeHash8)), Seq(inconsistentMcHash), 6)
    addNewBestBlockShallBeFailed(chain, inconsistentBlockId2, inconsistentBlockInfo2, Some(mcHash7))
  }

  private def getBlockInfo(parentId: ModifierId, headersBaseInfo: Seq[MainchainHeaderBaseInfo], refData: Seq[MainchainHeaderHash], height: Int): SidechainBlockInfo = {
    SidechainBlockInfo(
      height,
      height,
      parentId,
      1000,
      ModifierSemanticValidity.Unknown,
      headersBaseInfo,
      refData,
      WithdrawalEpochInfo(0, height),
      Option(VrfGenerator.generateVrfOutput(height)),
      parentId
    )
  }

  //@Test
  def checkTests(): Unit = {
    (1 to 100000).foreach{_ =>
      val newSeed = System.nanoTime()
      println(s"Start tests for seed ${newSeed}")

      testSeed = newSeed

      tipUpdate()
      checkEmptyChain()
      genesisBlockWithoutMainchainReferences()
      verifyAddedData()
      checkFilledByDataChain()
    }
  }
}
