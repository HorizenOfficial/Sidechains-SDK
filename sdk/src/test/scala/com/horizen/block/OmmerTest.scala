package com.horizen.block

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}

import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures._
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.proof.Signature25519
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.VrfSecretKey
import com.horizen.utils.BytesUtils
import com.horizen.validation.{InconsistentOmmerDataException, InvalidOmmerDataException}
import com.horizen.vrf.VrfGeneratedDataProvider
import org.junit.Assert.{assertArrayEquals, assertEquals, assertFalse, assertNotEquals, assertTrue, fail => jFail}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import scorex.util.ModifierId

import scala.io.Source
import scala.util.{Failure, Success}

class OmmerTest extends JUnitSuite with CompanionsFixture with SidechainBlockFixture {

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val sidechainBlockSerializer = new SidechainBlockSerializer(sidechainTransactionsCompanion)

  val params: NetworkParams = MainNetParams()
  val mcBlockRef1: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473173_mainnet").getLines().next()), params).get
  val mcBlockRef2: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473174_mainnet").getLines().next()), params).get
  val mcBlockRef3: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473175_mainnet").getLines().next()), params).get
  val mcBlockRef4: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473176_mainnet").getLines().next()), params).get

  val vrfGenerationSeed = 143
  val vrfGenerationPrefix = "OmmerTest"

  //set to true if you want to update vrf related data
  if (false) {
    VrfGeneratedDataProvider.updateVrfSecretKey(vrfGenerationPrefix, vrfGenerationSeed)
    VrfGeneratedDataProvider.updateVrfProof(vrfGenerationPrefix, vrfGenerationSeed)
  }

  @Test
  def semanticValidity(): Unit = {
    // Test 1: ommer with valid SidechainBlockHeader and no headers&ommers at all must be valid
    // Create Block with no MainchainBlockReferencesData and MainchainHeaders
    var block: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 444L, timestampOpt = Some(100000L), includeReference = false)
    var ommer: Ommer = Ommer.toOmmer(block)
    ommer.verifyDataConsistency() match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have consistent data, instead exception: ${e.getMessage}")
    }
    ommer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }


    // Test 2: ommers with valid SidechainBlockHeader, without mc block references data but with defined RefDataHash must be invalid
    var invalidOmmer = ommer.copy(mainchainReferencesDataMerkleRootHashOption = Some(new Array[Byte](32)))
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }


    // Test 3: ommer with valid SidechainBlockHeader, MainchainBlockReferencesData and MainchainHeaders must be valid
    val seed: Long = 11L
    val parentId: ModifierId = getRandomBlockId(seed)
    val (forgerBox, forgerMetadata) = ForgerBoxFixture.generateForgerBox(seed)
    val vrfProof = VrfGenerator.generateProof(seed)
    // Create block with 1 MainchainBlockReferencesData and 2 MainchainHeader
    block = SidechainBlock.create(
      parentId,
      SidechainBlock.BLOCK_VERSION,
      123444L,
      Seq(mcBlockRef1.data),
      Seq(),  // No txs
      Seq(mcBlockRef1.header, mcBlockRef2.header),
      Seq(),  // No ommers
      forgerMetadata.blockSignSecret,
      forgerMetadata.forgingStakeInfo,
      vrfProof,
      MerkleTreeFixture.generateRandomMerklePath(seed),
      sidechainTransactionsCompanion
    ).get

    ommer = Ommer.toOmmer(block)
    ommer.verifyDataConsistency() match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have consistent data, instead exception: ${e.getMessage}")
    }
    ommer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }


    // Test 4: ommer with valid SidechainBlockHeader, MainchainBlockReferencesData and MainchainHeaders, but missed RefDataHash must be invalid
    invalidOmmer = ommer.copy(mainchainReferencesDataMerkleRootHashOption = None)
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }


    // Test 5: ommer with SidechainBlockHeader, MainchainBlockReferencesData and MainchainHeaders, but invalid RefDataHash must be invalid
    invalidOmmer = ommer.copy(mainchainReferencesDataMerkleRootHashOption = Some(new Array[Byte](32)))
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }


    // Test 6: ommer with valid SidechainBlockHeader and non consistent chain of MainchainHeaders must be invalid
    invalidOmmer = ommer.copy(mainchainHeaders = Seq(mcBlockRef1.header, mcBlockRef3.header, mcBlockRef4.header))
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
        jFail(s"Ommer expected to have invalid data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data verification.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 7: ommer with valid SidechainBlockHeader but with inconsistent MainchainHeaders must be invalid
    // Without first MainchainHeaders
    invalidOmmer = ommer.copy(mainchainHeaders = ommer.mainchainHeaders.tail)
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }

    // With one more MainchainHeader in the end
    invalidOmmer = ommer.copy(mainchainHeaders = ommer.mainchainHeaders :+ mcBlockRef3.header)
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }


    // Test 8: ommer with valid SidechainBlockHeader that expects MainchainHeaders, but no data in ommer must be invalid
    // With no MainchainHeaders at all
    invalidOmmer = ommer.copy(mainchainHeaders = Seq())
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }
    // With no MainchainBlockReferencesData and MainchainHeaders at all
    invalidOmmer = ommer.copy(mainchainHeaders = Seq(), mainchainReferencesDataMerkleRootHashOption = None)
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
        jFail(s"Ommer expected to have inconsistent data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data consistency verification.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have valid data, instead exception: ${e.getMessage}")
    }


    // Test 9: ommer with semantically invalid SidechainBlockHeader must be invalid
    // Broke block header signature
    invalidOmmer = ommer.copy(header = block.header.copy(signature = new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH))))
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have consistent data, instead exception: ${e.getMessage}")
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
        jFail(s"Ommer expected to have invalid data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data verification.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 10: ommer with valid SidechainBlockHeader, but broken mcHeader
    // Broke mc header
    val invalidMcHeader1 = new MainchainHeader(
      mcBlockRef1.header.mainchainHeaderBytes,
      mcBlockRef1.header.version,
      mcBlockRef1.header.hashPrevBlock,
      mcBlockRef1.header.hashMerkleRoot,
      mcBlockRef1.header.hashScTxsCommitment,
      -1, // broke time
      mcBlockRef1.header.bits,
      mcBlockRef1.header.nonce,
      mcBlockRef1.header.solution
    )

    invalidOmmer = ommer.copy(mainchainHeaders = Seq(invalidMcHeader1, mcBlockRef2.header))
    invalidOmmer.verifyDataConsistency() match {
      case Success(_) =>
      case Failure(e) => jFail(s"Ommer expected to have consistent data, instead exception: ${e.getMessage}")
    }
    invalidOmmer.verifyData(params) match {
      case Success(_) =>
        jFail(s"Ommer expected to have invalid data.")
      case Failure(e) =>
        assertEquals("Different exception type expected during data verification.",
          classOf[InvalidOmmerDataException], e.getClass)
    }
  }

  @Test
  def compare(): Unit = {
    val vrfKeyPairOpt: Option[(VrfSecretKey, VrfPublicKey)] = {
      val secret: VrfSecretKey = VrfGeneratedDataProvider.getVrfSecretKey(vrfGenerationPrefix, vrfGenerationSeed)
      val publicKey: VrfPublicKey = secret.publicImage();
      Option((secret, publicKey))
    }
    val vrfProofOpt = Option(VrfGeneratedDataProvider.getVrfProof(vrfGenerationPrefix, vrfGenerationSeed))

    val block1: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 444L, timestampOpt = Some(100000L), includeReference = false, vrfKeysOpt = vrfKeyPairOpt, vrfProofOpt = vrfProofOpt)
    val ommer1: Ommer = Ommer.toOmmer(block1)

    // Test 1: Compare different ommers:
    val block2: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 555L, timestampOpt = Some(100000L), includeReference = false)
    val ommer2: Ommer = Ommer.toOmmer(block2)
    assertNotEquals("Ommers expected to be different", ommer1.hashCode(), ommer2.hashCode())
    assertNotEquals("Ommers expected to be different", ommer1, ommer2)


    // Test 2: Compare equal ommers:
    val block3: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 444L, timestampOpt = Some(100000L), includeReference = false, vrfKeysOpt = vrfKeyPairOpt, vrfProofOpt = vrfProofOpt)
    val ommer3: Ommer = Ommer.toOmmer(block3)
    assertEquals("Ommers expected to be equal", ommer1.hashCode(), ommer3.hashCode())
    assertEquals("Ommers expected to be equal", ommer1, ommer3)


    // Test 3: Compare equal ommers with MC data:
    val block4: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 666L, timestampOpt = Some(100000L), includeReference = true)
    val ommer4: Ommer = Ommer.toOmmer(block4)
    val ommer5: Ommer = ommer4.copy()
    assertEquals("Ommers expected to be equal", ommer4.hashCode(), ommer5.hashCode())
    assertEquals("Ommers expected to be equal", ommer4, ommer5)


    // Test 4: Compare different ommers with MC data:
    val ommer6: Ommer = ommer4.copy(mainchainHeaders = Seq())
    assertEquals("Ommers expected to be equal", ommer4.hashCode(), ommer6.hashCode())
    assertNotEquals("Ommers expected to be different", ommer4, ommer6)


    // Test 5: Compare with different class object
    val diffObject: Int = 1
    assertFalse("Object expected to be different", ommer1.equals(diffObject))
  }

  @Test
  def serialization(): Unit = {
    // Test 1: Ommer with SidechainBlockHeader and no MainchainBlockReferencesData and MainchainHeaders
    var block: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(
      sidechainTransactionsCompanion,
      basicSeed = 444L,
      timestampOpt = Some(100000L),
      includeReference = false
    )
    var ommer: Ommer = Ommer.toOmmer(block)
    var bytes = ommer.bytes

    var serializedOmmerTry = OmmerSerializer.parseBytesTry(bytes)
    assertTrue("Ommer expected to by parsed.", serializedOmmerTry.isSuccess)

    var serializedOmmer = serializedOmmerTry.get
    assertEquals("Ommer sidechainBlockHeader is different", ommer.header.id, serializedOmmer.header.id)
    assertEquals("Ommer mainchainReferencesDataMerkleRootHashOption expected to be None",
      ommer.mainchainReferencesDataMerkleRootHashOption, serializedOmmer.mainchainReferencesDataMerkleRootHashOption)
    assertEquals("Ommer MainchainHeaders seq is different", ommer.mainchainHeaders, serializedOmmer.mainchainHeaders)
    assertEquals("Ommer ommers seq is different", ommer.ommers, serializedOmmer.ommers)
    assertArrayEquals("Ommer id is different", ommer.id, serializedOmmer.id)
    assertEquals("Ommer is different", ommer, serializedOmmer)


    // Test 2: ommer with MainchainBlockReferencesData and MainchainHeaders
    val seed: Long = 11L
    val parentId: ModifierId = getRandomBlockId(seed)

    val vrfKeyPairOpt: Option[(VrfSecretKey, VrfPublicKey)] = {
      val secret: VrfSecretKey = VrfGeneratedDataProvider.getVrfSecretKey(vrfGenerationPrefix, vrfGenerationSeed)
      val publicKey: VrfPublicKey = secret.publicImage();
      Option((secret, publicKey))
    }
    val vrfProof = VrfGeneratedDataProvider.getVrfProof(vrfGenerationPrefix, vrfGenerationSeed)

    val (forgerBox, forgerMetadata) = ForgerBoxFixture.generateForgerBox(seed, vrfKeyPairOpt)
    // Create block with 1 MainchainBlockReferencesData and 2 MainchainHeader
    block = SidechainBlock.create(
      parentId,
      SidechainBlock.BLOCK_VERSION,
      123444L,
      Seq(mcBlockRef1.data),
      Seq(),  // No txs
      Seq(mcBlockRef1.header, mcBlockRef2.header),
      Seq(),  // No ommers
      forgerMetadata.blockSignSecret,
      forgerMetadata.forgingStakeInfo,
      vrfProof,
      MerkleTreeFixture.generateRandomMerklePath(seed),
      sidechainTransactionsCompanion
    ).get

    ommer = Ommer.toOmmer(block)
    bytes = ommer.bytes

    // Set to true to regenerate regression data
    if(false) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/ommer_hex"))
      out.write(BytesUtils.toHexString(bytes))
      out.close()
    }

    serializedOmmerTry = OmmerSerializer.parseBytesTry(bytes)
    assertTrue("Ommer expected to by parsed.", serializedOmmerTry.isSuccess)

    serializedOmmer = serializedOmmerTry.get
    assertEquals("Ommer sidechainBlockHeader is different", ommer.header.id, serializedOmmer.header.id)
    assertArrayEquals("Ommer mainchainReferencesDataMerkleRootHashOption is different",
      ommer.mainchainReferencesDataMerkleRootHashOption.get, serializedOmmer.mainchainReferencesDataMerkleRootHashOption.get)
    assertEquals("Ommer MainchainHeaders seq is different", ommer.mainchainHeaders, serializedOmmer.mainchainHeaders)
    assertEquals("Ommer ommers seq is different", ommer.ommers, serializedOmmer.ommers)
    assertArrayEquals("Ommer id is different", ommer.id, serializedOmmer.id)
    assertEquals("Ommer is different", ommer, serializedOmmer)


    // Test 3: try to deserialize broken bytes.
    assertTrue("OmmerSerializer expected to be not parsed due to broken data.", OmmerSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serializationRegression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("ommer_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }


    val seed: Long = 11L
    val parentId: ModifierId = getRandomBlockId(seed)
    val vrfKeyPairOpt: Option[(VrfSecretKey, VrfPublicKey)] = {
      val secret: VrfSecretKey = VrfGeneratedDataProvider.getVrfSecretKey(vrfGenerationPrefix, vrfGenerationSeed)
      val publicKey: VrfPublicKey = secret.publicImage();
      Option((secret, publicKey))
    }
    val vrfProof = VrfGeneratedDataProvider.getVrfProof(vrfGenerationPrefix, vrfGenerationSeed)

    val (_, forgerMetadata) = ForgerBoxFixture.generateForgerBox(seed, vrfKeyPairOpt)
    // Create block with 1 MainchainBlockReferencesData and 2 MainchainHeader
    val block = SidechainBlock.create(
      parentId,
      SidechainBlock.BLOCK_VERSION,
      123444L,
      Seq(mcBlockRef1.data),
      Seq(),  // No txs
      Seq(mcBlockRef1.header, mcBlockRef2.header),
      Seq(),  // No ommers
      forgerMetadata.blockSignSecret,
      forgerMetadata.forgingStakeInfo,
      vrfProof,
      MerkleTreeFixture.generateRandomMerklePath(seed),
      sidechainTransactionsCompanion
    ).get

    val ommer = Ommer.toOmmer(block)

    val deserializedOmmerTry = OmmerSerializer.parseBytesTry(bytes)
    assertTrue("Ommer expected to by parsed.", deserializedOmmerTry.isSuccess)

    val deserializedOmmer = deserializedOmmerTry.get
    assertEquals("Ommer sidechainBlockHeader is different", ommer.header.id, deserializedOmmer.header.id)
    assertArrayEquals("Ommer mainchainReferencesDataMerkleRootHashOption is different",
      ommer.mainchainReferencesDataMerkleRootHashOption.get, deserializedOmmer.mainchainReferencesDataMerkleRootHashOption.get)
    assertEquals("Ommer MainchainHeaders seq is different", ommer.mainchainHeaders, deserializedOmmer.mainchainHeaders)
    assertEquals("Ommer ommers seq is different", ommer.ommers, deserializedOmmer.ommers)
    assertArrayEquals("Ommer id is different", ommer.id, deserializedOmmer.id)
    assertEquals("Ommer is different", ommer, deserializedOmmer)
  }
}
