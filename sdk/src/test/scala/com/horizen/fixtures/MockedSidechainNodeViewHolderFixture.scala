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
  extends SidechainNodeViewHolder(sidechainSettings, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) {

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    Some(history, state, wallet, mempool)
  }
}


trait MockedSidechainNodeViewHolderFixture extends MockitoSugar {
  def getMockedSidechainNodeViewHolderRef(history: SidechainHistory, state: SidechainState, wallet: SidechainWallet, mempool: SidechainMemoryPool)
                                         (implicit actorSystem: ActorSystem): ActorRef = {
    val sidechainSettings = mock[SidechainSettings]
    val scorexSettings = mock[ScorexSettings]
    val networkSettings = mock[NetworkSettings]
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
      10
    })

    actorSystem.actorOf(Props(new MockedSidechainNodeViewHolder(sidechainSettings, history, state, wallet, mempool)))
  }
}
