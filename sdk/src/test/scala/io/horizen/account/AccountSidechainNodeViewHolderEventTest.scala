package io.horizen.account

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestActorRef, TestProbe}
import io.horizen.account.AccountSidechainNodeViewHolder.NewExecTransactionsEvent
import io.horizen.account.block.AccountBlock
import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.state._
import io.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.account.wallet.AccountWallet
import io.horizen.consensus.ConsensusDataStorage
import io.horizen.cryptolibprovider.CircuitTypes.NaiveThresholdSignatureCircuit
import io.horizen.evm.{Address, Database}
import io.horizen.fixtures._
import io.horizen.fork.{ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.metrics.MetricsManager
import io.horizen.params.NetworkParams
import io.horizen.storage.SidechainSecretStorage
import io.horizen.utils.BytesUtils
import io.horizen.{AccountMempoolSettings, SidechainSettings, SidechainTypes, WalletSettings}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.core.VersionTag
import sparkz.core.utils.NetworkTimeProvider
import sparkz.util.SparkzEncoding

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

/*
  This class is used for testing events sent by memory pool.
 */

class AccountSidechainNodeViewHolderEventTest
    extends JUnitSuite
      with EthereumTransactionFixture
      with StoreFixture
      with SparkzEncoding {
  var historyMock: AccountHistory = _
  var state: AccountState = _
  var stateViewMock: AccountStateView = _
  var wallet: AccountWallet = _
  var mempool: AccountMemoryPool = _

  var eventNotifierProvider:AccountEventNotifierProvider = _

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
  var mockedNodeViewHolderRef: ActorRef = _

  val mockStateDbNonces:TrieMap[Address, BigInteger]  = TrieMap[Address, BigInteger]()


  @Before
  def setUp(): Unit = {
    MetricsManager.init(mock[NetworkTimeProvider])
    ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")
    historyMock = mock[AccountHistory]

    stateViewMock = mock[AccountStateView]
    Mockito
      .when(stateViewMock.getBalance(ArgumentMatchers.any[Address]))
      .thenReturn(ZenWeiConverter.MAX_MONEY_IN_WEI) // Has always enough balance
    Mockito.when(stateViewMock.isEoaAccount(ArgumentMatchers.any[Address])).thenReturn(true)
    Mockito.when(stateViewMock.getNextBaseFee).thenReturn(BigInteger.ZERO)

    Mockito.when(stateViewMock.getNonce(ArgumentMatchers.any[Address])).thenAnswer { answer =>
      {
        mockStateDbNonces.getOrElse(answer.getArgument(0), BigInteger.ZERO)
      }
    }

    wallet = mock[AccountWallet]
    Mockito.when(wallet.scanOffchain(ArgumentMatchers.any[SidechainTypes#SCAT])).thenReturn(wallet)

  }

  @Test
  def sendNewExecTxsEventTest(): Unit = {

    val mempoolSettings = AccountMempoolSettings()
    val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)

    val eventListener = TestProbe()
    actorSystem.eventStream.subscribe(eventListener.ref, classOf[NewExecTransactionsEvent])

    val tx10 = createEIP1559Transaction(BigInteger.ONE, nonce = BigInteger.valueOf(10))
    nodeViewHolder.txModify(tx10.asInstanceOf[SidechainTypes#SCAT])

    assertEquals(1, mempool.size)
    eventListener.expectNoMessage(3.seconds)

    val tx0 = createEIP1559Transaction(BigInteger.ONE, nonce = BigInteger.ZERO)
    nodeViewHolder.txModify(tx0.asInstanceOf[SidechainTypes#SCAT])

    assertEquals(2, mempool.size)
    var event = eventListener.expectMsgType[NewExecTransactionsEvent]
    assertEquals("Wrong number of txs in the event", 1, event.newExecTxs.size)
    assertTrue(event.newExecTxs.exists(_.equals(tx0)))

    val block: AccountBlock = mock[AccountBlock]
    Mockito.when(block.transactions).thenReturn(Seq.empty[SidechainTypes#SCAT])
    val listOfAppliedBlocks = Seq[AccountBlock](block)
    var listOfRejectedBlocks = Seq[AccountBlock](block)

    nodeViewHolder.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks, mempool, state)

    eventListener.expectNoMessage(3.seconds)

    val rejectedBlock: AccountBlock = mock[AccountBlock]
    val rejectedTx = createEIP1559Transaction(BigInteger.ONE, nonce = BigInteger.ZERO).asInstanceOf[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(Seq(rejectedTx))
    listOfRejectedBlocks = Seq[AccountBlock](rejectedBlock)

    nodeViewHolder.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks, mempool, state)
    event = eventListener.expectMsgType[NewExecTransactionsEvent]
    assertEquals("Wrong number of txs in the event", 1, event.newExecTxs.size)
    assertTrue(event.newExecTxs.exists(_.equals(rejectedTx)))

  }

  class MockedAccountSidechainNodeViewHolder(
      sidechainSettings: SidechainSettings,
      params: NetworkParams,
      timeProvider: NetworkTimeProvider,
      historyStorage: AccountHistoryStorage,
      consensusDataStorage: ConsensusDataStorage,
      stateMetadataStorage: AccountStateMetadataStorage,
      stateDbStorage: Database,
      customMessageProcessors: Seq[MessageProcessor],
      secretStorage: SidechainSecretStorage,
      genesisBlock: AccountBlock
  ) extends AccountSidechainNodeViewHolder(
        sidechainSettings,
        params,
        timeProvider,
        historyStorage,
        consensusDataStorage,
        stateMetadataStorage,
        stateDbStorage,
        customMessageProcessors,
        secretStorage,
        genesisBlock
      ) {
    override def txModify(tx: SidechainTypes#SCAT): Unit = super.txModify(tx)

    override def minimalState(): AccountState = state

    override def history(): AccountHistory = historyMock

    override def vault(): AccountWallet = wallet

    override def memoryPool(): AccountMemoryPool = mempool

    override protected def genesisState: (HIS, MS, VL, MP) = (history(), state, wallet, mempool)

    override def updateMemPool(
        blocksRemoved: Seq[AccountBlock],
        blocksApplied: Seq[AccountBlock],
        memPool: AccountMemoryPool,
        state: AccountState
    ): AccountMemoryPool = super.updateMemPool(blocksRemoved, blocksApplied, memPool, state)

  }

  def getMockedAccountSidechainNodeViewHolder(mempoolSettings: AccountMempoolSettings)(implicit
      actorSystem: ActorSystem
  ): MockedAccountSidechainNodeViewHolder = {
    val sidechainSettings = mock[SidechainSettings]
    val mockWalletSettings: WalletSettings = mock[WalletSettings]
    Mockito.when(mockWalletSettings.maxTxFee).thenReturn(100L)
    Mockito.when(sidechainSettings.wallet).thenReturn(mockWalletSettings)
    val params: NetworkParams = mock[NetworkParams]
    Mockito.when(params.chainId).thenReturn(1997)
    Mockito.when(params.circuitType).thenReturn(NaiveThresholdSignatureCircuit)
    val timeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]

    val historyStorage: AccountHistoryStorage = mock[AccountHistoryStorage]
    val consensusDataStorage: ConsensusDataStorage = mock[ConsensusDataStorage]
    val stateMetadataStorage: AccountStateMetadataStorage = mock[AccountStateMetadataStorage]
    Mockito.when(stateMetadataStorage.isEmpty).thenReturn(true)
    Mockito.when(stateMetadataStorage.getConsensusEpochNumber).thenReturn(None)
    val stateDbStorage: Database = mock[Database]
    val customMessageProcessors: Seq[MessageProcessor] = Seq()
    val secretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val genesisBlock: AccountBlock = mock[AccountBlock]

    val versionTag: VersionTag = VersionTag @@ BytesUtils.toHexString(getVersion.data())

    state = new AccountState(
      params,
      timeProvider,
      MockedHistoryBlockHashProvider,
      versionTag,
      stateMetadataStorage,
      stateDbStorage,
      Seq()
    ) {
      override def getView: AccountStateView = stateViewMock
    }

    eventNotifierProvider = mock[AccountEventNotifierProvider]

    mempool = AccountMemoryPool.createEmptyMempool(() => state, () => state, mempoolSettings, eventNotifierProvider)

    val nodeViewHolderRef: TestActorRef[MockedAccountSidechainNodeViewHolder] = TestActorRef(
      Props(
        new MockedAccountSidechainNodeViewHolder(
          sidechainSettings,
          params,
          timeProvider,
          historyStorage,
          consensusDataStorage,
          stateMetadataStorage,
          stateDbStorage,
          customMessageProcessors,
          secretStorage,
          genesisBlock
        )
      )
    )
    Mockito.when(eventNotifierProvider.getEventNotifier()).thenReturn(nodeViewHolderRef.underlyingActor)
    nodeViewHolderRef.underlyingActor
  }

}
