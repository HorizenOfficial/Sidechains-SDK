package com.horizen.block

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import java.time.Instant

import com.horizen.fixtures.{CompanionsFixture, ForgerBoxGenerationMetadata, SidechainBlockFixture}
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertArrayEquals, assertEquals, assertFalse, assertTrue, fail => jFail}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class SidechainBlockHeaderTest extends JUnitSuite with CompanionsFixture with SidechainBlockFixture {

  val header: SidechainBlockHeader = createUnsignedBlockHeader(123L)._1

  @Test
  def serialization(): Unit = {
    val bytes = header.bytes


    // Test 1: try to deserializer valid bytes
    val serializedHeaderTry = SidechainBlockHeaderSerializer.parseBytesTry(bytes)
    assertTrue("SidechainBlockHeader expected to by parsed.", serializedHeaderTry.isSuccess)

    val serializedHeader = serializedHeaderTry.get
    assertEquals("SidechainBlockHeader version is different", header.version, serializedHeader.version)
    assertEquals("SidechainBlockHeader parentId is different", header.parentId, serializedHeader.parentId)
    assertEquals("SidechainBlockHeader timestamp is different", header.timestamp, serializedHeader.timestamp)
    assertEquals("SidechainBlockHeader forgerBox is different", header.forgerBox, serializedHeader.forgerBox)
    assertEquals("SidechainBlockHeader forgerBoxMerklePath is different", header.forgerBoxMerklePath, serializedHeader.forgerBoxMerklePath)
    assertArrayEquals("SidechainBlockHeader vrfProof is different", header.vrfProof.bytes, serializedHeader.vrfProof.bytes) // TODO: replace with vrfProof inself later
    assertArrayEquals("SidechainBlockHeader sidechainTransactionsMerkleRootHash is different", header.sidechainTransactionsMerkleRootHash, serializedHeader.sidechainTransactionsMerkleRootHash)
    assertArrayEquals("SidechainBlockHeader mainchainMerkleRootHash is different", header.mainchainMerkleRootHash, serializedHeader.mainchainMerkleRootHash)
    assertArrayEquals("SidechainBlockHeader ommersMerkleRootHash is different", header.ommersMerkleRootHash, serializedHeader.ommersMerkleRootHash)
    assertEquals("SidechainBlockHeader ommersNumber is different", header.ommersCumulativeScore, serializedHeader.ommersCumulativeScore)
    assertEquals("SidechainBlockHeader id is different", header.id, serializedHeader.id)

    // Set to true to regenerate regression data
    if(false) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/sidechainblockheader_hex"))
      out.write(BytesUtils.toHexString(bytes))
      out.close()
    }

    // Test 2: try to deserialize broken bytes.
    assertTrue("SidechainBlockHeaderSerializer expected to be not parsed due to broken data.", SidechainBlockHeaderSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serializationRegression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("sidechainblockheader_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }


    val deserializedHeaderTry = SidechainBlockHeaderSerializer.parseBytesTry(bytes)
    assertTrue("SidechainBlockHeader expected to by parsed.", deserializedHeaderTry.isSuccess)

    val deserializedHeader = deserializedHeaderTry.get
    assertEquals("SidechainBlockHeader version is different", header.version, deserializedHeader.version)
    assertEquals("SidechainBlockHeader parentId is different", header.parentId, deserializedHeader.parentId)
    assertEquals("SidechainBlockHeader timestamp is different", header.timestamp, deserializedHeader.timestamp)
    assertEquals("SidechainBlockHeader forgerBox is different", header.forgerBox, deserializedHeader.forgerBox)
    assertEquals("SidechainBlockHeader forgerBoxMerklePath is different", header.forgerBoxMerklePath, deserializedHeader.forgerBoxMerklePath)
    assertArrayEquals("SidechainBlockHeader vrfProof is different", header.vrfProof.bytes, deserializedHeader.vrfProof.bytes) // TODO: replace with vrfProof inself later
    assertArrayEquals("SidechainBlockHeader sidechainTransactionsMerkleRootHash is different", header.sidechainTransactionsMerkleRootHash, deserializedHeader.sidechainTransactionsMerkleRootHash)
    assertArrayEquals("SidechainBlockHeader mainchainMerkleRootHash is different", header.mainchainMerkleRootHash, deserializedHeader.mainchainMerkleRootHash)
    assertArrayEquals("SidechainBlockHeader ommersMerkleRootHash is different", header.ommersMerkleRootHash, deserializedHeader.ommersMerkleRootHash)
    assertEquals("SidechainBlockHeader ommersNumber is different", header.ommersCumulativeScore, deserializedHeader.ommersCumulativeScore)
    assertEquals("SidechainBlockHeader id is different", header.id, deserializedHeader.id)
  }

  @Test
  def semanticValidity(): Unit = {
    val (baseUnsignedHeader: SidechainBlockHeader, forgerMetadata: ForgerBoxGenerationMetadata) = createUnsignedBlockHeader(433L)

    // Test 1: unsigned header must be not semantically valid
    assertFalse("Unsigned header expected to be semantically Invalid.", baseUnsignedHeader.semanticValidity())

    // Test 2: signed header with invalid signature must be not semantically valid
    val invalidSignature = forgerMetadata.rewardSecret.sign("different_message".getBytes())
    val invalidSignedHeader = baseUnsignedHeader.copy(signature = invalidSignature)
    assertFalse("Header with wrong signature expected to be semantically Invalid.", invalidSignedHeader.semanticValidity())

    // Test 3: signed header must be not semantically valid
    val validSignature = forgerMetadata.rewardSecret.sign(baseUnsignedHeader.messageToSign)
    val validSignedHeader = baseUnsignedHeader.copy(signature = validSignature)
    assertTrue("Signed header expected to be semantically Valid.", validSignedHeader.semanticValidity())

    // Test 4: invalid timestamp < 0
    var header = baseUnsignedHeader.copy(timestamp = -1L)
    var headerSignature = forgerMetadata.rewardSecret.sign(header.messageToSign)
    var signedHeader = header.copy(signature = headerSignature)
    assertFalse("Signed header with negative timestamp expected to be semantically Invalid.", signedHeader.semanticValidity())

    // Test 5: invalid timestamp from future to far
    header = baseUnsignedHeader.copy(timestamp = Instant.now.getEpochSecond + 2 * 60 * 60 + 1)
    headerSignature = forgerMetadata.rewardSecret.sign(header.messageToSign)
    signedHeader = header.copy(signature = headerSignature)
    assertFalse("Signed header with timestamp from far future expected to be semantically Invalid.", signedHeader.semanticValidity())

    // Test 6: valid timestamp from acceptable future (<= 2*60*60)
    header = baseUnsignedHeader.copy(timestamp = Instant.now.getEpochSecond + 2 * 60 * 60)
    headerSignature = forgerMetadata.rewardSecret.sign(header.messageToSign)
    signedHeader = header.copy(signature = headerSignature)
    assertTrue("Signed header with timestamp from nearest future expected to be semantically Valid.", signedHeader.semanticValidity())
  }
}
