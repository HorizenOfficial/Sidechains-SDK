package com.horizen.validation

import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.NoncedBox
import com.horizen.chain.SidechainBlockInfo
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{VrfGenerator, _}
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proposition.Proposition
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, WithdrawalEpochInfo}
import org.junit.Assert.assertTrue
import org.junit.{Before, Ignore, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId}

import scala.io.Source

class WithdrawalEpochValidatorTest extends JUnitSuite with MockitoSugar with MainchainBlockReferenceFixture with TransactionFixture with CompanionsFixture{

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val params: NetworkParams = mock[NetworkParams]
  val historyStorage: SidechainHistoryStorage = mock[SidechainHistoryStorage]
  val history: SidechainHistory = mock[SidechainHistory]

  // Genesis MC block hex created in regtest from MC branch beta_v1 on 25.05.2020
  val mcBlockHex: String = "030000002905b49f5bcd0b8e29f91170467e6900a82f4ec9a51e6d6ff12bfd4156b6110191fdc3a11d8a7014e3cf25197a56d3e0199ec92fc70cd0e91d49ff7c270fb435ae628657d19a111490839a6a66c4dd09a422c94a2a486e411d0fe54e858eb353afd4cb5e030f0f2017006d83d7e2ebd3c45ad11831e620d5f8b7b5c4f4c912cdc9f7c33a5daa0000240975303f82ac016f1418a4d3f6151fc753c11ed498d635ee2b1fc22a2e4e370306b14ee10201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dd000101ffffffff04c21bb42c000000001976a914c5be5df5a7b85620af7324891f4107adf2e05a7888ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff0523e3e0ffb44e0084187db48ad78b854b034271e0d52de1db710455875bc2d9d5000000006b483045022100b7146011794140b618cd99b026f2bdb4b7a3f8b5850748583714793a5e6dd8ed02203cc86851619f0219a51b24df0120ed9efa8fb708ac0f27a178789af0304a63f70121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff3e3b7f63715ddd6451de91104c503c5ce0400151eed36e24f9d4fbfda7ed77ab000000006b483045022100a83e9e5e2736909805015058b7b8f1926f22d879e674f08f2bfd798b441bbcff022055eb00442e872fd03fb3399c48b4f5b6ac72d2d42ad098b9f51fa1b0696c8c2c0121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff714dcef46d1b8944f8562cd8fc528c15474938f2f88cd2a94964c4d17970379c000000006a473044022002eb0b187e5335c946699fa66177bfa8bfc6e426fc0606bc304e9bb11e2a66b902206dca231c7cb52289ef2bd0333d4ebc1324c03587e9d6d60176aff20f44425acc0121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffffff4646d63099c44b1e5be6dfc2ab8cb3f2db6bd55f49eade7b25dc138a55417d000000006a4730440220766b9fc27888c9ebb3f54d5e2c27b6cb93789c1d8682052944d567c6bacff7ab02201a0e220d86a33f9f83ded0dd166b0152a3180d2c563eb9946ee24dbdb2768fb80121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff2b02c1d304db30119d69fa07751b386ee2e9019aad8cc0abdda217ed674244bc000000006b483045022100ff309d03234ca8afe7e5a79e406590746c47043736e44b44ed21d15cc302999602204712d0dda3fa5f65f834acbfdc7e76f97878a3b732a74623f4efacff915786680121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff013e70d21a000000003c76a91457aa4f67e7d37b86d7c75b8bde5c1124a64dc30d88ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010100000000000000000000000000000000000000000000000000000000000000e803000000f2052a01000000ddea6ff950e5efff4da66306155bcc1219bf2a3776804458c4fc166cd75f46a4c13c158597376bccf305f7a363f7ba2b04e91e393eaedaa95a5e6284cd3607b58014a631e9e955dbdb2352a5b679e2d1269abeb59f81ef815d93280d8b46630b625f64e7c83e366c2bbd483ed0134322168ae8a6e1e0dd3d7f99ea038fe3df00003c29f7bdb83189a56e3ede091575f278b00d2754a842e0df517b8067304eada8b4bd31b3a51b4aa5365ebd35496102e8fb131684cb539d795f3b2624f84e0d82dc810075618c7b8259280ce14d8b03681ecc01fc69bb7bf0985b98140d0601000000d200000000"

  @Before
  def setUp(): Unit = {
    Mockito.when(history.storage).thenReturn(historyStorage)
  }

  @Ignore
  @Test
  def genesisBlockValidation(): Unit = {
    val validator = new WithdrawalEpochValidator(params)

    // Test 1: invalid genesis block - no MainchainBlockReferenceData
    val (forgerBox1, forgerMeta1) = ForgerBoxFixture.generateForgerBox(32)
    var block: SidechainBlock = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta1.blockSignSecret,
      forgerBox1,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    assertTrue("Sidechain genesis block with no MainchainBlockReferenceData expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 2: invalid genesis block - multiple MainchainBlockReferenceData
    val (forgerBox2, forgerMeta2) = ForgerBoxFixture.generateForgerBox(322)
    var mcRefs: Seq[MainchainBlockReference] = Seq(generateMainchainBlockReference(), generateMainchainBlockReference())

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta2.blockSignSecret,
      forgerBox2,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    assertTrue("Sidechain genesis block with multiple MainchainBlockReferenceData expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 3: invalid genesis block - 1 MainchainBlockReferenceData without sc creation tx
    val (forgerBox3, forgerMeta3) = ForgerBoxFixture.generateForgerBox(32)
    mcRefs = Seq(generateMainchainBlockReference())

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta3.blockSignSecret,
      forgerBox3,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    assertTrue("Sidechain genesis block with 1 MainchainBlockReferenceData without sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: NoSuchElementException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 4: valid genesis block with 1 MainchainBlockReferenceData with sc creation tx with INVALID withdrawalEpochLength (different to the one specified in params)
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000001"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))
    val mcBlockRefRegTestParams = RegTestParams(scId.data)
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcBlockRef = MainchainBlockReference.create(mcBlockBytes, mcBlockRefRegTestParams).get
    mcRefs = Seq(mcBlockRef)

    val (forgerBox4, forgerMeta4) = ForgerBoxFixture.generateForgerBox(324)
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta4.blockSignSecret,
      forgerBox4,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    Mockito.when(params.withdrawalEpochLength).thenReturn(123)
    assertTrue("Sidechain genesis block with 1 MainchainBlockReferenceData with sc creation inside with incorrect withdrawalEpochLength expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 5: the same as above but with valid withdrawalEpochLength specified in params / sc creation
    Mockito.when(params.withdrawalEpochLength).thenReturn(1000)
    assertTrue("Sidechain genesis block with 1 MainchainBlockReferencesData with sc creation with correct withdrawalEpochLength inside expected to be valid.", validator.validate(block, history).isSuccess)
  }

  @Ignore
  @Test
  def blockValidation(): Unit = {
    val validator = new WithdrawalEpochValidator(params)
    val withdrawalEpochLength = 100
    Mockito.when(params.sidechainGenesisBlockId).thenReturn(bytesToId(new Array[Byte](32)))
    Mockito.when(params.withdrawalEpochLength).thenReturn(withdrawalEpochLength)


    // Test 1: invalid block - no MainchainBlockReferencesData, parent is missed
    val (forgerBox1, forgerMeta1) = ForgerBoxFixture.generateForgerBox(1)

    var block: SidechainBlock = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta1.blockSignSecret,
      forgerBox1,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn(None)
    assertTrue("Sidechain block with missed parent expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }

    // Test 2: valid block - no MainchainBlockReferenceData, parent is the last block of previous epoch
    val (forgerBox2, forgerMeta2) = ForgerBoxFixture.generateForgerBox(22)

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta2.blockSignSecret,
      forgerBox2,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength), Option(VrfGenerator.generateVrfOutput(0)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with no MainchainBlockReferenceData expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 3: valid block - no MainchainBlockReferenceData, parent is in the middle of the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength / 2), Option(VrfGenerator.generateVrfOutput(1)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with no MainchainBlockReferenceData expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 4: valid block - no MainchainBlockReferenceData, parent is at the beginning of the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(),  Seq(),
        WithdrawalEpochInfo(1, 0), Option(VrfGenerator.generateVrfOutput(2)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with no MainchainBlockReferenceData expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 5: valid block - with MainchainBlockReferenceData, that are in the middle of the epoch
    val (forgerBox5, forgerMeta5) = ForgerBoxFixture.generateForgerBox(3524)
    var mcRefs: Seq[MainchainBlockReference] = Seq(generateMainchainBlockReference(), generateMainchainBlockReference()) // 2 MC block refs
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data), // 2 MainchainBlockReferenceData
      Seq(),
      Seq(), // No MainchainHeaders - no need of them
      Seq(),
      forgerMeta5.blockSignSecret,
      forgerBox5,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 3), // lead to the middle index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(3)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that are in the middle of the epoch expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 6: valid block - without SC transactions and with MainchainBlockReferenceData, that lead to the end of the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 2), // lead to the last epoch index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(4)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that lead to the finish of the epoch expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 7: invalid block - without SC transactions and with MainchainBlockReferenceData, that lead to switching the epoch
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 1), // lead to the switching of the epoch
        Option(VrfGenerator.generateVrfOutput(5)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that lead to epoch switching expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 8: valid block - with SC transactions and MainchainBlockReferenceData, that are in the middle of the epoch
    val (forgerBox8, forgerMeta8) = ForgerBoxFixture.generateForgerBox(324)
    mcRefs = Seq(generateMainchainBlockReference(), generateMainchainBlockReference()) // 2 MC block refs

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data), // 2 MainchainBlockReferenceData
      Seq(getRegularTransaction.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]]), // 1 SC Transaction
      Seq(),
      Seq(),
      forgerMeta8.blockSignSecret,
      forgerBox8,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 3), // lead to the middle index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(5)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with SC transactions andMainchainBlockReferenceData that are in the middle of the epoch expected to be valid.", validator.validate(block, history).isSuccess)


    // Test 9: invalid block - with SC transactions and MainchainBlockReferenceData, that lead to the end of the epoch (no sc tx allowed)
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 2), // lead to the last epoch index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(6)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with SC transactions and MainchainBlockReferenceData that lead to the finish of the epoch expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 10: invalid block - with SC transactions and MainchainBlockReferenceData, that lead to switching the epoch (no sc tx and no switch allowed)
    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 1), // lead to the switching of the epoch
        Option(VrfGenerator.generateVrfOutput(7)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with SC transactions and MainchainBlockReferenceData that lead to the epoch switching expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 11: invalid block - with 1 MainchainBlockReferenceData with sc creation tx with declared sidechain creation output
    val scIdHex = "0000000000000000000000000000000000000000000000000000000000000001"
    val scId = new ByteArrayWrapper(BytesUtils.fromHexString(scIdHex))
    val mcBlockRefRegTestParams = RegTestParams(scId.data)
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcBlockRef = MainchainBlockReference.create(mcBlockBytes, mcBlockRefRegTestParams).get

    val (forgerBox11, forgerMeta11) = ForgerBoxFixture.generateForgerBox(32114)
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(mcBlockRef.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta11.blockSignSecret,
      forgerBox11,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    assertTrue("Sidechain non-genesis block with 1 MainchainBlockReferenceData with sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 12: invalid block - with 2 MainchainBlockReferenceData, the second one is with sc creation tx
    val (forgerBox12, forgerMeta12) = ForgerBoxFixture.generateForgerBox(31224)
    mcRefs = Seq(generateMainchainBlockReference(blockHash = Some(mcBlockRef.header.hashPrevBlock)), mcBlockRef)

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta12.blockSignSecret,
      forgerBox12,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    assertTrue("Sidechain non-genesis block with 2 MainchainBlockReferenceData, the second one with sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 13: invalid block - with 3 MainchainBlockReferenceData, the second one is with sc creation tx
    val (forgerBox13, forgerMeta13) = ForgerBoxFixture.generateForgerBox(32413)
    mcRefs = Seq(generateMainchainBlockReference(blockHash = Some(mcBlockRef.header.hashPrevBlock)), mcBlockRef, generateMainchainBlockReference(parentOpt = Some(new ByteArrayWrapper(mcBlockRef.header.hash))))
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta13.blockSignSecret,
      forgerBox13,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      mcBlockRefRegTestParams
    ).get

    assertTrue("Sidechain non-genesis block with 3 MainchainBlockReferenceData, the second one with sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: IllegalArgumentException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 14: valid block - with 2 MainchainBlockReferenceData, that lead to epoch ending, and 2 more MainchainHeaders
    val (forgerBox14, forgerMeta14) = ForgerBoxFixture.generateForgerBox(35274)
    mcRefs = Seq(generateMainchainBlockReference(), generateMainchainBlockReference(), generateMainchainBlockReference(), generateMainchainBlockReference()) // 4 MC block refs
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      mcRefs.take(2).map(_.data), // 2 MainchainBlockReferenceData
      Seq(),
      mcRefs.map(_.header), // 4 MainchainHeaders, from different withdrawal epochs
      Seq(),
      forgerMeta14.blockSignSecret,
      forgerBox14,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    Mockito.when(historyStorage.blockInfoOptionById(ArgumentMatchers.any[ModifierId]())).thenReturn({
      Some(SidechainBlockInfo(0, 0, null, 0, ModifierSemanticValidity.Valid, Seq(), Seq(),
        WithdrawalEpochInfo(1, withdrawalEpochLength - 2), // lead to the last epoch index -> no epoch switch
        Option(VrfGenerator.generateVrfOutput(7)), bytesToId(new Array[Byte](32))
      ))
    })
    assertTrue("Sidechain block with MainchainBlockReferenceData that lead to the finish of the epoch and 2 more MainchainHeaders expected to be valid.",
      validator.validate(block, history).isSuccess)
  }
}
