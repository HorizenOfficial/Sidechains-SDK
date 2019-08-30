package com.horizen.chain

import java.io.File
import java.nio.file.Files

import com.horizen.fixtures.SidechainBlockInfoFixture
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId, idToBytes}


class SidechainBlockInfoTest extends JUnitSuite with SidechainBlockInfoFixture {
  setSeed(1000L)

  val height = 100
  val score: Long = 1L << 32 + 1
  val parentId: ModifierId = getRandomModifier()
  val semanticValidity: ModifierSemanticValidity = ModifierSemanticValidity.Valid

  @Test
  def creation(): Unit = {
    val clonedParentId: ModifierId = bytesToId(idToBytes(parentId))

    val info: SidechainBlockInfo = SidechainBlockInfo(height, score, parentId, semanticValidity)

    assertEquals("SidechainBlockInfo height is different", height, info.height)
    assertEquals("SidechainBlockInfo score is different", score, info.score)
    assertEquals("SidechainBlockInfo parentId is different", clonedParentId, info.parentId)
    assertEquals("SidechainBlockInfo semanticValidity is different",  ModifierSemanticValidity.Valid, info.semanticValidity)
  }

  @Test
  def serialization(): Unit = {
    val info: SidechainBlockInfo = SidechainBlockInfo(height, score, parentId, semanticValidity)
    val bytes = info.bytes


    // Test 1: try to deserializer valid bytes
    val serializedInfoTry = SidechainBlockInfoSerializer.parseBytesTry(bytes)

    assertTrue("SidechainBlockInfo expected to by parsed.", serializedInfoTry.isSuccess)
    assertEquals("SidechainBlockInfo height is different", info.height, serializedInfoTry.get.height)
    assertEquals("SidechainBlockInfo score is different", info.score, serializedInfoTry.get.score)
    assertEquals("SidechainBlockInfo parentId is different", info.parentId, serializedInfoTry.get.parentId)
    assertEquals("SidechainBlockInfo semanticValidity is different", info.semanticValidity, serializedInfoTry.get.semanticValidity)

    /*val out = Some(new FileOutputStream("src/test/resources/sidechainblockinfo_bytes"))
    out.get.write(bytes)
    out.get.close()*/


    // Test 2: try to deserialize broken bytes.
    assertTrue("SidechainBlockInfo expected to be not parsed due to broken data.", SidechainBlockInfoSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serialization_regression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new File(classLoader.getResource("sidechainblockinfo_bytes").getFile)
      bytes = Files.readAllBytes(file.toPath)
    }
    catch {
      case e: Exception =>
        assertEquals(e.toString(), true, false)
    }

    val serializedInfoTry = SidechainBlockInfoSerializer.parseBytesTry(bytes)
    assertTrue("SidechainBlockInfo expected to by parsed.", serializedInfoTry.isSuccess)
    assertEquals("SidechainBlockInfo height is different", height, serializedInfoTry.get.height)
    assertEquals("SidechainBlockInfo score is different", score, serializedInfoTry.get.score)
    assertEquals("SidechainBlockInfo parentId is different", parentId, serializedInfoTry.get.parentId)
    assertEquals("SidechainBlockInfo semanticValidity is different", semanticValidity, serializedInfoTry.get.semanticValidity)
  }
}
