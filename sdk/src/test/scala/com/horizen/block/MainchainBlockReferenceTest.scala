package com.horizen.block

import com.google.common.primitives.Ints
import com.horizen.box.RegularBox
import com.horizen.params.{MainNetParams, RegTestParams}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.horizen.proposition.PublicKey25519Proposition

import scala.io.Source
import scala.util.Try

class MainchainBlockReferenceTest extends JUnitSuite {

  @Test
  def blocksWithoutScSupportParsing(): Unit = {
    var mcBlockHex: String = null
    var mcBlockBytes: Array[Byte] = null
    var block: Try[MainchainBlockReference] = null


    val params = new MainNetParams()

    // Test 1: Block #473173
    // mcblock473173 data in RPC byte order: https://explorer.zen-solutions.io/api/rawblock/0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660
    mcBlockHex = Source.fromResource("mcblock473173").getLines().next()
    mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    block = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", block.isSuccess)
    assertEquals("Block Hash is different.", "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660", block.get.hashHex)
    assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.sidechainsMerkleRootsMap.isDefined)
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
    assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.sidechainsMerkleRootsMap.isDefined)
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
    assertFalse("Old Block occurred, SCMap expected to be undefined.", block.get.sidechainsMerkleRootsMap.isDefined)
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
  def blocksWithScSupportParsing_TxWithFT(): Unit = {
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000002"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    case class CustomParams(override val sidechainId: Array[Byte]) extends RegTestParams
    val params = CustomParams(scId.data)


    // Test: parse MC block with tx version -4 with 1 forward transfer.
    val mcBlockHex = Source.fromResource("mcblock_sc_support_regtest_ft").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block Hash is different.", "027285eac925e011a6a7644562a25319ce3af265400205a88c08558f39577953", mcblock.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "0e79aa08f24fc4af741d10ba6a21f0ba4d4cfb0d808497c58aaffac9c00e7407", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "6771345566a9ac7ff8c07dae6613c5118bdf76320bfa6e750ceec54f5201259d", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "75a8dbe7311509ba67fee413dfbc075b7c8ff83ec00db36ace02fa8c586550f2", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1568108710, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f09", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000d9e51e188dd519001d0390cb93d875058608d38cf06d953e56c056bf0039", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params))


    assertTrue("New Block occurred, SCMap expected to be defined.", mcblock.sidechainsMerkleRootsMap.isDefined)
    val scMap = mcblock.sidechainsMerkleRootsMap.get
    assertEquals("SidechainsMerkleRootsMap size is different.", 1, scMap.size)
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${scIdHex}.", scMap.contains(scId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${scIdHex} root hash is different.",
      "75a8dbe7311509ba67fee413dfbc075b7c8ff83ec00db36ace02fa8c586550f2", BytesUtils.toHexString(scMap(scId)))


    assertTrue("New Block occurred, MC2SCAggTx expected to be defined.", mcblock.sidechainRelatedAggregatedTransaction.isDefined)
    val aggTx = mcblock.sidechainRelatedAggregatedTransaction.get
    val newBoxes = aggTx.newBoxes()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx.unlockers().size())
    assertEquals("MC2SCAggTx new boxes size is different", 1, newBoxes.size())

    assertTrue("MC2SCAggTx first box expected to be a RegularBox.", newBoxes.get(0).isInstanceOf[RegularBox])
    val box = newBoxes.get(0).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 1000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000002add3")),
      box.proposition())
  }

  @Test
  def blocksWithScSupportParsing_TxWithScCreation(): Unit = {
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000001"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    case class CustomParams(override val sidechainId: Array[Byte]) extends RegTestParams
    val params = CustomParams(scId.data)


    // Test: parse MC block with tx version -4 with 1 sc creation output and 3 forward transfer.
    val mcBlockHex = Source.fromResource("mcblock_sc_support_regtest_sc_creation").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block Hash is different.", "0235ef3a41499b355d1720b51c4cfeb541aa8a19d3f5563df418db02cfb9d8fb", mcblock.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "049c0d3341c7ec2c55706509d91e1131182c93a12f3e80ac5d847d89f9c389ad", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "ed81a220975a9d7fe72bd869acb83164121905c1347aa7f28eda286c87b92a57", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "b6d1738baa99dc33490b84e33e09c1c023076a3d59a9241c45f3bd59748620ed", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1568126422, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f09", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000f3460c670529fe4ea20202b05686d44fe959f7c51c403bc8eec22ff80023", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params))


    assertTrue("New Block occurred, SCMap expected to be defined.", mcblock.sidechainsMerkleRootsMap.isDefined)
    val scMap = mcblock.sidechainsMerkleRootsMap.get
    assertEquals("SidechainsMerkleRootsMap size is different.", 1, scMap.size)
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${scIdHex}.", scMap.contains(scId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${scIdHex} root hash is different.",
      "b6d1738baa99dc33490b84e33e09c1c023076a3d59a9241c45f3bd59748620ed", BytesUtils.toHexString(scMap(scId)))


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
  def blocksWithScSupportParsing_MultipleScOutputs(): Unit = {
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000001"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    val anotherScIdHex = "0000000000000000000000000000000000000000000000000000000000000002"
    val anotherScId = new ByteArrayWrapper(BytesUtils.fromHexString(anotherScIdHex))

    case class CustomParams(override val sidechainId: Array[Byte]) extends RegTestParams
    val params = CustomParams(scId.data)


    // Test: parse MC block with tx version -4 with 1 sc creation output and 3 forward transfer.
    val mcBlockHex = Source.fromResource("mcblock_sc_support_regtest_multiple_sc_outputs").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertEquals("Block Hash is different.", "0bf87140636d69c34fc7af88aa4b914a8f6a02f6f07327ef36f922455c7af176", mcblock.hashHex)
    assertEquals("Block version = 536870912 expected.", 536870912, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "0ab4cdc19f1c58abeb7e2cd4f436c79ef6adfb217e624011f62f973bce7856ea", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "d1e2fc18683b06ec83558e39f1a74cb22c52b9e78703d5525ec080adeedcdf42", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "4f4346916048cc9da2c520e6bd63b5638ca4605a73e358c39e8c0a5fbe2aecb6", BytesUtils.toHexString(mcblock.header.hashSCMerkleRootsMap))
    assertEquals("Block creation time is different", 1568126527, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f09", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000787c8e57be7e2691e28e1a62bebc9ad46d93fc87168ff60f80592950002c", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params))


    assertTrue("New Block occurred, SCMap expected to be defined.", mcblock.sidechainsMerkleRootsMap.isDefined)
    val scMap = mcblock.sidechainsMerkleRootsMap.get
    assertEquals("SidechainsMerkleRootsMap size is different.", 2, scMap.size)
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${scIdHex}.", scMap.contains(scId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${scIdHex} root hash is different.",
      "fb4e636e6cb15b264dde58f0b7443b53e6552cf370f9170f1f69c02a24c4a4c4", BytesUtils.toHexString(scMap(scId)))
    assertTrue(s"SidechainsMerkleRootsMap expected to contain sc id '${anotherScIdHex}.", scMap.contains(anotherScId))
    assertEquals(s"SidechainsMerkleRootsMap sc id '${anotherScIdHex} root hash is different.",
      "df920b03d583b19401534e9f2f74c4bed1c6df242d7ad5ce68f4d64a6777e98d", BytesUtils.toHexString(scMap(anotherScId)))


    assertTrue("New Block occurred, MC2SCAggTx expected to be defined.", mcblock.sidechainRelatedAggregatedTransaction.isDefined)
    val aggTx = mcblock.sidechainRelatedAggregatedTransaction.get
    val newBoxes = aggTx.newBoxes()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx.unlockers().size())
    assertEquals("MC2SCAggTx new boxes size is different", 3, newBoxes.size())

    assertTrue("MC2SCAggTx first box expected to be a RegularBox.", newBoxes.get(0).isInstanceOf[RegularBox])
    var box = newBoxes.get(0).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 1000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add1")),
      box.proposition())

    assertTrue("MC2SCAggTx second box expected to be a RegularBox.", newBoxes.get(1).isInstanceOf[RegularBox])
    box = newBoxes.get(1).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 2000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add2")),
      box.proposition())

    assertTrue("MC2SCAggTx third box expected to be a RegularBox.", newBoxes.get(2).isInstanceOf[RegularBox])
    box = newBoxes.get(2).asInstanceOf[RegularBox]
    assertEquals("MC2SCAggTx first box value is different", 3000000, box.value())
    assertEquals("MC2SCAggTx first box proposition is different",
      new PublicKey25519Proposition(BytesUtils.fromHexString("000000000000000000000000000000000000000000000000000000000000add3")),
      box.proposition())
  }
}
