package io.horizen.utxo.integration

import io.horizen._
import io.horizen.block.MainchainBlockReferenceData
import io.horizen.consensus._
import io.horizen.fixtures.{SecretFixture, SidechainTypesTestsExtension, StoreFixture, TransactionFixture}
import io.horizen.fork.{ConsensusParamsFork, ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.params.MainNetParams
import io.horizen.proposition.Proposition
import io.horizen.secret.PrivateKey25519
import io.horizen.utils.{ByteArrayWrapper, BytesUtils, TimeToEpochUtils, WithdrawalEpochInfo, Pair => JPair}
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.box.data.{BoxData, ForgerBoxData, ZenBoxData}
import io.horizen.utxo.box.{Box, ForgerBox, WithdrawalRequestBox, ZenBox}
import io.horizen.utxo.companion.SidechainBoxesCompanion
import io.horizen.utxo.customtypes.DefaultApplicationState
import io.horizen.utxo.state.{SidechainState, SidechainStateUtxoMerkleTreeProvider, SidechainUtxoMerkleTreeProviderCSWDisabled, SidechainUtxoMerkleTreeProviderCSWEnabled}
import io.horizen.utxo.storage.{SidechainStateForgerBoxStorage, SidechainStateStorage, SidechainStateUtxoMerkleTreeStorage}
import io.horizen.utxo.transaction.RegularTransaction
import io.horizen.utxo.utils.{BlockFeeInfo, FeePaymentsUtils}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.{bytesToId, bytesToVersion}

import java.io.{File => JFile}
import java.nio.charset.StandardCharsets
import java.util.{ArrayList => JArrayList, HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class SidechainStateIntegrationTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypesTestsExtension
{
  val sidechainBoxesCompanion = SidechainBoxesCompanion(new JHashMap())
  val applicationState = new DefaultApplicationState()

  var stateStorage: SidechainStateStorage = _
  var stateForgerBoxStorage: SidechainStateForgerBoxStorage = _
  var initialVersion: ByteArrayWrapper = _
  var sidechainUtxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider = _

  var initialForgerBoxes: Seq[ForgerBox] = _
  val boxList = new ListBuffer[SidechainTypes#SCB]()
  val secretList = new ListBuffer[PrivateKey25519]()
  val params = MainNetParams()

  val initialWithdrawalEpochInfo = WithdrawalEpochInfo(1, params.withdrawalEpochLength - 1)
  val initialConsensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(1)

  val initialBlockFeeInfo: BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("1234".getBytes(StandardCharsets.UTF_8)).publicImage())

  val simpleForkConfigurator = new SimpleForkConfigurator
  ForkManagerUtil.initializeForkManager(simpleForkConfigurator, "mainnet")

  def getRegularTransaction(zenOutputsCount: Int, forgerOutputsCount: Int): RegularTransaction = {
    val outputsCount = zenOutputsCount + forgerOutputsCount

    val from: JList[JPair[ZenBox,PrivateKey25519]] = new JArrayList[JPair[ZenBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition, _ <: Box[_ <: Proposition]]] = new JArrayList()
    var totalFrom = 0L


    for (b <- boxList) {
      if(b.isInstanceOf[ZenBox]) {
        from.add(new JPair(b.asInstanceOf[ZenBox],
          secretList.find(_.publicImage().equals(b.proposition())).get))
        totalFrom += b.value()
      }
    }

    val minimumFee = 5L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L

    for(s <- getPrivateKey25519List(zenOutputsCount).asScala) {
      val value = maxTo / outputsCount
      to.add(new ZenBoxData(s.publicImage(), value))
      totalTo += value
    }

    for(s <- getPrivateKey25519List(forgerOutputsCount).asScala) {
      val value = maxTo / outputsCount
      to.add(new ForgerBoxData(s.publicImage(), value, s.publicImage(), getVRFPublicKey(totalTo)))
      totalTo += value
    }

    val fee = totalFrom - totalTo

    RegularTransaction.create(from, to, fee)
  }

  @Before
  def setUp(): Unit = {
    secretList.clear()
    secretList ++= getPrivateKey25519List(10).asScala

    boxList.clear()
    boxList ++= getZenBoxList(secretList.asJava).asScala.toList

    // Initialize forger boxes: first two must be aggregated to the ForgingStakeInfo
    val vrfPubKey1 = getVRFPublicKey(112233L)
    val vrfPubKey2 = getVRFPublicKey(445566L)
    initialForgerBoxes = Seq(
      getForgerBox(secretList.head.publicImage(), 100L, 100L, secretList.head.publicImage(), vrfPubKey1),
      getForgerBox(secretList.head.publicImage(), 100L, 200L, secretList.head.publicImage(), vrfPubKey1),
      getForgerBox(secretList.head.publicImage(), 100L, 700L, secretList.head.publicImage(), vrfPubKey2)
    )
    boxList ++= initialForgerBoxes.asInstanceOf[Seq[SidechainTypes#SCB]]

    // Init SidechainStateStorage with boxList
    val tmpDir = tempDir()
    val stateDir = new JFile(s"${tmpDir.getAbsolutePath}/state")
    stateDir.mkdirs()

    initialVersion = getVersion

    stateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion, params)
    stateStorage.update(
      initialVersion,
      initialWithdrawalEpochInfo,
      boxList.toSet,
      Set(),
      Seq[WithdrawalRequestBox](),
      initialConsensusEpoch,
      None,
      initialBlockFeeInfo,
      None,
      scHasCeased = false,
      new Array[Int](0),
      0
    )

    // Init SidechainStateForgerBoxStorage with forger boxes
    val stateForgerBoxesDir = new JFile(s"${tmpDir.getAbsolutePath}/stateForgerBoxes")
    stateForgerBoxesDir.mkdirs()
    val forgerBoxesStore = getStorage(stateForgerBoxesDir)

    stateForgerBoxStorage = new SidechainStateForgerBoxStorage(forgerBoxesStore)
    stateForgerBoxStorage.update(
      initialVersion,
      initialForgerBoxes,
      Set()
    )

    val stateUtxoMerkleTree = new JFile(s"${tmpDir.getAbsolutePath}/utxoMerkleTree")
    stateUtxoMerkleTree.mkdirs()
    val utxoMerkleTreeStore = getStorage(stateUtxoMerkleTree)
    val stateUtxoMerkleTreeStorage = new SidechainStateUtxoMerkleTreeStorage(utxoMerkleTreeStore)
    stateUtxoMerkleTreeStorage.update(
      initialVersion,
      boxList, // All boxes in the list are coin boxes
      Set()
    )

    sidechainUtxoMerkleTreeProvider =  SidechainUtxoMerkleTreeProviderCSWEnabled(stateUtxoMerkleTreeStorage)

  }

  @Test
  def closedBoxes(): Unit = {
    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, stateForgerBoxStorage, sidechainUtxoMerkleTreeProvider, params, applicationState).get

    // Test that initial boxes list present in the State
    for (box <- boxList) {
      assertEquals("State must return existing box.",
        box, sidechainState.closedBox(box.id()).get)

      assertEquals("State contains different box for given key.",
        box, sidechainState.getClosedBox(box.id()).get)
    }

  }

  @Test
  def currentConsensusEpochInfo(): Unit = {
    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, stateForgerBoxStorage, sidechainUtxoMerkleTreeProvider, params, applicationState).get

    // Test that initial currentConsensusEpochInfo is valid
    val(modId, consensusEpochInfo) = sidechainState.getCurrentConsensusEpochInfo
    assertEquals("Consensus epoch info modifier id should be different.", bytesToId(initialVersion.data), modId)
    assertEquals("Consensus epoch info epoch number should be different.", initialConsensusEpoch, consensusEpochInfo.epoch)
    assertEquals("Consensus epoch info stake ids merkle tree size should be different.",
      2, consensusEpochInfo.forgingStakeInfoTree.leaves().size())
    // Note we expect to have deterministic order of leaves: by total stake decreasing
    val expectedMerkleTreeLeaves = Seq(
      ForgingStakeInfo(
        initialForgerBoxes.last.blockSignProposition(),
        initialForgerBoxes.last.vrfPubKey(),
        initialForgerBoxes.last.value()
      ).hash,
      ForgingStakeInfo(
        initialForgerBoxes.head.blockSignProposition(),
        initialForgerBoxes.head.vrfPubKey(),
        initialForgerBoxes.head.value() + initialForgerBoxes(1).value()
      ).hash
    )
    assertEquals("Consensus epoch info stake ids merkle tree leaves are wrong.",
      expectedMerkleTreeLeaves.map(new ByteArrayWrapper(_)),
      consensusEpochInfo.forgingStakeInfoTree.leaves().asScala.map(new ByteArrayWrapper(_)))

    assertEquals("Consensus epoch info epoch total stake should be different.",
      initialForgerBoxes.map(_.value()).sum, consensusEpochInfo.forgersStake)
  }

  @Test
  def feePayments(): Unit = {
    // Create sidechainState with initial block applied.
    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, stateForgerBoxStorage, sidechainUtxoMerkleTreeProvider, params, applicationState).get

    // Collect and verify getFeePayments value
    val withdrawalEpochNumber: Int = initialWithdrawalEpochInfo.epoch
    val feePayments: Seq[ZenBox] = sidechainState.getFeePayments(withdrawalEpochNumber)

    assertEquals(s"Fee payments for epoch $withdrawalEpochNumber size expected to be different.",
      1, feePayments.size)

    val nonce: Long = SidechainState.calculateFeePaymentBoxNonce(withdrawalEpochNumber, 0)
    val expectedFeePaymentBox: ZenBox = new ZenBox(new ZenBoxData(initialBlockFeeInfo.forgerRewardKey, initialBlockFeeInfo.fee), nonce)
    assertEquals(s"Fee payments for epoch $withdrawalEpochNumber expected to be different.",
      Seq(expectedFeePaymentBox), feePayments)
  }

  def commonApplyModifierTest(sidechainState: SidechainState, transactionList: ListBuffer[RegularTransaction] ): SidechainState = {

    // Test applyModifier with a single RegularTransaction with zen and forger outputs
    val mockedBlock = mock[SidechainBlock]

    val forgerOutputsAmount = transactionList.head.newBoxes().asScala.filter(_.isInstanceOf[ForgerBox]).foldLeft(0L)(_ + _.value())

    val newVersion = getVersion

    val consensusSecondsInSlot = 120

    Mockito.when(mockedBlock.id)
      .thenReturn(bytesToId(newVersion.data))

    Mockito.when(mockedBlock.timestamp)
      .thenReturn(params.sidechainGenesisBlockTimestamp + consensusSecondsInSlot)
    
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(720, consensusSecondsInSlot)),
    ))
    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp)))

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.sidechainTransactions)
      .thenReturn(Seq())

    Mockito.when(mockedBlock.parentId)
      .thenReturn(bytesToId(initialVersion.data))

    // Mock 1 MCBlockRefData entry to simulate the last withdrawal epoch SidechainBlock
    // to check that fee payment boxes were created and appended to closed boxes.
    // to check that utxo merkle tree root was stored for the given epoch
    Mockito.when(mockedBlock.mainchainBlockReferencesData)
      .thenReturn(Seq[MainchainBlockReferenceData](mock[MainchainBlockReferenceData]))

    Mockito.when(mockedBlock.topQualityCertificateOpt).thenReturn(None)

    val blockFeeInfo = BlockFeeInfo(307, getPrivateKey25519("mod".getBytes(StandardCharsets.UTF_8)).publicImage())
    Mockito.when(mockedBlock.feeInfo).thenReturn(blockFeeInfo)

    Mockito.when(mockedBlock.feePaymentsHash).thenAnswer(_ => {
      val feePayments = sidechainState.getFeePayments(initialWithdrawalEpochInfo.epoch, Some(blockFeeInfo))
      FeePaymentsUtils.calculateFeePaymentsHash(feePayments)
    })

    // Check that there is no record for utxo merkle tree before applying the last block of the withdrawal epoch
    assertTrue("No utxo merkle tree root expected to be found before finishing the epoch: " + initialWithdrawalEpochInfo,
      sidechainState.utxoMerkleTreeRoot(initialWithdrawalEpochInfo.epoch).isEmpty)

    // Check that SC is alive
    assertFalse("SC must be alive", sidechainState.hasCeased)

    val applyTry = sidechainState.applyModifier(mockedBlock)
    assertTrue("ApplyChanges for block must be successful.",
      applyTry.isSuccess)

    val sidechainStateAfterApplyModifier = applyTry.get

    assertEquals(s"State storage version must be updated to $newVersion",
      bytesToVersion(newVersion.data), sidechainStateAfterApplyModifier.version)

    assertEquals("Rollback depth must be 2.",
      2, sidechainStateAfterApplyModifier.maxRollbackDepth)

    for (b <- transactionList.head.newBoxes().asScala) {
      assertTrue("Box in state after applyModifier must contain newBoxes from transaction.",
        sidechainStateAfterApplyModifier.closedBox(b.id()).isDefined)
    }

    for (b <- transactionList.head.unlockers().asScala.map(_.closedBoxId())) {
      assertTrue("Box in state after applyModifier must not contain unlocked boxes from transaction.",
        sidechainStateAfterApplyModifier.closedBox(b).isEmpty)
    }

    // Test that currentConsensusEpochInfo was changed
    val (modId, consensusEpochInfo) = sidechainStateAfterApplyModifier.getCurrentConsensusEpochInfo
    assertEquals("Consensus epoch info modifier id should be different.", bytesToId(newVersion.data), modId)
    assertEquals("Consensus epoch info epoch number should be different.", 2, consensusEpochInfo.epoch)
    assertEquals("Consensus epoch info stake ids merkle tree size should be different.",
      3, consensusEpochInfo.forgingStakeInfoTree.leavesNumber)
    assertEquals("Consensus epoch info epoch total stake should be different.",
      initialForgerBoxes.map(_.value()).sum + forgerOutputsAmount, consensusEpochInfo.forgersStake)


    // Test that getFeePayments changed after modifier was applied
    val withdrawalEpochNumber: Int = initialWithdrawalEpochInfo.epoch
    val feePayments: Seq[ZenBox] = sidechainStateAfterApplyModifier.getFeePayments(withdrawalEpochNumber)

    assertEquals(s"Fee payments for epoch $withdrawalEpochNumber size expected to be different.",
      2, feePayments.size)
    assertTrue(s"Fee payments box ${BytesUtils.toHexString(feePayments.head.id())} for epoch $withdrawalEpochNumber expected to be closed.",
      sidechainStateAfterApplyModifier.closedBox(feePayments.head.id()).isDefined)
    assertTrue(s"Fee payments box ${BytesUtils.toHexString(feePayments(1).id())} for epoch $withdrawalEpochNumber expected to be closed.",
      sidechainStateAfterApplyModifier.closedBox(feePayments(1).id()).isDefined)

    val poolPayments: Long = Math.ceil((initialBlockFeeInfo.fee + blockFeeInfo.fee) * (1 - params.forgerBlockFeeCoefficient)).longValue()

    val nonce1: Long = SidechainState.calculateFeePaymentBoxNonce(withdrawalEpochNumber, 0)
    // Simplified version of fee computation with lower precision, but is quite enough for the tests.
    val payment1: Long = (initialBlockFeeInfo.fee * params.forgerBlockFeeCoefficient).longValue() + poolPayments / 2 + poolPayments % 2
    val expectedFeePaymentBox1: ZenBox = new ZenBox(new ZenBoxData(initialBlockFeeInfo.forgerRewardKey, payment1), nonce1)

    val nonce2: Long = SidechainState.calculateFeePaymentBoxNonce(withdrawalEpochNumber, 1)
    // Simplified version of fee computation with lower precision, but is quite enough for the tests.
    val payment2: Long = (blockFeeInfo.fee * params.forgerBlockFeeCoefficient).longValue() + poolPayments / 2
    val expectedFeePaymentBox2: ZenBox = new ZenBox(new ZenBoxData(blockFeeInfo.forgerRewardKey, payment2), nonce2)

    assertEquals(s"Fee payments for epoch $withdrawalEpochNumber expected to be different.",
      Seq(expectedFeePaymentBox1, expectedFeePaymentBox2), feePayments)

    sidechainStateAfterApplyModifier
  }



  @Test
  def applyModifierWithCSWEnabled(): Unit = {
    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, stateForgerBoxStorage, sidechainUtxoMerkleTreeProvider, params, applicationState).get

    val transactionList = new collection.mutable.ListBuffer[RegularTransaction]()
    transactionList.append(getRegularTransaction(2, 1))
    val sidechainStateAfterApplyModifier = commonApplyModifierTest(sidechainState,transactionList)

    // Test that utxo merkle tree root was stored
    assertTrue("Utxo merkle tree root expected to be found after finishing the epoch: " + initialWithdrawalEpochInfo,
      sidechainStateAfterApplyModifier.utxoMerkleTreeRoot(initialWithdrawalEpochInfo.epoch).isDefined)

    // Test rollback
    val rollbackTry = sidechainStateAfterApplyModifier.rollbackTo(bytesToVersion(initialVersion.data))

    assertTrue("Rollback must be successful.",
      rollbackTry.isSuccess)

    val sidechainStateAfterRollback = rollbackTry.get
    assertEquals(s"State storage version must be rolled back to $initialVersion",
      bytesToVersion(initialVersion.data), sidechainStateAfterRollback.version)

    assertEquals("Rollback depth must be 1.",
      1, sidechainStateAfterRollback.maxRollbackDepth)

    for (b <- transactionList.head.newBoxes().asScala) {
      assertTrue("Box in state after applyModifier must not contain newBoxes from transaction.",
        sidechainStateAfterRollback.closedBox(b.id()).isEmpty)
    }

    for (b <- transactionList.head.unlockers().asScala.map(_.closedBoxId())) {
      assertTrue("Box in state after applyModifier must contain unlocked boxes from transaction.",
        sidechainStateAfterRollback.closedBox(b).isDefined)
    }

    assertTrue("Utxo merkle tree root was not rollback",
      sidechainStateAfterApplyModifier.utxoMerkleTreeRoot(initialWithdrawalEpochInfo.epoch).isEmpty)

  }

  @Test
  def applyModifierWithCSWDisabled(): Unit = {
    val sidechainState: SidechainState = SidechainState.restoreState(stateStorage, stateForgerBoxStorage,
                      SidechainUtxoMerkleTreeProviderCSWDisabled(), MainNetParams(isCSWEnabled = false), applicationState).get

    val transactionList = new collection.mutable.ListBuffer[RegularTransaction]()
    transactionList.append(getRegularTransaction(2, 1))
    val sidechainStateAfterApplyModifier = commonApplyModifierTest(sidechainState,transactionList)

    // Test that utxo merkle tree root was not stored
    assertFalse("Utxo merkle tree root expected to be None after finishing the epoch: " + initialWithdrawalEpochInfo,
      sidechainStateAfterApplyModifier.utxoMerkleTreeRoot(initialWithdrawalEpochInfo.epoch).isDefined)


    // Test rollback
    val rollbackTry = sidechainStateAfterApplyModifier.rollbackTo(bytesToVersion(initialVersion.data))

    assertTrue("Rollback must be successful.",
      rollbackTry.isSuccess)

    val sidechainStateAfterRollback = rollbackTry.get

    assertEquals(s"State storage version must be rolled back to $initialVersion",
      bytesToVersion(initialVersion.data), sidechainStateAfterRollback.version)

    assertEquals("Rollback depth must be 1.",
      1, sidechainStateAfterRollback.maxRollbackDepth)

    for (b <- transactionList.head.newBoxes().asScala) {
      assertTrue("Box in state after applyModifier must not contain newBoxes from transaction.",
        sidechainStateAfterRollback.closedBox(b.id()).isEmpty)
    }

    for (b <- transactionList.head.unlockers().asScala.map(_.closedBoxId())) {
      assertTrue("Box in state after applyModifier must contain unlocked boxes from transaction.",
        sidechainStateAfterRollback.closedBox(b).isDefined)
    }
  }


}
