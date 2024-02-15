package io.horizen.fixtures

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestActorRef
import io.horizen._
import io.horizen.utxo.SidechainNodeViewHolder
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.mempool.SidechainMemoryPool
import io.horizen.utxo.state.SidechainState
import io.horizen.utxo.wallet.SidechainWallet
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.block.Block.Timestamp
import sparkz.core.settings.{NetworkSettings, SparkzSettings}

class MockedSidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                                    history: SidechainHistory,
                                    state: SidechainState,
                                    wallet: SidechainWallet,
                                    var mempool: SidechainMemoryPool)
  extends SidechainNodeViewHolder(sidechainSettings, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null ) {

  override def dumpStorages: Unit = {}

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    Some(history, state, wallet, mempool)
  }

  override def getConsensusEpochNumber(timestamp: Timestamp) : Int = 100

  def updateMempool(blocksRemoved: Seq[SidechainBlock], blocksApplied: Seq[SidechainBlock], state: SidechainState): Unit = {
     this.mempool = this.updateMemPool(blocksRemoved, blocksApplied, this.mempool, state)
  }
}


trait MockedSidechainNodeViewHolderFixture extends MockitoSugar {
  val maxModifiersCacheSize = 100

  def getMockedSidechainNodeViewHolderRef(history: SidechainHistory, state: SidechainState, wallet: SidechainWallet, mempool: SidechainMemoryPool)
                                         (implicit actorSystem: ActorSystem): ActorRef = {
    val sidechainSettings = mock[SidechainSettings]
    val sparkzSettings = mock[SparkzSettings]
    val networkSettings = mock[NetworkSettings]
    val walletSettings = mock[WalletSettings]
    Mockito.when(sidechainSettings.sparkzSettings)
      .thenAnswer(answer => {
        sparkzSettings
      })
    Mockito.when(sparkzSettings.network)
      .thenAnswer(answer => {
      networkSettings
    })
    Mockito.when(networkSettings.maxModifiersCacheSize)
      .thenAnswer(answer => {
      maxModifiersCacheSize
    })
    Mockito.when(sidechainSettings.wallet)
      .thenAnswer(answer => {
        walletSettings
      })
    Mockito.when(sidechainSettings.wallet.maxTxFee)
      .thenAnswer(answer => {
        10000000L
      })
    actorSystem.actorOf(Props(new MockedSidechainNodeViewHolder(sidechainSettings, history, state, wallet, mempool)))
  }

  def getMockedSidechainNodeViewHolderTestRef(history: SidechainHistory, state: SidechainState, wallet: SidechainWallet, mempool: SidechainMemoryPool)
                                         (implicit actorSystem: ActorSystem): TestActorRef[MockedSidechainNodeViewHolder] = {
    val sidechainSettings = mock[SidechainSettings]
    val sparkzSettings = mock[SparkzSettings]
    val networkSettings = mock[NetworkSettings]
    val walletSettings = mock[WalletSettings]
    Mockito.when(sidechainSettings.sparkzSettings)
      .thenAnswer(answer => {
        sparkzSettings
      })
    Mockito.when(sparkzSettings.network)
      .thenAnswer(answer => {
        networkSettings
      })
    Mockito.when(networkSettings.maxModifiersCacheSize)
      .thenAnswer(answer => {
        maxModifiersCacheSize
      })
    Mockito.when(sidechainSettings.wallet)
      .thenAnswer(answer => {
        walletSettings
      })
    Mockito.when(sidechainSettings.wallet.maxTxFee)
      .thenAnswer(answer => {
        10000000L
      })
    TestActorRef(Props(new MockedSidechainNodeViewHolder(sidechainSettings, history, state, wallet, mempool)))
  }
}
