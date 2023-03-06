package io.horizen.block

import com.google.common.primitives.Ints
import io.horizen.block.SidechainCreationVersions.{SidechainCreationVersion0, SidechainCreationVersion1, SidechainCreationVersion2}
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.params.{MainNetParams, RegTestParams, TestNetParams}
import io.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import io.horizen.utils.{ByteArrayWrapper, BytesUtils, CustomSidechainsVersions, SidechainVersionZero, TestSidechainsVersionsManager}
import org.junit.Assert.{assertArrayEquals, assertEquals, assertFalse, assertTrue, fail => jFail}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import scala.io.Source
import scala.util.{Failure, Success, Try}

// Most of the tests are executed twice (once for ceasing, once for non-ceasing case)
// But the cases with multiple certificates for given sidechain are specific to the ceasing sidechains only:
// Non-ceasing sidechains can't have more than one certificate for in a single MC block.

// Note: At the earlier version of SDK (0.6.0-SNAPSHOT2) non-ceasing sidechains had a different structure
// of McBlockRefData compared to ceasing ones: multiple certificates for different epochs were allowed.
// In 0.6.0-SNAPSHOT3 this modification has been reverted, since became impossible from MC side (zend 4.0.0-beta).
// Thus, we still keep full coverage of McBlockRefs parsing for both ceasing and non-ceasing sidechains.
class MainchainBlockReferenceTest extends JUnitSuite {

  @Test
  def blocksWithoutScSupportParsing_Ceasing(): Unit = {
    blocksWithoutScSupportParsing(false)
  }

  @Test
  def blocksWithoutScSupportParsing_NonCeasing(): Unit = {
    blocksWithoutScSupportParsing(true)
  }

  def blocksWithoutScSupportParsing(isNonCeasing: Boolean): Unit = {
    var mcBlockHex: String = null
    var mcBlockBytes: Array[Byte] = null
    var block: Try[MainchainBlockReference] = null


    val params = MainNetParams(isNonCeasing=isNonCeasing)

    // Test 1: Block #473173
    // mcblock473173_mainnet data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660
    mcBlockHex = Source.fromResource("mcblock473173_mainnet").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params, TestSidechainsVersionsManager())

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660", block.get.header.hashHex)
    assertFalse("Old Block occurred, proof of existence expected to be undefined.", block.get.data.existenceProof.isDefined)
    assertFalse("Old Block occurred, proof of absence expected to be undefined.", block.get.data.absenceProof.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Old Block occurred, Certificate expected to be undefined.", block.get.data.topQualityCertificate.isEmpty)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "0000000009572f35ecc6e319216b29046fdb6695ad93b3e5d77053285df4af03", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "5bf368ee4fc02f055e8ca5447a21b9758e6435b3214bc10b55f533cc9b3d1a6d", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1551432137, block.get.header.time)
    assertEquals("Block PoW bits is different.", "1c2abb60", BytesUtils.toHexString(Ints.toByteArray(block.get.header.bits)))
    assertEquals("Block nonce is different.", "00000000000000000000000000030000000000009921008000000000c7cf0410", BytesUtils.toHexString(block.get.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, block.get.header.solution.length)
    block.get.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Block expected to be semantically valid, instead: ${e.getMessage}")
    }


    // Test 2: Block #501173
    // mcblock501173 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000011aec26c29306d608645a644a592e44add2988a9d156721423e714e0
    mcBlockHex = Source.fromResource("mcblock501173").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params, TestSidechainsVersionsManager())

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000011aec26c29306d608645a644a592e44add2988a9d156721423e714e0", block.get.header.hashHex)
    assertFalse("Old Block occurred, proof of existence expected to be undefined.", block.get.data.existenceProof.isDefined)
    assertFalse("Old Block occurred, proof of absence expected to be undefined.", block.get.data.absenceProof.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Old Block occurred, Certificate expected to be undefined.", block.get.data.topQualityCertificate.isEmpty)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "00000000106843ee0119c6db92e38e8655452fd85f638f6640475e8c6a3a3582", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "493232e7d362852c8e3fe6aa5a48d6f6e01220f617c258db511ee2386b6362ea", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1555658453, block.get.header.time)
    assertEquals("Block PoW bits is different.", "1c1bbecc", BytesUtils.toHexString(Ints.toByteArray(block.get.header.bits)))
    assertEquals("Block nonce is different.", "0000000000000000000000000019000000000000751d00600000000000000000", BytesUtils.toHexString(block.get.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, block.get.header.solution.length)
    block.get.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Block expected to be semantically valid, instead: ${e.getMessage}")
    }


    // Test 3: Block #273173
    // mcblock273173 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000009b9f4a9f2abe5cd129421df969d1eb1b02d3fd685ab0781939ead07
    mcBlockHex = Source.fromResource("mcblock273173").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params, TestSidechainsVersionsManager())

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000009b9f4a9f2abe5cd129421df969d1eb1b02d3fd685ab0781939ead07", block.get.header.hashHex)
    assertFalse("Old Block occurred, proof of existence expected to be undefined.", block.get.data.existenceProof.isDefined)
    assertFalse("Old Block occurred, proof of absence expected to be undefined.", block.get.data.absenceProof.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Old Block occurred, Certificate expected to be undefined.", block.get.data.topQualityCertificate.isEmpty)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "0000000071076828a1d738dfde576b21ac4e28998ae7a026f631e57d7561a28b", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "7169f926344ff99dbee02ed2429481bbbc0b84cb4773c1dcaee20458e0d0437a", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1521052551, block.get.header.time)
    assertEquals("Block PoW bits is different.", "1d010d77", BytesUtils.toHexString(Ints.toByteArray(block.get.header.bits)))
    assertEquals("Block nonce is different.", "000000000000000000000000cfcbffd9de586ff80e928e9b83e86c3c8c580000", BytesUtils.toHexString(block.get.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, block.get.header.solution.length)
    block.get.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Block expected to be semantically valid, instead: ${e.getMessage}")
    }
  }

  @Test
  def blockWithoutSidechains_Ceasing(): Unit = {
    blocksWithoutScSupportParsing(false)
  }

  @Test
  def blockWithoutSidechains_NonCeasing(): Unit = {
    blocksWithoutScSupportParsing(true)
  }

  def blockWithoutSidechains(isNonCeasing: Boolean): Unit = {
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000000" // valid FieldElement in BigEndian
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    val params = RegTestParams(scId.data, isNonCeasing = isNonCeasing)

    // Test: parse MC block with tx version -4 without sidechain related stuff.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_empty_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params, TestSidechainsVersionsManager())

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    // Set to true to regenerate regression data
    if (false) {
      val bytes = MainchainBlockReferenceDataSerializer.toBytes(mcblock.data)
      val out = new BufferedWriter(new FileWriter("src/test/resources/new_mc_blocks/mc_block_reference_data_empty_sidechains"))
      out.write(BytesUtils.toHexString(bytes))
      out.close()
    }

    assertTrue("Block must not contain transaction.", mcblock.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not contain proof of existence.", mcblock.data.existenceProof.isEmpty)
    assertTrue("Block must not contain proof of absence", mcblock.data.absenceProof.isEmpty)

    assertEquals("Block Hash is different.", "0d17b1c08fae201002e63a7bc8a570fd34b1f643bd3faaf43f4dc2e4298fcae2", mcblock.header.hashHex)
    assertEquals("Block version = 3 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "04317122f8a5725f68c62b2d1e4179b98b834e640db9c9b400dc3b51f7a0c322", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "ac702c8a1f0f81a32c8db1686b388538712b5d3c52d19abac9ef8677973762b9", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("CommTree Merkle root hash is different.", "0d65e96cc400f0e6537f6391d812c1959dd46e958928d2338b75d4662f01d466", BytesUtils.toHexString(mcblock.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1621952653, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f04", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000628c914ea82db6f1d5a1645c19056010ecc97c78e3a73d5ca6f1f5ae0004", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    mcblock.semanticValidity(params).get
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params).isSuccess)
  }

  @Test
  def blockWithSingleSidechainCreation_Ceasing(): Unit = {
    blockWithSingleSidechainCreation(false)
  }

  @Test
  def blockWithSingleSidechainCreation_NonCeasing(): Unit = {
    blockWithSingleSidechainCreation(true)
  }

  def blockWithSingleSidechainCreation(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with single sc creation output.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_1_sc_creation").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for the sidechain mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex = "2c446b6d305ca7aef113a2655a4e9b0ab6ce1cfb13533d0e7c2c7aac5cf85dcb" // BE
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data, isNonCeasing = isNonCeasing)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block must contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block AggTx must contain single output.", 1,
      mcblock1.data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().size())

    val output = mcblock1.data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().get(0)
    assertTrue("Block AggTx must contain exactly a sc creation output.", output.isInstanceOf[SidechainCreation])

    val scCreation = output.asInstanceOf[SidechainCreation]
    assertEquals("Sc creation output id is different", scId, new ByteArrayWrapper(scCreation.sidechainId()))


    assertTrue("Block must not contain certificate.", mcblock1.data.topQualityCertificate.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)
  }

  @Test
  def blockWithSingleForwardTransfer_Ceasing(): Unit = {
    blockWithSingleForwardTransfer(false)
  }

  @Test
  def blockWithSingleForwardTransfer_NonCeasing(): Unit = {
    blockWithSingleForwardTransfer(true)
  }

  def blockWithSingleForwardTransfer(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with single forward transfer output.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_1_ft").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for the sidechain mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex = "2d62224e739318fe16ef3e57bdc03b00003892a99e019d1e2a242ca7176a4aed" // BE
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data, isNonCeasing = isNonCeasing)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block must contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block AggTx must contain single output.", 1,
      mcblock1.data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().size())

    val output = mcblock1.data.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs().get(0)
    assertTrue("Block AggTx must contain exactly a FT output.", output.isInstanceOf[ForwardTransfer])

    val ft = output.asInstanceOf[ForwardTransfer]
    assertEquals("FT output id is different", scId, new ByteArrayWrapper(ft.sidechainId()))


    assertTrue("Block must not contain certificate.", mcblock1.data.topQualityCertificate.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)
  }

  @Test
  def blockWith3Sidechains_Ceasing(): Unit = {
    blockWith3Sidechains(false)
  }

  @Test
  def blockWith3Sidechains_NonCeasing(): Unit = {
    blockWith3Sidechains(true)
  }

  def blockWith3Sidechains(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with 3 FTs for 3 different sidechains.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_3_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)


    // Test 1: Check for the leftmost sidechain mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex1 = "37bfaf6f433503c16ce6c70fb13fb97090cb474034e2230fda23cb86476f2a9f"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data, isNonCeasing = isNonCeasing)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock1.data.topQualityCertificate.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for the sidechain in the middle, that is mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex2 = "2473b3945442bb247e0c05793bf7f6fcbd2c35679cb96eae45afe5ce4aea8eb6"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data, isNonCeasing = isNonCeasing)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2, TestSidechainsVersionsManager(params2))

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock2.data.topQualityCertificate.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock2.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock2.data.absenceProof.isEmpty)


    // Test 3:  Check for the rightmost sidechain mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex3 = "2d62224e739318fe16ef3e57bdc03b00003892a99e019d1e2a242ca7176a4aed"
    val scId3 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex3))) // LE

    val params3 = RegTestParams(scId3.data, isNonCeasing = isNonCeasing)

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3, TestSidechainsVersionsManager(params3))

    assertTrue("Block expected to be parsed", mcblockTry3.isSuccess)
    val mcblock3 = mcblockTry3.get

    assertTrue("Block expected to be semantically valid", mcblock3.semanticValidity(params3).isSuccess)

    assertTrue("Block must contain transaction.", mcblock3.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock3.data.topQualityCertificate.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock3.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock3.data.absenceProof.isEmpty)


    // Test 4: Check for the sidechain that is not mentioned in the block and has a leftmost neighbour.
    // We expect to get a MainchainBlockReference with only right neighbour proof of no data.
    val scIdHex4 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId4 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex4))) // LE
    val params4 = RegTestParams(scId4.data, isNonCeasing = isNonCeasing)

    val mcblockTry4 = MainchainBlockReference.create(mcBlockBytes, params4, TestSidechainsVersionsManager(params4))

    assertTrue("Block expected to be parsed", mcblockTry4.isSuccess)
    val mcblock4 = mcblockTry4.get

    assertTrue("Block expected to be semantically valid", mcblock4.semanticValidity(params4).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock4.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock4.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not contain proof of existence.", mcblock4.data.existenceProof.isEmpty)
    assertTrue("Block must not contain proof of absence", mcblock4.data.absenceProof.isDefined)


    // Test 5: Check for the sidechain that is not mentioned in the block and has a rightmost neighbour.
    // We expect to get a MainchainBlockReference with only left neighbour proof of no data.
    val scIdHex5 = "3fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val scId5 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex5))) // LE
    val params5 = RegTestParams(scId5.data, isNonCeasing = isNonCeasing)

    val mcblockTry5 = MainchainBlockReference.create(mcBlockBytes, params5, TestSidechainsVersionsManager(params5))

    assertTrue("Block expected to be parsed", mcblockTry5.isSuccess)
    val mcblock5 = mcblockTry5.get

    assertTrue("Block expected to be semantically valid", mcblock5.semanticValidity(params5).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock5.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock5.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not contain proof of existence.", mcblock5.data.existenceProof.isEmpty)
    assertTrue("Block must contain proof of absence.", mcblock5.data.absenceProof.isDefined)


    // Test 6: Check for the sidechain that is not mentioned in the block and has a both left and right neighbours.
    // We expect to get a MainchainBlockReference with left and right neighbour proofs of no data.
    // Take the value near to scIdHex2. Quite enough condition to have id, where, scIdHex1 < id < scIdHex2
    val scIdHex6 = "0000" + scIdHex2.substring(4)
    val scId6 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex6))) // LE
    val params6 = RegTestParams(scId6.data, isNonCeasing = isNonCeasing)

    val mcblockTry6 = MainchainBlockReference.create(mcBlockBytes, params6, TestSidechainsVersionsManager(params6))

    assertTrue("Block expected to be parsed", mcblockTry6.isSuccess)
    val mcblock6 = mcblockTry6.get

    assertTrue("Block expected to be semantically valid", mcblock6.semanticValidity(params6).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock6.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock6.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not contain proof of existence.", mcblock6.data.existenceProof.isEmpty)
    assertTrue("Block must contain proof of absence.", mcblock6.data.absenceProof.isDefined)


    // Test 7: Check for the sidechain that is not mentioned in the block and has a both left and right neighbours.
    // We expect to get a MainchainBlockReference with left and right neighbour proofs of no data.
    // Take the value near to scIdHex1. Quite enough condition to have id, where, scIdHex1 < id < scIdHex2
    val scIdHex7 = "3fff" + scIdHex1.substring(4)
    val scId7 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex7))) // LE
    val params7 = RegTestParams(scId7.data, isNonCeasing = isNonCeasing)

    val mcblockTry7 = MainchainBlockReference.create(mcBlockBytes, params7, TestSidechainsVersionsManager(params7))

    assertTrue("Block expected to be parsed", mcblockTry7.isSuccess)
    val mcblock7 = mcblockTry7.get

    assertTrue("Block expected to be semantically valid", mcblock7.semanticValidity(params7).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock7.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock7.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not contain proof of existence.", mcblock7.data.existenceProof.isEmpty)
    assertTrue("Block must contain proof of absence.", mcblock7.data.absenceProof.isDefined)
  }

  @Test
  def blockWithCertificateWithoutBTs_Ceasing(): Unit = {
    blockWithCertificateWithoutBTs(false)
  }

  @Test
  def blockWithCertificateWithoutBTs_NonCeasing(): Unit = {
    blockWithCertificateWithoutBTs(true)
  }

  def blockWithCertificateWithoutBTs(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with a single certificate without backward transfers.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_without_bts").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for existing sidechain
    val scIdHex1 = "043a46e2831bdf80657f39e6031c62e56b5d0b9399cc2f33ccc7b514ce8a7237"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data, isNonCeasing = isNonCeasing)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.topQualityCertificate.nonEmpty)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock1.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data, isNonCeasing = isNonCeasing)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2, TestSidechainsVersionsManager(SidechainVersionZero))

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock2.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock2.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock2.data.existenceProof.isEmpty)
    assertTrue("Block must not contain proof of absence", mcblock2.data.absenceProof.isDefined)
  }

  @Test
  def blockWithCertificateWithNonZeroScFee_Ceasing(): Unit = {
    blockWithCertificateWithNonZeroScFee(false)
  }

  @Test
  def blockWithCertificateWithNonZeroScFee_NonCeasing(): Unit = {
    blockWithCertificateWithNonZeroScFee(true)
  }

  def blockWithCertificateWithNonZeroScFee(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with a single certificate without backward transfers.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_with_non_zero_sc_fee")
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex.getLines().next())

    val scId1 = new ByteArrayWrapper(Array[Byte](83, -85, 98, 31, 59, -8, 55, -118, 9, -94, -45, -86, -83, 55, 47, -9, 124, -86, 82, 26, 2, -90, -101, 85, 119, -21, 2, 100, -70, -53, -84, 9)) // LE
    val params1 = RegTestParams(scId1.data, isNonCeasing = isNonCeasing)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.topQualityCertificate.nonEmpty)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock1.data.lowerCertificateLeaves.isEmpty)
    assertEquals("Block certificate must contain correct ft fee.", 54, mcblock1.data.topQualityCertificate.get.ftMinAmount)
    assertEquals("Block certificate must contain correct btr fee.", 0, mcblock1.data.topQualityCertificate.get.btrFee)
  }

  @Test
  def blockWithCertificateWithBTs_Ceasing(): Unit = {
    blockWithCertificateWithBTs(false)
  }

  @Test
  def blockWithCertificateWithBTs_NonCeasing(): Unit = {
    blockWithCertificateWithBTs(true)
  }

  def blockWithCertificateWithBTs(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with a single certificate with backward transfers.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_with_bts").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for existing sidechain
    val scIdHex1 = "043a46e2831bdf80657f39e6031c62e56b5d0b9399cc2f33ccc7b514ce8a7237"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data, isNonCeasing = isNonCeasing)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    // Set to true to regenerate regression data
    if (false) {
      val bytes = MainchainBlockReferenceDataSerializer.toBytes(mcblock1.data)
      val out = new BufferedWriter(new FileWriter("src/test/resources/new_mc_blocks/mc_block_reference_data_certificate_with_bts"))
      out.write(BytesUtils.toHexString(bytes))
      out.close()
    }

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.topQualityCertificate.nonEmpty)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock1.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data, isNonCeasing = isNonCeasing)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2, TestSidechainsVersionsManager(SidechainVersionZero))

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock2.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock2.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock2.data.existenceProof.isEmpty)
    assertTrue("Block must not contain proof of absence", mcblock2.data.absenceProof.isDefined)
  }

  def blockWithMultipleCertificates_Ceasing(): Unit = {
    // Test: parse MC block with 2 certificates.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_2_certificates").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)


    // Test 1: Check for existing sidechain
    val scIdHex1 = "1aabf493f8605e78177d71c0aef3b251b2b9aaa66715fc86729a4e86ce566d8e"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    // Set to true to regenerate regression data
    if (false) {
      val bytes = MainchainBlockReferenceDataSerializer.toBytes(mcblock1.data)
      val out = new BufferedWriter(new FileWriter("src/test/resources/new_mc_blocks/mc_block_reference_data_2_certificates"))
      out.write(BytesUtils.toHexString(bytes))
      out.close()
    }

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("For ceasing sidechain block must contain 1 top quality certificate.", mcblock1.data.topQualityCertificate.isDefined)
    assertEquals("For ceasing sidechain block must contain 1 low quality certificate.", 1,
      mcblock1.data.lowerCertificateLeaves.size)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence.", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2, TestSidechainsVersionsManager(SidechainVersionZero))

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain top quality certificate.", mcblock2.data.topQualityCertificate.isEmpty)
    assertEquals("Block must not contain lower quality certificates.", 0, mcblock2.data.lowerCertificateLeaves.size)
    assertTrue("Block must not contain proof of existence.", mcblock2.data.existenceProof.isEmpty)
    assertTrue("Block must contain proof of absence.", mcblock2.data.absenceProof.isDefined)
  }

  @Test
  def blockWithCertificateWithCustomFieldAndBitvector_Ceasing(): Unit = {
    blockWithCertificateWithCustomFieldAndBitvector(false)
  }

  @Test
  def blockWithCertificateWithCustomFieldAndBitvector_NonCeasing(): Unit = {
    blockWithCertificateWithCustomFieldAndBitvector(true)
  }

  def blockWithCertificateWithCustomFieldAndBitvector(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with a single certificate with 3 custom fields and 1 bitvector field.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_with_custom_fields").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for existing sidechain
    val scIdHex1 = "338a555811e24411880e99fbfeeb50e1775d041399e57ec0381a817758f55dcd"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(
      scId1.data,
      scCreationBitVectorCertificateFieldConfigs = Seq(
        // 4 field elements in a bitvector
        new CustomBitvectorElementsConfig(254*4, 255)
      ),
      sidechainCreationVersion = SidechainCreationVersion0,
      isNonCeasing = isNonCeasing
    )

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(SidechainVersionZero))

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.topQualityCertificate.nonEmpty)
    assertEquals("Block must contain certificate with 3 cert field elements.", 3, mcblock1.data.topQualityCertificate.get.fieldElementCertificateFields.length)
    assertEquals("Block must contain certificate with 1 cert bitvector.", 1, mcblock1.data.topQualityCertificate.get.bitVectorCertificateFields.length)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock1.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data, sidechainCreationVersion = SidechainCreationVersion0, isNonCeasing = isNonCeasing)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2, TestSidechainsVersionsManager(SidechainVersionZero))

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock2.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock2.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock2.data.existenceProof.isEmpty)
    assertTrue("Block must not contain proof of absence", mcblock2.data.absenceProof.isDefined)
  }

  @Test
  def blockWithCertificateWithCustomFieldAndBitvectorMixedScVersions_Ceasing(): Unit = {
    blockWithCertificateWithCustomFieldAndBitvectorMixedScVersions(false)
  }

  @Test
  def blockWithCertificateWithCustomFieldAndBitvectorMixedScVersions_NonCeasing(): Unit = {
    blockWithCertificateWithCustomFieldAndBitvectorMixedScVersions(true)
  }

  def blockWithCertificateWithCustomFieldAndBitvectorMixedScVersions(isNonCeasing: Boolean): Unit = {

    /*
     * Please, see the MC Python test sc_getscgenesisinfo.py for the generation of the test block and the
     * sidechains ID involved in it.
     * See also the analogous MC unit test: TEST(SidechainsField, CommitmentComputationFromSerializedBlock),
     * which is testing the same data
     */

    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_with_custom_fields_mixed_sc_versions").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val bitVectorSizeInBits = 254*4

    // Test: parse MC block with:
    // SC1: ---> creation (version=1)
    val scIdHex1 = "238a850eb3c33499aa2bdb17fa7001ffe930266377231b8cfaff06fdc1c8c53e"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    // Test: parse MC block with:
    // SC2: ---> creation (version 2 non-ceasing)
    val scIdHex2 = "28165facf93a7382d706e42d1e9ea7f5735f725a85abe99f255517b4e6adfdff"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    // SC3: ---> a single certificate with 2 custom fields and 1 bitvector field. (version=0)
    val scIdHex3 = "07de35daa2ad8647cea331b496b295257411efc2a166538946e7f718fababb4e"
    val scId3 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex3))) // LE

    // SC4: ---> a single certificate with 2 custom fields and 1 bitvector field. ((version=1)
    val scIdHex4 = "391c699ad12d478ea4f98bdb0fc2bbe56efdc703b642b54eaffd0c99f1569ab3"
    val scId4 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex4))) // LE

    // SC5: ---> a single certificate with 2 custom fields and 1 bitvector field. ((version=2 ceasing)
    val scIdHex5 = "03510ddeb9991c60c64b90b5fe5af42bd4fe9f3c1d38250c0c325b365e0c9b8a"
    val scId5 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex5))) // LE

    // SC6: ---> a single certificate with 2 custom fields and 1 bitvector field. ((version=2 non-ceasing)
    val scIdHex6 = "331aa93e94bdafda0fe3065f777c4c78465f86374971f5f4b063fbece7bea8b6"
    val scId6 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex6))) // LE

    val versionsManager = TestSidechainsVersionsManager(
      CustomSidechainsVersions(
        Map(
          scId1 -> SidechainCreationVersion1,
          scId2 -> SidechainCreationVersion2,
          scId3 -> SidechainCreationVersion0,
          scId4 -> SidechainCreationVersion1,
          scId5 -> SidechainCreationVersion2,
          scId6 -> SidechainCreationVersion2,
        )
      )
    )

    // Test 1: Check for existing sidechain
    val params1 = RegTestParams(
      scId1.data,
      sidechainCreationVersion = SidechainCreationVersion1,
      isNonCeasing = isNonCeasing
    )

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1, versionsManager)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock1.data.topQualityCertificate.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)

    // Test 2: Check for existing sidechain
    val params2 = RegTestParams(
      scId2.data,
      sidechainCreationVersion = SidechainCreationVersion2,
      isNonCeasing = isNonCeasing
    )

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2, versionsManager)

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock2.data.topQualityCertificate.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock2.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock2.data.absenceProof.isEmpty)

    // Test 3: Check for existing sidechain
    val params3 = RegTestParams(
      scId3.data,
      scCreationBitVectorCertificateFieldConfigs = Seq(
        new CustomBitvectorElementsConfig(bitVectorSizeInBits, 151)
      ),
      sidechainCreationVersion = SidechainCreationVersion0,
      isNonCeasing = isNonCeasing
    )

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3, versionsManager)

    assertTrue("Block expected to be parsed", mcblockTry3.isSuccess)
    val mcblock3 = mcblockTry3.get

    assertTrue("Block expected to be semantically valid", mcblock3.semanticValidity(params3).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock3.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock3.data.topQualityCertificate.nonEmpty)
    assertEquals("Block must contain certificate with 2 cert field elements.", 2, mcblock3.data.topQualityCertificate.get.fieldElementCertificateFields.length)
    assertEquals("Block must contain certificate with 1 cert bitvector.", 1, mcblock3.data.topQualityCertificate.get.bitVectorCertificateFields.length)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock3.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock3.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock3.data.absenceProof.isEmpty)

    // Test 4: Check for existing sidechain
    val params4 = RegTestParams(
      scId4.data,
      scCreationBitVectorCertificateFieldConfigs = Seq(
        new CustomBitvectorElementsConfig(bitVectorSizeInBits, 151)
      ),
      sidechainCreationVersion = SidechainCreationVersion1,
      isNonCeasing = isNonCeasing
    )

    val mcblockTry4 = MainchainBlockReference.create(mcBlockBytes, params4, versionsManager)

    assertTrue("Block expected to be parsed", mcblockTry4.isSuccess)
    val mcblock4 = mcblockTry4.get

    assertTrue("Block expected to be semantically valid", mcblock4.semanticValidity(params4).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock4.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock4.data.topQualityCertificate.nonEmpty)
    assertEquals("Block must contain certificate with 2 cert field elements.", 2, mcblock4.data.topQualityCertificate.get.fieldElementCertificateFields.length)
    assertEquals("Block must contain certificate with 1 cert bitvector.", 1, mcblock4.data.topQualityCertificate.get.bitVectorCertificateFields.length)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock4.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock4.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock4.data.absenceProof.isEmpty)

    // Test 5: Check for existing sidechain
    val params5 = RegTestParams(
      scId5.data,
      scCreationBitVectorCertificateFieldConfigs = Seq(
        new CustomBitvectorElementsConfig(bitVectorSizeInBits, 151)
      ),
      sidechainCreationVersion = SidechainCreationVersion2,
      isNonCeasing = isNonCeasing
    )

    val mcblockTry5 = MainchainBlockReference.create(mcBlockBytes, params5, versionsManager)

    assertTrue("Block expected to be parsed", mcblockTry5.isSuccess)
    val mcblock5 = mcblockTry5.get

    assertTrue("Block expected to be semantically valid", mcblock5.semanticValidity(params5).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock5.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock5.data.topQualityCertificate.nonEmpty)
    assertEquals("Block must contain certificate with 2 cert field elements.", 2, mcblock5.data.topQualityCertificate.get.fieldElementCertificateFields.length)
    assertEquals("Block must contain certificate with 1 cert bitvector.", 1, mcblock5.data.topQualityCertificate.get.bitVectorCertificateFields.length)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock5.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock5.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock5.data.absenceProof.isEmpty)

    // Test 6: Check for existing sidechain
    val params6 = RegTestParams(
      scId6.data,
      scCreationBitVectorCertificateFieldConfigs = Seq(
        new CustomBitvectorElementsConfig(bitVectorSizeInBits, 151)
      ),
      sidechainCreationVersion = SidechainCreationVersion2,
      isNonCeasing = isNonCeasing
    )

    val mcblockTry6 = MainchainBlockReference.create(mcBlockBytes, params6, versionsManager)

    assertTrue("Block expected to be parsed", mcblockTry6.isSuccess)
    val mcblock6 = mcblockTry6.get

    assertTrue("Block expected to be semantically valid", mcblock6.semanticValidity(params6).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock6.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock6.data.topQualityCertificate.nonEmpty)
    assertEquals("Block must contain certificate with 2 cert field elements.", 2, mcblock6.data.topQualityCertificate.get.fieldElementCertificateFields.length)
    assertEquals("Block must contain certificate with 1 cert bitvector.", 1, mcblock6.data.topQualityCertificate.get.bitVectorCertificateFields.length)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock6.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock6.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock6.data.absenceProof.isEmpty)


    // Test 7: Check for non-existing sidechain
    val scIdHex7 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId7 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex7))) // LE

    val params7 = RegTestParams(scId7.data, isNonCeasing = isNonCeasing)

    val mcblockTry7 = MainchainBlockReference.create(mcBlockBytes, params7, versionsManager)

    assertTrue("Block expected to be parsed", mcblockTry7.isSuccess)
    val mcblock7 = mcblockTry7.get

    assertTrue("Block expected to be semantically valid", mcblock7.semanticValidity(params7).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock7.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock7.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not contain lower quality certificate leaves.", mcblock7.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must not contain proof of existence.", mcblock7.data.existenceProof.isEmpty)
    assertTrue("Block must contain proof of absence", mcblock7.data.absenceProof.isDefined)
  }

  @Test
  def blockWithSingleMBTR_Ceasing(): Unit = {
    blockWithSingleMBTR(false)
  }

  @Test
  def blockWithSingleMBTR_NonCeasing(): Unit = {
    blockWithSingleMBTR(true)
  }

  def blockWithSingleMBTR(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with single MBTR output.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_1_mbtr").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Check for the sidechain NOT mentioned in the block.
    // NOTE: we don't allow MBTRs for the SDK based sidechains
    // Use dummy valid FE sc id
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data, isNonCeasing = isNonCeasing)

    val blockHash = "0c93b7438ec27250bb4b6a8995366225a8437ea91c642db9c4990416561a258f"
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block hash is different.", blockHash, mcblock.header.hashHex)
    assertTrue("Block must not contain transaction.", mcblock.data.sidechainRelatedAggregatedTransaction.isEmpty)

  }

  @Test
  def blockWithCSWs_Ceasing(): Unit = {
    blockWithCSWs(false)
  }

  @Test
  def blockWithCSWs_NonCeasing(): Unit = {
    blockWithCSWs(true)
  }

  def blockWithCSWs(isNonCeasing: Boolean): Unit = {
    // Test: parse MC block with tx version -4 with CSW inputs.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_csws").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Check for the sidechain NOT mentioned in the block.
    // NOTE: SDK based sidechains can't see its own CSWs because it is already ceased
    // Use dummy valid FE sc id
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data, isNonCeasing = isNonCeasing)

    val blockHash = "07e10cc67304769b37b02d35536410294deaf559a42e4d5fae33c3b84e6433b4"
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block hash is different.", blockHash, mcblock.header.hashHex)
    assertTrue("Block must not contain transaction.", mcblock.data.sidechainRelatedAggregatedTransaction.isEmpty)
  }

  @Test
  def mcBlockReferenceDataSerializationRegressionEmptySidechains(): Unit = {
    // Test: Regression for MC block ref data without certificates serialization.
    // Check that byte representation remains backward compatible after non-ceasing sidechain introduction

    var bytesFromFile: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("new_mc_blocks/mc_block_reference_data_empty_sidechains").getFile)
      bytesFromFile = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }

    val deserializedMcBlockReferenceDataTry = MainchainBlockReferenceDataSerializer.parseBytesTry(bytesFromFile)
    assertTrue("MainchainBlockReferenceData expected to be parsed.", deserializedMcBlockReferenceDataTry.isSuccess)
    val mcBlockReferenceData = deserializedMcBlockReferenceDataTry.get

    assertTrue("Block reference data must not contain transaction.", mcBlockReferenceData.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block reference data must not contain certificate.", mcBlockReferenceData.topQualityCertificate.isEmpty)
    assertTrue("Block reference data must not contain proof of existence.", mcBlockReferenceData.existenceProof.isEmpty)
    assertTrue("Block reference data must not contain proof of absence", mcBlockReferenceData.absenceProof.isEmpty)

    // Serialization regression check:
    assertArrayEquals("Serialized and regression bytes expected to be equal", bytesFromFile, mcBlockReferenceData.bytes)
  }

  @Test
  def mcBlockReferenceDataSerializationRegressionWithSingleCertificate(): Unit = {
    // Test: Regression for MC block ref data with 1 certificate serialization.
    // Check that byte representation remains backward compatible after non-ceasing sidechain introduction

    var bytesFromFile: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("new_mc_blocks/mc_block_reference_data_certificate_with_bts").getFile)
      bytesFromFile = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }

    val deserializedMcBlockReferenceDataTry = MainchainBlockReferenceDataSerializer.parseBytesTry(bytesFromFile)
    assertTrue("MainchainBlockReferenceData expected to be parsed.", deserializedMcBlockReferenceDataTry.isSuccess)
    val mcBlockReferenceData = deserializedMcBlockReferenceDataTry.get

    assertTrue("Block reference data must not contain transaction.", mcBlockReferenceData.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block reference data must contain certificate.", mcBlockReferenceData.topQualityCertificate.nonEmpty)
    assertTrue("Block reference data must not-contain lower quality certificate leaves.", mcBlockReferenceData.lowerCertificateLeaves.isEmpty)
    assertTrue("Block reference data must contain proof of existence.", mcBlockReferenceData.existenceProof.isDefined)
    assertTrue("Block reference data must not contain proof of absence", mcBlockReferenceData.absenceProof.isEmpty)

    // Serialization regression check:
    assertArrayEquals("Serialized and regression bytes expected to be equal", bytesFromFile, mcBlockReferenceData.bytes)
  }

  @Test
  def mcBlockReferenceDataSerializationRegressionMultipleCertificates(): Unit = {
    // Test: Regression for MC block ref data with 2 certificates serialization.
    // Check that byte representation remains backward compatible after non-ceasing sidechain introduction

    var bytesFromFile: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("new_mc_blocks/mc_block_reference_data_2_certificates").getFile)
      bytesFromFile = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }

    val deserializedMcBlockReferenceDataTry = MainchainBlockReferenceDataSerializer.parseBytesTry(bytesFromFile)
    assertTrue("MainchainBlockReferenceData expected to be parsed.", deserializedMcBlockReferenceDataTry.isSuccess)
    val mcBlockReferenceData = deserializedMcBlockReferenceDataTry.get

    assertTrue("Block reference data must contain 1 top quality certificate.", mcBlockReferenceData.topQualityCertificate.nonEmpty)
    assertEquals("Block reference data must contain 1 low quality certificate.",
      1, mcBlockReferenceData.lowerCertificateLeaves.size)
    assertTrue("Block reference data must contain proof of existence.", mcBlockReferenceData.existenceProof.isDefined)
    assertTrue("Block reference data must not contain proof of absence.", mcBlockReferenceData.absenceProof.isEmpty)

    // Serialization regression check:
    assertArrayEquals("Serialized and regression bytes expected to be equal", bytesFromFile, mcBlockReferenceData.bytes)
  }

  @Test
  def tokenmintMcBlock_Ceasing(): Unit = {
    tokenmintMcBlock(false)
  }

  @Test
  def tokenmintMcBlock_NonCeasing(): Unit = {
    tokenmintMcBlock(true)
  }

  def tokenmintMcBlock(isNonCeasing: Boolean): Unit = {
    // Sidechain testnet block #1051486 with 2 Txs and 1 Cert with 156 Bts
    val mcBlockHex = "03000000a579244a8432a36594ea66b19b537d16f0865e0e6f223ef5d1f50b793f4205001179ea842cf864a48d7a8d30061cc25c004a66a189f2990f99f01ea5749e128e090541974fc8606bc4fcad126902070b1ddbaccea5a9e6398000c637f02b5f38a6757762d236051f20000026f1010000000000000000000000000000000000000000000000000007fd4005007142252d23c02597bca087927fab2ed3f9d99db30bb07dd4dc9728218ce02270a34168128960d68e7d14b843d8f5c70c64d76fe80382d268ea3288fcc99f15ae8c0d9d59c106e87fb1eb1af537bd12eb79d03301615e360ec6ea54aac621d7eb5ab575410415695e24c58b90c794f07b53967465a37e514a22dcb2bf73087043932724c52f6bf3e55e16b58f9291871f315a2696da6b4fdcfcc10f17f4a69d2c804a64313dcb82079e8a6b38d07317e39fb0e2de92a29490aa92e3bf56aaede667e76439d3409a35be719b13d9c11f26b718db9fd073f87dd5d06d6387cca4a08540f1af439918fce24abd13f92303be922a076d4d9212ebf78f1a08ea466d828e25f96154928888276ba61f7a95d6d40e24577300bcc59bea27887410d73e2e7a213503140c736e36b3a2f2d9f690f23b50453bcce9ad14a35c2dbbe177c88cbd8dbfc3d30699d078cdfd6fbd104500f648dbdb82b2116afef2602ad6a195e3a05072610992a6ed5695d8e7c25f443a2bc4d9d54dcdff9e970bcb9424b650c9a39019728c97e55d2a1dae320537163a03fb89154177347e01a27d63e32d1620d5793601b82cd2ef8fd541ab9f91daa4ec70fdf8c6be751a25d8fa9ac32cb5dba96b73623cf9a7496e935d4e0208f999d22e246ab55c7ea2f50144321eacab5935c2096528cd7903805993756377cee77f4a0bc03db78905746d65d68c1e609f136425d5c68441d18c9c569c19cc12e1bff4fc01ae0fe20e175e63b58fd00d42721b7505e714fdde31f8dfd25219e350a96696378ebc20b9a38b18684d81f153476ee63c1286ac967f4d550e420cdbd957cf9f0e6db47e0b326ba2a208b8da5b10144590fadf2c1ff6b12290f0e1baa53af48a9dbf0fb0a647e2d89f03432a7414473f1dcef334b9fea62b5bace227a130cb201c53c064c73e31fa8934b5ec00a41c7d42f69abbe4f0b0d5c14f6dae0b247f3ea0346bd7be0a1789d95aa5b7dcb8cbcf52338d7aa99e2e1b7bf48fd991b532a1534e7ae0ac3aa94eb62ea0400412a80f5b0e2d642dd801dcf2ea6e1d7d95fc06063f3780057c322bf649c59f5938f495a41415696530d9db1a434cecca9f65a3c0b5fdc3e372df9e7d7d1a0fa79f504a573d921a238194e64e1ee8975e002344e33da5fb52eae4f71fb88f7f590e672205bc2c5800ead5b9a2409bb17929a2b6923a34bd0886369fd00767e7a185c1e465c350c6402a66b912226394954f0a2d47e74ed945a93e1a46ddb67032ff5a6a1b16aa3c2bf22203da87337b3fa5aa307830c2e014783bff02bae1bb5cae3c97780a5065a58f84ae20f7df4b0e38b00cd5c153fcd1d8e785838cf548bf79505ef6682d9d3cd54f72ad9faeb836750b70a2068908773f425536e74064f3991ba73c55e129733c075114be247100dc130ff6956648e5619244054b43ca6072f488f007db38f00de548134d184152a5cfff5111f17d416211bcd159efce76fca0327696ab63b67eb1d5d67d6414f0867e9f920df5a392b333eefb35750291f3aa1d0955405a1409b7b5e87ec2ef8e3a6760c24c1ad671330a247c4edffd7dab2c148124393be966ee173b9f179dcbe1c70e75e79907564d0bd044329487755dc91a84f3b9f1dee123dd3bb3e3b9bc7206672d967cb4011ae779e2d82a1f9336d050a766ef0b246d7c686a0e2ff89a754b4a32a04c22a228fe7b61c7b9b5f51d04d80100de19a85b36cf26f919f1256257dc92daab0cac9c50afe0935df3d633146f2d46e8d70a33f46501974edbb0819431ac19184c9373ad427a7af961a90804c3794b8ed4e50770239a5d5114add03ebcb2dd02f22f010c869d13ec26d3cfa1a6621d523198df5006cd43df6409f27d05ca2099bb2fbb68c0089f2a110201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff50035e0b100044656661756c7420732d6e6f6d7020706f6f6c2068747470733a2f2f6769746875622e636f6d2f732d6e6f6d702f732d6e6f6d702f77696b692f496e73696768742d706f6f6c2d6c696e6bffffffff04ee0c5a16000000001976a914da5bf5898d9e1bd7a892eeab7dd35354d6ffcd0a88ac405973070000000017a9148c884c4f61a6ad2dced9f820c6976397f7e4095b87a0acb9030000000017a914e8c49fa279f573cd24885aab5a436b53bd45434f87a0acb9030000000017a914ff834b75738d6945d6ef94a790c3a6558ab920d387000000000100000001ed862d14f057af8db3e605bafbba05e72a20edf881b7360427b4078d7a093e8b010000006b483045022100ce9b84804fb7f06c87eb3f3dc79ef16522bab328623b9fc3f0b061c47a6859e3022038b708eed1a02983c08e68dc0156e445e3907850531452047d0772b2037be355012102501e85cc2da4df02068bef900c6278f888f23367e2154413814bd8a823324261feffffff021098ac345c0200003f76a91434c0cd50d36ee99ba37f1094854b71c0e1ec9bcf88ac2077d756ffd698c6a609cbc282cf79896ffb3a95509d65501815ac4c87cdd3000003300a10b4008c8647000000003f76a914c0e7162ed51b4de9b432b1959ef65d367badd4ee88ac2077d756ffd698c6a609cbc282cf79896ffb3a95509d65501815ac4c87cdd3000003300a10b4520b100001fbffffffd1f3507465cd1b316d5c38287c9cc8240fedf934b209e245ea892723212f61050e0000000700000000000000207e83648b5a343ce4bc0cdbaad826e3db2b6343a06a7ebba5720df3f453787521fd660902010b38ebf5dd9a54ede278c4418a5c74ebc23696f4f8bcded2d429e916539e863c000001d17d647a10dce84aeade1dfe72c85cacb42585debfb8d7a108dad06afe52b9388000013f76e74dbae93ac78beb01bcfe0e92a21ac7d2c503653d19c1572fba914e880c8000013cf8fd8a3514035f6403547f52aead275c8e56913e5020f1d2dfd6bec94f982f0000015362aacfff5426c5312cec4c754c416a24d7bdd2d029b21f6e76d151131dc51a800001cb8ef93bc94e7957ace817a9184cc5645b3e265f4bbff05a9adfe18ac88a0f158000019c16761577dd81ae6cf9fc5d0c6c4b7d6db12c2d3d667a212f4aa450d059a32b000003656ed91e847b0ead0b4598114f14ac53f3f396d42460a4a7f34c5c2a1607652600c1d2f62f00d9cef198ae4d883b425daad7108a431befaaf162ba765eba58eb31803a6cf934fd3088a192e9b2916780bf51ddfcc93312c071a23e5e0b0d2a46bb2e8000da06e61ce5c689108d1fec85cee76ef27d6b8c8de0dcf4ea769b5b5953663e3deae09896041b51d4f4a7516c287d4312944df816ea4c14f8c6e6573cfebe9a2b761526f7599c40614a6edafa0cb028815ea2be5fcb239b5745decf0e61c0921d487863df2071cec095696dcab7040cd7f92dccecd052973253113ab00fb96f01d572636b43f71aa24b2dad35d990dda8b8e1d7d30df956c72b3c8e4add13083fa6457a8fa67fff6943e4a6afb92ae63dd6db387cb27ac7da528bddfc9e2bbb067bc1dc4e1b451f7300a4dc62f15ef363f32987230d25f57c34ee5ba97c03cf0fdd3124546b1a4c839111feb303cd30f270b4372f22556ea1b1e71041f9b9d13411a31f4ed5944600a497971f6924aadb0b8ab68c075a4887fb113023c28ae22df7a4e17be5931843f1a470ac8c9d73ba82907601a76f5a6101fbafe4c24fba3b442a94c36d43edaf92721125c4972acd1d0c874de2caa5067f45ac52e60003256bc29c2cce9f568dcb36227d83697eb45c79d2faa4c030cf22a19356e5969013808606b1bd1026865b9dc45acc84c92ec0c7eaf0d7b1bc3c4efa934e87bd85313944797288c56e9826c4f05804f8e1d9c24ccdef34dfa331a900ab740fd38e3202495fa57cc8b594f6004a89d4c29ddc7640d3c4205e7b7d5cf8136e62b27812a3ca2b8caf9e4699ef2f28950c8372fef91020e19052da63375a285565bd083c9f7da83186cab465db2639286fc9639246be0e35ce049cd437e34b3f3bcc5430a7f80ee76ac9f412e0c00cda3b3b2abcd910aaecbad4205ce2fda704b87bf12fca732a4a0e5bf8d9a56160c011d2c646fe8e61dbd33531c4a01a0b3dc356ca2aade45b6947c16fda958cc245914b7a69061139586a4ee335a202e43f6f8f5529a524826084bb0ae1f0fd6691b3fa764cad81583de38d9029713225d374081b0460eb27f04636a3df741627ea1834682ed904f21e678953a8df84fd76688a070b112e7c155ec6a764e1cc65c352176a7a99d1540f373dc8c19d5d34237b5ae9a913000b7542f4565e6b171182ff2f8262d9297be13de7ac8af7b5d5201936565ba40300a91c08193a06cdb8c6b25690e46366885e1f4b3d3fbfe71ed336c593c195f9308010fea4b39745c1b1f122492227e5d18ddf2ee0c7229ccf691e79b693b707cb0980074e7c5af0c2d81e57663a61c75b3a299358802fc566544b0f5bb5ebefe4350580ec17932cba954e3c4dd8297eb1f5ffe5aeccb0384694531a6b703576e64cab22004bdd546ae224dfdde5374a8e96228d9b9da1c6b314870eff6b4a204866978c2000f206e19be0808324f30bdddc56253d960a651f21461c29fa7ee19232eb80290e00f173fd52e019a6209bdb1b9d73ee82470ed0004641a1d857b392f62558c6c62e80924a1f0af2e7c0ecf2f73340e75336a75cea78d9a84136170a12428598b9241c009f775e6369ab2c9ac327edf7effdb971a2669f6f6a51ad36fdbf57b487abd638800dbd44b0e853ccbf795d9e99f98b037d781d8fb7699f97ccef707e16a2e2a515001a915f975d859ccb15ede9893116dc2bb650e987d2ce9194b61568566b9b80038076974175f0a61b46395f955c6a9496fa10f1fb7e10274353157d19e725ff360100a442fdab45f23ac67df2db162e3fb8eabfe0e3b6018f977cd620f494b581dd00803d8d2a3a339c1e6ee78fa39dd22c491b9c8f7ba79b689d4a5e766bfce452de37802953c0d8644891d6124fd9c858e3d96cf72b9824515e6c109af2727a7fb046340048750d0254c37937af010767d6293d2d6420ac846520ddb29aab7a86a0ee47100030a30170c2cd42c4a8f6e44a501f081ee0c503b9eaf8f057379eac99c78a330a80aaa56426f3db86012f305cb0a97b3c04b99f3fcc136cd32f7005945e089b7e1800a591d143357093258873029bf8bd111aa8ca2c3e519cedcccdfea768d8d1a83d00f8d06a6c24ba4bd4e5f5832a7ae56548e557827835a6ba3ce9bbee932922411680523e50f59460f724fcf7a30a96264714c6d6fad1cb6bfb461584b864afd1851c002b1c5b8f937448d279fdd2ca50a192a6d0d40ab1bba96405e11e626879d7f5080025427bba38797a42b025d2da2becf4e46ca5ec72535c972f0926d14e031dd01900043ce657f9c8bc8643410970e5337f6c799ec1a2db00c7f15ebe78ee45d9d839809097703372e6177afc235b829b67394a2fb9f939a50740e615e2e474d4c5ba2f80d934d8c2be2b57bdf8a27b6c809c750e63c8dd23c3a49d17080e0ae88682ec2d0093ecc9849c369339b73d47ba788f1e56e11bca92de47efb008fca68ea25f7a06800476a2ed49ba2bbedb88eeeaee0906bc9ee42e6b01b6ce123fd03fc033e4a31c002c5d0efeddca0e45e6fb2cabda6caf905a9dfe3c29f8449acbefb10ec1f7803a80ca7ca31e9bfb413c77dc16ac35b214ca2530ca6e2cab9b8660889a0fa191993d80bc9ec92d67b5cbfa3349d86fa5f5b63b0625cf94df92198b9babb3b3a9384c2d809530d2140e8f131a6a12c00b18d6c878f7b9a89039e965ea70c595f223787631001e2c1b0001b58fd7b2f0ff3e5cd03a3830748e5a84791bdd7f5c15b41cc3521c0017f5e8147de461163e7748757d9a6c24d135cb48d5496f17584e5994d82b062801c6cfb5712d21587cefb96c3b78b6e220d1de0adc7dbb65553c550ece2d4b591e00018a0271d85fc9f27d211e997ea9f68de91b00c77922a46eaf57d8c42652e926300316856bdc3a97fad234dfd3b207652e9004de6eb1122d63cd0dfa8d3b9423112b80f0bef8dc12b850deb72848be509203d53f6fb2406dd3a88a933830ea0e316e1b0009f3c148542f637bd02a8a7d469eac5febf1e1ae3ad06e577f8d73cbd7099d28800220c6475838b6274502eb11ec13f20e8878000000000000000000000000000000002023c8f2046b63de3a823bc9f238a83308000000000000000000000000000000000000000000000000000000000000000000010c968b8af13a9802d227660efaf18e4ce1bde47bb22e8305127cda7269c67687000000006a473044022036e576ecad7e565a4e4c22e9bfe719a6ee6f9d18ecdcf64c4d3105a8d5992bbc02204429ef5130fe848f442e8702a8af601ef3d2222f2225b73cbd48e4bd038626570121020286da6d49ce5f2eb5b331bcb75e49c324a289c97522af311eb86637fb47c35bffffffff0181598647000000003f76a9141703d705b9b9abc7b9aa63d39dc43310cdc4ef1088ac2077d756ffd698c6a609cbc282cf79896ffb3a95509d65501815ac4c87cdd3000003300a10b49c00e1f50500000000ae0d03e41e019d3952b8290cf0d9af8ac059ed8940420f000000000083080f457e0bc14e3c4b5f2f8757f69088bd796d00e1f5050000000081febab938b1d43750d2ae61c707659da436d49300e1f50500000000abf24a8f14c1682dbb576c78ac617f97df7c208700e1f505000000007b00e724014d081539b1cbd4793b4bbd3166951600e1f50500000000dbb0aa50275f0adeded76cffee5f0be6c85d322f00c2eb0b00000000915dafd6627b386a41d7e0acb4c9853c13ad2ff500e1f505000000004bcec17355702d31a7b6c525b97989cf8201737100ca9a3b000000008d25c0e17e6c4f03c6a59f6f069fb9925ca2c2f300ca9a3b00000000e8a3bb520f9e8ba656d975a3fc24bdfbff52fdca1027000000000000dbb0aa50275f0adeded76cffee5f0be6c85d322f00e1f50500000000b86780f3eb0f6ad35b2b47aba3d08827ff8a680100e1f50500000000461cae6303e76a3ce1607bf5a9816c40ac05150400e1f5050000000057c3675f1bdc053455448fce5c7b2717c63d8aec00e1f5050000000051b04c613855317d4f9ba44f247c9c946a4de1a900e1f50500000000325b70d7b4c5fb8e6f5cacd50aef2c567be85fc28096980000000000cab0a0d77fe8b1e401b050751cbd4df7243a8aaa0065cd1d00000000cab0a0d77fe8b1e401b050751cbd4df7243a8aaa00e1f50500000000c4adda016fa838d51a8751192d0798dd3286d81100e1f5050000000081d658888568a8c758b547c34beba7ccf681f0b680778e06000000007973b7b90fb787b45ded42ede3ff4fbd5494c7ef40420f0000000000f078bd7b209360ec8a696230d8fba301e9e3a43640420f0000000000f078bd7b209360ec8a696230d8fba301e9e3a43600c2eb0b000000008753f6b946fd7d15ff76c8a94ce57db12ce6276200e1f505000000007c0584dd61c56d4eb34c038e11474c755f29c8a300e1f505000000000fb7d4f120cd5dfa40d9ef30df10c6c6be33919f00c2eb0b000000002f2cd70a25ff65a3f5de76bb6dcefdc147cf32e100e1f50500000000db47221bbbb5bd266de54e88d9abd470d7d213c140420f0000000000ca90170739f9a82466b1ec1c6021c1a8704e3e2b0065cd1d000000007ebb71c956b2551e453c4c1cc054acc9cdefa99700e1f5050000000072c8a85ec5939669a1b2cd46aafe352328e8c5bc00ca9a3b00000000ac89b775ed181240e0ffd5712b5328f3d90d7f8600e1f50500000000a442fa802dd506a60b1c76d09d0a21903234d4a400a3e1110000000083080f457e0bc14e3c4b5f2f8757f69088bd796d00e1f50500000000a89a40a2ad882a4ef372176d7a24821ec03e20ce00e1f505000000002c2b5b74d51f70d0caf79cdb5d4f9fed12cc7f0300f2052a010000006fe75d36cf0758836e08eb0a6142e99b2f6c75950065cd1d0000000054b42ace2407e99efb485b1610ff792f52d2c6ed005ed0b20000000062ee499c947af2e300bd9e7939328b403a670cf300ca9a3b0000000055895f4abd7c21614221842022ad4490789a7a5d00c2eb0b0000000043cac3abe80cd0bfd7d09fd7795ef11fe680f1f500e1f505000000001876f42e757ff0809eebe0aed1f6c550513f00c20046c32300000000971f63026eaa2a37ea2ccd9052db4af8dffcd697005ed0b20000000051dca1985794e2c543375ceef6bd3c6f629118e10094357700000000426751dc10e07fdef8f93b353175e5d6654a1f4780778e06000000004d83e55e829d9ad0a3a762992f8bf14f5937eaa100ca9a3b00000000c08b72dfd1c60cc7483571f2bbe82e5845a51deb00e1f50500000000cb814f3c4b94a700610457f2206156da423936cc00093d0000000000b7700cd87571d656684b13e523907836f41733ff00e1f50500000000092880d4513d66f18e43b79db6fe16ac077276b800c2eb0b00000000eb8b5c93616f6a1cb0440c74e7a1224420c53e1000e1f50500000000eb8b5c93616f6a1cb0440c74e7a1224420c53e1000c2eb0b00000000ae0d03e41e019d3952b8290cf0d9af8ac059ed8900e1f505000000008f975cb7b06d655339266d7c144af0383e86fc3780f0fa0200000000ebd9a21d7f912b73152b0462f35c4134d91ba4d680f0fa020000000020dc7c85570b8c5e55f3ee9efd15f13e22cff05600e1f505000000005da0b37987540f6c97310dd4669d913e93b3adf100a3e1110000000020dc7c85570b8c5e55f3ee9efd15f13e22cff05600c2eb0b00000000d1c7c089545929ae5f895f77fb2ac535a11ebd3000a3e11100000000b52f97451f2e6c8ae5aa7a526a4df4af84b1417e00879303000000002fe29806970f85d76dfd6366e7cde4d7dc9002ba00e1f5050000000075c54106a9261580a29eea7c7bfcce7ff6866e8600e1f50500000000307cd1a33df5cef93f803794177ea0c9299fec8200ab904100000000cfef56a99e4a9ec592c92eedd08010ff6701fdf600e1f50500000000e087e1a44b24166d4d5dd35862a013a87951b24b00e40b540200000035bd40eba7e3f8e62acb8eb6c98aba1b42b13c6c00a3e11100000000d2d28cc4b6d93e354f98726339fa1101ae3726f600e1f5050000000056f0eca6fa2f987f2ca74cab5f762cf5cdbf7a80002f685900000000600ad5f0d6c1f59cb63b150ac35484c775f790b50084d71700000000b86e9442460179e2d49dc20cd855aeda0740ac8600e1f50500000000829f70d80bd86fcc8ad67362ab49713d2f7c329200e1f50500000000b024bcf75e4423bfdd77373629460b8e2b49778800e1f50500000000b6e5394318e4417f13f82b43b0fb618c82e1087f40420f000000000066c5b57567055898dbed26f24591ba34fce130c200e1f50500000000b7700cd87571d656684b13e523907836f41733ff80778e0600000000b7700cd87571d656684b13e523907836f41733ff00e1f50500000000ac89b775ed181240e0ffd5712b5328f3d90d7f8600a3e11100000000fcf64a14aa2c0fa5a1c48f17480664f0ce3e55b80084d71700000000bb046032f140ee8aad5a6e85023d3f8001d5b67200ca9a3b00000000355ed2edf9b2157c8af235f1c6c9930fe34a389b00e1f505000000008f975cb7b06d655339266d7c144af0383e86fc3760b8f50500000000355ed2edf9b2157c8af235f1c6c9930fe34a389b00e1f50500000000ce50e0f04e02939f1ad3e5d36be0c0d7d82618990065cd1d000000003a0a802ee86e5b3e66c003df61bc676d246657f80065cd1d000000000a3de507d10605be3f4a036056fa81feeb4a760300e1f5050000000094344fd2b911e78c73dcafb6dc4b4dcf80d2216c00e1f50500000000a68024c1dbf4f217a4bcfeada192e8a2fb42702d0065cd1d00000000c84dfc1da62d26b5091a1c318179d59ab34b7e340065cd1d000000006023a805b0ef8e98ef5b67197c607e8dc303a9328091e3050000000073fcd81c25c93ab177b192bfd137583d47bc846f00e1f50500000000f0651e9736c7b7bdb3db181df30a60c5f7809d4f00e1f505000000006baaa3b4f6ddcbc3ce7a70010cff5b83c68fb5f500e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a43580084d7170000000093c47888c41e7b4d978e5c3a2fab1c16fc0d9ab100e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800286bee00000000e47bf49c21a9757140f8c320b4154cb716bdaa4db895980000000000395aa3268c4ee8497e7cd97c0efa8136a91e3f2700e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000a745d5a0f32780b6ae2feaff0eb80e53678a435800e1f50500000000829f70d80bd86fcc8ad67362ab49713d2f7c329200c2eb0b00000000d15c2445b899ea5ae72617398319ed5208ec3d1900e1f505000000003c340fd6f820cc9811d4cab3c3bdf10bd0ebab8200e1f50500000000fb7c854cfe96707d1ee57755f1520c7931c7115300e1f5050000000073fcd81c25c93ab177b192bfd137583d47bc846f0027b929000000003c340fd6f820cc9811d4cab3c3bdf10bd0ebab8200ca9a3b000000003c340fd6f820cc9811d4cab3c3bdf10bd0ebab829cc1eb0b000000003c340fd6f820cc9811d4cab3c3bdf10bd0ebab8200e1f5050000000033657a5cd94b997e4f5a9ac48d46791fedd1315400e1f50500000000d0e2da588000a4c2d4a9d8a405da640e7e0a81660065cd1d00000000ec12ec5ad573f6941761a2ab7799b2b03887ae6500e1f505000000004ce10788cbc0ed5bff3c52c18be2e0ee6db486fe0065cd1d00000000c1320be2adf6196801b15c337954e3e6caff4dbf00e1f50500000000fd14cf2ff622de4bf5c958908b78ffafd54eaaf300e1f50500000000ebeebe2a895de328bd8c912388b953daf8edc55000e1f5050000000062494f0e3af250c3d4f808231a782f6c4951160e00e1f50500000000bf99aefbaa5da63afdc3559cb1044e6226a65f2000e1f505000000008c9d16eef2a4f733cc9c1f1ad4360634467f843e00e1f50500000000779888965416d423b9e65d99887fa18f60a7e77700c2eb0b00000000f1c326c79db2c55f78c3a51e9c28ca47fdb571ce00e1f505000000006bd6370389a2ddc494d5252cfbcd1c749cc62e9d00e1f5050000000033065d25bc584fa3bec8e75dd89257aaf4448da100ab9041000000006e2da865b96c4a3a6f2b11b1a7b1eba2da02730370c0eb0b00000000e9165edf8ef3a6152becf94b1151ffe1a1576e6900e1f50500000000e93e0a0fe0e5d3d6997bc1623e2460b618b8767800e1f5050000000054ae5612f3f0451be5a660040cf0a6b06fd5948080f0fa0200000000e5cc3b3e801e9b6c55348bf0a2ee28c084980d7500c2eb0b000000006d89241409ec84484640a74f82a9509636822cdf00e1f505000000002aece900b5fca65e7e80de0c89577748ced3258d4054890000000000ae57bed73841e54939a9e532e8e677d2eb841781008c864700000000ae57bed73841e54939a9e532e8e677d2eb84178100e1f505000000008df7915d3a6a0fdeeba02c7d4f300c1f6708ddb400e1f50500000000d0e2da588000a4c2d4a9d8a405da640e7e0a816600e1f5050000000055895f4abd7c21614221842022ad4490789a7a5d00e1f50500000000a755ba1579bfd88ebb35be882584f747206e2e2200e1f50500000000c84dfc1da62d26b5091a1c318179d59ab34b7e3400e1f5050000000083f4c39a1b0993f17941c0195622701e67e6c5970065cd1d00000000c84dfc1da62d26b5091a1c318179d59ab34b7e3400e1f50500000000a08e24861b6a84051356006d340d472ff1e5657b00c2eb0b0000000055f0c6d9a2ea6ee779d6e68b20e2120d3710bc4d00e1f5050000000066f896fd5c0518f8ddf0eedc0c1333e7e57c5cda00e1f505000000003438667e021040f25639731a0a4ce1f0eccd7bb000e1f50500000000102e7c0e1a9b72401590cf325286fc9a7d261a2c00e1f505000000004f25ac9af94ec446558fd9c1a3a1fcad7d5c1a9d00e1f505000000009973dd1ce91f3fa84c98e968defc248f6a17412600e1f505000000004c80e9648fac379fdeb6c0d30872815f0cad71e500ca9a3b000000002d51b8e06bc4b81d5e3a134dc8dfd083224fab7000e1f5050000000039636d755c6eb5d5399c871c5406ce99be73227200e1f50500000000efc0a93b77dcdd24394731836e5c163222fe15c300e1f505000000007c96f2a31dbe26d6ea603e9b1ce95bc9a11a356c"
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    val scIdHex = "05612f21232789ea45e209b234f9ed0f24c89c7c28385c6d311bcd657450f3d1"
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = TestNetParams(scId.data, isNonCeasing = isNonCeasing)

    val blockHash = "0000734bfddee691153ac6dce0515823c974c9b1800365202db271d7619c6e07"
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params1, TestSidechainsVersionsManager(params1))

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block hash is different.", blockHash, mcblock.header.hashHex)
    assertTrue("Block must not contain MC2SCAggTx transaction.", mcblock.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock.data.topQualityCertificate.nonEmpty)
    assertEquals("Certificate BTs number is different", 156, mcblock.data.topQualityCertificate.get.backwardTransferOutputs.size)
  }
}
