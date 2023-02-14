package com.horizen.account.forger

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.{SidechainTypes, _}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.wallet.AccountWallet
import com.horizen.forge.{AbstractForger, MainchainSynchronizer}
import com.horizen.params.NetworkParams
import sparkz.core.utils.NetworkTimeProvider


class AccountForger(settings: SidechainSettings,
             viewHolderRef: ActorRef,
             forgeMessageBuilder: AccountForgeMessageBuilder,
             timeProvider: NetworkTimeProvider,
             params: NetworkParams)
  extends AbstractForger[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock](
  settings, viewHolderRef, forgeMessageBuilder, timeProvider, params
) {
  override type FPI = AccountFeePaymentsInfo
  override type HSTOR = AccountHistoryStorage
  override type HIS = AccountHistory
  override type MS = AccountState
  override type VL = AccountWallet
  override type MP = AccountMemoryPool

}

object AccountForgerRef {
  def props(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainAccountTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams): Props = {
    val forgeMessageBuilder: AccountForgeMessageBuilder = new AccountForgeMessageBuilder(mainchainSynchronizer, companion, params, settings.websocketClient.allowNoConnectionInRegtest)

    Props(new AccountForger(settings, viewHolderRef, forgeMessageBuilder, timeProvider, params))
  }

  def apply(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainAccountTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = {
    val ref = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params))
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref

  }

  def apply(name: String,
            settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainAccountTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = {
    val ref = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params), name)
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref
  }
}
