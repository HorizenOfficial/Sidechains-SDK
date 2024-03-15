package io.horizen.account

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import akka.util.Timeout
import io.horizen.account.block.AccountBlock
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.fixtures.{AccountBlockFixture, EthereumTransactionFixture, ForgerAccountFixture, MockedAccountSidechainNodeViewHolderFixture}
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.state.{AccountEventNotifier, AccountState, AccountStateReaderProvider, BaseStateReaderProvider}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment}
import io.horizen.account.wallet.AccountWallet
import io.horizen.block.SidechainBlockBase
import io.horizen.consensus.{ConsensusEpochInfo, FullConsensusEpochInfo, intToConsensusEpochNumber}
import io.horizen.fixtures._
import io.horizen.params.{NetworkParams, RegTestParams}
import io.horizen.utils.{CountDownLatchController, MerkleTree, WithdrawalEpochInfo}
import io.horizen.{AccountMempoolSettings, SidechainSettings}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.times
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import sparkz.core.NodeViewHolder.ReceivableMessages.{LocallyGeneratedModifier, LocallyGeneratedTransaction, ModifiersFromRemote}
import sparkz.core.consensus.History.ProgressInfo
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{FailedTransaction, ModifiersProcessingResult, SemanticallySuccessfulModifier}
import sparkz.core.validation.RecoverableModifierError
import sparkz.core.{VersionTag, idToVersion}
import sparkz.util.{ModifierId, SparkzEncoding}

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class AccountSidechainNodeViewHolderTest extends JUnitSuite
  with MockedAccountSidechainNodeViewHolderFixture
  with AccountBlockFixture
  with CompanionsFixture
  with SparkzEncoding
  with EthereumTransactionFixture
{
  var history: AccountHistory = _
  var state: AccountState = _
  var wallet: AccountWallet = _
  var mempool: AccountMemoryPool = _
  var accountStateReaderProvider: AccountStateReaderProvider = _
  var baseStateReaderProvider: BaseStateReaderProvider = _
  var eventNotifier: AccountEventNotifier = _

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
  var mockedNodeViewHolderRef: ActorRef = _

  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion

  val genesisBlock: AccountBlock = AccountBlockFixture.generateAccountBlock(sidechainTransactionsCompanion)
  val params: NetworkParams = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())
  implicit lazy val timeout: Timeout = Timeout(10000 milliseconds)


  @Before
  def setUp(): Unit = {
    history = mock[AccountHistory]
    state = mock[AccountState]
    wallet = mock[AccountWallet]
    accountStateReaderProvider = mock[AccountStateReaderProvider]
    baseStateReaderProvider = mock[BaseStateReaderProvider]
    mempool = AccountMemoryPool.createEmptyMempool(accountStateReaderProvider, baseStateReaderProvider,
      AccountMempoolSettings(), () => mock[AccountEventNotifier])
    mockedNodeViewHolderRef = getMockedAccountSidechainNodeViewHolderRef(history, state, wallet, mempool)
  }

  @Test
  def consensusEpochSwitchNotification(): Unit = {
    // Test: Verify that consensus epoch switching block will emit the notification inside SidechainNodeViewHolder

    // Mock history to add the incoming block to the ProgressInfo append list
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer =>
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq(answer.getArgument(0).asInstanceOf[AccountBlock]))))
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[AccountBlock])).thenReturn(Try(history))
    // Mock state to notify that any incoming block to append will lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(true)
    // Mock state to apply incoming block successfully
    Mockito.when(state.applyModifier(ArgumentMatchers.any[AccountBlock])).thenReturn(Success(state))
    // Mock state withdrawal epoch methods
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(0, 1))
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(false)

    Mockito.when(state.getCurrentConsensusEpochInfo).thenReturn({
      val merkleTree = MerkleTree.createMerkleTree(util.Arrays.asList("StringShallBe32LengthOrTestFail.".getBytes(StandardCharsets.UTF_8)))
      (genesisBlock.id, ConsensusEpochInfo(intToConsensusEpochNumber(0), merkleTree, 0L))
    })

    Mockito.when(history.applyFullConsensusInfo(ArgumentMatchers.any[ModifierId], ArgumentMatchers.any[FullConsensusEpochInfo])).thenAnswer(_ => {
      history
    })

    // Mock wallet scanPersistent with checks
    var walletChecksPassed: Boolean = false
    Mockito.when(wallet.scanPersistent(
      ArgumentMatchers.any[AccountBlock]))
      .thenAnswer(_ => {
        walletChecksPassed = true
        wallet
      })


    Mockito.when(wallet.applyConsensusEpochInfo(ArgumentMatchers.any[ConsensusEpochInfo])).thenAnswer(_ => {
      wallet
    })


    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()

    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[AccountBlock]])
    val block = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[AccountBlock]]

    // Verify that all Consensus Epoch switching methods were executed
    Mockito.verify(state, times(1)).getCurrentConsensusEpochInfo
    Mockito.verify(history, times(1)).applyFullConsensusInfo(ArgumentMatchers.any[ModifierId], ArgumentMatchers.any[FullConsensusEpochInfo])
    Mockito.verify(wallet, times(1)).applyConsensusEpochInfo(ArgumentMatchers.any[ConsensusEpochInfo])
  }

  @Test
  def continueActiveChain(): Unit = {
    // Test: Verify the flow of continuation the active chain by a single block applied to the node.

    // Create block to apply
    val block: AccountBlock = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer => {
      val blockToAppend: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
      assertEquals("History received different block to append.", block.id, blockToAppend.id)
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq(blockToAppend)))
    })
    // History semantic validity check
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer => Try {
      val validBlock: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
      assertEquals("History received semantically valid notification about different block.", block.id, validBlock.id)
      history
    })
    // State apply check
    Mockito.when(state.applyModifier(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer => {
      val blockToApply: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
      assertEquals("State received different block to apply.", block.id, blockToApply.id)
      Success(state)
    })
    // Mock state withdrawal epoch methods
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(0, 1))
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(false)
    // Wallet apply
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer => {
      val blockToApply: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
      assertEquals("Wallet received different block to apply.", block.id, blockToApply.id)
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
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[AccountBlock]])
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[AccountBlock]]
  }

  @Test
  def chainSwitch(): Unit = {
    // Test: Verify the flow of chain switch caused by a single block applied to the node.

    // Create blocks for test
    val branchPointBlock: AccountBlock = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val firstBlockInActiveChain: AccountBlock = generateNextAccountBlock(branchPointBlock, sidechainTransactionsCompanion, params)
    val firstBlockInFork: AccountBlock = generateNextAccountBlock(branchPointBlock, sidechainTransactionsCompanion, params)
    val secondBlockInFork: AccountBlock = generateNextAccountBlock(firstBlockInFork, sidechainTransactionsCompanion, params)

    // Appending secondBlockInFork that should emit the chains switch
    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer => {
      val blockToAppend: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
      assertEquals("History received different block to append.", secondBlockInFork.id, blockToAppend.id)
      Success(history -> ProgressInfo[AccountBlock](Some(branchPointBlock.id), Seq(firstBlockInActiveChain), Seq(firstBlockInFork, secondBlockInFork)))
    })
    // History semantic validity check for fork blocks - one by one.
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[AccountBlock]))
      .thenAnswer( answer => Try {
      val validBlock: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
      assertEquals("History received semantically valid notification about different block. First fork block expected.",
        firstBlockInFork.id, validBlock.id)
      history
      })
      .thenAnswer( answer => Try {
      val validBlock: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
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
    Mockito.when(state.applyModifier(ArgumentMatchers.any[AccountBlock]))
      .thenAnswer( answer => {
      val blockToApply: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
      assertEquals("State received different block to apply. First fork block expected.", firstBlockInFork.id, blockToApply.id)
      Success(state)
      })
      .thenAnswer( answer => {
      val blockToApply: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
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

    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[AccountBlock]))
      .thenAnswer( answer => {
        val blockToApply: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
        assertEquals("Wallet received different block to apply. First fork block expected.", firstBlockInFork.id, blockToApply.id)
        wallet
      })
      .thenAnswer( answer => {
        val blockToApply: AccountBlock = answer.getArgument(0).asInstanceOf[AccountBlock]
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
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[AccountBlock]])
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(secondBlockInFork)


    // Verify successful applying for 2 fork blocks
    eventListener.expectMsgType[SemanticallySuccessfulModifier[AccountBlock]]
    eventListener.expectMsgType[SemanticallySuccessfulModifier[AccountBlock]]
  }

  @Test
  def withdrawalEpochInTheMiddle(): Unit = {
    // Test: Verify that SC block in the middle of withdrawal epoch will NOT process fee payments.

    // Mock history to add the incoming block to the ProgressInfo append list
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer =>
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq(answer.getArgument(0).asInstanceOf[AccountBlock]))))
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[AccountBlock])).thenReturn(Try(history))
    // Mock state to notify that any incoming block to append will NOT lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(false)
    // Mock state to apply incoming block successfully
    Mockito.when(state.applyModifier(ArgumentMatchers.any[AccountBlock])).thenReturn(Success(state))

    // Mock state withdrawal epoch methods
    val withdrawalEpochInfo = WithdrawalEpochInfo(0, 1)
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(withdrawalEpochInfo)
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(false)

    // Mock state fee payments with checks
    Mockito.when(state.getFeePaymentsInfo(ArgumentMatchers.any[Int](), any(), any(),ArgumentMatchers.any[Option[AccountBlockFeeInfo]])).thenAnswer(_ => {
      (Seq(), BigInteger.valueOf(Long.MaxValue))
    })

    // Mock wallet scanPersistent with checks
    var walletChecksPassed: Boolean = false
    Mockito.when(wallet.scanPersistent(
      ArgumentMatchers.any[AccountBlock]))
      .thenAnswer(_ => {
        walletChecksPassed = true
        wallet
      })

    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[AccountBlock]])
    val block = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[AccountBlock]]
    Thread.sleep(100)

    // Verify that all the checks passed
    Mockito.verify(state, times(0)).getFeePaymentsInfo(ArgumentMatchers.any[Int](), any(), any(),ArgumentMatchers.any[Option[AccountBlockFeeInfo]])
  }

  @Test
  def withdrawalEpochLastIndex(): Unit = {
    // Test: Verify that SC block leading to the last withdrawal epoch index will calculate fee payments.

    // Mock history to add the incoming block to the ProgressInfo append list
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer( answer =>
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq(answer.getArgument(0).asInstanceOf[AccountBlock]))))
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[AccountBlock])).thenReturn(Try(history))
    Mockito.when(history.updateFeePaymentsInfo(ArgumentMatchers.any[ModifierId],ArgumentMatchers.any[AccountFeePaymentsInfo])).thenReturn(history)
    // Mock state to notify that any incoming block to append will NOT lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[Long])).thenReturn(false)
    // Mock state to apply incoming block successfully
    Mockito.when(state.applyModifier(ArgumentMatchers.any[AccountBlock])).thenReturn(Success(state))

    val withdrawalEpochInfo = WithdrawalEpochInfo(3, params.withdrawalEpochLength)
    // Mock state to reach the last withdrawal epoch index
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(withdrawalEpochInfo)
    Mockito.when(state.isWithdrawalEpochLastIndex).thenReturn(true)
    Mockito.when(state.getCurrentConsensusEpochInfo).thenReturn({
      val merkleTree = MerkleTree.createMerkleTree(util.Arrays.asList("StringShallBe32LengthOrTestFail.".getBytes(StandardCharsets.UTF_8)))
      (genesisBlock.id, ConsensusEpochInfo(intToConsensusEpochNumber(0), merkleTree, 0L))
    })

    // Mock state fee payments with checks
    val expectedFeePayments: Seq[AccountPayment] = Seq(ForgerAccountFixture.getAccountPayment(0L), ForgerAccountFixture.getAccountPayment(1L))
    Mockito.when(state.getFeePaymentsInfo(ArgumentMatchers.any[Int](), any(), any(), ArgumentMatchers.any[Option[AccountBlockFeeInfo]]())).thenAnswer(args => {
      val epochNumber: Int = args.getArgument(0)
      assertEquals("Different withdrawal epoch number expected.", withdrawalEpochInfo.epoch, epochNumber)
      (expectedFeePayments, BigInteger.valueOf(Long.MaxValue))
    })

    // Mock wallet scanPersistent with checks

    Mockito.when(wallet.scanPersistent(
      ArgumentMatchers.any[AccountBlock]))
      .thenAnswer(_ => {
        wallet
      })

    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[AccountBlock]])
    val block = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)

    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[AccountBlock]]
    Thread.sleep(100)

    // Verify that all the checks passed
    Mockito.verify(state, times(1)).getFeePaymentsInfo(ArgumentMatchers.any[Int](), any(), any(), ArgumentMatchers.any[Option[AccountBlockFeeInfo]])
  }

  @Test
  def remoteModifiers(): Unit = {
    val block1 = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val block2 = generateNextAccountBlock(block1, sidechainTransactionsCompanion, params)
    val block3 = generateNextAccountBlock(block2, sidechainTransactionsCompanion, params)
    val block4 = generateNextAccountBlock(block3, sidechainTransactionsCompanion, params)
    val block5 = generateNextAccountBlock(block4, sidechainTransactionsCompanion, params)
    val block6 = generateNextAccountBlock(block5, sidechainTransactionsCompanion, params)
    val block7 = generateNextAccountBlock(block6, sidechainTransactionsCompanion, params)
    val block8 = generateNextAccountBlock(block7, sidechainTransactionsCompanion, params)

    val blocks = Array(block1, block2, block3, block4, block5, block6, block7, block8)
    var blockIndex: Int = 0

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer( _ => {
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())

    Mockito.when(history.applicableTry(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      val block: AccountBlock = answer.getArgument(0)

      if (block.id == blocks(blockIndex).id) {
        blockIndex += 1
        Success(Unit)
      } else
        Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[AccountBlock]])

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
    val block1 = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val block2 = generateNextAccountBlock(block1, sidechainTransactionsCompanion, params)
    val block3 = generateNextAccountBlock(block2, sidechainTransactionsCompanion, params)
    val block4 = generateNextAccountBlock(block3, sidechainTransactionsCompanion, params)
    val block5 = generateNextAccountBlock(block4, sidechainTransactionsCompanion, params)
    val block6 = generateNextAccountBlock(block5, sidechainTransactionsCompanion, params)

    val firstRequestBlocks = Seq(block1, block2, block6)
    val secondRequestBlocks = Seq(block3, block4, block5)
    val correctSequence = Array(block1, block2, block3, block4, block5, block6)
    var blockIndex = 0

    // Mock wallet scanPersistent with checks
    Mockito.when(wallet.scanPersistent(
      ArgumentMatchers.any[AccountBlock]))
      .thenAnswer(_ => {
        wallet
      })

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer(_ => {
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())

    Mockito.when(history.applicableTry(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      val block: AccountBlock = answer.getArgument(0)

      if (block.id == correctSequence(blockIndex).id) {
        blockIndex += 1
        Success(Unit)
      } else
        Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[AccountBlock]])

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
    val block1 = generateNextAccountBlock(genesisBlock, sidechainTransactionsCompanion, params)
    val block2 = generateNextAccountBlock(block1, sidechainTransactionsCompanion, params)
    val block3 = generateNextAccountBlock(block2, sidechainTransactionsCompanion, params)
    val block4 = generateNextAccountBlock(block3, sidechainTransactionsCompanion, params)
    val block5 = generateNextAccountBlock(block4, sidechainTransactionsCompanion, params)
    val block6 = generateNextAccountBlock(block5, sidechainTransactionsCompanion, params)

    val firstRequestBlocks = Seq(block1, block2, block6)
    val secondRequestBlocks = Seq(block3, block4, block5)
    val correctSequence = Array(block1, block2, block3, block4, block5, block6)
    var blockIndex = 0

    val countDownController: CountDownLatchController = new CountDownLatchController(1)

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())

    Mockito.when(history.applicableTry(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      val block: AccountBlock = answer.getArgument(0)

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
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[AccountBlock]])

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
   *  - create 200 blocks
   *  - apply first 3 blocks
   *  - reject all other blocks
   *  - check that 3 blocks were applied
   *  - check that number of cleared blocks(200 - numberOfAppliedBlock - cacheSize)
   */
  @Test
  def remoteModifiersCacheClean(): Unit = {
    val blocksNumber = maxModifiersCacheSize * 2
    val blocks = generateAccountBlockSeq(blocksNumber, sidechainTransactionsCompanion, params, Some(genesisBlock.id))
    var blockIndex = 0
    val blockToApply = 3

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
       Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())
    val blockMock = Mockito.mock(classOf[AccountBlock])
    Mockito.when(history.bestBlock).thenReturn(blockMock)
    Mockito.when(blockMock.timestamp).thenReturn(Instant.now().toEpochMilli)

    Mockito.when(history.applicableTry(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      val block: AccountBlock = answer.getArgument(0)

      if (block.id == blocks(blockIndex).id && blockIndex < blockToApply) {
        blockIndex += 1
        Success(Unit)
      } else
        Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[AccountBlock]])

    mockedNodeViewHolderRef ! ModifiersFromRemote(blocks)

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) =>
            assertEquals("Different number of applied blocks", blockToApply, applied.length)
            assertEquals("Different number of cleared blocks from cached", (blocksNumber - blockToApply - maxModifiersCacheSize), cleared.length)
            true
          case _ => false
        }
    }
  }

  /*
 * This test check that in case the modifier cache is at least half full,
 *  we will start skipping blocks that are 24h older than the best block.
 */
  @Test
  def remoteModifiersSkipTooFarTimestamp(): Unit = {
    val halfFullCache = maxModifiersCacheSize / 2
    val halfFullCacheBlocks = generateAccountBlockSeq(halfFullCache, sidechainTransactionsCompanion, params, Some(genesisBlock.id))
    val twoHundredBlocks = generateAccountBlockSeq(200, sidechainTransactionsCompanion, params, Some(genesisBlock.id))

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())
    val blockMock = Mockito.mock(classOf[AccountBlock])
    Mockito.when(history.bestBlock).thenReturn(blockMock)
    Mockito.when(blockMock.timestamp).thenReturn(Instant.now().toEpochMilli / 1000 - TimeUnit.HOURS.toSeconds(48))

    Mockito.when(history.applicableTry(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      Failure(new RecoverableModifierError("Parent block is not in history yet"))
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[AccountBlock]])

    mockedNodeViewHolderRef ! ModifiersFromRemote(halfFullCacheBlocks)
    mockedNodeViewHolderRef ! ModifiersFromRemote(twoHundredBlocks)

    eventListener.fishForMessage(timeout.duration) {
      case m =>
        m match {
          case ModifiersProcessingResult(applied, cleared) =>
            assertEquals("Different number of applied blocks", 0, applied.length)
            assertEquals("Different number of cleared blocks from cached", 200, cleared.length)
            true
          case _ => false
        }
    }
  }

  /*
  * This test check that the sequence of applied modifiers gets flushed every 100 modifiers to avoid memory leaks.
  */
  @Test
  def flushAppliedModifiers(): Unit = {
    val twoHundredBlocks = generateAccountBlockSeq(200, sidechainTransactionsCompanion, params, Some(genesisBlock.id))

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      Success(history -> ProgressInfo[AccountBlock](None, Seq(), Seq()))
    })

    Mockito.when(history.openSurfaceIds()).thenReturn(Seq())
    val blockMock = Mockito.mock(classOf[AccountBlock])
    Mockito.when(history.bestBlock).thenReturn(blockMock)
    Mockito.when(blockMock.timestamp).thenReturn(Instant.now().toEpochMilli / 1000)

    Mockito.when(history.applicableTry(ArgumentMatchers.any[AccountBlock])).thenAnswer(answer => {
      Success(Unit)
    })

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[ModifiersProcessingResult[AccountBlock]])

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
  def testForbidLegacyTransaction(): Unit = {
    val settings = mock[SidechainSettings]
    val mempoolSetting = mock[AccountMempoolSettings]
    mockedNodeViewHolderRef = getMockedAccountSidechainNodeViewHolderRef(history, state, wallet, mempool, settings)
    val tx = mock[EthereumTransaction]

    Mockito.when(settings.accountMempool).thenReturn(mempoolSetting)
    Mockito.when(mempoolSetting.allowUnprotectedTxs).thenReturn(false)
    Mockito.when(tx.isLegacy).thenReturn(true)
    Mockito.when(tx.isEIP155).thenReturn(false)

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[FailedTransaction])
    mockedNodeViewHolderRef ! LocallyGeneratedTransaction(tx)


    val failure = eventListener.expectMsgType[FailedTransaction]
    assertEquals(failure.error.getMessage,"Legacy unprotected transactions are not allowed.")
  }
}
