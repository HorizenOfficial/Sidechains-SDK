package io.horizen.network

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.{TestActor, TestProbe}
import akka.util.Timeout
import io.horizen.account.api.http.AccountNodeViewUtilMocks
import io.horizen.api.http.SidechainApiMockConfiguration
import io.horizen.chain.SidechainBlockInfo
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.mempool.SidechainMemoryPool
import io.horizen.utxo.state.SidechainState
import io.horizen.utxo.wallet.SidechainWallet
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.utils.NetworkTimeProvider

import java.util.Optional
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class SyncStatusUtilTest {

  implicit lazy val actorSystem: ActorSystem = ActorSystem("tx-actor-test")
  implicit val timeout: Timeout = 5 second

  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()
  val utilMocks = new AccountNodeViewUtilMocks()

  /*
    In this test we calculate the estimated highest block given the following data (all timestamp are in GMT zone):
      - genesis block timestamp:        February 10, 2023  1:28:48 PM (1676035728)
      - node current block timestamp:   February 24, 2023  1:28:48 PM (1677245328)
      - test current timestamp:         March     3, 2023  1:31:31 PM (1677850291)
      - node current block height (related to the timestamp February 24, 2023 1:28:48 PM) is 63500
      - sidechain block rate is 12 seconds
      - block timestamp for sidechain half (block number 31750) is February 17, 2023 3:04:52 PM (1676646292)

    The SyncStatusUtil.calculateEstimatedHighestBlock method will estimate the potential highest block as follows:

    block correction calculated on genesis block will be:
      timestamp difference between current block timestamp and genesis block timestamp =  1677245328 - 1676035728 = 1209600
      max blocks from genesis block to current timestamp with 12s block rate = 1209600 / 12 = 100800
      block correction from genesis = 63500 / 100800 ~ 0.6299

    block correction calculated on sidechain half will be:
      timestamp difference between current block timestamp and sidechain half block timestamp =  1677245328 - 1676646292 = 599036
      max blocks from sidechain half block to current timestamp with 12s block rate = 599036 / 12 ~ 49920
      block correction from genesis = 31750 / 49920 ~ 0.636

    weighted average between slot correction
      block correction from genesis will be a weight of 0.5
      block correction from sidechain half will have a weight of 1.0
      average = [(0.6299 * 0.5) + (0.636 * 1)] / (0.5 + 1) ~ 0.634

    estimated highest block given the correction
      timestamp difference between the current timestamp and the current block timestamp = 1677850291 - 1677245328 = 604963
      max blocks in this range = 604963 / 12 ~ 50414
      estimated highest block = (50414 * 0.634) + current block height ~ 31962 + 63500 = 95462
   */
  @Test
  def checkSyncStatus(): Unit = {

    val timeoutDuration: FiniteDuration = new FiniteDuration(5, SECONDS)
    val consensusSecondsInSlot: Int = 12
    val currentBlockHeight: Int = 63500
    // 1677245328 --> February 24, 2023 1:28:48 PM
    val currentBlockTimestamp: Long = 1677245328L

    // time provider mock, return a mocked timestamp in millisecond
    // 1677850291000 --> March 3, 2023 1:31:31 PM
    val mockedTimeProvider = mock[NetworkTimeProvider]
    Mockito.when(mockedTimeProvider.time()).thenReturn(1677850291000L)

    // 1676035728 --> February 10, 2023 1:28:48 PM
    val genesisBlockTimestamp: Long = 1676035728L

    // sidechain block info mock, it will be retrieved by the mocked view
    val mockedSidechainBlockInfo = mock[SidechainBlockInfo]
    // 1676646292 --> February 17, 2023 3:04:52 PM
    Mockito.when(mockedSidechainBlockInfo.timestamp).thenReturn(1676646292L)

    // define the mocked sidechain node view holder
    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedBlockId = "0x1b"
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          val history: SidechainHistory = mock[SidechainHistory]
          // return mocked id when getBlockIdByHeight is called for sidechain half height
          when(history.getBlockIdByHeight(ArgumentMatchers.eq(currentBlockHeight/2)))
            .thenAnswer(_ => Optional.of(mockedBlockId))
          when(history.getBlockInfoById(ArgumentMatchers.eq(mockedBlockId)))
            .thenAnswer(_ => Optional.of(mockedSidechainBlockInfo))
          sender ! f(CurrentView(history, mock[SidechainState], mock[SidechainWallet], mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

    val estimatedHighestBlock = Await.result(mockedSidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
      SyncStatusUtil.calculateEstimatedHighestBlock(
        view,
        mockedTimeProvider,
        consensusSecondsInSlot,
        genesisBlockTimestamp,
        currentBlockHeight,
        currentBlockTimestamp
      )), timeoutDuration)
      .asInstanceOf[Int]

    assertEquals(estimatedHighestBlock, 95462)
  }

  /*
    In this test we calculate the estimated highest block in case the current block height is below the required threshold
    required for the block correction calculation, this threshold is set a SyncStatusUtil level and its value is currently
    15000 block. In this case the block correction will be the default value (0.65)

    The following data will be used:
      - current block height:    8500
      - current block timestamp: March 1, 2023 4:33:07 PM (1677688387)
      - test current timestamp:  March 3, 2023 1:31:31 PM (1677850291)
      - sidechain block rate:    12 s

    The SyncStatusUtil.calculateEstimatedHighestBlock method will estimate the potential highest block as follows:
      timestamp difference between the current timestamp and the current block timestamp = 1677850291 - 1677688387 = 161904
      max blocks in this range = 161904 / 12 ~ 13492
      estimated highest block = (13492 * 0.65) + current block height ~ 8769 + 8500 = 17269

   */
  @Test
  def checkSyncStatusUnderThreshold(): Unit = {

    val timeoutDuration: FiniteDuration = new FiniteDuration(5, SECONDS)
    val consensusSecondsInSlot: Int = 12
    val currentBlockHeight: Int = 8500
    // 1677245328 --> February 24, 2023 1:28:48 PM
    val currentBlockTimestamp: Long = 1677688387L

    // time provider mock, return a mocked timestamp in millisecond
    // 1677850291000 --> March 3, 2023 1:31:31 PM
    val mockedTimeProvider = mock[NetworkTimeProvider]
    Mockito.when(mockedTimeProvider.time()).thenReturn(1677850291000L)

    // 1676035728 --> February 10, 2023 1:28:48 PM
    val genesisBlockTimestamp: Long = 1676035728L

    // define the mocked sidechain node view holder
    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          sender ! f(CurrentView(mock[SidechainHistory], mock[SidechainState], mock[SidechainWallet], mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

    val estimatedHighestBlock = Await.result(mockedSidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
      SyncStatusUtil.calculateEstimatedHighestBlock(
        view,
        mockedTimeProvider,
        consensusSecondsInSlot,
        genesisBlockTimestamp,
        currentBlockHeight,
        currentBlockTimestamp
      )), timeoutDuration)
      .asInstanceOf[Int]

    assertEquals(estimatedHighestBlock, 17269)
  }

}
