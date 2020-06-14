package com.horizen

import java.util

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus.{ConsensusEpochInfo, FullConsensusEpochInfo, intToConsensusEpochNumber}
import com.horizen.fixtures._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.utils.MerkleTree
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import scorex.core.NodeViewHolder.DownloadRequest
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import scorex.core.consensus.History.ProgressInfo
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import scorex.core.{VersionTag, idToVersion}
import scorex.util.ModifierId

import scala.util.Success

class SidechainNodeViewHolderTest extends JUnitSuite
  with MockedSidechainNodeViewHolderFixture
  with SidechainBlockFixture
  with CompanionsFixture
  with scorex.core.utils.ScorexEncoding
{
  var history: SidechainHistory = _
  var state: SidechainState = _
  var wallet: SidechainWallet = _
  var mempool: SidechainMemoryPool = _

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
  var mockedNodeViewHolderRef: ActorRef = _

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val genesisBlock: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion)
  val params: NetworkParams = RegTestParams()

  @Before
  def setUp(): Unit = {
    history = mock[SidechainHistory]
    state = mock[SidechainState]
    wallet = mock[SidechainWallet]
    mempool = SidechainMemoryPool.emptyPool
    mockedNodeViewHolderRef = getMockedSidechainNodeViewHolderRef(history, state, wallet, mempool)
  }

  @Test
  def consensusEpochSwitchNotification(): Unit = {
    // Test: Verify that consensus epoch switching block will emit the notification inside SidechainNodeViewHolder

    // Mock history to add the incoming block to the ProgressInfo append list
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer =>
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq(answer.getArgument(0).asInstanceOf[SidechainBlock]), Seq())))
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock])).thenReturn(history)
    // Mock state to notify that any incoming block to append will lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[SidechainBlock])).thenReturn(true)
    // Mock state to apply incoming block successfully
    Mockito.when(state.applyModifier(ArgumentMatchers.any[SidechainBlock])).thenReturn(Success(state))
    // Mock wallet to apply incoming block successfully
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[SidechainBlock])).thenReturn(wallet)


    var stateNotificationExecuted: Boolean = false
    Mockito.when(state.getCurrentConsensusEpochInfo).thenReturn({
      stateNotificationExecuted = true
      val merkleTree = MerkleTree.createMerkleTree(util.Arrays.asList("StringShallBe32LengthOrTestFail.".getBytes()))
      (genesisBlock.id, ConsensusEpochInfo(intToConsensusEpochNumber(0), merkleTree, 0L))
    })


    var historyNotificationExecuted: Boolean = false
    Mockito.when(history.applyFullConsensusInfo(ArgumentMatchers.any[ModifierId], ArgumentMatchers.any[FullConsensusEpochInfo])).thenAnswer(_ => {
      historyNotificationExecuted = true
      history
    })


    var walletNotificationExecuted: Boolean = false
    Mockito.when(wallet.applyConsensusEpochInfo(ArgumentMatchers.any[ConsensusEpochInfo])).thenAnswer(_ => {
      walletNotificationExecuted = true
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
    assertTrue("State epoch info calculation was not emitted.", stateNotificationExecuted)
    assertTrue("History epoch info processing was not emitted.", historyNotificationExecuted)
    assertTrue("Wallet epoch info processing was not emitted.", walletNotificationExecuted)
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
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq(blockToAppend), Seq()))
    })
    // History semantic validity check
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer => {
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
    // Wallet apply
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[SidechainBlock])).thenAnswer( answer => {
      val blockToApply: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("Wallet received different block to apply.", block.id, blockToApply.id)
      wallet
    })


    // Consensus epoch switching checks
    // Mock state to notify that any incoming block to append will NOT lead to chain switch
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[SidechainBlock])).thenReturn(false)
    // Check that consensus epoch data was not requested from the State.
    Mockito.when(state.getCurrentConsensusEpochInfo).thenAnswer( _ => {
      fail("Consensus epoch data should not being requested from the State.")
      null
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
      Success(history -> ProgressInfo[SidechainBlock](Some(branchPointBlock.id), Seq(firstBlockInActiveChain), Seq(firstBlockInFork, secondBlockInFork), Seq()))
    })
    // History semantic validity check for fork blocks - one by one.
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock]))
      .thenAnswer( answer => {
      val validBlock: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("History received semantically valid notification about different block. First fork block expected.",
        firstBlockInFork.id, validBlock.id)
      history
      })
      .thenAnswer( answer => {
      val validBlock: SidechainBlock = answer.getArgument(0).asInstanceOf[SidechainBlock]
      assertEquals("History received semantically valid notification about different block. Second fork block expected.",
        secondBlockInFork.id, validBlock.id)
      history
      })

    // Mock current version of the State
    Mockito.when(state.version).thenReturn(idToVersion(firstBlockInActiveChain.id))
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
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[SidechainBlock]))
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
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[SidechainBlock])).thenReturn(false)
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
  def downloadRequest(): Unit = {
    // Test: Verify the flow of requesting for download

    // Define block id to download
    val blockIdToDownload: ModifierId = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params).id
    // Create block to apply - just for test
    val block: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params)

    // History appending check
    Mockito.when(history.append(ArgumentMatchers.any[SidechainBlock])).thenAnswer( _ => {
      Success(history -> ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq(SidechainBlock.ModifierTypeId -> blockIdToDownload)))
    })
    // History semantic validity check
    Mockito.when(history.reportModifierIsValid(ArgumentMatchers.any[SidechainBlock])).thenAnswer( _ => {
      fail("History should NOT receive semantically valid notifications.")
      history
    })
    // State apply check
    Mockito.when(state.applyModifier(ArgumentMatchers.any[SidechainBlock])).thenAnswer( _ => {
      fail("State should NOT receive block to apply.")
      Success(state)
    })
    // Wallet apply
    Mockito.when(wallet.scanPersistent(ArgumentMatchers.any[SidechainBlock])).thenAnswer( _ => {
      fail("Wallet should NOT receive block to apply.")
      wallet
    })


    // Consensus epoch switching checks
    Mockito.when(state.isSwitchingConsensusEpoch(ArgumentMatchers.any[SidechainBlock])).thenAnswer( _ => {
      fail("State switching consensus check should NOT being emitted.")
      false
    })


    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[DownloadRequest])
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify requesting for download
    eventListener.expectMsgType[DownloadRequest]
  }
}
