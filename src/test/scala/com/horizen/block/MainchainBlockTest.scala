package com.horizen.block

import com.horizen.utils.BytesUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.io.Source
import scala.util.Try

// mcblock1 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660
class MainchainBlockTest extends JUnitSuite {

  @Test
  def MainchainBlockTest_CreationTest(): Unit = {
    val MCBlockHex : String = Source.fromResource("mcblock1").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(MCBlockHex)

    val mb: Try[MainchainBlock] = MainchainBlock.create(bytes, new Array[Byte](32))

    assertEquals("Block expected to be parsed", true, mb.isSuccess)
    assertEquals("Block Hash is different.", "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660", mb.get.hashHex)
    assertEquals("Block expected to be semantically valid", true, mb.get.semanticValidity())
  }

}
