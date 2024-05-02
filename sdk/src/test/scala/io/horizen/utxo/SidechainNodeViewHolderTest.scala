package io.horizen.utxo

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActorRef, TestProbe}
import akka.util.Timeout
import io.horizen.MempoolSettings
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.consensus.{ConsensusEpochInfo, FullConsensusEpochInfo, intToConsensusEpochNumber}
import io.horizen.fixtures._
import io.horizen.params.{NetworkParams, RegTestParams}
import io.horizen.SidechainTypes
import io.horizen.metrics.MetricsManager
import io.horizen.utils.{CountDownLatchController, MerkleTree, WithdrawalEpochInfo}
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.box.ZenBox
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.mempool.SidechainMemoryPool
import io.horizen.utxo.state.{SidechainState, UtxoMerkleTreeView}
import io.horizen.utxo.transaction.RegularTransaction
import io.horizen.utxo.utils.BlockFeeInfo
import io.horizen.utxo.wallet.SidechainWallet
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.mockito.Mockito.times
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.core.NodeViewHolder.ReceivableMessages.{LocallyGeneratedModifier, ModifiersFromRemote}
import sparkz.core.consensus.History.ProgressInfo
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{ModifiersProcessingResult, SemanticallySuccessfulModifier}
import sparkz.core.utils.NetworkTimeProvider
import sparkz.core.validation.RecoverableModifierError
import sparkz.core.{VersionTag, idToVersion}
import sparkz.util.{ModifierId, SparkzEncoding}

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}
import scala.collection.mutable

class SidechainNodeViewHolderTest extends JUnitSuite
  with MockedSidechainNodeViewHolderFixture
  with SidechainBlockFixture
  with CompanionsFixture
  with SparkzEncoding
  with TransactionFixture
{
  var history: SidechainHistory = _
  var state: SidechainState = _
  var wallet: SidechainWallet = _
  var mempool: SidechainMemoryPool = _

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
  var mockedNodeViewHolderRef: ActorRef = _

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val genesisBlock: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion)
  val params: NetworkParams = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())
  implicit lazy val timeout: Timeout = Timeout(10000 milliseconds)


  @Before
  def setUp(): Unit = {
    MetricsManager.init(mock[NetworkTimeProvider])
    history = mock[SidechainHistory]
    state = mock[SidechainState]
    wallet = mock[SidechainWallet]
    mempool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300))
    mockedNodeViewHolderRef = getMockedSidechainNodeViewHolderRef(history, state, wallet, mempool)
  }

  private def getMockedMempoolSettings(maxSize: Int): MempoolSettings = {
    val mockedSettings: MempoolSettings = mock[MempoolSettings]
    Mockito.when(mockedSettings.maxSize).thenReturn(maxSize)
    Mockito.when(mockedSettings.minFeeRate).thenReturn(0)
    mockedSettings
  }

  @Test
  def consensusEpochSwitchNotification(): Unit = {
    // Test: Verify that consensus epoch switching block will emit the notification inside SidechainNodeViewHolder

    // Mock history to add the incoming block to the ProgressInfo append list
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer =>
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq(answer.getArgument(0).asInstanceOf[SidechainBlock]))))
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock])).thenReturn(Try(history))
    // Mock state to notify that any incoming block to append will lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(true)
    // Mock state to apply incoming block successfully
    Mockito.when(state.applyModifier(ArgumentMatchers.any[SidechainBlock])).thenReturn(Success(state))
    // Mock state withdrawal epoch methods
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(0, 1))
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(false)
    // Mock wallet to apply incoming block successfully
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[SidechainBlock], ArgumentMatchers.any[Int](), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(wallet)

    Mockito.when(state.getCurrentConsensusEpochInfo).thenReturn({
      val merkleTree = MerkleTree.createMerkleTree(util.Arrays.asList("StringShallBe32LengthOrTestFail.".getBytes(StandardCharsets.UTF_8)))
      (genesisBlock.id, ConsensusEpochInfo(intToConsensusEpochNumber(0), merkleTree, 0L))
    })

    Mockito.when(history.applyFullConsensusInfo(ArgumentMatchers.any[ModifierId], ArgumentMatchers.any[FullConsensusEpochInfo])).thenAnswer(_ => {
      history
    })

    Mockito.when(wallet.applyConsensusEpochInfo(ArgumentMatchers.any[ConsensusEpochInfo])).thenAnswer(_ => {
      wallet
    })

    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()

    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    val block = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[SidechainBlock]]

    // Verify that all Consensus Epoch switching methods were executed
    Mockito.verify(state, times(1)).getCurrentConsensusEpochInfo
    Mockito.verify(history, times(1)).applyFullConsensusInfo(ArgumentMatchers.any[ModifierId], ArgumentMatchers.any[FullConsensusEpochInfo])
    Mockito.verify(wallet, times(1)).applyConsensusEpochInfo(ArgumentMatchers.any[ConsensusEpochInfo])
  }

  @Test
  def continueActiveChain(): Unit = {
    // Test: Verify the flow of continuation the active chain by a single block applied to the node.

    // Create block to apply
    val block: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer => {
      val blockToAppend: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("History received different block to append.", block.id, blockToAppend.id)
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq(blockToAppend)))
    })
    // History semantic validity check
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer => Try {
      val validBlock: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("History received semantically valid notification about different block.", block.id, validBlock.id)
      history
    })
    // State apply check
    Mockito.when(state.applyModifier(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer => {
      val blockToApply: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("State received different block to apply.", block.id, blockToApply.id)
      Success(state)
    })
    // Mock state withdrawal epoch methods
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(0, 1))
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(false)
    // Wallet apply
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[Int](),
      ArgumentMatchers.any(),
      ArgumentMatchers.any())).thenAnswer( answer => {
      val blockToApply: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("Wallet received different block to apply.", block.id, blockToApply.id)
      wallet
    })


    // Consensus epoch switching checks
    // Mock state to notify that any incoming block to append will NOT lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(false)
    // Check that consensus epoch data was not requested from the State.
    Mockito.when(state.getCurrentConsensusEpochInfo).thenReturn({
      val merkleTree = MerkleTree.createMerkleTree(util.Arrays.asList("StringShallBe32LengthOrTestFail.".getBytes(StandardCharsets.UTF_8)))
      (genesisBlock.id, ConsensusEpochInfo(intToConsensusEpochNumber(0), merkleTree, 0L))
    })


    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[SidechainBlock]]
  }

  @Test
  def chainSwitch(): Unit = {
    // Test: Verify the flow of chain switch caused by a single block applied to the node.

    // Create blocks for test
    val branchPointBlock: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val firstBlockInActiveChain: SidechainBlock = generateNextSidechainBlock(branchPointBlock, sidechainTransactionsCompanion, params)
    val firstBlockInFork: SidechainBlock = generateNextSidechainBlock(branchPointBlock, sidechainTransactionsCompanion, params)
    val secondBlockInFork: SidechainBlock = generateNextSidechainBlock(firstBlockInFork, sidechainTransactionsCompanion, params)

    // Appending secondBlockInFork that should emit the chains switch
    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer => {
      val blockToAppend: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("History received different block to append.", secondBlockInFork.id, blockToAppend.id)
      Success(history -> ProgressInfo[SidechainBlock](Some(branchPointBlock.id), Seq(firstBlockInActiveChain), Seq(firstBlockInFork, secondBlockInFork)))
    })
    // History semantic validity check for fork blocks - one by one.
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock]))
      .thenAnswer( answer => Try {
      val validBlock: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("History received semantically valid notification about different block. First fork block expected.",
        firstBlockInFork.id, validBlock.id)
      history
      })
      .thenAnswer( answer =>  Try {
      val validBlock: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("History received semantically valid notification about different block. Second fork block expected.",
        secondBlockInFork.id, validBlock.id)
      history
      })

    // Mock current version of the State
    Mockito.when(state.version).thenReturn(idToVersion(firstBlockInActiveChain.id))
    // Mock state withdrawal epoch methods
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(0, 1))
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(false)

    // State rollback check
    Mockito.when(state.rollbackTo(ArgumentMatchers.any[VersionTag])).thenAnswer(answer => {
      val rollbackPoint: VersionTag = answer.getArgument(0).asInstanceOf[VersionTag]
      assertEquals("State received different rollback point.", branchPointBlock.id, rollbackPoint)
      Success(state)
    })
    // State apply check - one by one for fork chain.
    Mockito.when(state.applyModifier(ArgumentMatchers.any[SidechainBlock]))
      .thenAnswer( answer => {
      val blockToApply: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("State received different block to apply. First fork block expected.", firstBlockInFork.id, blockToApply.id)
      Success(state)
      })
      .thenAnswer( answer => {
      val blockToApply: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("State received different block to apply. Second fork block expected.", secondBlockInFork.id, blockToApply.id)
      Success(state)
      })

    // Wallet rollback check
    Mockito.when(wallet.rollback(ArgumentMatchers.any[VersionTag])).thenAnswer(answer => {
      val rollbackPoint: VersionTag = answer.getArgument(0).asInstanceOf[VersionTag]
      assertEquals("Wallet received different rollback point.", branchPointBlock.id, rollbackPoint)
      Success(wallet)
    })
    // Wallet apply - one by one for fork chain.
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[Int](),
      ArgumentMatchers.any(),
      ArgumentMatchers.any()))
      .thenAnswer( answer => {
        val blockToApply: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
        assertEquals("Wallet received different block to apply. First fork block expected.", firstBlockInFork.id, blockToApply.id)
        wallet
      })
      .thenAnswer( answer => {
        val blockToApply: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
        assertEquals("Wallet received different block to apply. Second fork block expected.", secondBlockInFork.id, blockToApply.id)
        wallet
      })


    // Consensus epoch switching checks
    // Mock state to notify that any incoming block to append will NOT lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(false)
    // Check that consensus epoch data was not requested from the State.
    Mockito.when(state.getCurrentConsensusEpochInfo).thenAnswer( _ => {
      fail("Consensus epoch data should not being requested from the State.")
      null
    })


    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(secondBlockInFork)


    // Verify successful applying for 2 fork blocks
    eventListener.expectMsgType[SemanticallySuccessfulModifier[SidechainBlock]]
    eventListener.expectMsgType[SemanticallySuccessfulModifier[SidechainBlock]]
  }

  @Test
  def withdrawalEpochInTheMiddle(): Unit = {
    // Test: Verify that SC block in the middle of withdrawal epoch will NOT emit notify wallet with fee payments and utxo merkle tree view.

    // Mock history to add the incoming block to the ProgressInfo append list
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer =>
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq(answer.getArgument(0).asInstanceOf[SidechainBlock]))))
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock])).thenReturn(Try(history))
    // Mock state to notify that any incoming block to append will NOT lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(false)
    // Mock state to apply incoming block successfully
    Mockito.when(state.applyModifier(ArgumentMatchers.any[SidechainBlock])).thenReturn(Success(state))

    // Mock state withdrawal epoch methods
    val withdrawalEpochInfo = WithdrawalEpochInfo(0, 1)
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(withdrawalEpochInfo)
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(false)

    // Mock state fee payments with checks
    Mockito.when(state.getFeePayments(ArgumentMatchers.any[Int](), ArgumentMatchers.any[Option[BlockFeeInfo]])).thenAnswer(args => {
      Seq()
    })

    // Mock wallet scanPersistent with checks
    Mockito.when(wallet.scanPersistent(
      ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[Int](),
      ArgumentMatchers.any[Seq[ZenBox]](),
      ArgumentMatchers.any[Option[UtxoMerkleTreeView]]()))
      .thenAnswer(args => {
        val epochNumber: Int = args.getArgument(1)
        val feePayments: Seq[ZenBox] = args.getArgument(2)
        val utxoView: Option[UtxoMerkleTreeView] = args.getArgument(3)
        assertEquals("Different withdrawal epoch number expected.", withdrawalEpochInfo.epoch, epochNumber)
        assertTrue("No fee payments expected while not in the end of the withdrawal epoch.", feePayments.isEmpty)
        assertTrue("No UtxoMerkleTreeView expected while not in the end of the withdrawal epoch.", utxoView.isEmpty)

        wallet
      })

    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    val block = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[SidechainBlock]]
    Thread.sleep(100)

    // Verify that all the checks passed
    Mockito.verify(state, times(0)).getFeePayments(ArgumentMatchers.any[Int](), ArgumentMatchers.any[Option[BlockFeeInfo]])
    Mockito.verify(wallet, times(1)).scanPersistent(
      ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[Int](),
      ArgumentMatchers.any[Seq[ZenBox]](),
      ArgumentMatchers.any[Option[UtxoMerkleTreeView]]())
  }

  @Test
  def withdrawalEpochLastIndex(): Unit = {
    // Test: Verify that SC block leading to the last withdrawal epoch index will emit notify wallet with fee payments and utxo merkle tree view.

    // Mock history to add the incoming block to the ProgressInfo append list
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer =>
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq(answer.getArgument(0).asInstanceOf[SidechainBlock]))))
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock])).thenReturn(Try(history))
    Mockito.when(history.updateFeePaymentsInfo(ArgumentMatchers.any[ModifierId],ArgumentMatchers.any[SidechainFeePaymentsInfo])).thenReturn(history)
    // Mock state to notify that any incoming block to append will NOT lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(false)
    // Mock state to apply incoming block successfully
    Mockito.when(state.applyModifier(ArgumentMatchers.any[SidechainBlock])).thenReturn(Success(state))

    val withdrawalEpochInfo = WithdrawalEpochInfo(3, params.withdrawalEpochLength)
    // Mock state to reach the last withdrawal epoch index
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(withdrawalEpochInfo)
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(true)
    Mockito.when(state.getCurrentConsensusEpochInfo).thenReturn({
      val merkleTree = MerkleTree.createMerkleTree(util.Arrays.asList("StringShallBe32LengthOrTestFail.".getBytes(StandardCharsets.UTF_8)))
      (genesisBlock.id, ConsensusEpochInfo(intToConsensusEpochNumber(0), merkleTree, 0L))
    })

    // Mock state fee payments with checks
    val expectedFeePayments: Seq[ZenBox] = Seq(getZenBox, getZenBox)
    Mockito.when(state.getFeePayments(ArgumentMatchers.any[Int](), ArgumentMatchers.any[Option[BlockFeeInfo]]())).thenAnswer(args => {
      val epochNumber: Int = args.getArgument(0)
      assertEquals("Different withdrawal epoch number expected.", withdrawalEpochInfo.epoch, epochNumber)
      expectedFeePayments
    })

    // Mock wallet scanPersistent with checks
    Mockito.when(wallet.scanPersistent(
      ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[Int](),
      ArgumentMatchers.any[Seq[ZenBox]](),
      ArgumentMatchers.any[Option[UtxoMerkleTreeView]]()))
      .thenAnswer(args => {
        val epochNumber: Int = args.getArgument(1)
        val feePayments: Seq[ZenBox] = args.getArgument(2)
        val utxoView: Option[UtxoMerkleTreeView] = args.getArgument(3)
        assertEquals("Different withdrawal epoch number expected.", withdrawalEpochInfo.epoch, epochNumber)
        assertEquals("Different fee payments expected while in the end of the withdrawal epoch.", expectedFeePayments, feePayments)
        assertTrue("UtxoMerkleTreeView expected to be defined while in the end of the withdrawal epoch.", utxoView.isDefined)

        wallet
      })

    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    val block = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[SidechainBlock]]
    Thread.sleep(100)

    // Verify that all the checks passed
    Mockito.verify(state, times(2)).getFeePayments(ArgumentMatchers.any[Int](), ArgumentMatchers.any[Option[BlockFeeInfo]])
    Mockito.verify(wallet, times(1))scanPersistent(
      ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[Int](),
      ArgumentMatchers.any[Seq[ZenBox]](),
      ArgumentMatchers.any[Option[UtxoMerkleTreeView]]())
  }

  @Test
  def remoteModifiers(): Unit = {
    val block1 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val block2 = generateNextSidechainBlock(block1, sidechainTransactionsCompanion, params)
    val block3 = generateNextSidechainBlock(block2, sidechainTransactionsCompanion, params)
    val block4 = generateNextSidechainBlock(block3, sidechainTransactionsCompanion, params)
    val block5 = generateNextSidechainBlock(block4, sidechainTransactionsCompanion, params)
    val block6 = generateNextSidechainBlock(block5, sidechainTransactionsCompanion, params)
    val block7 = generateNextSidechainBlock(block6, sidechainTransactionsCompanion, params)
    val block8 = generateNextSidechainBlock(block7, sidechainTransactionsCompanion, params)

    val blocks = Array(block1, block2, block3, block4, block5, block6, block7, block8)
    var blockIndex: Int = 0

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer => {
      val blockToAppend: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())
    val blockMock = Mockito.mock(classOf[SidechainBlock])
    Mockito.when(history.bestBlock).thenReturn(blockMock)
    Mockito.when(blockMock.timestamp).thenReturn(Instant.now().toEpochMilli)

    Mockito.when(history.applicableTry(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      val block: SidechainBlock = answer.getArgument(0)

      if (block.id == blocks(blockIndex).id) {
        blockIndex += 1
        Success(Unit)
      } else
        Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[SidechainBlock]])

    mockedNodeViewHolderRef ! ModifiersFromRemote(blocks)

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) => {
            assertTrue("Applied block sequence is differ", applied.toSet.equals(blocks.toSet))
            assertTrue("Cleared block sequence is not empty.", cleared.isEmpty)
            true
          }
          case _ => {
            false
          }
        }
    }
  }

  /*
   * This test check correctness of applying two remoteModifiers messages
   * Second remoteModifiers arrives during applying first block.
   * Steps:
   *  - creates 6 blocks
   *  - send remoteModifiers with 1st, 2nd and 6th blocks
   *  - send remoteModifiers with 3rd, 4th and 5th blocks during applying first block
   *  - check that ModifiersProcessingResult message contain 6 applied blocks
   */
  @Test
  def remoteModifiersTwoMessages(): Unit = {
    val block1 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val block2 = generateNextSidechainBlock(block1, sidechainTransactionsCompanion, params)
    val block3 = generateNextSidechainBlock(block2, sidechainTransactionsCompanion, params)
    val block4 = generateNextSidechainBlock(block3, sidechainTransactionsCompanion, params)
    val block5 = generateNextSidechainBlock(block4, sidechainTransactionsCompanion, params)
    val block6 = generateNextSidechainBlock(block5, sidechainTransactionsCompanion, params)

    val firstRequestBlocks = Seq(block1, block2, block6)
    val secondRequestBlocks = Seq(block3, block4, block5)
    val correctSequence = Array(block1, block2, block3, block4, block5, block6)
    var blockIndex = 0

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())
    val blockMock = Mockito.mock(classOf[SidechainBlock])
    Mockito.when(history.bestBlock).thenReturn(blockMock)
    Mockito.when(blockMock.timestamp).thenReturn(Instant.now().toEpochMilli)

    Mockito.when(history.applicableTry(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      val block: SidechainBlock = answer.getArgument(0)

      if (block.id == correctSequence(blockIndex).id) {
        blockIndex += 1
        Success(Unit)
      } else
        Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[SidechainBlock]])

    mockedNodeViewHolderRef ! ModifiersFromRemote(firstRequestBlocks)
    mockedNodeViewHolderRef ! ModifiersFromRemote(secondRequestBlocks)

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) => {
            assertTrue("Applied block sequence is differ", applied.toSet.equals(correctSequence.toSet))
            assertTrue("Cleared block sequence is not empty.", cleared.isEmpty)
            true
          }
          case _ => false // Log
        }
    }
  }

  /*
   * This test check correctness of applying two remoteModifiers messages
   * Second remoteModifiers arrives during applying first block.
   * Steps:
   *  - creates 6 blocks
   *  - send remoteModifiers with 1st, 2nd and 6th blocks
   *  - send remoteModifiers with 3rd, 4th and 5th blocks after applying second block
   *  - check that first ModifiersProcessingResult message contain 2 applied blocks(1st, 2nd)
   *  - check that second ModifiersProcessingResult message contain 4 applied blocks(3rd, 4th, 5th, 6th)
   */
  @Test
  def remoteModifiersTwoSequences(): Unit = {
    val block1 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val block2 = generateNextSidechainBlock(block1, sidechainTransactionsCompanion, params)
    val block3 = generateNextSidechainBlock(block2, sidechainTransactionsCompanion, params)
    val block4 = generateNextSidechainBlock(block3, sidechainTransactionsCompanion, params)
    val block5 = generateNextSidechainBlock(block4, sidechainTransactionsCompanion, params)
    val block6 = generateNextSidechainBlock(block5, sidechainTransactionsCompanion, params)

    val firstRequestBlocks = Seq(block1, block2, block6)
    val secondRequestBlocks = Seq(block3, block4, block5)
    val correctSequence = Array(block1, block2, block3, block4, block5, block6)
    var blockIndex = 0

    val countDownController: CountDownLatchController = new CountDownLatchController(1)

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())

    Mockito.when(history.applicableTry(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      val block: SidechainBlock = answer.getArgument(0)

      if (block.id == correctSequence(blockIndex).id) {
        if (blockIndex == 1) {
          countDownController.countDown()
        }

        blockIndex += 1
        Success(Unit)
      } else
        Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[SidechainBlock]])

    mockedNodeViewHolderRef ! ModifiersFromRemote(firstRequestBlocks)
    countDownController.await(3000)
    Thread.sleep(1000)
    mockedNodeViewHolderRef ! ModifiersFromRemote(secondRequestBlocks)

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) => {
            assertTrue("Applied block sequence is differ", applied.toSet.equals(Set(block1, block2)))
            assertTrue("Cleared block sequence is not empty.", cleared.isEmpty)
            true
          }
          case _ => false
        }
    }

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, _) => {
            assertTrue("Applied block sequence is differ", applied.toSet.equals(Set(block3, block4, block5, block6)))
            true
          }
          case _ => false
        }
    }
  }

  /*
   * This test check cache cleaning in case the number of rejected blocks overwhelms cache size.
   * Steps:
   *  - create 520 blocks
   *  - apply first 3 blocks
   *  - reject all other blocks
   *  - check that 3 blocks were applied
   *  - check that number of cleared blocks(520 - numberOfAppliedBlock - cacheSize)
   */
  @Test
  def remoteModifiersCacheClean(): Unit = {
    val blocksNumber = 520
    val blocks = generateSidechainBlockSeq(blocksNumber, sidechainTransactionsCompanion, params, Some(genesisBlock.id))
    var blockIndex = 0
    val blockToApply = 3

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
       Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())
    val blockMock = Mockito.mock(classOf[SidechainBlock])
    Mockito.when(history.bestBlock).thenReturn(blockMock)
    Mockito.when(blockMock.timestamp).thenReturn(Instant.now().toEpochMilli)

    Mockito.when(history.applicableTry(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      val block: SidechainBlock = answer.getArgument(0)

      if (block.id == blocks(blockIndex).id && blockIndex < blockToApply) {
        blockIndex += 1
        Success(Unit)
      } else
        Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[SidechainBlock]])

    mockedNodeViewHolderRef ! ModifiersFromRemote(blocks)

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) => {
            assertEquals("Different number of applied blocks", blockToApply, applied.length)
            assertEquals("Different number of cleared blocks from cached", (blocksNumber - blockToApply - maxModifiersCacheSize), cleared.length)
            true
          }
          case _ => false
        }
    }
  }

  /*
* This test check that the sequence of applied modifiers gets flushed every 100 modifiers to avoid memory leaks.
*/
  @Test
  def flushAppliedModifiers(): Unit = {
    val twoHundredBlocks = generateSidechainBlockSeq(200, sidechainTransactionsCompanion, params, Some(genesisBlock.id))

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())
    val blockMock = Mockito.mock(classOf[SidechainBlock])
    Mockito.when(history.bestBlock).thenReturn(blockMock)
    Mockito.when(blockMock.timestamp).thenReturn(Instant.now().toEpochMilli / 1000)

    Mockito.when(history.applicableTry(ArgumentMatchers.any[SidechainBlock])).thenAnswer(answer => {
      Success(Unit)
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[SidechainBlock]])

    mockedNodeViewHolderRef ! ModifiersFromRemote(twoHundredBlocks)

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) =>
            assertEquals("Different number of applied blocks", 100, applied.length)
            assertEquals("Different number of cleared blocks from cached", 0, cleared.length)
            true
          case _ => false
        }
    }
    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) =>
            assertEquals("Different number of applied blocks", 100, applied.length)
            assertEquals("Different number of cleared blocks from cached", 0, cleared.length)
            true
          case _ => false
        }
    }
  }

  @Test
  def applyWithFullMempool(): Unit = {
    // Filling mempool
    var list = mutable.MutableList[RegularTransaction]()
    val lowestFeeTx = getRegularRandomTransaction(10, 580) // Approximate size 19940 bytes
    val secondLowestFeeTx = getRegularRandomTransaction(20, 580)
    val compatibleTransactin = getCompatibleTransaction(30, 580)
    val incompatibleTransactin = getIncompatibleTransaction(30, 580)
    val incompatibleTransactin2 = getIncompatibleTransaction(30, 580)
    val highFeeTransaction = getRegularRandomTransaction(40, 580)
    list += lowestFeeTx
    list += secondLowestFeeTx
    list += compatibleTransactin
    var totalTxSize = lowestFeeTx.size() + secondLowestFeeTx.size() + compatibleTransactin.size()
    mempool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(1))

    // Create list of transactions to fill mempool
    while (totalTxSize <= mempool.maxPoolSizeBytes) {
      val newTx = getRegularRandomTransaction(30, 580)
      list += newTx
      totalTxSize = totalTxSize + newTx.size()
    }

    // Last transaction will overwhelmed mempool. It should be removed.
    list = list.dropRight(1)

    val mockedNodeViewHolderTestRef: TestActorRef[MockedSidechainNodeViewHolder] = getMockedSidechainNodeViewHolderTestRef(history, state, wallet, mempool)
    val nodeViewHolder:MockedSidechainNodeViewHolder = mockedNodeViewHolderTestRef.underlyingActor

    //put all transaction
    for (i <- 0 to (list.size - 1)) {
      assertEquals("Put tx operation must be success.", true, mempool.put(list.apply(i)).isSuccess)
    }
    assertEquals("MemoryPool must have correct size ", list.size, mempool.size)
    assertEquals("Lowest fee transaction must be present ", true, mempool.getTransactionById(lowestFeeTx.id()).isPresent)

    val commontransaction = getRegularRandomTransaction(15, 580)

    Mockito.when(state.validate(ArgumentMatchers.any[SidechainTypes#SCBT])).thenReturn(Try{})

    // Test blocks
    // Try to add andremove transaction from mempool
    val removedBlock1 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, sidechainTransactions = Seq(commontransaction))
    val appliedBlock1 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, sidechainTransactions = Seq(commontransaction))
    val appliedBlock2 = generateNextSidechainBlock(appliedBlock1, sidechainTransactionsCompanion, params, sidechainTransactions = Seq())

    nodeViewHolder.updateMempool(Seq(removedBlock1), Seq(appliedBlock1, appliedBlock2), state)
    assertEquals("MemoryPool must have correct size ", list.size, nodeViewHolder.mempool.size)
    assertEquals("Common transaction must not be present ", false, nodeViewHolder.mempool.getTransactionById(commontransaction.id()).isPresent)
    assertEquals("Lowest fee transaction must be present ", true, nodeViewHolder.mempool.getTransactionById(lowestFeeTx.id()).isPresent)

    // Try to add incompatible transaction
    val removedBlock2 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, sidechainTransactions = Seq(incompatibleTransactin))
    val appliedBlock3 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, sidechainTransactions = Seq())

    nodeViewHolder.mempool.maxPoolSizeBytes = totalTxSize
    nodeViewHolder.updateMempool(Seq(removedBlock2), Seq(appliedBlock3), state)
    assertEquals("MemoryPool must have correct size ", list.size, nodeViewHolder.mempool.size)
    assertEquals("IncompatibleTransactin transaction must not be present ", false, nodeViewHolder.mempool.getTransactionById(incompatibleTransactin.id()).isPresent)
    assertEquals("Lowest fee transaction must be present ", true, nodeViewHolder.mempool.getTransactionById(lowestFeeTx.id()).isPresent)

    // Try to replace transaction with lowest fee
    val removedBlock3 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, sidechainTransactions = Seq(highFeeTransaction, incompatibleTransactin, incompatibleTransactin2))
    val appliedBlock4 = generateNextSidechainBlock(appliedBlock1, sidechainTransactionsCompanion, params, sidechainTransactions = Seq())

    nodeViewHolder.mempool.maxPoolSizeBytes = totalTxSize
    nodeViewHolder.updateMempool(Seq(removedBlock3), Seq(appliedBlock4), state)
    assertEquals("MemoryPool must have correct size ", list.size, nodeViewHolder.mempool.size)
    assertEquals("HighFeeTransaction transaction must be present ", true, nodeViewHolder.mempool.getTransactionById(highFeeTransaction.id()).isPresent)
    assertEquals("Lowest fee transaction must not be present ", false, nodeViewHolder.mempool.getTransactionById(lowestFeeTx.id()).isPresent)
  }

}
