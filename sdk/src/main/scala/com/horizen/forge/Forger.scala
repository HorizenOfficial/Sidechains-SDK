package com.horizen.forge

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import com.horizen._
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus.{ConsensusEpochAndSlot, ConsensusEpochNumber, ConsensusSlotNumber}
import com.horizen.forge.Forger.ReceivableMessages.GetForgingInfo
import com.horizen.params.NetworkParams
import com.horizen.utils.TimeToEpochUtils
import scorex.core.NodeViewHolder.{CurrentView, ReceivableMessages}
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Forger(settings: SidechainSettings,
             viewHolderRef: ActorRef,
             forgeMessageBuilder: ForgeMessageBuilder,
             timeProvider: NetworkTimeProvider,
             params: NetworkParams) extends AbstractForger(
  settings, viewHolderRef, forgeMessageBuilder, timeProvider, params
) {
  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  protected def processGetForgeInfo: Receive = {
    case GetForgingInfo =>
      val forgerInfoRequester = sender()

      val getInfoMessage
      = ReceivableMessages.GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ConsensusEpochAndSlot](getEpochAndSlotForBestBlock)
      val epochAndSlotFut = (viewHolderRef ? getInfoMessage).asInstanceOf[Future[ConsensusEpochAndSlot]]
      epochAndSlotFut.onComplete {
        case Success(epochAndSlot: ConsensusEpochAndSlot) =>
          forgerInfoRequester ! Success(ForgingInfo(params.consensusSecondsInSlot, params.consensusSlotsInEpoch, epochAndSlot, isForgingEnabled))

        case failure@Failure(ex) =>
          forgerInfoRequester ! failure

      }
  }

  def getEpochAndSlotForBestBlock(view: View): ConsensusEpochAndSlot = {
    val history = view.history
    TimeToEpochUtils.timestampToEpochAndSlot(params, history.bestBlockInfo.timestamp)
  }
}

object Forger extends ScorexLogging {
  object ReceivableMessages {
    case object StartForging
    case object StopForging
    case class  TryForgeNextBlockForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber)
    case object GetForgingInfo
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