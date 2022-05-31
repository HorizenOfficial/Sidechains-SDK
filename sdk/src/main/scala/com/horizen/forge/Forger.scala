package com.horizen.forge

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus.{ConsensusEpochNumber, ConsensusSlotNumber}
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainHistoryStorage
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ScorexLogging

import scala.concurrent.Future

class Forger(settings: SidechainSettings,
             viewHolderRef: ActorRef,
             forgeMessageBuilder: ForgeMessageBuilder,
             timeProvider: NetworkTimeProvider,
             params: NetworkParams) extends AbstractForger(
  settings, viewHolderRef, forgeMessageBuilder, timeProvider, params
) {

  override type HSTOR = SidechainHistoryStorage
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  override def getForgedBlockAsFuture(epochNumber: ConsensusEpochNumber, slot: ConsensusSlotNumber, blockCreationTimeout: Timeout) : Future[ForgeResult] = {
    val forgeMessage: ForgeMessageBuilder#ForgeMessageType = forgeMessageBuilder.buildForgeMessageForEpochAndSlot(epochNumber, slot, blockCreationTimeout)
    val forgedBlockAsFuture = (viewHolderRef ? forgeMessage).asInstanceOf[Future[ForgeResult]]
    forgedBlockAsFuture
  }
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
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params))

  def apply(name: String,
            settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params), name)
}
