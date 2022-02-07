package com.horizen

import java.util.{ArrayList => JArrayList, List => JList}
import com.horizen.block.{MainchainBlockReferenceData, SidechainBlock, WithdrawalEpochCertificate}
import com.horizen.box.data.{ForgerBoxData, BoxData, ZenBoxData}
import com.horizen.box._
import com.horizen.consensus.{ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.fixtures.{SecretFixture, SidechainTypesTestsExtension, StoreFixture, TransactionFixture}
import com.horizen.librustsidechains.FieldElement
import com.horizen.params.MainNetParams
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.storage.{SidechainStateForgerBoxStorage, SidechainStateStorage, SidechainStateUtxoMerkleTreeStorage}
import com.horizen.state.{ApplicationState, SidechainStateReader}
import com.horizen.transaction.exception.TransactionSemanticValidityException
import com.horizen.transaction.{BoxTransaction, RegularTransaction}
import com.horizen.utils.{BlockFeeInfo, ByteArrayWrapper, BytesUtils, WithdrawalEpochInfo, Pair => JPair}
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.{bytesToId, bytesToVersion}
import scorex.util.ModifierId

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable.ListBuffer
import scala.util.{Random, Success}


class SidechainStateTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypesTestsExtension
{

  val mockedStateStorage: SidechainStateStorage = mock[SidechainStateStorage]
  val mockedStateForgerBoxStorage: SidechainStateForgerBoxStorage = mock[SidechainStateForgerBoxStorage]
  val mockedStateUtxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage = mock[SidechainStateUtxoMerkleTreeStorage]
  val mockedApplicationState: ApplicationState = mock[ApplicationState]

  val boxList: ListBuffer[SidechainTypes#SCB] = new ListBuffer[SidechainTypes#SCB]()
  val stateVersion = new ListBuffer[ByteArrayWrapper]()
  val transactionList = new ListBuffer[RegularTransaction]()

  val secretList = new ListBuffer[PrivateKey25519]()

  val params = MainNetParams()


  def getRegularTransaction(regularOutputsCount: Int,
                            forgerOutputsCount: Int,
                            boxesWithSecretToOpen: Seq[(ZenBox,PrivateKey25519)],
                            maxInputs: Int): RegularTransaction = {
    val outputsCount = regularOutputsCount + forgerOutputsCount

    val from: JList[JPair[ZenBox,PrivateKey25519]] = new JArrayList[JPair[ZenBox,PrivateKey25519]]()
    from.addAll(boxesWithSecretToOpen.map{case (box, secret) => new JPair[ZenBox,PrivateKey25519](box, secret)}.asJava)
    val to: JList[BoxData[_ <: Proposition, _ <: Box[_ <: Proposition]]] = new JArrayList()
    var totalFrom = boxesWithSecretToOpen.map{case (box, _) => box.value()}.sum

    for (b <- boxList) {
      if(b.isInstanceOf[ZenBox] && maxInputs > from.size()) {
        from.add(new JPair(b.asInstanceOf[ZenBox],
          secretList.find(_.publicImage().equals(b.proposition())).get))
        totalFrom += b.value()
      }
    }

    val minimumFee = 5L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L

    for(s <- getPrivateKey25519List(regularOutputsCount).asScala) {
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

  @Test
  def testStateless(): Unit = {
    // Set base Secrets data
    secretList.clear()
    secretList ++= getPrivateKey25519List(5).asScala
    // Set base Box data
    boxList.clear()
    boxList ++= getZenBoxList(secretList.asJava).asScala.toList
    stateVersion.clear()
    stateVersion += getVersion
    transactionList.clear()
    transactionList += getRegularTransaction(1, 0, Seq(), 5)

    // Mock get and update methods of StateStorage
    Mockito.when(mockedStateStorage.lastVersionId).thenReturn(Some(stateVersion.last))

    Mockito.when(mockedStateStorage.getBox(ArgumentMatchers.any[Array[Byte]]()))
      .thenAnswer(answer => {
        val boxId = answer.getArgument(0).asInstanceOf[Array[Byte]]
        boxList.find(_.id().sameElements(boxId))
      })

    Mockito.when(mockedStateStorage.getWithdrawalEpochInfo).thenReturn(None)
    // Mock get and update methods of StateForgerBoxStorage
    Mockito.when(mockedStateForgerBoxStorage.lastVersionId).thenReturn(Some(stateVersion.last))

    Mockito.when(mockedStateForgerBoxStorage.getForgerBox(ArgumentMatchers.any[Array[Byte]]())).thenReturn(None)

    Mockito.when(mockedStateUtxoMerkleTreeStorage.lastVersionId).thenReturn(Some(stateVersion.last))

    val sidechainState: SidechainState = new SidechainState(mockedStateStorage, mockedStateForgerBoxStorage, mockedStateUtxoMerkleTreeStorage,
      params, bytesToVersion(stateVersion.last.data), mockedApplicationState)

    //Test get
    assertEquals("State must return existing box.",
      boxList.head, sidechainState.closedBox(boxList.head.id()).get)

    //Test getClosedBox
    assertEquals("",
      boxList.head, sidechainState.getClosedBox(boxList.head.id()).get)

    //Test semanticValidity
    val mockedTransaction = mock[SidechainTypes#SCBT]

    assertTrue("Call of semanticValidity must be successful.",
      sidechainState.semanticValidity(mockedTransaction).isSuccess)

    Mockito.when(mockedTransaction.semanticValidity())
      .thenThrow(new TransactionSemanticValidityException("test case exception."))
    assertTrue("Call of semanticValidity must be unsuccessful.",
      sidechainState.semanticValidity(mockedTransaction).isFailure)

    // Mock ApplicationState always successfully validate
    Mockito.doNothing().when(mockedApplicationState).validate(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[BoxTransaction[Proposition, Box[Proposition]]]())

    //Test validate(Transaction)
    val tryValidate = sidechainState.validate(transactionList.head)
    assertTrue("Transaction validation must be successful.",
      tryValidate.isSuccess)

    //Test validate(Block)
    val mockedBlock = mock[SidechainBlock]

    Mockito.when(mockedBlock.topQualityCertificateOpt).thenReturn(None)

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.mainchainBlockReferencesData).thenReturn(Seq())

    Mockito.when(mockedBlock.parentId)
      .thenReturn(bytesToId(stateVersion.last.data))
      .thenReturn(bytesToId(stateVersion.last.data))
      .thenReturn("00000000000000000000000000000000".asInstanceOf[ModifierId])

    Mockito.doNothing().when(mockedApplicationState).validate(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[SidechainBlock]())

    val validateTry1 = sidechainState.validate(mockedBlock)
    assertTrue(s"Block validation must be successful. But result is - $validateTry1",
      validateTry1.isSuccess)

    val expectedException = new IllegalArgumentException("Some exception")
    Mockito.reset(mockedApplicationState)
    Mockito.when(mockedApplicationState.validate(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[SidechainBlock]())).thenThrow(expectedException)

    val validateTry2 = sidechainState.validate(mockedBlock)
    assertTrue(s"Block validation must be unsuccessful.",
      validateTry2.isFailure)
    assertEquals(s"Block validation different exception expected.", expectedException,
      validateTry2.failed.get)

    //Test changes
    val changes = sidechainState.changes(mockedBlock)

    assertTrue("Extracting changes from block must be successful.",
      changes.isSuccess)


    //test mutuality transaction check
    val mutualityMockedBlock = mock[SidechainBlock]
    Mockito.when(mutualityMockedBlock.topQualityCertificateOpt).thenReturn(None)
    Mockito.when(mutualityMockedBlock.mainchainBlockReferencesData).thenReturn(Seq())
    Mockito.when(mutualityMockedBlock.parentId).thenReturn(bytesToId(stateVersion.last.data))
    Mockito.when(mutualityMockedBlock.id).thenReturn(ModifierId @@ "testBlock")

    val secret = getPrivateKey25519List(1).get(0)
    val boxAndSecret = Seq((getZenBox(secret.publicImage(), 1, Random.nextInt(100)), secret))
    Mockito.when(mutualityMockedBlock.transactions)
      .thenReturn(transactionList.toList ++ transactionList)
      .thenReturn(List(getRegularTransaction(1, 0, boxAndSecret, 1), getRegularTransaction(1, 0, boxAndSecret, 1)))

    val sameTransactionsCheckTry = sidechainState.validate(mutualityMockedBlock)
    assertTrue(s"Block validation must be failed with message. But result is - $sameTransactionsCheckTry",
      "Block testBlock contains duplicated transactions" == sameTransactionsCheckTry.failed.get.getMessage)

    val sameInputsInTransactions = sidechainState.validate(mutualityMockedBlock)
    assertTrue(s"Block validation must be failed with message. But result is - $sameInputsInTransactions",
      "Block testBlock contains duplicated input boxes to open" == sameInputsInTransactions.failed.get.getMessage)


    val doubleSpendTransactionMockedBlock = mock[SidechainBlock]
    Mockito.when(doubleSpendTransactionMockedBlock.topQualityCertificateOpt).thenReturn(None)
    Mockito.when(doubleSpendTransactionMockedBlock.mainchainBlockReferencesData).thenReturn(Seq())
    Mockito.when(doubleSpendTransactionMockedBlock.parentId).thenReturn(bytesToId(stateVersion.last.data))
    Mockito.when(doubleSpendTransactionMockedBlock.id).thenReturn(ModifierId @@ "testBlock")

    val boxAndSecret2: Seq[(ZenBox,PrivateKey25519)] = Seq((boxList.last.asInstanceOf[ZenBox], secretList.last))

    Mockito.when(doubleSpendTransactionMockedBlock.transactions)
      .thenReturn(List(getRegularTransaction(0, 0, boxAndSecret2 ++ boxAndSecret2, 1)))

    val doubleSpendInTransaction = sidechainState.validate(doubleSpendTransactionMockedBlock)
    assertTrue(s"Block validation must be failed with message. But result is - $doubleSpendInTransaction",
      doubleSpendInTransaction.failed.get.getMessage.contains("inputs double spend found."))

    for(b <- changes.get.toRemove) {
      assertFalse("Box to remove is not found in storage.",
        boxList.indexWhere(_.id().sameElements(b.boxId)) == -1)
    }

    assertTrue("Box to add must be same as in transaction.",
      transactionList.head.newBoxes().asScala.head.equals(changes.get.toAppend.head.box))
  }

  @Test
  def testApplyModifier(): Unit = {
    // Set base Secrets data
    secretList.clear()
    secretList ++= getPrivateKey25519List(5).asScala
    // Set base Box data
    boxList.clear()
    boxList ++= getZenBoxList(secretList.asJava).asScala.toList
    stateVersion.clear()
    stateVersion += getVersion
    transactionList.clear()
    transactionList += getRegularTransaction(2, 2, Seq(), 2)
    val forgerBoxes = transactionList.head.newBoxes().asScala
      .view
      .filter(_.isInstanceOf[ForgerBox])
      .map(_.asInstanceOf[ForgerBox])

    val modBlockFeeInfo = BlockFeeInfo(123, getPrivateKey25519.publicImage())

    // Mock get and update methods of BoxStorage
    Mockito.when(mockedStateStorage.lastVersionId)
        .thenAnswer(answer => {Some(stateVersion.last)})

    Mockito.when(mockedStateStorage.getBox(ArgumentMatchers.any[Array[Byte]]()))
      .thenAnswer(answer => {
        val boxId = answer.getArgument(0).asInstanceOf[Array[Byte]]
        boxList.find(_.id().sameElements(boxId))
      })

    Mockito.when(mockedStateStorage.update(ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[WithdrawalEpochInfo](),
      ArgumentMatchers.any[Set[SidechainTypes#SCB]](),
      ArgumentMatchers.any[Set[ByteArrayWrapper]](),
      ArgumentMatchers.any[Seq[WithdrawalRequestBox]](),
      ArgumentMatchers.any[ConsensusEpochNumber](),
      ArgumentMatchers.any[Option[WithdrawalEpochCertificate]](),
      ArgumentMatchers.any[BlockFeeInfo](),
      ArgumentMatchers.any[Option[Array[Byte]]](),
      ArgumentMatchers.any[Boolean]()))
      .thenAnswer( answer => {
        val version = answer.getArgument[ByteArrayWrapper](0)
        val withdrawalEpochInfo = answer.getArgument[WithdrawalEpochInfo](1)
        val boxToUpdate = answer.getArgument[Set[SidechainTypes#SCB]](2)
        val boxToRemove = answer.getArgument[Set[ByteArrayWrapper]](3)
        val withdrawalRequestAppendSeq = answer.getArgument[ListBuffer[WithdrawalRequestBox]](4)
        val consensusEpoch = answer.getArgument[ConsensusEpochNumber](5)
        val backwardTransferCertificate = answer.getArgument[Option[WithdrawalEpochCertificate]](6)
        val blockFeeInfo = answer.getArgument[BlockFeeInfo](7)
        val utxoMerkleTreeRootOpt = answer.getArgument[Option[Array[Byte]]](8)
        val scHasCeased = answer.getArgument[Boolean](9)

        // Verify withdrawals
        assertTrue("Withdrawals to append expected to be empty.", withdrawalRequestAppendSeq.isEmpty)
        // Verify consensus epoch number
        assertEquals("Consensus epoch  number should be different.", 2, consensusEpoch)
        // Verify certificate presence
        assertEquals("Certificate expected to be absent.", None, backwardTransferCertificate)
        // Verify blockFeeInfo
        assertEquals("blockFeeInfo expected to be different.", modBlockFeeInfo, blockFeeInfo)
        // Verify utxoMerkleTreeRoot
        assertTrue("utxoMerkleTreeRoot expected to be empty.", utxoMerkleTreeRootOpt.isEmpty)
        assertFalse("sc not ceased.", scHasCeased)


        stateVersion += version

        for (b <- boxToRemove.map(_.data) ++ boxToUpdate.map(_.id())) {
          val i = boxList.indexWhere(_.id().sameElements(b))
          if (i != -1)
            boxList.remove(i)
        }

        boxList ++= boxToUpdate

        Success(mockedStateStorage)
      })

    Mockito.when(mockedStateStorage.getWithdrawalEpochInfo).thenReturn(None)

    Mockito.when(mockedStateForgerBoxStorage.lastVersionId).thenAnswer(_ => Some(stateVersion.last))

    Mockito.when(mockedStateForgerBoxStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[Seq[ForgerBox]](),
      ArgumentMatchers.any[Set[ByteArrayWrapper]]()
    )).thenAnswer( answer => {
      val forgerBoxToUpdate = answer.getArgument[ListBuffer[ForgerBox]](1)

      assertEquals("ForgerBox seq should be different.", forgerBoxes, forgerBoxToUpdate)

      Success(mockedStateForgerBoxStorage)
    })

    Mockito.when(mockedStateUtxoMerkleTreeStorage.lastVersionId).thenAnswer(_ => Some(stateVersion.last))

    Mockito.when(mockedStateUtxoMerkleTreeStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[Seq[SidechainTypes#SCB]](),
      ArgumentMatchers.any[Set[ByteArrayWrapper]]()
    )).thenAnswer( answer => {
      val expectedBoxesToAppend = transactionList.flatMap(tx => tx.newBoxes().asScala)
      val expectedBoxesToRemove = transactionList.flatMap(tx => tx.unlockers().asScala.map(u => new ByteArrayWrapper(u.closedBoxId()))).toSet

      val boxesToAppend = answer.getArgument[Seq[SidechainTypes#SCB]](1)
      val boxesToRemove = answer.getArgument[Set[ByteArrayWrapper]](2)

      assertEquals("Different boxes to append found.", expectedBoxesToAppend, boxesToAppend)
      assertEquals("Different boxes to remove found.", expectedBoxesToRemove, boxesToRemove)

      Success(mockedStateUtxoMerkleTreeStorage)
    })

    val mockedBlock = mock[SidechainBlock]

    Mockito.when(mockedBlock.id)
      .thenReturn({
        bytesToId(getVersion.data)
      })

    Mockito.when(mockedBlock.timestamp)
      .thenReturn(params.sidechainGenesisBlockTimestamp + params.consensusSecondsInSlot)

    Mockito.when(mockedBlock.transactions)
      .thenReturn(transactionList.toList)

    Mockito.when(mockedBlock.parentId)
      .thenReturn(bytesToId(stateVersion.last.data))

    Mockito.when(mockedBlock.mainchainBlockReferencesData)
      .thenAnswer(answer => Seq[MainchainBlockReferenceData]())

    Mockito.when(mockedBlock.topQualityCertificateOpt).thenReturn(None)

    Mockito.when(mockedBlock.feeInfo).thenReturn(modBlockFeeInfo)

    Mockito.doNothing().when(mockedApplicationState).validate(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[SidechainBlock]())

    Mockito.doNothing().when(mockedApplicationState).validate(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[BoxTransaction[Proposition, Box[Proposition]]]())

    Mockito.when(mockedApplicationState.onApplyChanges(ArgumentMatchers.any[SidechainStateReader](),
      ArgumentMatchers.any[Array[Byte]](),
      ArgumentMatchers.any[JList[SidechainTypes#SCB]](),
      ArgumentMatchers.any[JList[Array[Byte]]]()))
      .thenReturn(Success(mockedApplicationState))

    val sidechainState: SidechainState = new SidechainState(mockedStateStorage, mockedStateForgerBoxStorage, mockedStateUtxoMerkleTreeStorage,
      params, bytesToVersion(stateVersion.last.data), mockedApplicationState)

    val applyTry = sidechainState.applyModifier(mockedBlock)

    assertTrue("ApplyChanges for block must be successful.",
      applyTry.isSuccess)

    assertTrue("Box in state must be same as in transaction.",
      sidechainState.closedBox(transactionList.head.newBoxes().asScala.head.id()).isDefined)
  }

  @Test
  def feePayments(): Unit = {
    val stateStorage: SidechainStateStorage = mock[SidechainStateStorage]
    val stateForgerBoxStorage: SidechainStateForgerBoxStorage = mock[SidechainStateForgerBoxStorage]
    val stateUtxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage = mock[SidechainStateUtxoMerkleTreeStorage]
    val applicationState: ApplicationState = mock[ApplicationState]

    val version = getVersion
    Mockito.when(stateStorage.lastVersionId).thenReturn(Some(version))
    Mockito.when(stateForgerBoxStorage.lastVersionId).thenReturn(Some(version))
    Mockito.when(stateUtxoMerkleTreeStorage.lastVersionId).thenReturn(Some(version))

    val sidechainState = new SidechainState(stateStorage, stateForgerBoxStorage, stateUtxoMerkleTreeStorage,
      params, bytesToVersion(version.data), applicationState)


    // Test 1: No block fee info record in the storage
    Mockito.when(stateStorage.getFeePayments(ArgumentMatchers.any[Int]())).thenReturn(Seq())
    var feePayments = sidechainState.getFeePayments(0)
    assertEquals(s"Fee payments size expected to be different.", 0, feePayments.size)


    // Test 2: with single block fee info record in the storage
    Mockito.reset(stateStorage)
    val blockFee1: Long = 100
    val blockFeeInfo1: BlockFeeInfo = BlockFeeInfo(blockFee1, getPrivateKey25519("forger1".getBytes()).publicImage())
    Mockito.when(stateStorage.getFeePayments(ArgumentMatchers.any[Int]())).thenReturn(Seq(blockFeeInfo1))

    feePayments = sidechainState.getFeePayments(0)
    assertEquals(s"Fee payments size expected to be different.", 1, feePayments.size)
    assertEquals(s"Fee value for box ${BytesUtils.toHexString(feePayments.head.id())} is wrong", blockFee1, feePayments.head.value())


    // Test 3: with multiple block fee info records for different forger keys in the storage
    Mockito.reset(stateStorage)
    val blockFee2: Long = 100
    val blockFeeInfo2: BlockFeeInfo = BlockFeeInfo(blockFee2, getPrivateKey25519("forger2".getBytes()).publicImage())
    val blockFee3: Long = 201
    val blockFeeInfo3: BlockFeeInfo = BlockFeeInfo(blockFee3, getPrivateKey25519("forger3".getBytes()).publicImage())
    Mockito.when(stateStorage.getFeePayments(ArgumentMatchers.any[Int]()))
      .thenReturn(Seq(blockFeeInfo1, blockFeeInfo2, blockFeeInfo3))

    feePayments = sidechainState.getFeePayments(0)
    assertEquals(s"Fee payments size expected to be different.", 3, feePayments.size)
    var totalFee = blockFee1 + blockFee2 + blockFee3
    assertEquals(s"Total fee value is wrong", totalFee, feePayments.map(_.value()).sum)
    val poolFee = Math.ceil((blockFee1 + blockFee2 + blockFee3) * (1 - params.forgerBlockFeeCoefficient)).longValue()
    val forger1Fee = Math.floor(blockFee1 * params.forgerBlockFeeCoefficient).longValue() + poolFee / 3 + 1 // plus 1 undistributed satoshi
    val forger2Fee = Math.floor(blockFee2 * params.forgerBlockFeeCoefficient).longValue() + poolFee / 3
    val forger3Fee = Math.floor(blockFee3 * params.forgerBlockFeeCoefficient).longValue() + poolFee / 3
    assertEquals(s"Fee value for box ${BytesUtils.toHexString(feePayments.head.id())} is wrong", forger1Fee, feePayments.head.value())
    assertEquals(s"Fee value for box ${BytesUtils.toHexString(feePayments(1).id())} is wrong", forger2Fee, feePayments(1).value())
    assertEquals(s"Fee value for box ${BytesUtils.toHexString(feePayments(2).id())} is wrong", forger3Fee, feePayments(2).value())


    // Test 4: with multiple block fee info records for non-unique forger keys in the storage
    Mockito.reset(stateStorage)
    // Block was created with the forger3 (second time in the epoch)
    val blockFee4: Long = 50
    val blockFeeInfo4: BlockFeeInfo = BlockFeeInfo(blockFee4, blockFeeInfo3.forgerRewardKey)
    Mockito.when(stateStorage.getFeePayments(ArgumentMatchers.any[Int]()))
      .thenReturn(Seq(blockFeeInfo1, blockFeeInfo2, blockFeeInfo3, blockFeeInfo4))

    feePayments = sidechainState.getFeePayments(0)
    assertEquals(s"Fee payments size expected to be different.", 3, feePayments.size)

    totalFee = blockFee1 + blockFee2 + blockFee3 + blockFee4
    assertEquals(s"Total fee value is wrong", totalFee, feePayments.map(_.value()).sum)


    // Test 5: with multiple block fee info records created by 2 unique forgers
    val bfi1 = BlockFeeInfo(0, getPrivateKey25519("forger1".getBytes()).publicImage())
    val bfi2 = BlockFeeInfo(1000, getPrivateKey25519("forger1".getBytes()).publicImage())
    val bfi3 = BlockFeeInfo(0, getPrivateKey25519("forger1".getBytes()).publicImage())
    val bfi4 = BlockFeeInfo(200, getPrivateKey25519("forger2".getBytes()).publicImage())
    val bfi5 = BlockFeeInfo(0, getPrivateKey25519("forger2".getBytes()).publicImage())
    Mockito.reset(stateStorage)
    Mockito.when(stateStorage.getFeePayments(ArgumentMatchers.any[Int]()))
      .thenReturn(Seq(bfi1, bfi2, bfi3, bfi4, bfi5))
    feePayments = sidechainState.getFeePayments(0)
    assertEquals(s"Fee payments size expected to be different.", 2, feePayments.size)
  }

  @Test
  def utxoMerkleTreeRoot(): Unit = {
    val stateStorage: SidechainStateStorage = mock[SidechainStateStorage]
    val stateForgerBoxStorage: SidechainStateForgerBoxStorage = mock[SidechainStateForgerBoxStorage]
    val stateUtxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage = mock[SidechainStateUtxoMerkleTreeStorage]
    val applicationState: ApplicationState = mock[ApplicationState]

    val version = getVersion
    Mockito.when(stateStorage.lastVersionId).thenReturn(Some(version))
    Mockito.when(stateForgerBoxStorage.lastVersionId).thenReturn(Some(version))
    Mockito.when(stateUtxoMerkleTreeStorage.lastVersionId).thenReturn(Some(version))

    val sidechainState = new SidechainState(stateStorage, stateForgerBoxStorage, stateUtxoMerkleTreeStorage,
      params, bytesToVersion(version.data), applicationState)


    // Test 1: No utxoMerkleTreeRoot found for given epoch
    val withdrawalEpochNumber: Int = 0
    Mockito.when(stateStorage.getUtxoMerkleTreeRoot(ArgumentMatchers.any[Int]())).thenAnswer(data => {
      val epoch: Int = data.getArgument(0)
      assertEquals("Different withdrawal epoch number found.", withdrawalEpochNumber, epoch)
      None
    })
    val rootOpt1 = sidechainState.utxoMerkleTreeRoot(0)
    assertTrue(s"No root expected to be found for given epoch.", rootOpt1.isEmpty)


    // Test 2: utxoMerkleTreeRoot exists for given epoch
    Mockito.reset(stateStorage)
    val expectedRoot = FieldElementUtils.randomFieldElementBytes()
    Mockito.when(stateStorage.getUtxoMerkleTreeRoot(ArgumentMatchers.any[Int]())).thenAnswer(data => {
      val epoch: Int = data.getArgument(0)
      assertEquals("Different withdrawal epoch number found.", withdrawalEpochNumber, epoch)
      Some(expectedRoot)
    })
    val rootOpt2 = sidechainState.utxoMerkleTreeRoot(0)
    assertTrue(s"Root expected to be found for given epoch.", rootOpt2.isDefined)
    assertArrayEquals("Different root value found.", expectedRoot, rootOpt2.get)
  }

  @Test
  def ceased(): Unit = {
    val stateStorage: SidechainStateStorage = mock[SidechainStateStorage]
    val stateForgerBoxStorage: SidechainStateForgerBoxStorage = mock[SidechainStateForgerBoxStorage]
    val stateUtxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage = mock[SidechainStateUtxoMerkleTreeStorage]
    val applicationState: ApplicationState = mock[ApplicationState]

    val version = getVersion
    Mockito.when(stateStorage.lastVersionId).thenReturn(Some(version))
    Mockito.when(stateForgerBoxStorage.lastVersionId).thenReturn(Some(version))
    Mockito.when(stateUtxoMerkleTreeStorage.lastVersionId).thenReturn(Some(version))

    val sidechainState = new SidechainState(stateStorage, stateForgerBoxStorage, stateUtxoMerkleTreeStorage,
      params, bytesToVersion(version.data), applicationState)


    // Test 1: Sidechain is alive
    Mockito.when(stateStorage.hasCeased).thenReturn(false)
    assertFalse(s"Sidechain must be alive.", sidechainState.hasCeased)

    // Test 1: Sidechain has ceased
    Mockito.when(stateStorage.hasCeased).thenReturn(true)
    assertTrue(s"Sidechain must be ceased.", sidechainState.hasCeased)
  }
}
