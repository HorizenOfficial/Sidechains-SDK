package com.horizen.block

import java.time.Instant

import com.google.common.primitives.Ints
import com.horizen.fixtures.MainchainHeaderFixture
import com.horizen.params.MainNetParams
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.io.Source
import scala.util.Try

class MainchainHeaderTest extends JUnitSuite with MainchainHeaderFixture {

  val params = new MainNetParams()

  @Test
  def MainchainHeaderTest_SuccessCreationTest(): Unit = {
    var mcHeaderHex: String = null
    var mcHeaderBytes: Array[Byte] = null
    var header: Try[MainchainHeader] = null


    // Test 1: Header for Block #300001
    // mcheader300001 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000058f40e90e203ceae655ae4d8a5c27d72732698e0c7959700c2787a01
    mcHeaderHex = Source.fromResource("mcheader300001").getLines().next()
    mcHeaderBytes = BytesUtils.fromHexString(mcHeaderHex)
    header = MainchainHeader.create(mcHeaderBytes, 0)

    assertTrue("Header expected to be parsed", header.isSuccess)
    assertEquals("Header Hash is different.", "0000000058f40e90e203ceae655ae4d8a5c27d72732698e0c7959700c2787a01", header.get.hashHex)
    assertEquals("Header block version = 536870912 expected.", 536870912, header.get.version)
    assertEquals("Hash of previous block is different.", "00000000368e5124aaaa18a6d1e7ccd3fab6a771a337fd0575fff8dcdb08db8a", BytesUtils.toHexString(header.get.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "887291c26089a67ac7aaa7d75b37fadcebececb66cca7c94ef8663845cc0a9f7", BytesUtils.toHexString(header.get.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(header.get.hashSCMerkleRootsMap))
    assertEquals("Creation time is different", 1525191363, header.get.time)
    assertEquals("PoW bits is different.", "1c6e36cd", BytesUtils.toHexString(Ints.toByteArray(header.get.bits)))
    assertEquals("Nonce is different.", "00000000000000000000000000000000000000000000000000112231e1ed0000", BytesUtils.toHexString(header.get.nonce))
    assertEquals("Equihash solution length is wrong.", params.EquihashSolutionLength, header.get.solution.length)
    assertTrue("Header expected to be semantically valid", header.get.semanticValidity(params))


    // Test 2: Header for Block #503014
    // mcheader503014 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/000000001918f6d26d0b128dd4a6e6fce71f3cd96694470d4e24ddaf0dc0404f
    mcHeaderHex = Source.fromResource("mcheader503014").getLines().next()
    mcHeaderBytes = BytesUtils.fromHexString(mcHeaderHex)
    header = MainchainHeader.create(mcHeaderBytes, 0)

    assertTrue("Header expected to be parsed", header.isSuccess)
    assertEquals("Header Hash is different.", "000000001918f6d26d0b128dd4a6e6fce71f3cd96694470d4e24ddaf0dc0404f", header.get.hashHex)
    assertEquals("Header block version = 536870912 expected.", 536870912, header.get.version)
    assertEquals("Hash of previous block is different.", "0000000012a6f7043ff79a447976e0a326c37546fd99fe22cf010ce1bc92f676", BytesUtils.toHexString(header.get.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "60360bbca66fee8acd969258943f8dbe255a623b70121a05115efcfdb8725195", BytesUtils.toHexString(header.get.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(header.get.hashSCMerkleRootsMap))
    assertEquals("Creation time is different", 1555936766, header.get.time)
    assertEquals("PoW bits is different.", "1c19e23e", BytesUtils.toHexString(Ints.toByteArray(header.get.bits)))
    assertEquals("Nonce is different.", "399f2d410000000000000000000000000000000000000000388626f4a1130010", BytesUtils.toHexString(header.get.nonce))
    assertEquals("Equihash solution length is wrong.", params.EquihashSolutionLength, header.get.solution.length)
    assertTrue("Header expected to be semantically valid", header.get.semanticValidity(params))
  }

  @Test
  def MainchainHeaderTest_SemanticValidityTest(): Unit = {
    val mcHeaderHex = Source.fromResource("mcheader300001").getLines().next()
    val mcHeaderBytes = BytesUtils.fromHexString(mcHeaderHex)
    var tmpHeaderBytes: Array[Byte] = null


    // Test 1: valid header without changes
    var header: Try[MainchainHeader] = MainchainHeader.create(mcHeaderBytes, 0)
    assertTrue("Header expected to be parsed.", header.isSuccess)
    assertTrue("Header expected to be semantically valid.", header.get.semanticValidity(params))


    // Test 2: change time to negative value
    tmpHeaderBytes = changeTime(mcHeaderBytes, -10)
    header = MainchainHeader.create(tmpHeaderBytes, 0)
    assertTrue("Header expected to be parsed.", header.isSuccess)
    assertFalse("Header expected to be NOT semantically valid.", header.get.semanticValidity(params))


    // Test 3: change time to one 2 hours and 1 second far in future
    tmpHeaderBytes = changeTime(mcHeaderBytes, (Instant.now.getEpochSecond + 2 * 60 * 60 + 1).toInt)
    header = MainchainHeader.create(tmpHeaderBytes, 0)
    assertTrue("Header expected to be parsed.", header.isSuccess)
    assertFalse("Header expected to be NOT semantically valid.", header.get.semanticValidity(params))

    // Test 4: brake PoW
    // TO DO: maybe add more tests related to PoW, see ProofOfWorkVerifierTest
    tmpHeaderBytes = changeBits(mcHeaderBytes, BytesUtils.getInt(BytesUtils.fromHexString("1c21c09e"), 0))
    header = MainchainHeader.create(tmpHeaderBytes, 0)
    assertTrue("Header expected to be parsed.", header.isSuccess)
    assertFalse("Header expected to be NOT semantically valid.", header.get.semanticValidity(params))

    // Test 5: break Equihash Solution
    // TO DO: add more tests related to solution
    tmpHeaderBytes = mcHeaderBytes.clone()
    val brokenBytes: Array[Byte] = BytesUtils.fromHexString("abcd")
    System.arraycopy(brokenBytes, 0, tmpHeaderBytes, tmpHeaderBytes.length - brokenBytes.length, brokenBytes.length)
    header = MainchainHeader.create(tmpHeaderBytes, 0)
    assertTrue("Header expected to be parsed.", header.isSuccess)
    assertFalse("Header expected to be NOT semantically valid.", header.get.semanticValidity(params))
  }
}
