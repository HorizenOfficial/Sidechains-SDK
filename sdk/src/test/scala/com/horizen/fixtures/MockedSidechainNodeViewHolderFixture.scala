package com.horizen.fixtures

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.settings.{NetworkSettings, SparkzSettings}

class MockedSidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                                    history: SidechainHistory,
                                    state: SidechainState,
                                    wallet: SidechainWallet,
                                    mempool: SidechainMemoryPool)
  extends SidechainNodeViewHolder(sidechainSettings, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null ) {

  override def dumpStorages: Unit = {}

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    Some(history, state, wallet, mempool)
  }
}


trait MockedSidechainNodeViewHolderFixture extends MockitoSugar {
  val maxModifiersCacheSize = 10

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
}
