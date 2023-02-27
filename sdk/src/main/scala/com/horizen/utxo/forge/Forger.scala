package com.horizen.utxo.forge

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.utxo.companion.SidechainTransactionsCompanion
import com.horizen.forge.{AbstractForger, MainchainSynchronizer}
import com.horizen.params.NetworkParams
import com.horizen.utxo.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.storage.SidechainHistoryStorage
import sparkz.core.utils.NetworkTimeProvider


class Forger(settings: SidechainSettings,
             viewHolderRef: ActorRef,
             forgeMessageBuilder: ForgeMessageBuilder,
             timeProvider: NetworkTimeProvider,
             params: NetworkParams)
  extends AbstractForger[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock](
  settings, viewHolderRef, forgeMessageBuilder, timeProvider, params
) {
  override type FPI = SidechainFeePaymentsInfo
  override type HSTOR = SidechainHistoryStorage
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

}


object ForgerRef {
  def props(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams): Props = {
    val forgeMessageBuilder: ForgeMessageBuilder = new ForgeMessageBuilder(mainchainSynchronizer, companion, params, settings.websocket.allowNoConnectionInRegtest)

    Props(new Forger(settings, viewHolderRef, forgeMessageBuilder, timeProvider, params))
  }

  def apply(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
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
            companion: SidechainTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = {
    val ref = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params), name)
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref
  }
}
