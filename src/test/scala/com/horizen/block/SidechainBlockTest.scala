package com.horizen.block

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.SidechainTypes
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.SidechainBlockFixture
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.transaction.TransactionSerializer
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder

class SidechainBlockTest
  extends JUnitSuite
  with SidechainBlockFixture
{

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())

  @Test
  def testToJson(): Unit = {
    val sb = generateGenesisBlock(sidechainTransactionsCompanion)

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(sb)

    val node : JsonNode = serializer.getObjectMapper().readTree(jsonStr)

    try {
      val id = node.path("id").asText()
      assertEquals("Block id json value must be the same.",
        ScorexEncoder.default.encode(sb.id), id)
    }catch {
      case _ => fail("Block id doesn't not found in json.")
    }
    try {
      val parentId = node.path("parentId").asText()
      assertEquals("Block parentId json value must be the same.",
        ScorexEncoder.default.encode(sb.parentId), parentId)
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


}
