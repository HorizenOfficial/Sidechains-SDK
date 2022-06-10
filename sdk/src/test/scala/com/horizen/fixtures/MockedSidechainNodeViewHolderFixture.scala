package com.horizen.fixtures

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.settings.{NetworkSettings, ScorexSettings}

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
    val scorexSettings = mock[ScorexSettings]
    val networkSettings = mock[NetworkSettings]
    val walletSettings = mock[WalletSettings]
    Mockito.when(sidechainSettings.scorexSettings)
      .thenAnswer(answer => {
        scorexSettings
      })
    Mockito.when(scorexSettings.network)
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
    Mockito.when(sidechainSettings.wallet.maxFee)
      .thenAnswer(answer => {
        10
      })
    actorSystem.actorOf(Props(new MockedSidechainNodeViewHolder(sidechainSettings, history, state, wallet, mempool)))
  }
}
