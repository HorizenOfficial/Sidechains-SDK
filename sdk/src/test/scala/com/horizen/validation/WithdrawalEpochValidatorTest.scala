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

  // Genesis MC block hex created in regtest by STF sc_bootstrap.py test on 01.06.2021
  val mcBlockHex: String = "030000009bc83df13e66a80aceb53007db2a6c631cffc4d0714dfc26969104a2fa0b8f064cb5e7ed1591f35e2ad680ec2bdb4a1e4a472f1f2b7674175ebe32f1c2e305f15447785754469c34f3044fe8ddf825916f8017dfb3afedce1a3a6ff870a60d30f927b660030f0f2005008c5d2d185fe3665516070bec32fb2773045f7fba21cd092823e164ae0000240a46209311d6309d143e7cd7544aee6b17f7186e562d553f495ad732c450d42afdd745f40201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dc000101ffffffff04da20b42c000000001976a914bdbef23725aa04893cece385d80baf5ae775cf0288ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff095833fe3031763d3425d037d5bb45d18192ed36118de9a3e9588af7f96646c3cb000000006a4730440220466745be0f6665f74bf1bcb814b6c7d7404a4f49872a04fad8fead41e5fdced60220121c874e79c1b35697fc877a4649606cc658b74ccb72df138b9e4f3f4e9ebc4f01210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff08b790eef51bbdb46e4c1719fc7d25847298132f9a95c15b7e6f9e2a2c541e6e000000006b48304502210088e177d863d89fdcf22e29f6093cf1b4fba3d7451daa856bc6890d1fdc32a2e7022011314f14bd24e9b953416f953e7532801aa89082a7983e23db509ebff8b07a3401210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197fefffffff663819d3337940dde5697a86c418d3a452085601b12fa855c42f3d94dde8054000000006a4730440220300d522429faafcf675a64da33561385663731eb8084fe5477e85d65f5d394b2022029a30083b5ae89a7900db6804b915378b67e2be0ccbb9e8d1b2f8e90faadd20b01210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff42c105768ad3759de038bc2f8b5b9716414cf59a3426ca64cc8e4d4d1e59370f000000006b483045022100bc55db6e37832d58d1ec89e56ae29041ffb2c28121a2913a653209f1519deab6022031e33164f2c91f96e4289b22ba7025b5bdca3d73951c54c7fd1e4a39f764569701210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff1dea5bf1398e4a96363bc8d11f6442f31d6c2962788a173f4f67a465bfb0009b000000006b483045022100d687f9edf25d09a50af11d182122fdb46a0ace25a2fbe7aefedf2c38b8ba273302203223483b9cd5782ef5d1b7306fc36770213722a8d418a25fbbad33758e3533b901210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff7fba1a938fcda6335d0ee234e7efd03bf52198aceeb7fb24b2052e18e41c75a2000000006a47304402205e404de5a60acc973db0750e14efcba5bbbed1510c27af4bd45acfc0e172486202204a61dea05dd63442bc55777443e293d8156f1367735d1fb02fb01ef97b3e098601210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff7e5ddfc882e825032f61a06940e41ee537cef1d234aa123f9bf4d9c78e6f97ae000000006a47304402201ee4ac01d9f5bb7d676d867311383172ddb5e0c14b2e5457ae3221891ff2015802203ddc30bee5ec98852165b402d49ad442a6bedf167020fd1758103ba461ff934801210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff75f7acf5074453e029f98a2c6561a6437146cb331bb007c241f5e3f4e134e91a000000006a4730440220051cf52cdbc6a3b1dc31dcdabf385368ccec9f6d0bb1c67ccfab938867f627f5022043ae888c522e0f7e1ca8c3cb698eca27dbe44b6402c604b9513cbaf30786634301210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffffd627b8b5cddc7c26f219a591f60628d577158978191187db385da19fb7198244000000006a47304402204cfbb07a0eb5b4341278ef8e076fba026f1f832ff7fd4afce9aa13f1f003f87b02207780916d69352f5b4750f873db141f86375726165074e38939b90513181ff6ff01210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff01e66e7d01000000003c76a914f73d9399c7bfc956daec93b94725768733e74acb88ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b40001e803000000e40b5402000000acb24b2f08e8e56fe1784f1600879c697c7cd90846e076724b090fd72206b1a521894dba38c1b29a58900abac445c01201f677615f741773a6a5c8c8c9c705842b8001205ebec91ed4b34dba38c88f072be677d55932086185d0cb27542d387ab7ddd337fd51030231840000000000003184000000000000ec230200000000000c000000000000000289ee1d3e10eb2b9371398f343e5103e098555f9c016bdda911c4925ede85ea35804cac36c9112c4eed9ee8e305ecadb141cf74c354fdbc25186d760dcdd224f9190000027b364cb46a3fccf2798a4a3e7c12620e6ecefa2047cbd5ca2b801670fcce663880799badd0ed62419d24b71a26390ccc2283884a51da6b9e67e61b09116bb3fb0b800002dea9d31b7d4e1d749199a28a422b23087ab6d189adf452bc04bc7183cad55600004b1ca68282d835c71a74ae432d4ea78cb083a0e40257559f99c865ef9dbe8b2b000002d76cea2dd78ef1eb68281837ec8abfb3919212df8a21aea34eff5c58cb132817806067fbcd4ac4414cc96fba953a03d20689fac637adfbcfe366b7e04b684d4f2e800002f8062a62e9d101dd7854e9451e176129ebb27ec79219bfbafd4abf78d239e51f805a7ea318b15237db138e9d8520a0126357bcf1a64aee88f94957ffffa1f06c198000024eeee6aa2656f25b3513b9c2b0988d82d0778de2e6fa02811d1c78517ee14c0200e34fa134dff33b749771375f1caeaa2e3482c57febf0855620208510fbe2fd33800002952560eb67c3c1d28183bada0ca1553c2e8f2a9e7e6ebb17c9427d259cb7cd2a80bea17528344a1fa934dae0c27a23b6f36821795ddd2e96060b2abe1ef4bfb22200000236d51c2f6800f6a3ffd095c066d34a9a922b144fc6144ff08922570387d2e03980ee9870e6b200611522e50731f6baeab7607acdf5f3df9ee57eadc514182044218000024a401f7048b4f52d9132ad304debbb7f5b9e24b90590636f78b086769a9e2e3b008ff02b3d3d945af0ab6e2c1f162156422e718f5b4f007e9e2473a38f126b5e190000028d44bba3d03a691d378c01b2c49b02ee8d89dc5549c1da8080d7099fe0eb381f8032670e46621cd77f48e944553e2af43dd227f9c2f87e7f0227d3ed687c41e426800002c040b38baab12f1aec8b4679e60f721e0641139e0da28b3fb483c8893d952a3380adc51c8fae4e32c2dd8728098751a5f39a48676ad4639c001099deea69bf0e35800002383166c101405262498fbdd30dacade60c525350ebcc13b55cb663131c37ef30002373b397189d93fa820d92f194f693ea91414e03a368089bbc0513a3db642405800000000000000000000000000000000000000000000000c100000000"

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
    val scIdHex = "10eaeed096570c6d97c1b3bfb5edda550dcfc070fce0c0563afe78431e5971c0"
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex)))
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
      Instant.now.getEpochSecond - 10000,
      mcRefs.map(_.data), // 2 MainchainBlockReferenceData
      Seq(getRegularTransaction.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]]), // 1 SC Transaction
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
    val scIdHex = "10eaeed096570c6d97c1b3bfb5edda550dcfc070fce0c0563afe78431e5971c0"
    val scId = new ByteArrayWrapper(BytesUtils.reverseBytes(BytesUtils.fromHexString(scIdHex)))
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
