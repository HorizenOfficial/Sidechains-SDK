package com.horizen.block

import com.google.common.primitives.Ints
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
    assertFalse("Old Block occurred, MProof expected to be undefined.", block.get.data.mProof.isDefined)
    assertFalse("Old Block occurred, proof of no data for left neighbour expected to be undefined.", block.get.data.proofOfNoData._1.isDefined)
    assertFalse("Old Block occurred, proof of no data for right neighbour expected to be undefined.", block.get.data.proofOfNoData._2.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertFalse("Old Block occurred, Certificate expected to be undefined.", block.get.data.withdrawalEpochCertificate.isDefined)
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
    assertFalse("Old Block occurred, MProof expected to be undefined.", block.get.data.mProof.isDefined)
    assertFalse("Old Block occurred, proof of no data for left neighbour expected to be undefined.", block.get.data.proofOfNoData._1.isDefined)
    assertFalse("Old Block occurred, proof of no data for right neighbour expected to be undefined.", block.get.data.proofOfNoData._2.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertFalse("Old Block occurred, Certificate expected to be undefined.", block.get.data.withdrawalEpochCertificate.isDefined)
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
    assertFalse("Old Block occurred, MProof expected to be undefined.", block.get.data.mProof.isDefined)
    assertFalse("Old Block occurred, proof of no data for left neighbour expected to be undefined.", block.get.data.proofOfNoData._1.isDefined)
    assertFalse("Old Block occurred, proof of no data for right neighbour expected to be undefined.", block.get.data.proofOfNoData._2.isDefined)
    assertFalse("Old Block occurred, MC2SCAggTx expected to be undefined.", block.get.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertFalse("Old Block occurred, Certificate expected to be undefined.", block.get.data.withdrawalEpochCertificate.isDefined)
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
  @Ignore //Will be fixed and enabled again in future
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
    assertEquals("ScTxCommitment hash is different.", "0000000000000000000000000000000000000000000000000000000000000000", BytesUtils.toHexString(mcblock.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1572469894, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "1f6c7b5d", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000cefd46238a6721302931021ee4b444e54c55adad68229872877b49c10012", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    mcblock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"Block expected to be semantically valid, instead: ${e.getMessage}")
    }


    assertFalse("New Block occurred without SC mentioned inside, MProof expected to be undefined.",
      mcblock.data.mProof.isDefined)
    assertFalse("New Block occurred without SC mentioned inside, MC2SCAggTx expected to be undefined.",
      mcblock.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertFalse("New Block occurred without SC mentioned inside, Certificate expected to be undefined.",
      mcblock.data.withdrawalEpochCertificate.isDefined)
  }

  @Test
  def blockWithoutSidechains(): Unit = {
    val scIdHex = "00000000000000000000000000000000000000000000000000000000deadbeef"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))

    val params = RegTestParams(scId.data)

    // Test: parse MC block with tx version -4 without sidechain related stuff.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_empty_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcblockTry = MainchainBlockReference.create(mcBlockBytes, params)

    assertTrue("Block expected to be parsed", mcblockTry.isSuccess)
    val mcblock = mcblockTry.get

    assertTrue("Block must not contain transaction.", mcblock.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock.data.mProof.isEmpty)
    assertTrue("Block must not contain proof for left neighbour.", mcblock.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbour.", mcblock.data.proofOfNoData._2.isEmpty)

    assertEquals("Block Hash is different.", "08c776943ee8b41e9b27bdcfcdfb508b36cd6bc04338e07e45310814c4cb7013", mcblock.header.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock.header.version)
    assertEquals("Hash of previous block is different.", "02d7cd7df9aabfe60fa334c4a5cec864c1e62661bdac6c67d06fe3b4bf07826e", BytesUtils.toHexString(mcblock.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "77dabfb11d94e29833477ccd3d6b09b582e4d0020c59c3de2b09d54bd6839120", BytesUtils.toHexString(mcblock.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "0000000000000000000000000000000000000000000000000000000000000000", BytesUtils.toHexString(mcblock.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1584793090, mcblock.header.time)
    assertEquals("Block PoW bits is different.", "200f0f02", BytesUtils.toHexString(Ints.toByteArray(mcblock.header.bits)))
    assertEquals("Block nonce is different.", "0000a2c6b51d11ca740936a888c9ade2539df60ce1593506711ebb5a8ef10007", BytesUtils.toHexString(mcblock.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params.EquihashSolutionLength, mcblock.header.solution.length)
    assertTrue("Block expected to be semantically valid", mcblock.semanticValidity(params).isSuccess)
  }

  // fix test and uncomment
  @Test
  def blockCreate3Sidechains(): Unit = {
    // Test: parse MC block with tx version -4 with creation of 3 sidechains.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_create_3_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    //Check for sidechain 1
    val scIdHex1 = "8e87cf97de8f5f565c5cffeebb8f3db1abc6386bd6cb7143bf2e7c753587b023"
    val scId1 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex1))

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertTrue("Block must contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock1.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock1.data.mProof.isDefined)
    assertTrue("Block must not contain proof for left neighbour.", mcblock1.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbour.", mcblock1.data.proofOfNoData._2.isEmpty)

    //Check sidechain creation transaction.
    val aggTx1 = mcblock1.data.sidechainRelatedAggregatedTransaction.get
    val outputs1 = aggTx1.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx1.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs1.size())

    assertTrue("Output type must be SidechainCreation.", outputs1.get(0).isInstanceOf[SidechainCreation])

    assertEquals("Block Hash is different.", "0b1bd6c4d1b49ff890fb944c3d0368b08f7f35c4cd9e9e5863e2ca02d70422bc", mcblock1.header.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock1.header.version)
    assertEquals("Hash of previous block is different.", "0177ebdc383cfd354f309f9359cc7f2a1498d646255c47a086e958c09e51ab73", BytesUtils.toHexString(mcblock1.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "8a0e1948966e88212b2d8dee86ed5a4f52776046127cdb2e6374eb9d55163d83", BytesUtils.toHexString(mcblock1.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "28c429cd02099d838f7b0f9e46f62bca6a028e4a03353cb5fe89705ce42705e1", BytesUtils.toHexString(mcblock1.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1592902409, mcblock1.header.time)
    assertEquals("Block PoW bits is different.", "200f0f03", BytesUtils.toHexString(Ints.toByteArray(mcblock1.header.bits)))
    assertEquals("Block nonce is different.", "0000c3246648c0ba7a1891106a95dea59769f42d038aee1e9b910cd20d10001a", BytesUtils.toHexString(mcblock1.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params1.EquihashSolutionLength, mcblock1.header.solution.length)

    //Check for sidechain 2
    val scIdHex2 = "efad97d0a9ae9d846d11b7f93ba1f4329f8d4cd06708f054f880fbf7d78d9276"
    val scId2 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex2))

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock2.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock2.data.mProof.isDefined)
    assertTrue("Block must not contain proof for left neighbour.", mcblock2.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbour.", mcblock2.data.proofOfNoData._2.isEmpty)

    //Check sidechain creation transaction.
    val aggTx2 = mcblock2.data.sidechainRelatedAggregatedTransaction.get
    val outputs2 = aggTx2.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx2.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs2.size())

    //Check for sidechain 3
    val scIdHex3 = "fe8e50b23fd4f5381d1c6926d390a0aa42620def790f197632ebd0c18ecd59b2"
    val scId3 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex3))

    val params3 = RegTestParams(scId3.data)

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3)

    assertTrue("Block expected to be parsed", mcblockTry3.isSuccess)
    val mcblock3 = mcblockTry3.get

    assertTrue("Block expected to be semantically valid", mcblock3.semanticValidity(params3).isSuccess)

    assertTrue("Block must contain transaction.", mcblock3.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock3.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock3.data.mProof.isDefined)
    assertTrue("Block must not contain proof for left neighbour.", mcblock3.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbour.", mcblock3.data.proofOfNoData._2.isEmpty)

    //Check sidechain creation transaction.
    val aggTx3 = mcblock3.data.sidechainRelatedAggregatedTransaction.get
    val outputs3 = aggTx3.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx3.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs3.size())
  }

  // to do: fix test and uncomment
  @Test
  def blockForwardTransfer2Sidechains(): Unit = {

    // Test: parse MC block with tx version -4 with forward transfers to two sidechains and no operation for one sidechain.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_forward_transfer_2_sidechains").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    //Check for sidechain 1
    val scIdHex1 = "8e87cf97de8f5f565c5cffeebb8f3db1abc6386bd6cb7143bf2e7c753587b023"
    val scId1 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex1))

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertEquals("Block Hash is different.", "0439d55792e68a0631feb970d33862408f11f045ced81ed24cdc307b5b75c1de", mcblock1.header.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock1.header.version)
    assertEquals("Hash of previous block is different.", "0b1bd6c4d1b49ff890fb944c3d0368b08f7f35c4cd9e9e5863e2ca02d70422bc", BytesUtils.toHexString(mcblock1.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "11bc587a319cb2a6ec25f3abd33848dee934b667fb034575e9dcf38ec06f094a", BytesUtils.toHexString(mcblock1.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "ac71a6407ed198df4f058addc3efd2a9201d782d877f253048b4cdafbaa495f2", BytesUtils.toHexString(mcblock1.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1592903375, mcblock1.header.time)
    assertEquals("Block PoW bits is different.", "200f0f02", BytesUtils.toHexString(Ints.toByteArray(mcblock1.header.bits)))
    assertEquals("Block nonce is different.", "0000d4a60f07f5789b4734ace8b4aad2779a0abf5d88c8381b1cf57318990020", BytesUtils.toHexString(mcblock1.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params1.EquihashSolutionLength, mcblock1.header.solution.length)

    assertTrue("Block must contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock1.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock1.data.mProof.isDefined)
    assertTrue("Block must not contain proof for left neighbour.", mcblock1.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbour.", mcblock1.data.proofOfNoData._2.isEmpty)

    //Check forward transfer transaction.
    val aggTx1 = mcblock1.data.sidechainRelatedAggregatedTransaction.get
    val outputs1 = aggTx1.mc2scTransactionsOutputs()
    assertEquals("MC2SCAggTx unlockers size is different", 0, aggTx1.unlockers().size())
    assertEquals("MC2SCAggTx outputs size is different", 1,outputs1.size())

    assertTrue("Output type must be SidechainCreation.", outputs1.get(0).isInstanceOf[ForwardTransfer])

    //Check for sidechain 2
    val scIdHex2 = "efad97d0a9ae9d846d11b7f93ba1f4329f8d4cd06708f054f880fbf7d78d9276"
    val scId2 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex2))

    val params2 = RegTestParams(scId2.data)

    val mcblockTry2 = MainchainBlockReference.create(mcBlockBytes, params2)

    assertTrue("Block expected to be parsed", mcblockTry2.isSuccess)
    val mcblock2 = mcblockTry2.get

    assertTrue("Block expected to be semantically valid", mcblock2.semanticValidity(params2).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock2.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock2.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock2.data.mProof.isEmpty)
    assertTrue("Block must contain proof for left neighbour.", mcblock2.data.proofOfNoData._1.isDefined)
    assertTrue("Block must contain proof for right neighbour.", mcblock2.data.proofOfNoData._2.isDefined)

    //Check for sidechain 3
    val scIdHex3 = "fe8e50b23fd4f5381d1c6926d390a0aa42620def790f197632ebd0c18ecd59b2"
    val scId3 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex3))

    val params3 = RegTestParams(scId3.data)

    val mcblockTry3 = MainchainBlockReference.create(mcBlockBytes, params3)

    assertTrue("Block expected to be parsed", mcblockTry3.isSuccess)
    val mcblock3 = mcblockTry3.get

    assertTrue("Block expected to be semantically valid", mcblock3.semanticValidity(params3).isSuccess)

    assertTrue("Block must contain transaction.", mcblock3.data.sidechainRelatedAggregatedTransaction.isDefined)
    assertTrue("Block must not contain certificate.", mcblock3.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must contain proof.", mcblock3.data.mProof.isDefined)
    assertTrue("Block must not contain proof for left neighbour.", mcblock3.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbour.", mcblock3.data.proofOfNoData._2.isEmpty)

  }

  @Test
  def blockBackwardTransfer(): Unit = {
    // Test: parse MC block with tx version -4 with backward transfer from 1 sidechain.
    val mcBlockHex = Source.fromResource("new_mc_blocks/mc_block_backward_transfer").getLines().next()
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)

    //Check for sidechain 1
    val scIdHex1 = "a4d2cbc61a77c33c1a8a7f3828ddda9a76d80a9787f6790bc0d95683f5053165"
    val scId1 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex1))

    val params1 = RegTestParams(scId1.data)

    val mcblockTry1 = MainchainBlockReference.create(mcBlockBytes, params1)

    assertTrue("Block expected to be parsed", mcblockTry1.isSuccess)
    val mcblock1 = mcblockTry1.get

    assertTrue("Block expected to be semantically valid", mcblock1.semanticValidity(params1).isSuccess)

    assertEquals("Block Hash is different.", "0a5dfb43088de2ac3c3ab49c65a67a05b092fd30eaa4b22f99a66a1d9ddee90a", mcblock1.header.hashHex)
    assertEquals("Block version = 3 expected.", 3, mcblock1.header.version)
    assertEquals("Hash of previous block is different.", "00d1d22440e361af06fcf8a6873df751f8d9486cfd0bab538d417d55c9071fd9", BytesUtils.toHexString(mcblock1.header.hashPrevBlock))
    assertEquals("Merkle root hash is different.", "c0e0e0d7539425d875e72918106453d6d3a2222c8bb11c5a8c364b141b2621ba", BytesUtils.toHexString(mcblock1.header.hashMerkleRoot))
    assertEquals("SCMap Merkle root hash is different.", "cf2fa2a6cc60eb1b1b6daa8374c09833e38d515dc38f4b3f3fadc3f576f2469d", BytesUtils.toHexString(mcblock1.header.hashScTxsCommitment))
    assertEquals("Block creation time is different", 1592906662, mcblock1.header.time)
    assertEquals("Block PoW bits is different.", "200f0f01", BytesUtils.toHexString(Ints.toByteArray(mcblock1.header.bits)))
    assertEquals("Block nonce is different.", "000038033df472612ad5bd2a1866399b0d1e88ace5b12b94da8859029253001d", BytesUtils.toHexString(mcblock1.header.nonce))
    assertEquals("Block equihash solution length is wrong.", params1.EquihashSolutionLength, mcblock1.header.solution.length)

    assertTrue("Block must not contain transaction.", mcblock1.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must contain certificate.", mcblock1.data.withdrawalEpochCertificate.isDefined)
    assertTrue("Block must contain proof.", mcblock1.data.mProof.isDefined)
    assertTrue("Block must not contain proof for left neighbour.", mcblock1.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must not contain proof for right neighbour.", mcblock1.data.proofOfNoData._2.isEmpty)

    //Check for non-existing sidechain, leftmost
    val scIdHex4 = "00000000000000000000000000000000000000000000000000000000deadbeea"
    val scId4 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex4))

    val params4 = RegTestParams(scId4.data)

    val mcblockTry4 = MainchainBlockReference.create(mcBlockBytes, params4)

    assertTrue("Block expected to be parsed", mcblockTry4.isSuccess)
    val mcblock4 = mcblockTry4.get

    assertTrue("Block expected to be semantically valid", mcblock4.semanticValidity(params4).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock4.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock4.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock4.data.mProof.isEmpty)
    assertTrue("Block must not contain proof for left neighbour.", mcblock4.data.proofOfNoData._1.isEmpty)
    assertTrue("Block must contain proof for right neighbour.", mcblock4.data.proofOfNoData._2.isDefined)

    //Check for non-existing sidechain, rightmost
    val scIdHex5 = "ff000000000000000000000000000000000000000000000000000000deadbeff"
    val scId5 = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex5))

    val params5 = RegTestParams(scId5.data)

    val mcblockTry5 = MainchainBlockReference.create(mcBlockBytes, params5)

    assertTrue("Block expected to be parsed", mcblockTry5.isSuccess)
    val mcblock5 = mcblockTry5.get

    assertTrue("Block expected to be semantically valid", mcblock5.semanticValidity(params5).isSuccess)

    assertTrue("Block must not contain transaction.", mcblock5.data.sidechainRelatedAggregatedTransaction.isEmpty)
    assertTrue("Block must not contain certificate.", mcblock5.data.withdrawalEpochCertificate.isEmpty)
    assertTrue("Block must not contain proof.", mcblock5.data.mProof.isEmpty)
    assertTrue("Block must contain proof for left neighbour.", mcblock5.data.proofOfNoData._1.isDefined)
    assertTrue("Block must not contain proof for right neighbour.", mcblock5.data.proofOfNoData._2.isEmpty)
  }
}