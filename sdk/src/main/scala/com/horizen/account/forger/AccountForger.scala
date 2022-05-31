package com.horizen.account.forger

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.wallet.AccountWallet
import com.horizen.consensus.{ConsensusEpochNumber, ConsensusSlotNumber}
import com.horizen.forge.{AbstractForger, ForgeResult, MainchainSynchronizer}
import com.horizen.params.NetworkParams

import scorex.core.utils.NetworkTimeProvider
import scorex.util.ScorexLogging

import scala.concurrent.Future

class AccountForger(settings: SidechainSettings,
             viewHolderRef: ActorRef,
             forgeMessageBuilder: AccountForgeMessageBuilder,
             timeProvider: NetworkTimeProvider,
             params: NetworkParams) extends AbstractForger(
  settings, viewHolderRef, forgeMessageBuilder, timeProvider, params
) {
  override type HSTOR = AccountHistoryStorage
  override type HIS = AccountHistory
  override type MS = AccountState
  override type VL = AccountWallet
  override type MP = AccountMemoryPool

  override def getForgedBlockAsFuture(epochNumber: ConsensusEpochNumber, slot: ConsensusSlotNumber, blockCreationTimeout: Timeout) : Future[ForgeResult] = {
    val forgeMessage: AccountForgeMessageBuilder#ForgeMessageType = forgeMessageBuilder.buildForgeMessageForEpochAndSlot(epochNumber, slot, blockCreationTimeout)
    val forgedBlockAsFuture = (viewHolderRef ? forgeMessage).asInstanceOf[Future[ForgeResult]]
    forgedBlockAsFuture
  }
}

object AccountForger extends ScorexLogging {
  object ReceivableMessages {
    case object StartForging
    case object StopForging
    case class  TryForgeNextBlockForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber)
    case object GetForgingInfo
  }
}

object AccountForgerRef {
  def props(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainAccountTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams): Props = {
    val forgeMessageBuilder: AccountForgeMessageBuilder = new AccountForgeMessageBuilder(mainchainSynchronizer, companion, params, settings.websocket.allowNoConnectionInRegtest)

    Props(new AccountForger(settings, viewHolderRef, forgeMessageBuilder, timeProvider, params))
  }

  def apply(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainAccountTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params))

  def apply(name: String,
            settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainAccountTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params), name)
}