package com.horizen.validation

import java.time.Instant

import com.horizen.SidechainHistory
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.Box
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

  // Genesis MC block hex created in regtest by STF mc_blocks_data.py test on 02.12.2021
  val mcBlockHex: String = "03000000d21165164bcbde81cd402182536abbb981a7d99bed6368b7eaa44c67d78e7d0c10266cfcf9242e7b2730cc6ff4ebcdeeb2791bdf5fb89b0db5c6088f6607e89e2eced037bb77a5cbb8829f198e8137bf98c6bfffbe695f5a28c6f036e746262d6bd8a861f70e0f201100901d7b54088410abb50bee029b7549de46fa51c63d9b346a9df74804000024011bcf70397f9aeb8a1ee4dcb7230a5cd4e6273f4e5c32b5d97f8e3c4415b0292eee8dd80201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502a4010101ffffffff040e24b42c000000001976a914f333985088cfc3da23106792ab995cf5ac653bf588ac80b2e60e0000000017a914ea81ee2d877a25c7530a33fcf5a65c72f681250f87405973070000000017a914e7d25d82be231cf77ab8aecb80b6066923819ffc87405973070000000017a914ca76beb25c5f1c29c305a2b3e71a2de5fe1d2eed8700000000fcffffff0e8de3b0d1128475943c8e1dc52f5cd8e0339f2bc634539382282e2a4df3ad4fe3000000006a4730440220323a754652c43aa97db5c93b99a6eeb6bae4c501d933ea4b348f051ad8a8702602206a37ed3c3e6cff746040d82a4011cbef4921717b25de32b8056e7aba98f6317d012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff7c07c55a111ccb91f63cbeff0a8dcc869bfaf0c51aebdd8a643df326cccd5cbe000000006a473044022079c2d0405b26896720a8bbc6440993c4bb08867dd4578a041b7e6ec24a74ccd902205b2b4519f20479258793bdc5c473305da3a4dc963748542290157143a815609c012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff7c3af7ec13394b332dabdbe35bb6eab22f043add489276da613e414c3acb97cc000000006b483045022100eed46ec67d1cff279409236abb8dc300496c09957d677899ac79d4888309318302202d5bb754ca99258aa57973b601d52b8d3797c3fe003bb96acfb823b749362c49012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff7ce03f0c3f76dda13f2bee75e76a7d9d3b78d0e7fceaf7a9c88afe12f5935807000000006b48304502210093a3a0a386d1d4e3b537adacf23b61102be402b323827483a2692ee53406ce650220084105978f47d516d2fce6c97df17e05a034c3ff916b3057d820db096d8bfe7c012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff7d27a5d6e7e6824c238289816ecb0dcfd9936fabe8821a2730a78fd7f7deb693000000006b483045022100a8890895c85f410c4fef29db36312fe9bba2507d9a764957334045cee6cee2c502205560ae2fd8e926f07cf235b92752c3865ad04cfb9cd8cc9e30ee24418b3517de012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff8266cc4b73f4ca931a15e23b3be30d75dd03921e7a481298eca9d9b1ae2221a1000000006a47304402207b7819b1fc5247693b9f454a516397b82eca8733587a68bf80dde38e3dc577900220041cd681f4d3057ac3194431f617545a08ab667d9a5abf1b6f48cd4c9280ea7f012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff83c44044e04b6c80941f69472aedf5566f748c5ae9fa9ae94b58c271435c2821000000006a47304402203796b0b035a63200614866c34a967ebefd011add0e9ed0d86cc063383bfaa8dd02201f6069ee6dbbdb42eb6a1929560c4be024cb3256dca37416f8bc3e43d0ab7686012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff844b4b7090f1498a360d4cda7bf67b6d1db8a179c173e06ec255bb8e4aa25bfd000000006a47304402200be4d8195f26990fbc9e6655aacc87ca5cce902a23f9fcd4511252e9aa5acd6a022047295ce6f9ba22ebff733cf89e84601f98376530da83cae1c9f1cad67c10b1ee012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff8591397e664092d2d3fe81c8cb2b375a0e03e2613853c93c481a67e8b9747934000000006b483045022100d8954f35e88d1e0aae5771aa226b2527cc57b06295c4e3adda13c99459ffecb0022050e87255c1bb65327e7cdf298afb718d68ec6c8b28f4c7ad76f05426f5dde8e1012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff86a8d6f4df25ea5a8b076f6b0689f65f245dca3dbae4d64e45ba663c93dd198e000000006a4730440220056f7ff6dff2aafec54d60603ac5ae3a2d91744e069388156a66fb295834238b02207be0b0a89955ab4810b41baccaede1524188eeb37a2830c5df8f8daa909c8b02012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff8ae1859562be46cf1a73c512c83f50d94d4347604f1b581b773a80068ec92f54000000006a47304402202b4fd5e7cf130e27f4f6e74fe79ee5a01cb793cc4f7241bb17cc7b5719fb77ac022076bc52efaa3bf854588f3158692d6b4fd9a12055eef355be963659a1a2c008ae012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff7bb727b508c4d4af7323ae204e4287f6e64400b5167053c8b378dd6d7e47d1df000000006b483045022100cecf209232006e3936f8375d14428af772fc2d16f7243bc69d63d946a818fc5702203a65e102998425bd14552d276ac8a63d6526af46314c56e30d2adef51e02de19012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff90cfc8db074cf841dfd0d48674efe3b45c235a88d57a9d07b48b5b9c769e3258000000006b483045022100d4b9119743e45217b9867fa177512d02534a0cff6e776e38bd33efa3f1308999022002aab5e15a5ef6aad3808e1b77ec90874773167248ed752d831c3e2c1aab2098012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff9557e9a22938b56a04899db10c4b50f6ff0c8ac0c604ececb9fb070e275750e6000000006b4830450221008a110fb9a06795c5709874abdcbaf4363ec43b40c3a3921b4a701e9519f847a402203fcd56af41d99c320dcb753c65df57b33f880eb50bac94b664fb68693365163f012103daaa5a8ffce95f5c2821dea1b93d13f17c4d26831ad6da5f744fdc34587286f4ffffffff017258cd1d000000003d76a914f333985088cfc3da23106792ab995cf5ac653bf588ac205cea48c86d1d2ccae56514c2c8cf9b45456e6b0bc56b87e8fe574e39ad34040c0177b40001e803000000e40b5402000000acb24b2f08e8e56fe1784f1600879c697c7cd90846e076724b090fd72206b1a5219ab43ef30775c343b43a291c5ee40c355e102e3fcf4703428948ae3d8252162480012067d15353aafb79aae543884ef931d585c2ce7e4992fc2d3297de274c97229028fdcd0102386700000000000004000000000000003c67000000000000bfbc0100000000000c00000000000000016234e8c194d555b5ff6a082286b8241f7a1bc3d4dd71e8a88e6db8729a3de324000001c8bbcabb872abdd97870980b4020f7d6a69bd8650298c0e8984299f6ddc9eb35800001e0b56d7145dd7b006e0d9b2ea1b5bf65c26dafc33dd36fc7675eeb58ba247613800001101c1881ee4e14ce72f762869875980e3e338ce353efd0469d2d74b6254fcc0c0000015e77dbb1889b9d36cb1f345b4290894b5d27c4fea13c19ff202cbffc54d78123800001cadc641bb884c20af1d99f02c6fdb3bc837e8a436a7710252165ec6880a8d23f8000013b43af52de1e9c5e4eb52414ff12c7ec15bc499b83395dea1351640e778bf228000001c3913d5f157b058abe12eda99febaa5bf8cd3eecd768497a12112f22dc3cdd3e000001bb2e8f15c078157fbdd179843702ff55e432cac0fa60ba7459ed918f2c2df91c000001ebcda0614fa4946d2a109bd5871a10966510b9e7fa4e05d2848a4cbe49c46f1480000191aa89e90907e0c714f7c0d931dc6707d7ff2ab4e85f79769b8392d99bc95d1e000001a0877645bea04a236ec766cc0b2439c22c4c8e0fca9755ed070a1d21c4273414800001fdcd0102d7160000000000000400000000000000db16000000000000a3a40000000000000c0000000000000001240b188009319f7b6d12739001b47917cad72fde9d0ffaa7dabf7836b46bb4300000014f0c379235a1038e5851edd33a6586a8f714c4ec898ddcd066f7ae43b7a0f317800001a5efe5004f93193ec208703cd2b9b97d4a323ba0f7b7c1454ba435a3de51b130000001a98bfe4439a478c0dddf5e75f504e21f7681a022253a19bc9b8801c59e3aef39800001487a4d0201582fe4d8eafa36fc5d8d65dc9620fdfa204f19e63cd3a69962ec3c800001263ca5ab720ee91d4db9b668a8fb3304453ac58fef70587681c567b1b27fa332800001fa0c2b7b756fab8968631267404b93c76baf329f05be068f1397e728c2a5960e80000121483d74799c0d77720e12a913ebae05014d521b0dea4235263ccd1b5c97a50c000001b3c7a90ddf7f74f4f24013a6ab97f5b791da9bb0dd109c079db6ff8511d2233780000175a25117ecd062b6eff590623ad2ad97f71afb4ba7f1951b4ba97cb49f87ae3200000171543933865335ccc19f7162f34eb43cd4554b2a61ab33c011ea66b60d2cfc1a0000013799cf00e1c8194ea1195f047330f55056d713b15448565e9158cf37a644153e800002ffff00000000000000000000000000000000000000000000000000"
  val scIdHex = "1a6d30d71c7e09bb4f06e8738d8b5d1a41c4edc275296daeb911330137d0d656"

  @Before
  def setUp(): Unit = {
    Mockito.when(history.storage).thenReturn(historyStorage)
  }

  @Test
  def genesisBlockValidation(): Unit = {
    val validator = new WithdrawalEpochValidator(params)

    // Test 1: invalid genesis block - no MainchainBlockReferenceData
    val (forgerBox1, forgerMeta1) = ForgerBoxFixture.generateForgerBox(32)
    var block: SidechainBlock = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta1.blockSignSecret,
      forgerMeta1.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta2.blockSignSecret,
      forgerMeta2.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta3.blockSignSecret,
      forgerMeta3.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
    ).get

    Mockito.when(params.sidechainGenesisBlockId).thenReturn(block.id)
    assertTrue("Sidechain genesis block with 1 MainchainBlockReferenceData without sc creation inside expected to be invalid.", validator.validate(block, history).isFailure)
    validator.validate(block, history).failed.get match {
      case _: NoSuchElementException =>
      case e => assertTrue("Different exception type expected, got %s".format(e.getClass.getName), false)
    }


    // Test 4: valid genesis block with 1 MainchainBlockReferenceData with sc creation tx with INVALID withdrawalEpochLength (different to the one specified in params)
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex)))
    val mcBlockRefRegTestParams = RegTestParams(scId.data)
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcBlockRef = MainchainBlockReference.create(mcBlockBytes, mcBlockRefRegTestParams).get
    mcRefs = Seq(mcBlockRef)

    val (forgerBox4, forgerMeta4) = ForgerBoxFixture.generateForgerBox(324)
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      mcRefs.map(_.header),
      Seq(),
      forgerMeta4.blockSignSecret,
      forgerMeta4.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta1.blockSignSecret,
      forgerMeta1.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta2.blockSignSecret,
      forgerMeta2.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data), // 2 MainchainBlockReferenceData
      Seq(),
      Seq(), // No MainchainHeaders - no need of them
      Seq(),
      forgerMeta5.blockSignSecret,
      forgerMeta5.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data), // 2 MainchainBlockReferenceData
      Seq(getRegularTransaction.asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]]), // 1 SC Transaction
      Seq(),
      Seq(),
      forgerMeta8.blockSignSecret,
      forgerMeta8.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex)))
    val mcBlockRefRegTestParams = RegTestParams(scId.data)
    val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
    val mcBlockRef = MainchainBlockReference.create(mcBlockBytes, mcBlockRefRegTestParams).get

    val (forgerBox11, forgerMeta11) = ForgerBoxFixture.generateForgerBox(32114)
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(mcBlockRef.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta11.blockSignSecret,
      forgerMeta11.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta12.blockSignSecret,
      forgerMeta12.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta13.blockSignSecret,
      forgerMeta13.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      mcRefs.take(2).map(_.data), // 2 MainchainBlockReferenceData
      Seq(),
      mcRefs.map(_.header), // 4 MainchainHeaders, from different withdrawal epochs
      Seq(),
      forgerMeta14.blockSignSecret,
      forgerMeta14.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion
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
