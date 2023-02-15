package com.horizen.account.block

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock.calculateReceiptRoot
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.fixtures.{AccountBlockFixture, EthereumTransactionFixture, ForgerAccountFixture}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import com.horizen.account.receipt.{Bloom, EthereumConsensusDataReceipt}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.FeeUtils.{GAS_LIMIT, INITIAL_BASE_FEE}
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, Ommer, SidechainBlock}
import com.horizen.evm.interop.EvmLog
import com.horizen.fixtures._
import com.horizen.fixtures.sidechainblock.generation.SidechainBlocksGenerator.txGen.getRandomBoxId
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.VrfSecretKey
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.utils.{BytesUtils, TestSidechainsVersionsManager}
import com.horizen.validation._
import com.horizen.vrf.{VrfGeneratedDataProvider, VrfOutput}
import org.junit.Assert.{assertEquals, assertTrue, fail => jFail}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.util.{ModifierId, idToBytes}

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import java.math.BigInteger
import java.util.Random
import scala.io.Source
import scala.util.{Failure, Success, Try}

class AccountBlockTest
  extends JUnitSuite
  with CompanionsFixture
  with EthereumTransactionFixture
  with AccountBlockFixture
{

  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
  val sidechainBlockSerializer = new AccountBlockSerializer(sidechainTransactionsCompanion)

  val random = new java.util.Random(123L)

  val params: NetworkParams = MainNetParams()
  val mcBlockRef1: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473173_mainnet").getLines().next()), params, TestSidechainsVersionsManager()).get
  val mcBlockRef2: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473174_mainnet").getLines().next()), params, TestSidechainsVersionsManager()).get
  val mcBlockRef3: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473175_mainnet").getLines().next()), params, TestSidechainsVersionsManager()).get
  val mcBlockRef4: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473176_mainnet").getLines().next()), params, TestSidechainsVersionsManager()).get

  val seed: Long = 11L
  val parentId: ModifierId = getRandomBlockId(seed)

  val generatedDataSeed = 908
  val vrfGenerationPrefix = "AccountBlockTest"

  if (false) {
    VrfGeneratedDataProvider.updateVrfSecretKey(vrfGenerationPrefix, generatedDataSeed)
    VrfGeneratedDataProvider.updateVrfProofAndOutput(vrfGenerationPrefix, generatedDataSeed)
  }

  val vrfKeyPair: Option[(VrfSecretKey, VrfPublicKey)] = {
    val secret: VrfSecretKey = VrfGeneratedDataProvider.getVrfSecretKey(vrfGenerationPrefix, generatedDataSeed)
    val publicKey: VrfPublicKey = secret.publicImage()
    Option((secret, publicKey))
  }

  val (accountPayment, forgerMetadata) = ForgerAccountFixture.generateForgerAccountData(seed, vrfKeyPair)
  val vrfProof: VrfProof = VrfGeneratedDataProvider.getVrfProof(vrfGenerationPrefix, generatedDataSeed)
  val vrfOutput: VrfOutput = VrfGeneratedDataProvider.getVrfOutput(generatedDataSeed)

  // Create Block with Txs, MainchainBlockReferencesData, MainchainHeaders and Ommers
  // Note: block is semantically invalid because Block contains the same MC chain as Ommers, but it's ok for serialization test
  val block: AccountBlock = createBlock(
    sidechainTransactions = Seq(
      getEoa2EoaLegacyTransaction,
      getUnsignedEoa2EoaLegacyTransaction
    ),
    receipts = Seq(
      new EthereumConsensusDataReceipt(2, ReceiptStatus.SUCCESSFUL.id, BigInteger.valueOf(112233), Array.empty[EvmLog]),
      new EthereumConsensusDataReceipt(2, ReceiptStatus.FAILED.id, BigInteger.valueOf(22334455), Array.empty[EvmLog])
    ),
    mainchainBlockReferencesData = Seq(mcBlockRef1.data, mcBlockRef2.data),
    mainchainHeaders = Seq(mcBlockRef2.header, mcBlockRef3.header, mcBlockRef4.header),
    ommers = generateOmmersSeq(parentId, 123444L,
      Seq(
        (Seq(mcBlockRef1.data), Seq(mcBlockRef1.header, mcBlockRef2.header)),
        (Seq(), Seq()),
        (Seq(mcBlockRef2.data, mcBlockRef3.data, mcBlockRef4.data), Seq(mcBlockRef3.header, mcBlockRef4.header))
      ),
      rnd = random
    ),
    rnd = random
  )

  @Test
  def testToJson(): Unit = {
    val sb = createBlock()

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(sb)

    val node : JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    try {
      val id = node.path("id").asText()
      assertEquals("Block id json value must be the same.",
        BytesUtils.toHexString(idToBytes(sb.id)), id)
    }catch {
      case _: Exception => fail("Block id doesn't not found in json.")
    }
    try {
      val parentId = node.path("parentId").asText()
      assertEquals("Block parentId json value must be the same.",
        BytesUtils.toHexString(idToBytes(sb.parentId)), parentId)
    }catch {
      case _: Exception => fail("Block parentId doesn't not found in json.")
    }
    try {
      val timestamp = node.path("timestamp").asLong()
      assertEquals("Block timestamp json value must be the same.",
        sb.timestamp, timestamp)
    }catch {
      case _: Exception => fail("Block timestamp doesn't not found in json.")
    }

  }

  @Test
  def serialization(): Unit = {
    val blockBytes = sidechainBlockSerializer.toBytes(block)

    val deserializedBlockTry = sidechainBlockSerializer.parseBytesTry(blockBytes)
    assertTrue("Block deserialization failed.", deserializedBlockTry.isSuccess)

    val deserializedBlock = deserializedBlockTry.get
    assertEquals("Deserialized Block transactions are different.", block.transactions, deserializedBlock.transactions)
    assertEquals("Deserialized Block version is different.", block.version, deserializedBlock.version)
    assertEquals("Deserialized Block mainchain reference data seq is different.", block.mainchainBlockReferencesData, deserializedBlock.mainchainBlockReferencesData)
    assertEquals("Deserialized Block mainchain headers are different.", block.mainchainHeaders, deserializedBlock.mainchainHeaders)
    assertEquals("Deserialized Block ommers are different.", block.ommers, deserializedBlock.ommers)
    assertEquals("Deserialized Block id is different.", block.id, deserializedBlock.id)


    // Set to true to regenerate regression data
    if(false) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/accountblock_hex"))
      out.write(BytesUtils.toHexString(blockBytes))
      out.close()
    }

    // Test 2: try to deserialize broken bytes.
    assertTrue("AccountBlockSerializer expected to be not parsed due to broken data.", sidechainBlockSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serializationRegression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("accountblock_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }


    val deserializedBlockTry = sidechainBlockSerializer.parseBytesTry(bytes)
    assertTrue("AccountBlock expected to be parsed.", deserializedBlockTry.isSuccess)

    val deserializedBlock = deserializedBlockTry.get
    assertEquals("Deserialized Block transactions are different.", block.transactions, deserializedBlock.transactions)
    assertEquals("Deserialized Block version is different.", block.version, deserializedBlock.version)
    assertEquals("Deserialized Block mainchain reference data seq is different.", block.mainchainBlockReferencesData, deserializedBlock.mainchainBlockReferencesData)
    assertEquals("Deserialized Block mainchain headers are different.", block.mainchainHeaders, deserializedBlock.mainchainHeaders)
    assertEquals("Deserialized Block ommers are different.", block.ommers, deserializedBlock.ommers)
    assertEquals("Deserialized Block id is different.", block.id, deserializedBlock.id)
  }

  @Test
  def semanticValidity(): Unit = {
    // Test1: AccountBlock with no Txs, MainchainBlockReferencesData, MainchainHeaders and Ommers must to be valid.
    var validBlock = createBlock()
    validBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) =>
        jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }

    // Test2: AccountBlock with invalid AccountBlockHeader must to be invalid.
    var invalidBlock = invalidateBlock(
      validBlock,
      headerOpt = Some(validBlock.header.copy(signature = new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH))))
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidSidechainBlockHeaderException], e.getClass)
    }


    // Test 3: AccountBlock with semantically valid AccountBlockHeader, and consistent SidechainTransaction must be valid.
    validBlock = createBlock(sidechainTransactions = Seq(
      getContractDeploymentEip1559Transaction,
      getEoa2EoaEip155LegacyTransaction
    )
    )
    validBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }

    // Test 4: AccountBlock with semantically valid AccountBlockHeader, but NOT consistent SidechainTransaction must be invalid.
    // 1 tx is missed -> list is not consistent to AccountBlockHeader
    invalidBlock = invalidateBlock(
      validBlock,
      sidechainTransactionsOpt = Some(Seq(
         getEoa2EoaLegacyTransaction
      ))
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }

    // No Txs in body, but AccountBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      sidechainTransactionsOpt = Some(Seq()) // No txs
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }


    // Test 5: AccountBlock with semantically valid AccountBlockHeader, and consistent MainchainBlockReferencesData seq must be valid.
    validBlock = createBlock(mainchainBlockReferencesData = Seq(mcBlockRef1.data, mcBlockRef2.data))
    validBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }


    // Test 6: AccountBlock with semantically valid AccountBlockHeader, but inconsistent MainchainBlockReferencesData must be invalid.
    // 1 ref data is missed -> list is not consistent to AccountBlockHeader
    invalidBlock = invalidateBlock(
      validBlock,
      mainchainBlockReferencesDataOpt = Some(Seq(mcBlockRef2.data)) // first was removed
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }

    // No Ref data in body, but AccountBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      mainchainBlockReferencesDataOpt = Some(Seq()) // no mc ref data
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }


    // Test 7: AccountBlock with semantically valid AccountBlockHeader, and consistent MainchainHeader seq must be valid.
    validBlock = createBlock(mainchainHeaders = Seq(mcBlockRef1.header, mcBlockRef2.header))
    validBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }

    // Test 8: AccountBlock with semantically valid AccountBlockHeader, but inconsistent MainchainHeader must be invalid.
    // 1 header is missed -> list is not consistent to AccountBlockHeader
    invalidBlock = invalidateBlock(
      validBlock,
      mainchainHeadersOpt = Some(Seq(mcBlockRef2.header)) // first was removed
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }

    // No headers in body, but AccountBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      mainchainHeadersOpt = Some(Seq()) // no headers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }


    // Test 9: AccountBlock with MainchainHeaders in a wrong order must be invalid
    invalidBlock = createBlock(mainchainHeaders = Seq(mcBlockRef1.header, mcBlockRef2.header, mcBlockRef4.header))
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidSidechainBlockDataException], e.getClass)
    }


    // Test 10: AccountBlock with 2 Txs, 2 MainchainBlockReferencesData, 2 MainchainHeader must be valid
    validBlock = createBlock(
      sidechainTransactions = Seq(
        getEoa2EoaLegacyTransaction,
        getEoa2EoaEip1559Transaction
      ),
      mainchainBlockReferencesData = Seq(mcBlockRef1.data, mcBlockRef2.data),
      mainchainHeaders = Seq(mcBlockRef2.header, mcBlockRef3.header)
    )
    validBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }

    // Test 11: AccountBlock with semantically invalid MainchainHeader must be invalid
    val invalidMcHeader3 = new MainchainHeader(
      mcBlockRef3.header.mainchainHeaderBytes,
      mcBlockRef3.header.version,
      mcBlockRef3.header.hashPrevBlock,
      mcBlockRef3.header.hashMerkleRoot,
      mcBlockRef3.header.hashScTxsCommitment,
      -1, // broke time
      mcBlockRef3.header.bits,
      mcBlockRef3.header.nonce,
      mcBlockRef3.header.solution
    )

    invalidBlock = createBlock(mainchainHeaders = Seq(mcBlockRef1.header, mcBlockRef2.header, invalidMcHeader3))
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidMainchainHeaderException], e.getClass)
    }


    // Test12: AccountBlock has unsupported version
    invalidBlock = createBlock(blockVersion = SidechainBlock.BLOCK_VERSION) // UTXO model version
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidSidechainBlockDataException], e.getClass)
    }


    // Test 13: AccountBlock with semantically valid AccountBlockHeader and large number of txes
    val seq_35001 = List.fill(35001){getContractDeploymentEip1559Transaction}
    invalidBlock = createBlock(sidechainTransactions = seq_35001)
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) =>
        jFail(s"AccountBlock expected to be semantically valid, instead got exception: ${e.getMessage}")
    }


    // Test 13b: AccountBlock with semantically valid AccountBlockHeader and very large size.
    invalidBlock = createBlock(
      sidechainTransactions = Seq(getBigDataTransaction(5000000, BigInteger.valueOf(100000000))))
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) =>
        jFail(s"AccountBlock expected to be semantically valid, instead got exception: ${e.getMessage}")
    }

  }


  @Test
  def ommersContainerValidation(): Unit = {
    // In this test verifyOmmersSeqData() method of OmmersContainer is tested
    // The same check both for Block and Ommer classes

    val ommers: Seq[Ommer[AccountBlockHeader]] = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(mcBlockRef1.data), Seq(mcBlockRef1.header, mcBlockRef2.header)),
        (Seq(), Seq()),
        (Seq(mcBlockRef2.data, mcBlockRef3.data, mcBlockRef4.data), Seq(mcBlockRef3.header, mcBlockRef4.header))
      )
    )
    val ommer1 = ommers.head
    val ommer2 = ommers(1)
    val ommer3 = ommers(2)

    // Create Seq of mocked MCHeaders with stubs needed for semanticValidity verifications
    val forkMainchainHeaders = mockForkMainchainHeaders(Seq(mcBlockRef1, mcBlockRef2, mcBlockRef3, mcBlockRef4).map(_.header))


    // Test 1: AccountBlock with semantically valid and consistent Ommers must be valid.
    var validBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers = ommers
    )
    validBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }

    var anotherOmmers = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(), Seq(mcBlockRef1.header, mcBlockRef2.header)),
        (Seq(mcBlockRef1.data), Seq(mcBlockRef3.header, mcBlockRef4.header))
      )
    )
    val anotherValidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers = anotherOmmers
    )
    anotherValidBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }

    // Test 2: AccountBlock with semantically valid AccountBlockHeader, but NOT consistent Ommers must be invalid.
    // 1 Ommer is missed -> list is not consistent to AccountBlockHeader
    var invalidBlock = invalidateBlock(
      validBlock,
      ommersOpt = Some(Seq(ommer1, ommer2)) // ommer3 removed
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }

    // No Ommers in body, but AccountBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      ommersOpt = Some(Seq()) // No Ommers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }

    // Another ommers list of the same length
    anotherOmmers = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(mcBlockRef1.data), Seq(mcBlockRef2.header)),
        (Seq(mcBlockRef2.data), Seq()),
        (Seq(mcBlockRef3.data, mcBlockRef4.data), Seq(mcBlockRef3.header, mcBlockRef4.header))
      )
    )
    invalidBlock = invalidateBlock(
      validBlock,
      ommersOpt = Some(anotherOmmers) // Different ommers of the same length as origin
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }


    // Test 3: AccountBlock parent is different to first Ommer parent -> must be invalid.
    val anotherParentId = getRandomBlockId(seed + 1)
    invalidBlock = createBlock(
      parent = anotherParentId,
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers = ommers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 4: AccountBlock Ommers sidechain headers are not ordered -> must be invalid.
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers =  Seq(ommer1, ommer3) // ommer2 is missed
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 5.1: AccountBlock contains Ommer with invalid data -> must be invalid
    var invalidOmmer1 = new Ommer(
      ommer1.header,
      ommer1.mainchainReferencesDataMerkleRootHashOption,
      ommer1.mainchainHeaders,
      ommer1.ommers
    ) {
      override def verifyData(params: NetworkParams): Try[Unit] = Failure(new InvalidOmmerDataException("Invalid data."))
    }
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers =  Seq(invalidOmmer1, ommer2, ommer3)
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 5.2: AccountBlock contains Ommer with inconsistent data -> must be invalid
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers =  Seq(ommer1.copy(mainchainHeaders = Seq()), ommer2, ommer3)
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }


    // Test 5.3: AccountBlock contains Ommer with both inconsistent and invalid data -> must be invalid
    // Note: Inconsistent Data Exception must be emitted.
    invalidOmmer1 = new Ommer(
      ommer1.header,
      ommer1.mainchainReferencesDataMerkleRootHashOption,
      ommer1.mainchainHeaders,
      ommer1.ommers
    ) {
      override def verifyData(params: NetworkParams): Try[Unit] = Failure(new InvalidOmmerDataException("Invalid data."))
      override def verifyDataConsistency(): Try[Unit] = Failure(new InconsistentOmmerDataException("Invalid data."))
    }
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers =  Seq(invalidOmmer1, ommer2, ommer3)
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }


    // Test 6: AccountBlock with not consistent Ommers mc headers chain -> must be invalid
    // First Ommer is invalid, it has no mc headers at all
    var invalidOmmers: Seq[Ommer[AccountBlockHeader]] = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(), Seq()),
        (Seq(mcBlockRef1.data), Seq()),
        (Seq(), Seq(mcBlockRef2.header, mcBlockRef3.header, mcBlockRef4.header))
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }

    // Second Ommer duplicate MainchainHeader from first Ommer
    invalidOmmers = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(mcBlockRef1.data), Seq(mcBlockRef2.header)),
        (Seq(), Seq(mcBlockRef2.header))
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }

    // Second Ommer MainchainHeader not connected to last MainchainHeader of first Ommer
    invalidOmmers = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(), Seq(mcBlockRef1.header)),
        (Seq(), Seq(mcBlockRef3.header, mcBlockRef4.header))
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 7: AccountBlock with less or equal headers than in Ommers must be invalid.
    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = Seq(), // no headers
      ommers = ommers // 4 headers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }

    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = Seq(forkMainchainHeaders.head), // 1 header
      ommers = ommers // 4 headers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }

    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders.take(4), // 4 headers
      ommers = ommers // 4 headers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 8: AccountBlock contains NO Ommers and AccountBlockHeader.ommersCumulativeScore = 0, but ommersMerkleRootHash not equal to default
    validBlock = createBlock()
    val anotherOmmersHash: Array[Byte] = new Array[Byte](32)
    random.nextBytes(anotherOmmersHash)
    val unsignedModifiedHeader = validBlock.header.copy(ommersMerkleRootHash = anotherOmmersHash)
    val signedModifiedHeader = unsignedModifiedHeader.copy(
      signature = forgerMetadata.blockSignSecret.sign(unsignedModifiedHeader.messageToSign)
    )
    invalidBlock = invalidateBlock(
      validBlock,
      headerOpt = Some(signedModifiedHeader)
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentSidechainBlockDataException], e.getClass)
    }


    // Test 9: AccountBlock MC headers follows different parent than Ommers MC headers -> must be invalid
    // Create Seq of mocked MCHeaders with stubs needed for semanticValidity verifications
    val anotherMcBranchPoint: Array[Byte] = new Array[Byte](32)
    random.nextBytes(anotherMcBranchPoint)
    val inconsistentForkMainchainHeaders = mockForkMainchainHeaders(Seq(mcBlockRef1, mcBlockRef2, mcBlockRef3, mcBlockRef4).map(_.header), Some(anotherMcBranchPoint))

    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = inconsistentForkMainchainHeaders, // first header parent is different to first ommer mc header parent
      ommers = ommers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 11: AccountBlock contains valid Ommer with valid sub Ommers must be valid
    val validBlockWithOmmers = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers = ommers
    )

    val ommerWithOmmers = Ommer.toOmmer(validBlockWithOmmers)
    val forkOfForkMainchainHeaders = mockForkMainchainHeaders(forkMainchainHeaders, basicSeed = 100L)

    invalidBlock = createBlock(
      timestamp = 123888L,
      mainchainHeaders = forkOfForkMainchainHeaders,
      ommers = Seq(ommerWithOmmers)
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => jFail(s"AccountBlock expected to be semantically Valid, instead exception: ${e.getMessage}")
    }


    // Test 12: AccountBlock contains Ommer with sub Ommer with inconsistent data:
    val ommerWithOmmersWithInconsistentData = Ommer.toOmmer(validBlockWithOmmers).copy(mainchainHeaders = Seq())
    invalidBlock = createBlock(
      timestamp = 123888L,
      mainchainHeaders = forkOfForkMainchainHeaders,
      ommers = Seq(ommerWithOmmersWithInconsistentData)
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InconsistentOmmerDataException], e.getClass)
    }

    // Test 13: AccountBlock contains Ommers that are not properly ordered in epochs&slots
    val slotOmmer1: Ommer[AccountBlockHeader] = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(mcBlockRef1.data), Seq(mcBlockRef2.header))
      )
    ).head

    val slotOmmer2: Ommer[AccountBlockHeader] = generateOmmersSeq(slotOmmer1.header.id, 121444L, // Ommer Slot is before previous Ommer Slot
      Seq(
        (Seq(), Seq(mcBlockRef2.header))
      )
    ).head

    invalidBlock = createBlock(
      timestamp = 123666L,
      mainchainHeaders = forkMainchainHeaders,
      ommers = Seq(slotOmmer1, slotOmmer2)
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }


    // Test 14: AccountBlock contains Ommers that are not properly ordered in epochs&slots
    invalidOmmers = generateOmmersSeq(parentId, 122444L,
      Seq(
        (Seq(mcBlockRef1.data), Seq(mcBlockRef2.header)), // timestamp  = 122444L
        (Seq(), Seq(mcBlockRef2.header))  // timestamp = 122664L
      )
    )

    invalidBlock = createBlock(
      timestamp = 122500L, // Block slot is before last Ommer slot
      mainchainHeaders = forkMainchainHeaders,
      ommers = invalidOmmers
    )
    invalidBlock.semanticValidity(params) match {
      case Success(_) =>
        jFail("AccountBlock expected to be semantically Invalid.")
      case Failure(e) =>
        assertEquals("Different exception type expected during semanticValidity.",
          classOf[InvalidOmmerDataException], e.getClass)
    }
  }


  // Accept N real MainchainHeader objects
  // Return N+1 mocked semantically valid MainchainHeader objects started from branching point
  private def mockForkMainchainHeaders(headers: Seq[MainchainHeader],
                                       firstMockedHeaderParentOpt: Option[Array[Byte]] = None,
                                       basicSeed: Long = 1L): Seq[MainchainHeader] = {
    var nextParent: Array[Byte] = firstMockedHeaderParentOpt.getOrElse(headers.head.hashPrevBlock)
    var seed: Long = basicSeed

    (headers :+ headers.last).map(h => {
      seed += 1
      val currentParent = new Array[Byte](32)
      System.arraycopy(nextParent, 0, currentParent, 0, 32)
      nextParent = getRandomBoxId(seed) // ok for mc headers
      new MainchainHeader(
        h.mainchainHeaderBytes,
        h.version,
        currentParent, // mock parent hash
        h.hashMerkleRoot,
        h.hashScTxsCommitment,
        h.time,
        h.bits,
        h.nonce,
        h.solution
      ) {
        val h: Array[Byte] = nextParent // Just a hack for lazy vals
        override lazy val hash: Array[Byte] = h

        override def semanticValidity(params: NetworkParams): Try[Unit] = Success(Unit)
      }
    })
  }


  private def createBlock(parent: ModifierId = parentId,
                          blockVersion:Byte = AccountBlock.ACCOUNT_BLOCK_VERSION,
                          timestamp: Long = 122444L,
                          sidechainTransactions: Seq[EthereumTransaction] = Seq(),
                          receipts: Seq[EthereumConsensusDataReceipt] = Seq(),
                          mainchainBlockReferencesData: Seq[MainchainBlockReferenceData] = Seq(),
                          mainchainHeaders: Seq[MainchainHeader] = Seq(),
                          ommers: Seq[Ommer[AccountBlockHeader]] = Seq(),
                          rnd: Random = new Random()
                         ): AccountBlock = {

    val stateRoot = new Array[Byte](32)
    val receiptsRoot: Array[Byte] = calculateReceiptRoot(receipts)

    val forgerAddress: AddressProposition = accountPayment.address
    val baseFee: BigInteger = INITIAL_BASE_FEE
    val gasUsed: BigInteger = BigInteger.valueOf(21000)
    val gasLimit: BigInteger = GAS_LIMIT
    val logsBloom: Bloom = new Bloom()

    AccountBlock.create(
      parent,
      blockVersion,
      timestamp,
      mainchainBlockReferencesData,
      sidechainTransactions.map(t => t.asInstanceOf[SidechainTypes#SCAT]),
      mainchainHeaders,
      ommers,
      forgerMetadata.blockSignSecret,
      forgerMetadata.forgingStakeInfo,
      vrfProof,
      vrfOutput,
      MerkleTreeFixture.generateRandomMerklePath(rnd.nextLong()),
      new Array[Byte](32),
      stateRoot,
      receiptsRoot,
      forgerAddress,
      baseFee,
      gasUsed,
      gasLimit,
      sidechainTransactionsCompanion,
      logsBloom
    ).get
  }

  private def invalidateBlock(block: AccountBlock,
                              headerOpt: Option[AccountBlockHeader] = None,
                              sidechainTransactionsOpt: Option[Seq[EthereumTransaction]] = None,
                              mainchainBlockReferencesDataOpt: Option[Seq[MainchainBlockReferenceData]] = None,
                              mainchainHeadersOpt: Option[Seq[MainchainHeader]] = None,
                              ommersOpt: Option[Seq[Ommer[AccountBlockHeader]]] = None): AccountBlock = {
    val txes = sidechainTransactionsOpt match {
      case Some(s) => s.map(t => t.asInstanceOf[SidechainTypes#SCAT])
      case None => block.sidechainTransactions
    }
    new AccountBlock(
      headerOpt.getOrElse(block.header),
      txes,
      mainchainBlockReferencesDataOpt.getOrElse(block.mainchainBlockReferencesData),
      mainchainHeadersOpt.getOrElse(block.mainchainHeaders),
      ommersOpt.getOrElse(block.ommers),
      sidechainTransactionsCompanion
    )
  }


  private def generateOmmersSeq(parent: ModifierId, firstTimestamp: Long, ommersData: Seq[(Seq[MainchainBlockReferenceData], Seq[MainchainHeader])], rnd: Random = new Random()): Seq[Ommer[AccountBlockHeader]] = {
    var blockSeq: Seq[AccountBlock] = Seq()
    var currentTimestamp = firstTimestamp
    var currentParent = parent

    for(i <- ommersData.indices) {
      blockSeq = blockSeq :+ createBlock(
        parent = currentParent,
        timestamp = currentTimestamp,
        mainchainBlockReferencesData = ommersData(i)._1,
        mainchainHeaders = ommersData(i)._2,
        rnd = rnd
      )

      currentParent = blockSeq.last.id
      currentTimestamp += params.consensusSecondsInSlot
    }

    blockSeq.map(block => Ommer.toOmmer(block))
  }
}
