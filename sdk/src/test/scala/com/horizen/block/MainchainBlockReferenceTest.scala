package com.horizen.block

import com.google.common.primitives.Ints
import com.horizen.box.RegularBox
import com.horizen.params.{MainNetParams, RegTestParams, TestNetParams}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail => jFail}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.horizen.proposition.PublicKey25519Proposition

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
    assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.data.sidechainsMerkleRootsMap.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "0000000009572f35ecc6e319216b29046fdb6695ad93b3e5d77053285df4af03", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "5bf368ee4fc02f055e8ca5447a21b9758e6435b3214bc10b55f533cc9b3d1a6d", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashSCMerkleRootsMap))
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
    assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.data.sidechainsMerkleRootsMap.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "00000000106843ee0119c6db92e38e8655452fd85f638f6640475e8c6a3a3582", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "493232e7d362852c8e3fe6aa5a48d6f6e01220f617c258db511ee2386b6362ea", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashSCMerkleRootsMap))
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
    assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.data.sidechainsMerkleRootsMap.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertEquals("Block version = 536870912 expected.", 536870912, block.get.header.version)
    assertEquals("Hash of previous block is different.", "0000000071076828a1d738dfde576b21ac4e28998ae7a026f631e57d7561a28b", BytesUtils.toHexString(block.get.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "7169f926344ff99dbee02ed2429481bbbc0b84cb4773c1dcaee20458e0d0437a", BytesUtils.toHexString(block.get.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash expected to be zero bytes.", BytesUtils.toHexString(params.zeroHashBytes), BytesUtils.toHexString(block.get.header.hashSCMerkleRootsMap))
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

    assertEquals("Block Hash is different.", "002061121ddac5438c6de656d046e80d3a044d06bac87c2501afc69eaff6fafa", mcblock.header.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "006d20987ff5322196a1e5f530d52ed5c0b44750f4d1997daeda6a6c128ee14f", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "68bc6ede2f15ebb01dd6c9ff2c6dfcbe9ad824f4ec82264e4d38a8860af1d065", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "0000000000000000000000000000000000000000000000000000000000000000", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1572469894, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "1f6c7b5d", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000cefd46238a6721302931021ee4b444e54c55adad68229872877b49c10012", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    mcblock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Block expected to be semantically valid, instead: ${e.getMessage}")
    }


    assertFalse("New Block occurred without SC mentioned inside, SCMap expected to be undefined.",
      mcblock.data.sidechainsMerkleRootsMap.isDefined)
  }

  @Test
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

    assertEquals("Block Hash is different.", "0b9b3f9e2d3e136eaeec9c9d51f445a6f49005950cc4d91f957b20e3f50e36e7", mcblock.header.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "05518c30b2346285a57c7494ef884fb7dd8e4c5807166533cb36f0877b952bf9", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "82e0729fae0d7217086583c5eafda535b8a2f62af65425647808eee6c608dd06", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "5e3add878e57f80c2d14822ecd50d50cd210cb22f2707739e40247ee04507126", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1571158246, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f03", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "00008838e18206aa16b8c4a62321248207bfd0687bb0cd39e8e10da28684002a", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    mcblock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Block expected to be semantically valid, instead: ${e.getMessage}")
    }


    assertTrue("New Block occurred, SCMap expected to be defined.", mcblock.data.sidechainsMerkleRootsMap.isDefined)
    val scMap = mcblock.data.sidechainsMerkleRootsMap.get
    assertEquals("SidechainsMerkleRootsMap size is different.", 1, scMap.size)
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${scIdHex}.", scMap.contains(scId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${scIdHex} root hash is different.",
      "e08426e2eb3e760037397e3cf7acd507ed7b5139e23ad34c17ebc623a122ef25", BytesUtils.toHexString(scMap(scId)))


    assertTrue("New Block occurred, MC2SCAggTx expected to be defined.", mcblock.data.sidechainRelatedAggregatedTransaction.isDefined)
    val aggTx = mcblock.data.sidechainRelatedAggregatedTransaction.get
    val newBoxes = aggTx.newBoxes()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx.unlockers().size())
    assertEquals("MC2SCAggTx new boxes size is different", 3 + 1, //where +1 due hardcoded Forger box created in com.horizen.transaction.mainchain.SidechainCreation.getHardcodedGenesisForgerBox
      newBoxes.size())

    assertTrue("MC2SCAggTx first box expected to be a RegularBox.", newBoxes.get(0 + 1).isInstanceOf[RegularBox]) //where +1 due hardcoded Forer box created in com.horizen.transaction.mainchain.SidechainCreation.getHardcodedGenesisForgerBox
    var box = newBoxes.get(0 + 1).asInstanceOf[RegularBox] //where +1 due hardcoded Forger box created in com.horizen.transaction.mainchain.SidechainCreation.getHardcodedGenesisForgerBox
    assertEquals("MC2SCAggTx first box value is different", 10000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add1")),
      box.proposition())

    assertTrue("MC2SCAggTx second box expected to be a RegularBox.", newBoxes.get(1 + 1).isInstanceOf[RegularBox]) //where +1 is hardcoded Forer box created in com.horizen.transaction.mainchain.SidechainCreation.getHardcodedGenesisForgerBox
    box = newBoxes.get(1 + 1).asInstanceOf[RegularBox] //where +1 due hardcoded Forger box created in com.horizen.transaction.mainchain.SidechainCreation.getHardcodedGenesisForgerBox
    assertEquals("MC2SCAggTx first box value is different", 20000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add2")),
      box.proposition())

    assertTrue("MC2SCAggTx third box expected to be a RegularBox.", newBoxes.get(2 + 1).isInstanceOf[RegularBox]) //where +1 is hardcoded Forer box created in com.horizen.transaction.mainchain.SidechainCreation.getHardcodedGenesisForgerBox
    box = newBoxes.get(2 + 1).asInstanceOf[RegularBox] //where +1 due hardcoded Forger box created in com.horizen.transaction.mainchain.SidechainCreation.getHardcodedGenesisForgerBox
    assertEquals("MC2SCAggTx first box value is different", 30000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add3")),
      box.proposition())
  }

  @Test
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

    assertEquals("Block Hash is different.", "0aac2834707dffeea13d33bc655c1fd80f163540cdf221dd2b9eb8b889d1e18f", mcblock.header.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "0092d667863a6cb73dea30c69de18b0596725adaa9df1aaa6c467c873c14850e", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "e15ebc9e1dd5e7a5137b46823da18818f47ccc2d50725cfc899d5d01fa91a982", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "b1c03111552c8f8ee37143baa1b7ff00636511b0189b02a2a917e6110efb58c3", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1571158330, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f02", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000e2812ac29d6651d39bf27aa6e23d85b4783842c3511ea45f72ff875a0009", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    mcblock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Block expected to be semantically valid, instead: ${e.getMessage}")
    }


    assertTrue("New Block occurred, SCMap expected to be defined.", mcblock.data.sidechainsMerkleRootsMap.isDefined)
    val scMap = mcblock.data.sidechainsMerkleRootsMap.get
    assertEquals("SidechainsMerkleRootsMap size is different.", 2, scMap.size)
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${scIdHex}.", scMap.contains(scId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${scIdHex} root hash is different.",
      "801b054e584c6173628dd7f15fd385d22511e11d85318931b1433070146ec4bc", BytesUtils.toHexString(scMap(scId)))
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${anotherScIdHex}.", scMap.contains(anotherScId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${anotherScIdHex} root hash is different.",
      "5473dd00c8cecfd2de59f0432e6853484e11a2a46ad8a276c588d9185b8b1749", BytesUtils.toHexString(scMap(anotherScId)))


    assertTrue("New Block occurred, MC2SCAggTx expected to be defined.", mcblock.data.sidechainRelatedAggregatedTransaction.isDefined)
    val aggTx = mcblock.data.sidechainRelatedAggregatedTransaction.get
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
}
