package com.horizen

import java.io._

import com.horizen.fixtures.SidechainBlockInfoFixture
import com.horizen.utils.BytesUtils
import org.scalatestplus.junit.JUnitSuite
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import scorex.util.ModifierId

class SidechainSyncInfoTest extends JUnitSuite with SidechainBlockInfoFixture {
  val size: Int = 255
  val modifiers: Seq[ModifierId] = getRandomModifiersSeq(size, basicSeed = 444123L)

  @Test
  def creation(): Unit = {
    // Test 1: create empty sync info
    var info: SidechainSyncInfo = SidechainSyncInfo(Seq())
    assertTrue("SidechainSyncInfo expected to be empty", info.knownBlockIds.isEmpty)
    assertTrue("SidechainSyncInfo starting points expected to be empty", info.startingPoints.isEmpty)


    // Test 2: create sync info with data
    info = SidechainSyncInfo(modifiers)
    assertEquals("SidechainSyncInfo size expected to be different", size, info.knownBlockIds.size)
    assertEquals("SidechainSyncInfo starting points size should be different", 1, info.startingPoints.size)
    assertEquals("SidechainSyncInfo starting points expected to be different", modifiers.head, info.startingPoints.head._2)
  }

  @Test
  def serialization(): Unit = {
    val info: SidechainSyncInfo = SidechainSyncInfo(modifiers)
    val bytes = info.bytes

    // Test 1: try to deserializer valid bytes
    val serializedInfoTry = SidechainSyncInfoSerializer.parseBytesTry(bytes)
    assertTrue("SidechainSyncInfo expected to by parsed", serializedInfoTry.isSuccess)
    assertEquals("SidechainSyncInfo known blocks count is different", info.knownBlockIds.size, serializedInfoTry.get.knownBlockIds.size)
    for(i <- info.knownBlockIds.indices)
      assertEquals("SidechainSyncInfo known block %d is different".format(i), info.knownBlockIds(i), serializedInfoTry.get.knownBlockIds(i))



//    Uncomment and run if you want to update regression data.
//    val out = new BufferedWriter(new FileWriter("src/test/resources/sidechainsyncinfo_hex"))
//    out.write(BytesUtils.toHexString(bytes))
//    out.close()



    // Test 2: try to deserialize broken bytes.
    assertTrue("SidechainSyncInfo expected to be not parsed due to broken data.", SidechainSyncInfoSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serialization_regression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("sidechainsyncinfo_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        assertEquals(e.toString(), true, false)
    }

    val serializedInfoTry = SidechainSyncInfoSerializer.parseBytesTry(bytes)
    assertTrue("SidechainSyncInfo expected to by parsed.", serializedInfoTry.isSuccess)
    for(i <- modifiers.indices)
      assertEquals("SidechainSyncInfo known block %d is different".format(i), modifiers(i), serializedInfoTry.get.knownBlockIds(i))
  }
}
