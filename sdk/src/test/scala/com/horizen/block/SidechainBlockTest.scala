package com.horizen.block

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.SidechainTypes
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.util.idToBytes

class SidechainBlockTest
  extends JUnitSuite
  with CompanionsFixture
  with SidechainBlockFixture
{

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  @Test
  def testToJson(): Unit = {
    val sb = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion)

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(sb)

    val node : JsonNode = serializer.getObjectMapper().readTree(jsonStr)

    try {
      val id = node.path("id").asText()
      assertEquals("Block id json value must be the same.",
        BytesUtils.toHexString(idToBytes(sb.id)), id)
    }catch {
      case _ => fail("Block id doesn't not found in json.")
    }
    try {
      val parentId = node.path("parentId").asText()
      assertEquals("Block parentId json value must be the same.",
        BytesUtils.toHexString(idToBytes(sb.parentId)), parentId)
    }catch {
      case _ => fail("Block parentId doesn't not found in json.")
    }
    try {
      val timestamp = node.path("timestamp").asLong()
      assertEquals("Block timestamp json value must be the same.",
        sb.timestamp, timestamp)
    }catch {
      case _ => fail("Block timestamp doesn't not found in json.")
    }

  }

  @Test
  def serialization(): Unit = {
    val block = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 444L)
    val sidechainBlockSerializer = new SidechainBlockSerializer(sidechainTransactionsCompanion)

    val blockBytes = sidechainBlockSerializer.toBytes(block)

    val parsedBlockTry = sidechainBlockSerializer.parseBytesTry(blockBytes)
    assertTrue("Block deserialization failed.", parsedBlockTry.isSuccess)
  }
}
