package com.horizen.chain

import java.io._

import com.horizen.fixtures.SidechainBlockInfoFixture
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import com.horizen.vrf.{VrfGeneratedDataProvider, VrfOutput}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.block.Block
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId, idToBytes}

class SidechainBlockInfoTest extends JUnitSuite with SidechainBlockInfoFixture {
  setSeed(1000L)

  val vrfGenerationSeed = 234
  val vrfGenerationPrefix = "SidechainBlockInfoTest"

  val height: Int = 100
  val score: Long = 1L << 32 + 1
  val parentId: ModifierId = getRandomModifier()
  val timestamp: Block.Timestamp = 567081L
  val semanticValidity: ModifierSemanticValidity = ModifierSemanticValidity.Valid
  val mainchainHeadersBaseInfo: Seq[MainchainHeaderBaseInfo] = Seq(
    MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(BytesUtils.fromHexString("0269861FB647BA5730425C79AC164F8A0E4003CF30990628D52CEE50DFEC9213")),
      BytesUtils.fromHexString("d6847c3b30727116b109caa0dae303842cbd26f9041be6cf05072e9f3211a457")),
    MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(BytesUtils.fromHexString("E78283E4B2A92784F252327374D6D587D0A4067373AABB537485812671645B70")),
      BytesUtils.fromHexString("04c9d52cd10e1798ecce5752bf9dc1675ee827dd1568eee78fe4a5aa7ff1e6bd")),
    MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(BytesUtils.fromHexString("77B57DC4C97CD30AABAA00722B0354BE59AB74397177EA1E2A537991B39C7508")),
      BytesUtils.fromHexString("8e1a02ff813f5023ab656e4ec55d8683fbb63f6c9e4339741de0696c6553a4ca"))
  )
  val mainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = Seq("CEE50DFEC92130269861FB647BA5730425C79AC164F8A0E4003CF30990628D52", "0269861FB647BA5730425C79AC164F8A0E4003CF30990628D52CEE50DFEC9213")
    .map(hex => byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(hex)))
  val withdrawalEpochInfo: WithdrawalEpochInfo = WithdrawalEpochInfo(10, 100)

  //set to true if you want to update vrf related data
  if (false) {
    VrfGeneratedDataProvider.updateVrfOutput(vrfGenerationPrefix, vrfGenerationSeed);
  }

  val vrfOutput: VrfOutput = VrfGeneratedDataProvider.getVrfOutput(vrfGenerationPrefix, vrfGenerationSeed);
  val lastBlockIdInPreviousConsensusEpoch: ModifierId = parentId

  @Test
  def creation(): Unit = {
    val clonedParentId: ModifierId = bytesToId(idToBytes(parentId))
    val info: SidechainBlockInfo = SidechainBlockInfo(height, score, parentId, timestamp, semanticValidity, mainchainHeadersBaseInfo, mainchainReferenceDataHeaderHashes, withdrawalEpochInfo, Option(vrfOutput), lastBlockIdInPreviousConsensusEpoch)

    assertEquals("SidechainBlockInfo height is different", height, info.height)
    assertEquals("SidechainBlockInfo score is different", score, info.score)
    assertEquals("SidechainBlockInfo parentId is different", clonedParentId, info.parentId)
    assertEquals("SidechainBlockInfo timestamp is different", timestamp, info.timestamp)
    assertEquals("SidechainBlockInfo semanticValidity is different", semanticValidity, info.semanticValidity)
    assertEquals("SidechainBlockInfo mainchain lock reference size is different", mainchainHeadersBaseInfo.length, info.mainchainHeaderBaseInfo.length)
    mainchainHeadersBaseInfo.zipWithIndex.foreach{case (baseInfo, index) =>
      assertEquals("SidechainBlockInfo mainchain header is different", baseInfo, info.mainchainHeaderBaseInfo(index))
    }
    mainchainReferenceDataHeaderHashes.zipWithIndex.foreach{case (hash, index) =>
      assertEquals("SidechainBlockInfo mainchain reference data header is different", hash, info.mainchainReferenceDataHeaderHashes(index))
    }
    assertEquals("SidechainBlockInfo withdrawalEpochInfo is different", withdrawalEpochInfo, info.withdrawalEpochInfo)
  }

  @Test
  def serialization(): Unit = {
    val info: SidechainBlockInfo = SidechainBlockInfo(height, score, parentId, timestamp, semanticValidity, mainchainHeadersBaseInfo, mainchainReferenceDataHeaderHashes, withdrawalEpochInfo, Option(vrfOutput), lastBlockIdInPreviousConsensusEpoch)
    val bytes = info.bytes


    // Test 1: try to deserializer valid bytes
    val serializedInfoTry = SidechainBlockInfoSerializer.parseBytesTry(bytes)

    assertTrue("SidechainBlockInfo expected to by parsed.", serializedInfoTry.isSuccess)
    assertEquals("SidechainBlockInfo height is different", info.height, serializedInfoTry.get.height)
    assertEquals("SidechainBlockInfo score is different", info.score, serializedInfoTry.get.score)
    assertEquals("SidechainBlockInfo timestamp is different", info.timestamp, serializedInfoTry.get.timestamp)
    assertEquals("SidechainBlockInfo parentId is different", info.parentId, serializedInfoTry.get.parentId)
    assertEquals("SidechainBlockInfo semanticValidity is different", info.semanticValidity, serializedInfoTry.get.semanticValidity)
    val mcHeaderBaseInfos = serializedInfoTry.get.mainchainHeaderBaseInfo
    assertEquals("Size of MainchainHeaderBaseInfo must be the same", info.mainchainHeaderBaseInfo.size, mcHeaderBaseInfos.size)
    for(index <- mcHeaderBaseInfos.indices) {
      assertEquals("SidechainBlockInfo MainchainHeaderBaseInfo is different", info.mainchainHeaderBaseInfo(index), mcHeaderBaseInfos(index))
    }
    val mcRefDataHeaderHashes = serializedInfoTry.get.mainchainReferenceDataHeaderHashes
    assertEquals("Size of mainchain reference data header hashes must be the same", info.mainchainReferenceDataHeaderHashes.size, mcRefDataHeaderHashes.size)
    for(index <- mcRefDataHeaderHashes.indices)
      assertEquals("SidechainBlockInfo reference data header hash is different", info.mainchainReferenceDataHeaderHashes(index), mcRefDataHeaderHashes(index))
    assertEquals("SidechainBlockInfo withdrawalEpochInfo is different", info.withdrawalEpochInfo, serializedInfoTry.get.withdrawalEpochInfo)


    //Set to true and run if you want to update regression data.
    if (false) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/sidechainblockinfo_hex"))
      out.write(BytesUtils.toHexString(bytes))
      out.close()
    }


    // Test 2: try to deserialize broken bytes.
    assertTrue("SidechainBlockInfo expected to be not parsed due to broken data.", SidechainBlockInfoSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serialization_regression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("sidechainblockinfo_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        assertEquals(e.toString, true, false)
    }

    val serializedInfoTry = SidechainBlockInfoSerializer.parseBytesTry(bytes)
    assertTrue("SidechainBlockInfo expected to by parsed.", serializedInfoTry.isSuccess)
    assertEquals("SidechainBlockInfo height is different", height, serializedInfoTry.get.height)
    assertEquals("SidechainBlockInfo score is different", score, serializedInfoTry.get.score)
    assertEquals("SidechainBlockInfo parentId is different", parentId, serializedInfoTry.get.parentId)
    assertEquals("SidechainBlockInfo semanticValidity is different", semanticValidity, serializedInfoTry.get.semanticValidity)
    val mcHeaderBaseInfo = serializedInfoTry.get.mainchainHeaderBaseInfo
    assertEquals("Size of MainchainHeaderBaseInfos must be the same", mainchainHeadersBaseInfo.size, mcHeaderBaseInfo.size)
    for(index <- mcHeaderBaseInfo.indices) {
      assertEquals("SidechainBlockInfo MainchainHeaderBaseInfo is different", mainchainHeadersBaseInfo(index), mcHeaderBaseInfo(index))
    }
    val mcRefDataHeaderHashes = serializedInfoTry.get.mainchainReferenceDataHeaderHashes
    assertEquals("Size of mainchain reference data header hashes must be the same", mainchainReferenceDataHeaderHashes.size, mcRefDataHeaderHashes.size)
    for(index <- mcRefDataHeaderHashes.indices)
      assertEquals("SidechainBlockInfo reference data header hash is different", mainchainReferenceDataHeaderHashes(index), mcRefDataHeaderHashes(index))
    assertEquals("SidechainBlockInfo withdrawalEpochInfo is different", withdrawalEpochInfo, serializedInfoTry.get.withdrawalEpochInfo)

    //check equals and hash code
    val info: SidechainBlockInfo = SidechainBlockInfo(height, score, parentId, timestamp, semanticValidity, mcHeaderBaseInfo, mcRefDataHeaderHashes, withdrawalEpochInfo, Option((vrfOutput)), lastBlockIdInPreviousConsensusEpoch)

    assert(serializedInfoTry.get == info)
    assert(serializedInfoTry.get.hashCode() == info.hashCode())
  }
}
