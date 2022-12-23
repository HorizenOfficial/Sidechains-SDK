package com.horizen.forge

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.{SidechainTypes, _}
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.sc2sc.Sc2ScConfigurator
import com.horizen.storage.SidechainHistoryStorage
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
            sc2ScConfigurator: Sc2ScConfigurator,
            params: NetworkParams): Props = {
    val forgeMessageBuilder: ForgeMessageBuilder = new ForgeMessageBuilder(mainchainSynchronizer, companion, sc2ScConfigurator, params,  settings.websocket.allowNoConnectionInRegtest)
    Props(new Forger(settings, viewHolderRef, forgeMessageBuilder, timeProvider, params))
  }

  def apply(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            sc2ScConfigurator: Sc2ScConfigurator,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = {
    val ref = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, sc2ScConfigurator, params))
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref

  }

  def apply(name: String,
            settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            sc2ScConfigurator: Sc2ScConfigurator,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = {
    val ref = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, sc2ScConfigurator, params), name)
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref
  }
}
