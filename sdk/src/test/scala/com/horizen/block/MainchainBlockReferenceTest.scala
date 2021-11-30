package com.horizen.block

import com.google.common.primitives.Ints
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import com.horizen.params.{MainNetParams, RegTestParams, TestNetParams}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.{Ignore, Test}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail => jFail}
import org.scalatest.junit.JUnitSuite
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}

import scala.io.Source
import scala.util.{Failure, Success, Try}

class MainchainBlockReferenceTest extends JUnitSuite {

  @Test
  def blocksWithoutScSupportParsing(): Unit = {
    var mcBlockHex: String = null
    var mcBlockBytes: Array[Byte] = null
    var block: Try[MainchainBlockReference] = null


    val params = MainNetParams()

    // Test 1: Block #473173
    // mcblock473173_mainnet data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660
    mcBlockHex = Source.fromResource("mcblock473173_mainnet").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660", block.get.header.hashHex)
    assertFalse("Old Block occurred, proof of existence expected to be undefined.", block.get.data.existenceProof.isDefined)
    assertFalse("Old Block occurred, proof of absence expected to be undefined.", block.get.data.absenceProof.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertFalse("Old Block occurred, Certificate expected to be undefined.", block.get.data.topQualityCertificate.isDefined)
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
    block = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000011aec26c29306d608645a644a592e44add2988a9d156721423e714e0", block.get.header.hashHex)
    assertFalse("Old Block occurred, proof of existence expected to be undefined.", block.get.data.existenceProof.isDefined)
    assertFalse("Old Block occurred, proof of absence expected to be undefined.", block.get.data.absenceProof.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertFalse("Old Block occurred, Certificate expected to be undefined.", block.get.data.topQualityCertificate.isDefined)
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
    block = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000009b9f4a9f2abe5cd129421df969d1eb1b02d3fd685ab0781939ead07", block.get.header.hashHex)
    assertFalse("Old Block occurred, proof of existence expected to be undefined.", block.get.data.existenceProof.isDefined)
    assertFalse("Old Block occurred, proof of absence expected to be undefined.", block.get.data.absenceProof.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertFalse("Old Block occurred, Certificate expected to be undefined.", block.get.data.topQualityCertificate.isDefined)
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
  def blockWithoutSidechains(): Unit = {
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000000" // valid FieldElement in BigEndian
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    val params = RegTestParams(scId.data)

    // Test: parse MC block with tx version -4 without sidechain related stuff.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_empty_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

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
  def blockWithSingleSidechainCreation(): Unit = {
    // Test: parse MC block with tx version -4 with single sc creation output.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_1_sc_creation").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for the sidechain mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex = "2c446b6d305ca7aef113a2655a4e9b0ab6ce1cfb13533d0e7c2c7aac5cf85dcb" // BE
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

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
  def blockWithSingleForwardTransfer(): Unit = {
    // Test: parse MC block with tx version -4 with single forward transfer output.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_1_ft").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for the sidechain mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex = "2d62224e739318fe16ef3e57bdc03b00003892a99e019d1e2a242ca7176a4aed" // BE
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

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
  def blockWith3Sidechains(): Unit = {
    // Test: parse MC block with tx version -4 with 3 FTs for 3 different sidechains.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_3_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)


    // Test 1: Check for the leftmost sidechain mentioned in the block.
    // We expect to get a MainchainBlockReference with AggTx and proof of presence.
    val scIdHex1 = "37bfaf6f433503c16ce6c70fb13fb97090cb474034e2230fda23cb86476f2a9f"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

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

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

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

    val params3 = RegTestParams(scId3.data)

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3)

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
    val params4 = RegTestParams(scId4.data)

    val mcblockTry4 = MainchainBlockReference.create(mcBlockBytes, params4)

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
    val params5 = RegTestParams(scId5.data)

    val mcblockTry5 = MainchainBlockReference.create(mcBlockBytes, params5)

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
    val params6 = RegTestParams(scId6.data)

    val mcblockTry6 = MainchainBlockReference.create(mcBlockBytes, params6)

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
    val params7 = RegTestParams(scId7.data)

    val mcblockTry7 = MainchainBlockReference.create(mcBlockBytes, params7)

    assertTrue("Block expected to be parsed", mcblockTry7.isSuccess)
    val mcblock7 = mcblockTry7.get

    assertTrue("Block expected to be semantically valid", mcblock7.semanticValidity(params7).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock7.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock7.data.topQualityCertificate.isEmpty)
    assertTrue("Block must not contain proof of existence.", mcblock7.data.existenceProof.isEmpty)
    assertTrue("Block must contain proof of absence.", mcblock7.data.absenceProof.isDefined)
  }

  @Test
  def blockWithCertificateWithoutBTs(): Unit = {
    // Test: parse MC block with tx version -4 with a single certificate without backward transfers.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_without_bts").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for existing sidechain
    val scIdHex1 = "043a46e2831bdf80657f39e6031c62e56b5d0b9399cc2f33ccc7b514ce8a7237"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.topQualityCertificate.isDefined)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock1.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

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
  def blockWithCertificateWithBTs(): Unit = {
    // Test: parse MC block with tx version -4 with a single certificate with backward transfers.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_with_bts").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for existing sidechain
    val scIdHex1 = "043a46e2831bdf80657f39e6031c62e56b5d0b9399cc2f33ccc7b514ce8a7237"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.topQualityCertificate.isDefined)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock1.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

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
  def blockWithMultipleCertificates(): Unit = {
    // Test: parse MC block with tx version -4 with backward transfer.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_2_certificates").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)


    // Test 1: Check for existing sidechain
    val scIdHex1 = "1aabf493f8605e78177d71c0aef3b251b2b9aaa66715fc86729a4e86ce566d8e"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain top quality certificate.", mcblock1.data.topQualityCertificate.isDefined)
    assertEquals("Block must contain 1 low quality certificate.", 1, mcblock1.data.lowerCertificateLeaves.size)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence.", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

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
  def blockWithCertificateWithCustomFieldAndBitvector(): Unit = {
    // Test: parse MC block with tx version -4 with a single certificate with 3 custom fields and 1 bitvector field.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_certificate_with_custom_fields").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Test 1: Check for existing sidechain
    val scIdHex1 = "338a555811e24411880e99fbfeeb50e1775d041399e57ec0381a817758f55dcd"
    val scId1 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex1))) // LE

    val params1 = RegTestParams(
      scId1.data,
      scCreationBitVectorCertificateFieldConfigs = Seq(
        new CustomBitvectorElementsConfig(127, 255)
      )
    )

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.topQualityCertificate.isDefined)
    assertEquals("Block must contain certificate with 3 cert field elements.", 3, mcblock1.data.topQualityCertificate.get.fieldElementCertificateFields.length)
    assertEquals("Block must contain certificate with 1 cert bitvector.", 1, mcblock1.data.topQualityCertificate.get.bitVectorCertificateFields.length)
    assertTrue("Block must not-contain lower quality certificate leaves.", mcblock1.data.lowerCertificateLeaves.isEmpty)
    assertTrue("Block must contain proof of existence.", mcblock1.data.existenceProof.isDefined)
    assertTrue("Block must not contain proof of absence", mcblock1.data.absenceProof.isEmpty)


    // Test 2: Check for non-existing sidechain
    val scIdHex2 = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId2 = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex2))) // LE

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

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
  def blockWithSingleMBTR(): Unit = {
    // Test: parse MC block with tx version -4 with single MBTR output.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_1_mbtr").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Check for the sidechain NOT mentioned in the block.
    // NOTE: we don't allow MBTRs for the SDK based sidechains
    // Use dummy valid FE sc id
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data)

    val blockHash = "0c93b7438ec27250bb4b6a8995366225a8437ea91c642db9c4990416561a258f"
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block hash is different.", blockHash, mcblock.header.hashHex)
    assertTrue("Block must not contain transaction.", mcblock.data.sidechainRelatedAggregatedTransaction.isEmpty)

  }

  @Test
  def blockWithCSWs(): Unit = {
    // Test: parse MC block with tx version -4 with CSW inputs.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_with_csws").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    // Check for the sidechain NOT mentioned in the block.
    // NOTE: we don't allow CSWs for the SDK based sidechains
    // Use dummy valid FE sc id
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000000"
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex))) // LE

    val params1 = RegTestParams(scId.data)

    val blockHash = "07e10cc67304769b37b02d35536410294deaf559a42e4d5fae33c3b84e6433b4"
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block hash is different.", blockHash, mcblock.header.hashHex)
    assertTrue("Block must not contain transaction.", mcblock.data.sidechainRelatedAggregatedTransaction.isEmpty)
  }
}