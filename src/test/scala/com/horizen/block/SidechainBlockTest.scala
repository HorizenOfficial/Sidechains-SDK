package com.horizen.block

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import java.io.{File => JFile}

import com.horizen.SidechainTypes
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.SidechainBlockFixture
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

    val json = sb.toJson

    json.hcursor.get[String]("id") match {
      case Right(id) => assertEquals("Block id json value must be the same.",
        ScorexEncoder.default.encode(sb.id), id)
      case Left(decodingFailure) => fail("Block id doesn't not found in json.")
    }

    json.hcursor.get[String]("parentId") match {
      case Right(parentId) => assertEquals("Block parentId json value must be the same.",
        ScorexEncoder.default.encode(sb.parentId), parentId)
      case Left(decodingFailure) => fail("Block parentId doesn't not found in json.")
    }

    json.hcursor.get[Long]("timestamp") match {
      case Right(timestamp) => assertEquals("Block timestamp json value must be the same.",
        sb.timestamp, timestamp)
      case Left(decodingFailure) => fail("Block timestamp doesn't not found in json.")
    }
  }


}
