package com.horizen

import akka.actor.ActorRef
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures._
import com.horizen.transaction.TransactionSerializer
import org.junit.{Before, Test}
import org.scalatest.junit.JUnitSuite
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.utils.ConsensusEpochInfo
import org.mockito.{ArgumentMatchers, Mockito}
import scorex.core.consensus.History.ProgressInfo
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier

import scala.util.Success
import org.junit.Assert.{assertEquals, assertTrue}
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import akka.testkit.TestProbe


class SidechainNodeViewHolderTest extends JUnitSuite
  with MockedSidechainNodeViewHolderFixture
  with SidechainBlockFixture
  with scorex.core.utils.ScorexEncoding
{
  var history: SidechainHistory = _
  var state: SidechainState = _
  var wallet: SidechainWallet = _
  var mempool: SidechainMemoryPool = _
  var mockedNodeViewHolderRef: ActorRef = _

  val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]] = new JHashMap()
  val sidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers)

  @Before
  def setUp(): Unit = {
    history = mock[SidechainHistory]
    state = mock[SidechainState]
    wallet = mock[SidechainWallet]
    mempool = SidechainMemoryPool.emptyPool
    mockedNodeViewHolderRef = getMockedSidechainNodeViewHolderRef(history, state, wallet, mempool)

  }

  @Test
  def consensusEpochSwitchNotification: Unit = {
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
      mock[ConsensusEpochInfo]
    })


    var historyNotificationExecuted: Boolean = false
    Mockito.when(history.applyConsensusEpochInfo(ArgumentMatchers.any[ConsensusEpochInfo])).thenAnswer(answer => {
      historyNotificationExecuted = true
      history
    })


    var walletNotificationExecuted: Boolean = false
    Mockito.when(wallet.applyConsensusEpochInfo(ArgumentMatchers.any[ConsensusEpochInfo])).thenAnswer(answer => {
      walletNotificationExecuted = true
      wallet
    })



    // Send locally generated block to the NodeViewHolder
    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
    val block: SidechainBlock = generateGenesisBlock(sidechainTransactionsCompanion)
    mockedNodeViewHolderRef ! LocallyGeneratedModifier(block)


    // Verify successful applying
    eventListener.expectMsgType[SemanticallySuccessfulModifier[SidechainBlock]]

    // Verify that all Consensus Epoch swithichng methods were executed
    assertTrue("State epoch info calculation was not emitted.", stateNotificationExecuted)
    assertTrue("History epoch info processing was not emitted.", historyNotificationExecuted)
    assertTrue("Wallet epoch info processing was not emitted.", walletNotificationExecuted)
  }
}
