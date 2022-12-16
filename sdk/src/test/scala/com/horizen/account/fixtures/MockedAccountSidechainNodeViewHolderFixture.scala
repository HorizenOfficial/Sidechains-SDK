package com.horizen.account.fixtures

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.account.AccountSidechainNodeViewHolder
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.wallet.AccountWallet
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.settings.{NetworkSettings, SparkzSettings}

class MockedAccountSidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                                    history: AccountHistory,
                                    state: AccountState,
                                    wallet: AccountWallet,
                                    mempool: AccountMemoryPool)
  extends AccountSidechainNodeViewHolder(
    sidechainSettings,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null ) {

  override def dumpStorages(): Unit = {}

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    Some(history, state, wallet, mempool)
  }
}


trait MockedAccountSidechainNodeViewHolderFixture extends MockitoSugar {
  val maxModifiersCacheSize = 10

  def getMockedAccountSidechainNodeViewHolderRef(
                                           history: AccountHistory,
                                           state: AccountState,
                                           wallet: AccountWallet,
                                           mempool: AccountMemoryPool)
                                                (implicit actorSystem: ActorSystem): ActorRef = {
    val sidechainSettings = mock[SidechainSettings]
    val sparkzSettings = mock[SparkzSettings]
    val networkSettings = mock[NetworkSettings]
    val walletSettings = mock[WalletSettings]
    Mockito.when(sidechainSettings.sparkzSettings)
      .thenAnswer(_ => {
        sparkzSettings
      })
    Mockito.when(sparkzSettings.network)
      .thenAnswer(_ => {
      networkSettings
    })
    Mockito.when(networkSettings.maxModifiersCacheSize)
      .thenAnswer(_ => {
      maxModifiersCacheSize
    })
    Mockito.when(sidechainSettings.wallet)
      .thenAnswer(_ => {
        walletSettings
      })

    actorSystem.actorOf(Props(new MockedAccountSidechainNodeViewHolder(sidechainSettings, history, state, wallet, mempool)))
  }
}
