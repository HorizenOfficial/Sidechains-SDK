package com.horizen.block

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue, assertFalse, fail => jFail}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.util.idToBytes

class SidechainBlockTest
  extends JUnitSuite
  with CompanionsFixture
  with SidechainBlockFixture
{

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val sidechainBlockSerializer = new SidechainBlockSerializer(sidechainTransactionsCompanion)

  // NOTE: creates block with no references, headers and ommers and transactions
  val block: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 444L, timestampOpt = Some(10000L), includeReference = false)

  @Test
  def testToJson(): Unit = {
    val sb = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion)

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(sb)

    val node : JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    try {
      val id = node.path("id").asText()
      assertEquals("Block id json value must be the same.",
        BytesUtils.toHexString(idToBytes(sb.id)), id)
    }catch {
      case _: Throwable => fail("Block id doesn't not found in json.")
    }
    try {
      val parentId = node.path("parentId").asText()
      assertEquals("Block parentId json value must be the same.",
        BytesUtils.toHexString(idToBytes(sb.parentId)), parentId)
    }catch {
      case _: Throwable => fail("Block parentId doesn't not found in json.")
    }
    try {
      val timestamp = node.path("timestamp").asLong()
      assertEquals("Block timestamp json value must be the same.",
        sb.timestamp, timestamp)
    }catch {
      case _: Throwable => fail("Block timestamp doesn't not found in json.")
    }

  }

  @Test
  def serialization(): Unit = {
    val blockBytes = sidechainBlockSerializer.toBytes(block)

    val deserializedBlockTry = sidechainBlockSerializer.parseBytesTry(blockBytes)
    assertTrue("Block deserialization failed.", deserializedBlockTry.isSuccess)

    val deserializedBlock = deserializedBlockTry.get
    assertEquals("Deserialized Block id is different.", block.id, deserializedBlock.id)
    assertEquals("Deserialized Block transactions are different.", block.transactions, deserializedBlock.transactions)
    assertEquals("Deserialized Block mainchain block references are different.", block.mainchainBlockReferences, deserializedBlock.mainchainBlockReferences)
    assertEquals("Deserialized Block next mainchain headers are different.", block.nextMainchainHeaders, deserializedBlock.nextMainchainHeaders)
    assertEquals("Deserialized Block ommers are different.", block.ommers, deserializedBlock.ommers)


    // Set to true to regenerate regression data
    if(false) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/sidechainblock_hex"))
      out.write(BytesUtils.toHexString(blockBytes))
      out.close()
    }

    // Test 2: try to deserialize broken bytes.
    assertTrue("SidechainBlockSerializer expected to be not parsed due to broken data.", sidechainBlockSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serialization_regression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("sidechainblock_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }


    val deserializedBlockTry = sidechainBlockSerializer.parseBytesTry(bytes)
    assertTrue("SidechainBlock expected to by parsed.", deserializedBlockTry.isSuccess)

    val deserializedBlock = deserializedBlockTry.get
    assertEquals("Deserialized Block id is different.", block.id, deserializedBlock.id)
    assertEquals("Deserialized Block transactions are different.", block.transactions, deserializedBlock.transactions)
    assertEquals("Deserialized Block mainchain block references are different.", block.mainchainBlockReferences, deserializedBlock.mainchainBlockReferences)
    assertEquals("Deserialized Block next mainchain headers are different.", block.nextMainchainHeaders, deserializedBlock.nextMainchainHeaders)
    assertEquals("Deserialized Block ommers are different.", block.ommers, deserializedBlock.ommers)
  }

  @Test
  def semanticValidity(): Unit = {
    //jFail("not implemented")
  }
}
