package com.horizen.block

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.box.NoncedBox
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures._
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.proof.Signature25519
import com.horizen.proposition.Proposition
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.BytesUtils
import com.horizen.vrf.VRFKeyGenerator
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail => jFail}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.util.{ModifierId, idToBytes}

import scala.io.Source

class SidechainBlockTest
  extends JUnitSuite
  with CompanionsFixture
  with TransactionFixture
  with SidechainBlockFixture
{

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val sidechainBlockSerializer = new SidechainBlockSerializer(sidechainTransactionsCompanion)

  val random = new scala.util.Random(123L)

  val params: NetworkParams = MainNetParams()
  val mcBlockRef1: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473173_mainnet").getLines().next()), params).get
  val mcBlockRef2: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473174_mainnet").getLines().next()), params).get
  val mcBlockRef3: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473175_mainnet").getLines().next()), params).get
  val mcBlockRef4: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(Source.fromResource("mcblock473176_mainnet").getLines().next()), params).get

  val seed: Long = 11L
  val parentId: ModifierId = getRandomBlockId(seed)
  val (forgerBox, forgerMetadata) = ForgerBoxFixture.generateForgerBox(seed)
  val vrfProof = VRFKeyGenerator.generate(Array.fill(32)(seed.toByte))._1.prove(Array.fill(32)((seed + 1).toByte))

  // Create Block with Txs, MCRefs, next Headers and Ommers
  // Note: block is semantically invalid because Block contains the same MC chain as Ommers, but it's ok for serialization test
  val block: SidechainBlock = createBlock(
    sidechainTransactions = Seq(
      generateRegularTransaction(random, 123000L, 2, 3),
      generateRegularTransaction(random, 123001L, 1, 4)
    ),
    mainchainBlockReferences = Seq(mcBlockRef1, mcBlockRef2),
    nextMainchainHeaders = Seq(mcBlockRef3.header, mcBlockRef4.header),
    ommers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(), Seq()),
        (Seq(mcBlockRef2, mcBlockRef3, mcBlockRef4), Seq())
      )
    )
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
      case _: Throwable => fail("Block id doesn't not found in json.")
    }
    try {
      val parentId = node.path("parentId").asText()
      assertEquals("Block parentId json value must be the same.",
        BytesUtils.toHexString(idToBytes(sb.parentId)), parentId)
    }catch {
      case _: Throwable => fail("Block parentId doesn't not found in json.")
    }
    try {
      val timestamp = node.path("timestamp").asLong()
      assertEquals("Block timestamp json value must be the same.",
        sb.timestamp, timestamp)
    }catch {
      case _: Throwable => fail("Block timestamp doesn't not found in json.")
    }

  }

  @Test
  def serialization(): Unit = {
    val blockBytes = sidechainBlockSerializer.toBytes(block)

    val deserializedBlockTry = sidechainBlockSerializer.parseBytesTry(blockBytes)
    assertTrue("Block deserialization failed.", deserializedBlockTry.isSuccess)

    val deserializedBlock = deserializedBlockTry.get
    assertEquals("Deserialized Block transactions are different.", block.transactions, deserializedBlock.transactions)
    assertEquals("Deserialized Block mainchain block references are different.", block.mainchainBlockReferences, deserializedBlock.mainchainBlockReferences)
    assertEquals("Deserialized Block next mainchain headers are different.", block.nextMainchainHeaders, deserializedBlock.nextMainchainHeaders)
    assertEquals("Deserialized Block ommers are different.", block.ommers, deserializedBlock.ommers)
    assertEquals("Deserialized Block id is different.", block.id, deserializedBlock.id)


    // Set to true to regenerate regression data
    if(false) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/sidechainblock_hex"))
      out.write(BytesUtils.toHexString(blockBytes))
      out.close()
    }

    // Test 2: try to deserialize broken bytes.
    assertTrue("SidechainBlockSerializer expected to be not parsed due to broken data.", sidechainBlockSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serializationRegression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("sidechainblock_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        jFail(e.toString)
    }


    val deserializedBlockTry = sidechainBlockSerializer.parseBytesTry(bytes)
    assertTrue("SidechainBlock expected to by parsed.", deserializedBlockTry.isSuccess)

    val deserializedBlock = deserializedBlockTry.get
    assertEquals("Deserialized Block transactions are different.", block.transactions, deserializedBlock.transactions)
    assertEquals("Deserialized Block mainchain block references are different.", block.mainchainBlockReferences, deserializedBlock.mainchainBlockReferences)
    assertEquals("Deserialized Block next mainchain headers are different.", block.nextMainchainHeaders, deserializedBlock.nextMainchainHeaders)
    assertEquals("Deserialized Block ommers are different.", block.ommers, deserializedBlock.ommers)
    assertEquals("Deserialized Block id is different.", block.id, deserializedBlock.id)
  }

  @Test
  def semanticValidity(): Unit = {
    // Test1: SidechainBlock with no txs, mc refs, mc headers and ommers must to be valid.
    var validBlock = createBlock()
    assertTrue("SidechainBlock expected to be semantically Valid.", validBlock.semanticValidity(params))


    // Test2: SidechainBlock with invalid SidechainBlockHeader must to be invalid.
    var invalidBlock = invalidateBlock(
      validBlock,
      headerOpt = Some(validBlock.header.copy(signature = new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH))))
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 3: SidechainBlock with semantically valid SidechainBlockHeader, and consistent SidechainTransaction must be valid.
    validBlock = createBlock(sidechainTransactions = Seq(
        generateRegularTransaction(random, 123000L, 2, 3),
        generateRegularTransaction(random, 123001L, 1, 4)
      )
    )
    assertTrue("SidechainBlock expected to be semantically Valid.", validBlock.semanticValidity(params))


    // Test 4: SidechainBlock with semantically valid SidechainBlockHeader, but NOT consistent SidechainTransaction must be invalid.
    // 1 tx is missed -> list is not consistent to SidechainBlockHeader
    invalidBlock = invalidateBlock(
      validBlock,
      sidechainTransactionsOpt = Some(Seq(
        generateRegularTransaction(random, 123000L, 2, 3)
      ))
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // No Txs in body, but SidechainBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      sidechainTransactionsOpt = Some(Seq()) // No txs
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 5: SidechainBlock with semantically valid SidechainBlockHeader, and consistent MainchainBlockReferences seq must be valid.
    validBlock = createBlock(mainchainBlockReferences = Seq(mcBlockRef1, mcBlockRef2))
    assertTrue("SidechainBlock expected to be semantically Valid.", validBlock.semanticValidity(params))


    // Test 6: SidechainBlock with semantically valid SidechainBlockHeader, but NOT consistent MainchainBlockReferences must be invalid.
    // 1 ref is missed -> list is not consistent to SidechainBlockHeader
    invalidBlock = invalidateBlock(
      validBlock,
      mainchainBlockReferencesOpt = Some(Seq(mcBlockRef2)) // first was removed
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // No Refs in body, but SidechainBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      mainchainBlockReferencesOpt = Some(Seq()) // no mc refs
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 7: SidechainBlock with semantically valid SidechainBlockHeader, and consistent nextMainchainHeader seq must be valid.
    validBlock = createBlock(nextMainchainHeaders = Seq(mcBlockRef1.header, mcBlockRef2.header))
    assertTrue("SidechainBlock expected to be semantically Valid.", validBlock.semanticValidity(params))


    // Test 8: SidechainBlock with semantically valid SidechainBlockHeader, but NOT consistent nextMainchainHeader must be invalid.
    // 1 header is missed -> list is not consistent to SidechainBlockHeader
    invalidBlock = invalidateBlock(
      validBlock,
      nextMainchainHeadersOpt = Some(Seq(mcBlockRef2.header)) // first was removed
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // No headers in body, but SidechainBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      nextMainchainHeadersOpt = Some(Seq()) // no headers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 9: SidechainBlock with MCReferences in a wrong order must be invalid
    invalidBlock = createBlock(mainchainBlockReferences = Seq(mcBlockRef1, mcBlockRef2, mcBlockRef4))
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 10: SidechainBlock with nextMainchainHeaders in a wrong order must be invalid
    invalidBlock = createBlock(nextMainchainHeaders = Seq(mcBlockRef1.header, mcBlockRef2.header, mcBlockRef4.header))
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 11: SidechainBlock with first nextMainchainHeader not connected to last MainchainBlockReferences must be invalid
    /* TODO: discuss
       Note: At the moment block [SB5] is Invalid, because 13 is not a parent of 15h.
      Block:  [SB1] - [SB2] - [SB3] - [SB4] - [SB5]
      MCRef:   10      11       12             13
      Header:          12h    13h,14h         15h,16h
     */
    invalidBlock = createBlock(
      mainchainBlockReferences = Seq(mcBlockRef1, mcBlockRef2),
      nextMainchainHeaders = Seq(mcBlockRef4.header)
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 12: SidechainBlock with 2 Txs, 2 MainchainBlockReferences, 2 nextMainchainHeader must be valid
    validBlock = createBlock(
      sidechainTransactions = Seq(
        generateRegularTransaction(random, 123000L, 2, 3),
        generateRegularTransaction(random, 123001L, 1, 4)
      ),
      mainchainBlockReferences = Seq(mcBlockRef1, mcBlockRef2),
      nextMainchainHeaders = Seq(mcBlockRef3.header, mcBlockRef4.header)
    )
    assertTrue("SidechainBlock expected to be semantically Valid.", validBlock.semanticValidity(params))


    // Test 13: SidechainBlock with semantically invalid MCRef must be invalid
    val invalidMcHeader3 = new MainchainHeader(
      mcBlockRef3.header.mainchainHeaderBytes,
      mcBlockRef3.header.version,
      mcBlockRef3.header.hashPrevBlock,
      mcBlockRef3.header.hashMerkleRoot,
      mcBlockRef3.header.hashSCMerkleRootsMap,
      -1, // broke time
      mcBlockRef3.header.bits,
      mcBlockRef3.header.nonce,
      mcBlockRef3.header.solution
    )
    val invalidMCRef3 = new MainchainBlockReference(
      invalidMcHeader3,
      mcBlockRef3.sidechainRelatedAggregatedTransaction,
      mcBlockRef3.sidechainsMerkleRootsMap
    )

    invalidBlock = createBlock(mainchainBlockReferences = Seq(mcBlockRef1, mcBlockRef2, invalidMCRef3))
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 14: SidechainBlock with semantically invalid nextMainchainHeader must be invalid
    invalidBlock = createBlock(
      mainchainBlockReferences = Seq(mcBlockRef1, mcBlockRef2),
      nextMainchainHeaders = Seq(invalidMcHeader3)
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))
  }

  @Test
  def ommersValidation(): Unit = {

    val ommers: Seq[Ommer] = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(), Seq()),
        (Seq(mcBlockRef2, mcBlockRef3, mcBlockRef4), Seq())
      )
    )
    val ommer1 = ommers.head
    val ommer2 = ommers(1)
    val ommer3 = ommers(2)

    // Create Seq of mocked MCHeaders with stubs needed for semanticValidity verifications
    val forkMainchainHeaders = mockForkMainchainHeaders(Seq(mcBlockRef1, mcBlockRef2, mcBlockRef3, mcBlockRef4).map(_.header))


    // Test 1: SidechainBlock with semantically valid and consistent Ommers must be valid.
    var validBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers = ommers
    )
    assertTrue("SidechainBlock expected to be semantically Valid.", validBlock.semanticValidity(params))

    var anotherOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(), Seq(mcBlockRef3.header, mcBlockRef4.header))
      )
    )
    val anotherValidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers = anotherOmmers
    )
    assertTrue("SidechainBlock expected to be semantically Valid.", anotherValidBlock.semanticValidity(params))

    // Test 2: SidechainBlock with semantically valid SidechainBlockHeader, but NOT consistent Ommers must be invalid.
    // 1 Ommer is missed -> list is not consistent to SidechainBlockHeader
    var invalidBlock = invalidateBlock(
      validBlock,
      ommersOpt = Some(Seq(ommer1, ommer2)) // ommer3 removed
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // No Ommers in body, but SidechainBlockHeader Hash expected
    invalidBlock = invalidateBlock(
      validBlock,
      ommersOpt = Some(Seq()) // No Ommers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // Another ommers list of the same length
    anotherOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(mcBlockRef2), Seq()),
        (Seq(mcBlockRef3, mcBlockRef4), Seq())
      )
    )
    invalidBlock = invalidateBlock(
      validBlock,
      ommersOpt = Some(anotherOmmers) // Different ommers of the same length as origin
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 3: SidechainBlock parent is different to first Ommer parent -> must be invalid.
    val anotherParentId = getRandomBlockId(seed + 1)
    invalidBlock = createBlock(
      parent = anotherParentId,
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers = ommers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 4: SidechainBlock Ommers sidechain headers are not ordered -> must be invalid.
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  Seq(ommer1, ommer3) // ommer2 is missed
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 5: SidechainBlock contains semantically invalid Ommer -> must be invalid
    val invalidOmmer1 = new Ommer(
      ommer1.sidechainBlockHeader,
      ommer1.mainchainReferencesDataMerkleRootHashOption,
      ommer1.mainchainReferencesHeaders,
      ommer1.nextMainchainHeaders
    ) {
      override def semanticValidity(params: NetworkParams): Boolean = false
    }
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  Seq(invalidOmmer1, ommer2, ommer3)
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 6: SidechainBlock with not consistent Ommers mc headers -> must be invalid
    // First Ommer has no mc headers at all
    var invalidOmmers: Seq[Ommer] = generateOmmersSeq(parentId,
      Seq(
        (Seq(), Seq()),
        (Seq(mcBlockRef1), Seq()),
        (Seq(mcBlockRef2, mcBlockRef3, mcBlockRef4), Seq())
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // Second Ommer duplicate nextMainchainHeader from first Ommer
    invalidOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(), Seq(mcBlockRef2.header))
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // Second Ommer MCRef header not connected to last MCRef header of first Ommer
    invalidOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq()),
        (Seq(mcBlockRef3), Seq(mcBlockRef4.header))
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // First Ommer MCRefHeaders and nextMainchainHeaders are not consisted (not ordered one by one)
    // Note: see case 11 in samanticValidity() Test
    invalidOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef3.header)),
        (Seq(mcBlockRef2), Seq()),
        (Seq(mcBlockRef3, mcBlockRef4), Seq())
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // Unprocessed inconsistent first nextMainchainHeader left -> must be invalid
    invalidOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(mcBlockRef2), Seq()),
        (Seq(), Seq(mcBlockRef4.header))
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    // Unprocessed inconsistent second nextMainchainHeader left -> must be invalid
    invalidOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(), Seq(mcBlockRef4.header)) // mcBlockRef4 header left and doesn't follow mcBlockRef2 header
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 7: Ommers have mc refs and next headers inconsistency -> must be invalid
    val differentMcHeader3 = new MainchainHeader(
      mcBlockRef3.header.mainchainHeaderBytes,
      mcBlockRef3.header.version,
      mcBlockRef3.header.hashPrevBlock, // still connected to mcBlockRef2
      mcBlockRef3.header.hashMerkleRoot,
      mcBlockRef3.header.hashSCMerkleRootsMap,
      mcBlockRef3.header.time,
      mcBlockRef3.header.bits,
      mcBlockRef3.header.nonce,
      mcBlockRef3.header.solution
    ) {
      override lazy val hash: Array[Byte] = new Array[Byte](32)
    }

    invalidOmmers = generateOmmersSeq(parentId,
      Seq(
        (Seq(mcBlockRef1), Seq(mcBlockRef2.header)),
        (Seq(mcBlockRef2), Seq(differentMcHeader3)),
        (Seq(mcBlockRef3), Seq()) // mcBlockRef3 conflicts with differentMcHeader3 in previous Ommer
      )
    )
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders,
      ommers =  invalidOmmers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 8: SidechainBlock with less or equal headers than in Ommers must be invalid.
    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = Seq(), // no headers
      ommers = ommers // 4 headers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = Seq(forkMainchainHeaders.head), // 1 header
      ommers = ommers // 4 headers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))

    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = forkMainchainHeaders.take(4), // 4 headers
      ommers = ommers // 4 headers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 9: SidechainBlock contains NO Ommers and SidechainBlockHeader.ommersNumber = 0, but ommersMerkleRootHash not equal to default
    validBlock = createBlock()
    val anotherOmmersHash: Array[Byte] = new Array[Byte](32)
    random.nextBytes(anotherOmmersHash)
    val unsignedModifiedHeader = validBlock.header.copy(ommersMerkleRootHash = anotherOmmersHash)
    val signedModifiedHeader = unsignedModifiedHeader.copy(
      signature = forgerMetadata.rewardSecret.sign(unsignedModifiedHeader.messageToSign)
    )
    invalidBlock = invalidateBlock(
      validBlock,
      headerOpt = Some(signedModifiedHeader)
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))


    // Test 10: SidechainBlock MC headers follows different parent than Ommers MC headers -> must be invalid
    // Create Seq of mocked MCHeaders with stubs needed for semanticValidity verifications
    val anotherMcBranchPoint: Array[Byte] = new Array[Byte](32)
    random.nextBytes(anotherMcBranchPoint)
    val inconsistentForkMainchainHeaders = mockForkMainchainHeaders(Seq(mcBlockRef1, mcBlockRef2, mcBlockRef3, mcBlockRef4).map(_.header), Some(anotherMcBranchPoint))

    invalidBlock = createBlock(
      timestamp = 123666L,
      nextMainchainHeaders = inconsistentForkMainchainHeaders, // first header parent is different to first ommer mc header parent
      ommers = ommers
    )
    assertFalse("SidechainBlock expected to be semantically Invalid.", invalidBlock.semanticValidity(params))
  }


  // Accept N real MainchainHeader objects
  // Return N+1 mocked semantically valid MainchainHeader objects started from branching point
  private def mockForkMainchainHeaders(headers: Seq[MainchainHeader],
                                       firstMockedHeaderParentOpt: Option[Array[Byte]] = None): Seq[MainchainHeader] = {
    var nextParent: Array[Byte] = firstMockedHeaderParentOpt.getOrElse(headers.head.hashPrevBlock)
    var seed: Long = 1L

    (headers :+ headers.last).map(h => {
      seed += 1
      var currentParent = new Array[Byte](32)
      System.arraycopy(nextParent, 0, currentParent, 0, 32)
      nextParent = getRandomBoxId(seed) // ok for mc headers
      new MainchainHeader(
        h.mainchainHeaderBytes,
        h.version,
        currentParent, // mock parent hash
        h.hashMerkleRoot,
        h.hashSCMerkleRootsMap,
        h.time,
        h.bits,
        h.nonce,
        h.solution
      ) {
        val h: Array[Byte] = nextParent // Just a hack for lazy vals
        override lazy val hash: Array[Byte] = h

        override def semanticValidity(params: NetworkParams): Boolean = true
      }
    })
  }


  private def createBlock(parent: ModifierId = parentId,
                          timestamp: Long = 123444L,
                          sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = Seq(),
                          mainchainBlockReferences: Seq[MainchainBlockReference] = Seq(),
                          nextMainchainHeaders: Seq[MainchainHeader] = Seq(),
                          ommers: Seq[Ommer] = Seq()
                         ): SidechainBlock = {
    SidechainBlock.create(
      parent,
      timestamp,
      mainchainBlockReferences,
      sidechainTransactions,
      nextMainchainHeaders,
      ommers,
      forgerMetadata.rewardSecret,
      forgerBox,
      vrfProof,
      MerkleTreeFixture.generateRandomMerklePath(seed),
      sidechainTransactionsCompanion,
      params
    ).get
  }

  private def invalidateBlock(block: SidechainBlock,
                              headerOpt: Option[SidechainBlockHeader] = None,
                              sidechainTransactionsOpt: Option[Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]]] = None,
                              mainchainBlockReferencesOpt: Option[Seq[MainchainBlockReference]] = None,
                              nextMainchainHeadersOpt: Option[Seq[MainchainHeader]] = None,
                              ommersOpt: Option[Seq[Ommer]] = None): SidechainBlock = {
    new SidechainBlock(
      headerOpt.getOrElse(block.header),
      sidechainTransactionsOpt.getOrElse(block.sidechainTransactions),
      mainchainBlockReferencesOpt.getOrElse(block.mainchainBlockReferences),
      nextMainchainHeadersOpt.getOrElse(block.nextMainchainHeaders),
      ommersOpt.getOrElse(block.ommers),
      sidechainTransactionsCompanion
    )
  }


  private def generateOmmersSeq(parent: ModifierId, ommersData: Seq[(Seq[MainchainBlockReference], Seq[MainchainHeader])]): Seq[Ommer] = {
    var blockSeq: Seq[SidechainBlock] = Seq()
    var currenTimestamp = 123444L
    var currentParent = parent

    for(i <- ommersData.indices) {
      blockSeq = blockSeq :+ createBlock(
        parent = currentParent,
        timestamp = currenTimestamp,
        mainchainBlockReferences = ommersData(i)._1,
        nextMainchainHeaders = ommersData(i)._2
      )

      currentParent = blockSeq.last.id
      currenTimestamp += params.consensusSecondsInSlot
    }

    blockSeq.map(block => Ommer.toOmmer(block))
  }
}
