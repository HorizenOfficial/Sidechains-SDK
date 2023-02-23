package com.horizen.account.block

import com.horizen.account.fixtures.{AccountBlockFixture, ForgerAccountGenerationMetadata}
import com.horizen.fixtures.CompanionsFixture
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.proof.VrfProof
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.VrfSecretKey
import com.horizen.utils.BytesUtils
import com.horizen.validation.InvalidSidechainBlockHeaderException
import com.horizen.vrf.{VrfGeneratedDataProvider, VrfOutput}
import org.junit.Assert.{assertArrayEquals, assertEquals, assertTrue, fail => jFail}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success}

class AccountBlockHeaderTest extends JUnitSuite with CompanionsFixture with AccountBlockFixture {
  val vrfGenerationDataSeed = 178
  val vrfGenerationPrefix = "AccountBlockHeaderTest"
  //set to true for update vrf proof
  if (false) {
    VrfGeneratedDataProvider.updateVrfProof(vrfGenerationPrefix, vrfGenerationDataSeed)
  }

  val vrfKeyPair: Option[(VrfSecretKey, VrfPublicKey)] = {
    val secret: VrfSecretKey = VrfGeneratedDataProvider.getVrfSecretKey(vrfGenerationDataSeed)
    val publicKey: VrfPublicKey = secret.publicImage()
    Option((secret, publicKey))
  }

  val vrfProofOpt: Option[VrfProof] = Option(VrfGeneratedDataProvider.getVrfProof(vrfGenerationPrefix, vrfGenerationDataSeed))
  val vrfOutputOpt: Option[VrfOutput] = Option(VrfGeneratedDataProvider.getVrfOutput(vrfGenerationDataSeed))
  val header: AccountBlockHeader = createUnsignedBlockHeader(123L, vrfKeyPair, vrfProofOpt, vrfOutputOpt)._1
  val params: NetworkParams = MainNetParams()

  @Test
  def serialization(): Unit = {
    val bytes = header.bytes


    // Test 1: try to deserialize valid bytes
    val serializedHeaderTry = AccountBlockHeaderSerializer.parseBytesTry(bytes)
    assertTrue("AccountBlockHeader expected to be parsed.", serializedHeaderTry.isSuccess)

    val serializedHeader = serializedHeaderTry.get
    assertEquals("AccountBlockHeader version is different", header.version, serializedHeader.version)
    assertEquals("AccountBlockHeader parentId is different", header.parentId, serializedHeader.parentId)
    assertEquals("AccountBlockHeader timestamp is different", header.timestamp, serializedHeader.timestamp)
    assertEquals("AccountBlockHeader forgingStakeInfo is different", header.forgingStakeInfo, serializedHeader.forgingStakeInfo)
    assertEquals("AccountBlockHeader forgingStakeMerklePath is different", header.forgingStakeMerklePath, serializedHeader.forgingStakeMerklePath)
    assertArrayEquals("AccountBlockHeader vrfProof is different", header.vrfProof.bytes, serializedHeader.vrfProof.bytes) // TODO: replace with vrfProof inself later
    assertArrayEquals("AccountBlockHeader sidechainTransactionsMerkleRootHash is different", header.sidechainTransactionsMerkleRootHash, serializedHeader.sidechainTransactionsMerkleRootHash)
    assertArrayEquals("AccountBlockHeader mainchainMerkleRootHash is different", header.mainchainMerkleRootHash, serializedHeader.mainchainMerkleRootHash)
    assertArrayEquals("AccountBlockHeader stateRoot is different", header.stateRoot, serializedHeader.stateRoot)
    assertArrayEquals("AccountBlockHeader receiptsRoot is different", header.receiptsRoot, serializedHeader.receiptsRoot)
    assertEquals("AccountBlockHeader forgerAddress is different", header.forgerAddress, serializedHeader.forgerAddress)
    assertEquals("AccountBlockHeader baseFee is different", header.baseFee, serializedHeader.baseFee)
    assertEquals("AccountBlockHeader gasUsed is different", header.gasUsed, serializedHeader.gasUsed)
    assertEquals("AccountBlockHeader gasLimit is different", header.gasLimit, serializedHeader.gasLimit)
    assertEquals("AccountBlockHeader ommersNumber is different", header.ommersCumulativeScore, serializedHeader.ommersCumulativeScore)
    assertEquals("AccountBlockHeader id is different", header.id, serializedHeader.id)

    // Set to true to regenerate regression data
    if(false) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/accountblockheader_hex"))
      out.write(BytesUtils.toHexString(bytes))
      out.close()
    }

    // Test 2: try to deserialize broken bytes.
    assertTrue("AccountBlockHeaderSerializer expected to be not parsed due to broken data.", AccountBlockHeaderSerializer.parseBytesTry("broken bytes".getBytes(StandardCharsets.UTF_8)).isFailure)
  }

  @Test
  def serializationRegression(): Unit = {
    var bytes: Array[Byte] = null
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("accountblockheader_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())

    val deserializedHeaderTry = AccountBlockHeaderSerializer.parseBytesTry(bytes)
    assertTrue("AccountBlockHeader expected to be parsed.", deserializedHeaderTry.isSuccess)

    val deserializedHeader = deserializedHeaderTry.get
    assertEquals("AccountBlockHeader version is different", header.version, deserializedHeader.version)
    assertEquals("AccountBlockHeader parentId is different", header.parentId, deserializedHeader.parentId)
    assertEquals("AccountBlockHeader timestamp is different", header.timestamp, deserializedHeader.timestamp)
    assertEquals("AccountBlockHeader forgingStakeInfo is different", header.forgingStakeInfo, deserializedHeader.forgingStakeInfo)
    assertEquals("AccountBlockHeader forgingStakeMerklePath is different", header.forgingStakeMerklePath, deserializedHeader.forgingStakeMerklePath)
    assertArrayEquals("AccountBlockHeader vrfProof is different", header.vrfProof.bytes, deserializedHeader.vrfProof.bytes) // TODO: replace with vrfProof inself later
    assertArrayEquals("AccountBlockHeader sidechainTransactionsMerkleRootHash is different", header.sidechainTransactionsMerkleRootHash, deserializedHeader.sidechainTransactionsMerkleRootHash)
    assertArrayEquals("AccountBlockHeader mainchainMerkleRootHash is different", header.mainchainMerkleRootHash, deserializedHeader.mainchainMerkleRootHash)
    assertArrayEquals("AccountBlockHeader stateRoot is different", header.stateRoot, deserializedHeader.stateRoot)
    assertArrayEquals("AccountBlockHeader receiptsRoot is different", header.receiptsRoot, deserializedHeader.receiptsRoot)
    assertEquals("AccountBlockHeader forgerAddress is different", header.forgerAddress, deserializedHeader.forgerAddress)
    assertEquals("AccountBlockHeader baseFee is different", header.baseFee, deserializedHeader.baseFee)
    assertEquals("AccountBlockHeader gasUsed is different", header.gasUsed, deserializedHeader.gasUsed)
    assertEquals("AccountBlockHeader gasLimit is different", header.gasLimit, deserializedHeader.gasLimit)
    assertArrayEquals("AccountBlockHeader ommersMerkleRootHash is different", header.ommersMerkleRootHash, deserializedHeader.ommersMerkleRootHash)
    assertEquals("AccountBlockHeader ommersNumber is different", header.ommersCumulativeScore, deserializedHeader.ommersCumulativeScore)
    assertEquals("AccountBlockHeader id is different", header.id, deserializedHeader.id)
  }

  @Test
  def semanticValidity(): Unit = {
    val (baseUnsignedHeader: AccountBlockHeader, forgerMetadata: ForgerAccountGenerationMetadata) = createUnsignedBlockHeader(433L)


    // Test 1: unsigned header must be not semantically valid
    baseUnsignedHeader.semanticValidity(params) match {
      case Success(_) =>
        jFail("Unsigned header expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidSidechainBlockHeaderException], e.getClass)
    }


    // Test 2: signed header with invalid signature must be not semantically valid
    val invalidSignature = forgerMetadata.blockSignSecret.sign("different_message".getBytes(StandardCharsets.UTF_8))
    val invalidSignedHeader = baseUnsignedHeader.copy(signature = invalidSignature)
    invalidSignedHeader.semanticValidity(params) match {
      case Success(_) =>
        jFail("Header with wrong signature expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidSidechainBlockHeaderException], e.getClass)
    }


    // Test 3: signed header must be semantically valid
    val validSignature = forgerMetadata.blockSignSecret.sign(baseUnsignedHeader.messageToSign)
    val validSignedHeader = baseUnsignedHeader.copy(signature = validSignature)
    validSignedHeader.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Signed header expected to be semantically valid, instead exception: ${e.getMessage}")
    }


    // Test 4: invalid timestamp < 0
    val header = baseUnsignedHeader.copy(timestamp = -1L)
    var headerSignature = forgerMetadata.blockSignSecret.sign(header.messageToSign)
    var signedHeader = header.copy(signature = headerSignature)
    signedHeader.semanticValidity(params) match {
      case Success(_) =>
        jFail("Signed header with negative timestamp expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidSidechainBlockHeaderException], e.getClass)
    }

    // Test 4: unsupported block version
    val invalidHeader = baseUnsignedHeader.copy(version = Byte.MaxValue)
    headerSignature = forgerMetadata.blockSignSecret.sign(invalidHeader.messageToSign)
    signedHeader = invalidHeader.copy(signature = headerSignature)
    signedHeader.semanticValidity(params) match {
      case Success(_) =>
        jFail("Signed header with negative timestamp expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidSidechainBlockHeaderException], e.getClass)
    }
  }
}
