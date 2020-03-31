package com.horizen.block

import com.google.common.primitives.Ints
import com.horizen.box.RegularBox
import com.horizen.params.{MainNetParams, RegTestParams, TestNetParams}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.{Ignore, Test}
import org.scalatest.junit.JUnitSuite
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}

import scala.io.Source
import scala.util.Try

class MainchainBlockReferenceTest extends JUnitSuite {

  @Test
  def blocksWithoutScSupportParsing(): Unit = {
    var mcBlockHex: String = null
    var mcBlockBytes: Array[Byte] = null
    var block: Try[MainchainBlockReference] = null


    val params = MainNetParams()

    // Test 1: Block #473173
    // mcblock473173 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660
    mcBlockHex = Source.fromResource("mcblock473173").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660", block.get.hashHex)
    //assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.sidechainsMerkleRootsMap.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "0000000009572f35ecc6e319216b29046fdb6695ad93b3e5d77053285df4af03", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "5bf368ee4fc02f055e8ca5447a21b9758e6435b3214bc10b55f533cc9b3d1a6d", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1551432137, block.get.header.time)
    assertEquals("Block PoW bits is different.", "1c2abb60", BytesUtils.toHexString(Ints.toByteArray(block.get.header.bits)))
    assertEquals("Block nonce is different.", "00000000000000000000000000030000000000009921008000000000c7cf0410", BytesUtils.toHexString(block.get.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, block.get.header.solution.length)
    assertTrue("Block expected to be semantically valid", block.get.semanticValidity(params))



    // Test 2: Block #501173
    // mcblock501173 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000011aec26c29306d608645a644a592e44add2988a9d156721423e714e0
    mcBlockHex = Source.fromResource("mcblock501173").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000011aec26c29306d608645a644a592e44add2988a9d156721423e714e0", block.get.hashHex)
    //assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.sidechainsMerkleRootsMap.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "00000000106843ee0119c6db92e38e8655452fd85f638f6640475e8c6a3a3582", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "493232e7d362852c8e3fe6aa5a48d6f6e01220f617c258db511ee2386b6362ea", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1555658453, block.get.header.time)
    assertEquals("Block PoW bits is different.", "1c1bbecc", BytesUtils.toHexString(Ints.toByteArray(block.get.header.bits)))
    assertEquals("Block nonce is different.", "0000000000000000000000000019000000000000751d00600000000000000000", BytesUtils.toHexString(block.get.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, block.get.header.solution.length)
    assertTrue("Block expected to be semantically valid", block.get.semanticValidity(params))


    // Test 3: Block #273173
    // mcblock273173 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000009b9f4a9f2abe5cd129421df969d1eb1b02d3fd685ab0781939ead07
    mcBlockHex = Source.fromResource("mcblock273173").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000009b9f4a9f2abe5cd129421df969d1eb1b02d3fd685ab0781939ead07", block.get.hashHex)
    //assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.sidechainsMerkleRootsMap.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "0000000071076828a1d738dfde576b21ac4e28998ae7a026f631e57d7561a28b", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "7169f926344ff99dbee02ed2429481bbbc0b84cb4773c1dcaee20458e0d0437a", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1521052551, block.get.header.time)
    assertEquals("Block PoW bits is different.", "1d010d77", BytesUtils.toHexString(Ints.toByteArray(block.get.header.bits)))
    assertEquals("Block nonce is different.", "000000000000000000000000cfcbffd9de586ff80e928e9b83e86c3c8c580000", BytesUtils.toHexString(block.get.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, block.get.header.solution.length)
    assertTrue("Block expected to be semantically valid", block.get.semanticValidity(params))
  }

  @Test
  @Ignore
  def blockWithSCSupportParsingOnTestnet3(): Unit = {
    val scIdHex = "1111111111111111111111111111111111111111111111111111111111111111"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))
    val params = TestNetParams(scId.data)

    // Test 1: Block #530292 on testnet3 (with sc support)
    val mcBlockHex = Source.fromResource("mcblock530290_testnet3").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block Hash is different.", "002061121ddac5438c6de656d046e80d3a044d06bac87c2501afc69eaff6fafa", mcblock.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "006d20987ff5322196a1e5f530d52ed5c0b44750f4d1997daeda6a6c128ee14f", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "68bc6ede2f15ebb01dd6c9ff2c6dfcbe9ad824f4ec82264e4d38a8860af1d065", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "0000000000000000000000000000000000000000000000000000000000000000", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1572469894, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "1f6c7b5d", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000cefd46238a6721302931021ee4b444e54c55adad68229872877b49c10012", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params))


    //assertFalse("New Block occurred without SC mentioned inside, SCMap expected to be undefined.",
    //  mcblock.sidechainsMerkleRootsMap.isDefined)
  }

  @Test
  @Ignore
  def blocksWithScSupportParsing_TxWithScCreationAndFt(): Unit = {
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000001"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))
    val params = RegTestParams(scId.data)


    // Test: parse MC block with tx version -4 with 1 sc creation output and 3 forward transfer.
    val mcBlockHex = Source.fromResource("mcblock_sc_support_regtest_sc_creation").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block Hash is different.", "0b9b3f9e2d3e136eaeec9c9d51f445a6f49005950cc4d91f957b20e3f50e36e7", mcblock.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "05518c30b2346285a57c7494ef884fb7dd8e4c5807166533cb36f0877b952bf9", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "82e0729fae0d7217086583c5eafda535b8a2f62af65425647808eee6c608dd06", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "5e3add878e57f80c2d14822ecd50d50cd210cb22f2707739e40247ee04507126", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1571158246, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f03", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "00008838e18206aa16b8c4a62321248207bfd0687bb0cd39e8e10da28684002a", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params))


    /*
    assertTrue("New Block occurred, SCMap expected to be defined.", mcblock.sidechainsMerkleRootsMap.isDefined)
    val scMap = mcblock.sidechainsMerkleRootsMap.get
    assertEquals("SidechainsMerkleRootsMap size is different.", 1, scMap.size)
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${scIdHex}.", scMap.contains(scId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${scIdHex} root hash is different.",
      "e08426e2eb3e760037397e3cf7acd507ed7b5139e23ad34c17ebc623a122ef25", BytesUtils.toHexString(scMap(scId)))*/


    assertTrue("New Block occurred, MC2SCAggTx expected to be defined.", mcblock.sidechainRelatedAggregatedTransaction.isDefined)
    val aggTx = mcblock.sidechainRelatedAggregatedTransaction.get
    val newBoxes = aggTx.newBoxes()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx.unlockers().size())
    assertEquals("MC2SCAggTx new boxes size is different", 3, newBoxes.size())

    assertTrue("MC2SCAggTx first box expected to be a RegularBox.", newBoxes.get(0).isInstanceOf[RegularBox])
    var box = newBoxes.get(0).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 10000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add1")),
      box.proposition())

    assertTrue("MC2SCAggTx second box expected to be a RegularBox.", newBoxes.get(1).isInstanceOf[RegularBox])
    box = newBoxes.get(1).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 20000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add2")),
      box.proposition())

    assertTrue("MC2SCAggTx third box expected to be a RegularBox.", newBoxes.get(2).isInstanceOf[RegularBox])
    box = newBoxes.get(2).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 30000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add3")),
      box.proposition())
  }

  @Test
  @Ignore
  def blocksWithScSupportParsing_MultipleScOutputs(): Unit = {
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000001"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    val anotherScIdHex = "0000000000000000000000000000000000000000000000000000000000000002"
    val anotherScId = new ByteArrayWrapper(BytesUtils.fromHexString(anotherScIdHex))
    val params = RegTestParams(scId.data)


    // Test: parse MC block with tx version -4 with 1 sc creation output and 3 forward transfer.
    val mcBlockHex = Source.fromResource("mcblock_sc_support_regtest_multiple_sc_outputs").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block Hash is different.", "0aac2834707dffeea13d33bc655c1fd80f163540cdf221dd2b9eb8b889d1e18f", mcblock.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "0092d667863a6cb73dea30c69de18b0596725adaa9df1aaa6c467c873c14850e", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "e15ebc9e1dd5e7a5137b46823da18818f47ccc2d50725cfc899d5d01fa91a982", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "b1c03111552c8f8ee37143baa1b7ff00636511b0189b02a2a917e6110efb58c3", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1571158330, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f02", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000e2812ac29d6651d39bf27aa6e23d85b4783842c3511ea45f72ff875a0009", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params))


    /*
    assertTrue("New Block occurred, SCMap expected to be defined.", mcblock.sidechainsMerkleRootsMap.isDefined)
    val scMap = mcblock.sidechainsMerkleRootsMap.get
    assertEquals("SidechainsMerkleRootsMap size is different.", 2, scMap.size)
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${scIdHex}.", scMap.contains(scId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${scIdHex} root hash is different.",
      "801b054e584c6173628dd7f15fd385d22511e11d85318931b1433070146ec4bc", BytesUtils.toHexString(scMap(scId)))
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${anotherScIdHex}.", scMap.contains(anotherScId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${anotherScIdHex} root hash is different.",
      "5473dd00c8cecfd2de59f0432e6853484e11a2a46ad8a276c588d9185b8b1749", BytesUtils.toHexString(scMap(anotherScId)))*/


    assertTrue("New Block occurred, MC2SCAggTx expected to be defined.", mcblock.sidechainRelatedAggregatedTransaction.isDefined)
    val aggTx = mcblock.sidechainRelatedAggregatedTransaction.get
    val newBoxes = aggTx.newBoxes()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx.unlockers().size())
    assertEquals("MC2SCAggTx new boxes size is different", 3, newBoxes.size())

    assertTrue("MC2SCAggTx first box expected to be a RegularBox.", newBoxes.get(0).isInstanceOf[RegularBox])
    var box = newBoxes.get(0).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 101000000L, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add1")),
      box.proposition())

    assertTrue("MC2SCAggTx second box expected to be a RegularBox.", newBoxes.get(1).isInstanceOf[RegularBox])
    box = newBoxes.get(1).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 202000000L, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add2")),
      box.proposition())

    assertTrue("MC2SCAggTx third box expected to be a RegularBox.", newBoxes.get(2).isInstanceOf[RegularBox])
    box = newBoxes.get(2).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 303000000L, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add3")),
      box.proposition())
  }


  @Test
  def blockWithoutSidechains(): Unit = {
    val scIdHex = "00000000000000000000000000000000000000000000000000000000deadbeef"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    val params = RegTestParams(scId.data)

    // Test: parse MC block with tx version -4 with 1 sc creation output and 3 forward transfer.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_empty_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertTrue("Block must not contain transaction.", mcblock.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock.backwardTransferCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock.mproof.isEmpty)
    assertTrue("Block must not contain proof for left neighbor.", mcblock.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock.proofOfNoData._2.isEmpty)

    assertEquals("Block Hash is different.", "08c776943ee8b41e9b27bdcfcdfb508b36cd6bc04338e07e45310814c4cb7013", mcblock.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "02d7cd7df9aabfe60fa334c4a5cec864c1e62661bdac6c67d06fe3b4bf07826e", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "77dabfb11d94e29833477ccd3d6b09b582e4d0020c59c3de2b09d54bd6839120", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "0000000000000000000000000000000000000000000000000000000000000000", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1584793090, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f02", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000a2c6b51d11ca740936a888c9ade2539df60ce1593506711ebb5a8ef10007", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params))
  }

  @Test
  def blockCreate3Sidechains(): Unit = {
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_create_3_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    //Check for sidechain 1
    val scIdHex1 = "00000000000000000000000000000000000000000000000000000000deadbeeb"
    val scId1 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex1))

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1))

    assertTrue("Block must contain transaction.", mcblock1.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock1.backwardTransferCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock1.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock1.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock1.proofOfNoData._2.isEmpty)

    //Check sidechain creation transaction.
    val aggTx1 = mcblock1.sidechainRelatedAggregatedTransaction.get
    val outputs1 = aggTx1.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx1.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs1.size())

    assertTrue("Output type must be SidechainCreation.", outputs1.get(0).isInstanceOf[SidechainCreation])

    assertEquals("Block Hash is different.", "00e1777df542e275f7c3469433b69e64e0e28e9a321ff8cb11ce60f8076216df", mcblock1.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock1.header.version)
    assertEquals("Hash of previous block is different.", "0c651c15429b1fc71adce8e6185dcfc2fa666e4fa2f4713b4b0df9a16c71b931", BytesUtils.toHexString(mcblock1.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "ff4acb1387b009ab30c361ab783bf9fe8ff0a8a5d9ecb0bf229bc10176f7e496", BytesUtils.toHexString(mcblock1.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "7e8d32d91749d1ae791a733086dae60f91e015ec2829a12d8d4734c23bf26800", BytesUtils.toHexString(mcblock1.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1585146607, mcblock1.header.time)
    assertEquals("Block PoW bits is different.", "200f0f03", BytesUtils.toHexString(Ints.toByteArray(mcblock1.header.bits)))
    assertEquals("Block nonce is different.", "0000fae657715e5907939b04eb7ca5db08698c0e0d1ce6a4ef2a1b110b990013", BytesUtils.toHexString(mcblock1.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params1.EquihashSolutionLength, mcblock1.header.solution.length)

    //Check for sidechain 2
    val scIdHex2 = "00000000000000000000000000000000000000000000000000000000deadbeed"
    val scId2 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex2))

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2))

    assertTrue("Block must contain transaction.", mcblock2.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock2.backwardTransferCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock2.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock2.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock2.proofOfNoData._2.isEmpty)

    //Check sidechain creation transaction.
    val aggTx2 = mcblock2.sidechainRelatedAggregatedTransaction.get
    val outputs2 = aggTx2.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx2.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs2.size())

    //Check for sidechain 3
    val scIdHex3 = "00000000000000000000000000000000000000000000000000000000deadbeef"
    val scId3 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex3))

    val params3 = RegTestParams(scId3.data)

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3)

    assertTrue("Block expected to be parsed", mcblockTry3.isSuccess)
    val mcblock3 = mcblockTry3.get

    assertTrue("Block expected to be semantically valid", mcblock3.semanticValidity(params3))

    assertTrue("Block must contain transaction.", mcblock3.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock3.backwardTransferCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock3.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock3.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock3.proofOfNoData._2.isEmpty)

    //Check sidechain creation transaction.
    val aggTx3 = mcblock3.sidechainRelatedAggregatedTransaction.get
    val outputs3 = aggTx3.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx3.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs3.size())

  }

  @Test
  def blockForwardTransfer2Sidechains(): Unit = {
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_forward_transfer_2_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    //Check for sidechain 1
    val scIdHex1 = "00000000000000000000000000000000000000000000000000000000deadbeeb"
    val scId1 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex1))

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1))

    assertEquals("Block Hash is different.", "0414e0c9b2c8baf21fafd176eaa0abb6d97356f28ef8933435cdc9ce8229e3ea", mcblock1.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock1.header.version)
    assertEquals("Hash of previous block is different.", "0b22fa5ce2f08f07248e9e3dd2b58380aa8818881f8ce397ef0c438ab073e6ee", BytesUtils.toHexString(mcblock1.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "e95e2448f0b00526953b6b5450a16ab87060e16be03455bc06d0f0d4a1406458", BytesUtils.toHexString(mcblock1.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "02c92e7b380a5e23af0d12f9f82766ed6fb3b3c4e4d980df21825937859ab531", BytesUtils.toHexString(mcblock1.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1584792721, mcblock1.header.time)
    assertEquals("Block PoW bits is different.", "200f0f02", BytesUtils.toHexString(Ints.toByteArray(mcblock1.header.bits)))
    assertEquals("Block nonce is different.", "00009bdc936625730375447fdf136cc73b22f94b5fa59c9e79bbb32ba6e8000f", BytesUtils.toHexString(mcblock1.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params1.EquihashSolutionLength, mcblock1.header.solution.length)

    assertTrue("Block must contain transaction.", mcblock1.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock1.backwardTransferCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock1.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock1.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock1.proofOfNoData._2.isEmpty)

    //Check forward transfer transaction.
    val aggTx1 = mcblock1.sidechainRelatedAggregatedTransaction.get
    val outputs1 = aggTx1.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx1.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs1.size())

    assertTrue("Output type must be SidechainCreation.", outputs1.get(0).isInstanceOf[ForwardTransfer])

    //Check for sidechain 2
    val scIdHex2 = "00000000000000000000000000000000000000000000000000000000deadbeed"
    val scId2 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex2))

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2))

    assertTrue("Block must not contain transaction.", mcblock2.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock2.backwardTransferCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock2.mproof.isEmpty)
    assertTrue("Block must contain proof for left neighbor.", mcblock2.proofOfNoData._1.isDefined)
    assertTrue("Block must contain proof for right neighbor.", mcblock2.proofOfNoData._2.isDefined)

    //Check for sidechain 3
    val scIdHex3 = "00000000000000000000000000000000000000000000000000000000deadbeef"
    val scId3 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex3))

    val params3 = RegTestParams(scId3.data)

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3)

    assertTrue("Block expected to be parsed", mcblockTry3.isSuccess)
    val mcblock3 = mcblockTry3.get

    assertTrue("Block expected to be semantically valid", mcblock3.semanticValidity(params3))

    assertTrue("Block must contain transaction.", mcblock3.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock3.backwardTransferCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock3.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock3.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock3.proofOfNoData._2.isEmpty)

  }

  @Test
  def blockBackwardTransfer3Sidechains(): Unit = {
    // Test: parse MC block with tx version -4 with 1 sc creation output and 3 forward transfer.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_forward_transfer_3_backward_transfer").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    //Check for sidechain 1
    val scIdHex1 = "00000000000000000000000000000000000000000000000000000000deadbeeb"
    val scId1 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex1))

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1))

    assertEquals("Block Hash is different.", "02e4f55eb6caddf3c8c67423ef8f4775bf32b9e4a5ba6cdde3c0ae9d2ee1b754", mcblock1.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock1.header.version)
    assertEquals("Hash of previous block is different.", "05c67454a79d2538af806b3f93e1535912092f80061edd036b572720f3222245", BytesUtils.toHexString(mcblock1.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "0df6e04ec862b2ccd54f6c234e33e985f86dd0a28156c16e2d79c19b710bc993", BytesUtils.toHexString(mcblock1.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "bb742377c11e086559b85106879c5bdfe91920c3c76e0adb8e13d6724a6f4f86", BytesUtils.toHexString(mcblock1.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1585220559, mcblock1.header.time)
    assertEquals("Block PoW bits is different.", "200f0f02", BytesUtils.toHexString(Ints.toByteArray(mcblock1.header.bits)))
    assertEquals("Block nonce is different.", "000075a2fd1cc118e12822c8db2ef49a36070bdfdb2889d9066c28d903580026", BytesUtils.toHexString(mcblock1.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params1.EquihashSolutionLength, mcblock1.header.solution.length)

    assertTrue("Block must not contain transaction.", mcblock1.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.backwardTransferCertificate.isDefined)
    assertTrue("Block must contain proof.", mcblock1.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock1.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock1.proofOfNoData._2.isEmpty)

    //Check for sidechain 2
    val scIdHex2 = "00000000000000000000000000000000000000000000000000000000deadbeed"
    val scId2 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex2))

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1))

    assertTrue("Block must contain transaction.", mcblock2.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must contain certificate.", mcblock2.backwardTransferCertificate.isDefined)
    assertTrue("Block must contain proof.", mcblock2.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock2.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock2.proofOfNoData._2.isEmpty)

    //Check for sidechain 3
    val scIdHex3 = "00000000000000000000000000000000000000000000000000000000deadbeef"
    val scId3 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex3))

    val params3 = RegTestParams(scId3.data)

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3)

    assertTrue("Block expected to be parsed", mcblockTry3.isSuccess)
    val mcblock3 = mcblockTry3.get

    assertTrue("Block expected to be semantically valid", mcblock3.semanticValidity(params3))

    assertTrue("Block must not contain transaction.", mcblock3.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock3.backwardTransferCertificate.isDefined)
    assertTrue("Block must contain proof.", mcblock3.mproof.isDefined)
    assertTrue("Block must not contain proof for left neighbor.", mcblock3.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbor.", mcblock3.proofOfNoData._2.isEmpty)

    //Check for non-existing sidechain, leftmost
    val scIdHex4 = "00000000000000000000000000000000000000000000000000000000deadbeea"
    val scId4 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex4))

    val params4 = RegTestParams(scId4.data)

    val mcblockTry4 = MainchainBlockReference.create(mcBlockBytes, params4)

    assertTrue("Block expected to be parsed", mcblockTry4.isSuccess)
    val mcblock4 = mcblockTry4.get

    assertTrue("Block expected to be semantically valid", mcblock4.semanticValidity(params4))

    assertTrue("Block must not contain transaction.", mcblock4.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock4.backwardTransferCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock4.mproof.isEmpty)
    assertTrue("Block must not contain proof for left neighbor.", mcblock4.proofOfNoData._1.isEmpty)
    assertTrue("Block must contain proof for right neighbor.", mcblock4.proofOfNoData._2.isDefined)

    //Check for non-existing sidechain, rightmost
    val scIdHex5 = "00000000000000000000000000000000000000000000000000000000deadbeff"
    val scId5 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex5))

    val params5 = RegTestParams(scId5.data)

    val mcblockTry5 = MainchainBlockReference.create(mcBlockBytes, params5)

    assertTrue("Block expected to be parsed", mcblockTry5.isSuccess)
    val mcblock5 = mcblockTry5.get

    assertTrue("Block expected to be semantically valid", mcblock5.semanticValidity(params5))

    assertTrue("Block must not contain transaction.", mcblock5.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock5.backwardTransferCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock5.mproof.isEmpty)
    assertTrue("Block must contain proof for left neighbor.", mcblock5.proofOfNoData._1.isDefined)
    assertTrue("Block must not contain proof for right neighbor.", mcblock5.proofOfNoData._2.isEmpty)
  }
}