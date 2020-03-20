package com.horizen.forge

import java.time.Instant
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus.{ConsensusEpochNumber, ConsensusSlotNumber, TimeToEpochSlotConverter}
import com.horizen.forge.Forger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.params.NetworkParams
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Forger(settings: SidechainSettings,
             viewHolderRef: ActorRef,
             mainchainSynchronizer: MainchainSynchronizer,
             companion: SidechainTransactionsCompanion,
             val params: NetworkParams) extends Actor with ScorexLogging with TimeToEpochSlotConverter {
  val forger: ForgeMessageBuilder = new ForgeMessageBuilder(mainchainSynchronizer, companion, params)
  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  @volatile private var forgingIsActive: Boolean = false

  val consensusMillisecondsInSlot: Int = params.consensusSecondsInSlot * 1000
  val forgingInitiator: Runnable = () => {
    while (true) {
      if (forgingIsActive) {
        tryToCreateBlockNow()
      }
      Thread.sleep(consensusMillisecondsInSlot)
    }
  }

  ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor).execute(forgingInitiator)

  override def receive: Receive = {
    processStartForgingMessage orElse processStopForgingMessage orElse processTryForgeNextBlockForEpochAndSlotMessage orElse {
      case message: Any => log.error(s"Forger received strange message: ${message} from ${sender().path.name}")
    }
  }

  protected def processStartForgingMessage: Receive = {
    case Forger.ReceivableMessages.StartForging => {
      log.info("Receive StartForging message")
      forgingIsActive = true
      tryToCreateBlockNow()
      sender() ! Success()
    }
  }

  protected def processStopForgingMessage: Receive = {
    case Forger.ReceivableMessages.StopForging => {
      log.info("Receive StopForging message")
      forgingIsActive = false
      sender() ! Success()
    }
  }

  protected def processTryForgeNextBlockForEpochAndSlotMessage: Receive = {
    case TryForgeNextBlockForEpochAndSlot(epochNumber, slotNumber) => tryToCreateBlockForEpochAndSlot(epochNumber, slotNumber, Some(sender()))
  }

  protected def tryToCreateBlockNow(): Unit = {
    val currentTime: Long = Instant.now.getEpochSecond
    val epochAndSlot = timestampToEpochAndSlot(currentTime)
    log.info(s"Send TryForgeNextBlockForEpochAndSlot message with epoch and slot ${epochAndSlot}")
    tryToCreateBlockForEpochAndSlot(epochAndSlot.epochNumber, epochAndSlot.slotNumber, None)
  }

  protected def tryToCreateBlockForEpochAndSlot(epochNumber: ConsensusEpochNumber, slot: ConsensusSlotNumber, respondsToOpt: Option[ActorRef]): Unit = {
    val forgeMessage: ForgeMessageBuilder#ForgeMessageType = forger.buildForgeMessageForEpochAndSlot(epochNumber, slot)
    val forgedBlockAsFuture = (viewHolderRef ? forgeMessage).asInstanceOf[Future[ForgeResult]]
    forgedBlockAsFuture.onComplete{
      case Success(ForgeSuccess(block)) => {
        log.info(s"Got successfully forged block with id ${block.id}")
        viewHolderRef ! LocallyGeneratedModifier[SidechainBlock](block)
        respondsToOpt.map(respondsTo => respondsTo ! Success(block.id))
      }

      case Success(SkipSlot) => {
        log.info(s"Slot is skipped")
        respondsToOpt.map(respondsTo => respondsTo ! Failure(new RuntimeException("Slot had been skipped")))
      }

      case Success(ForgeFailed(ex)) => {
        log.info("Forging had been failed")
        respondsToOpt.map(respondsTo => respondsTo ! Failure(ex))
      }

      case failure @ Failure(ex) => {
        log.info("Forging had been failed")
        respondsToOpt.map(respondsTo => respondsTo ! failure)
      }
    }
  }
}

object Forger extends ScorexLogging {
  object ReceivableMessages {
    case object StartForging
    case object StopForging
    case class  TryForgeNextBlockForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber)
  }
}


object ForgerRef {
  def props(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            params: NetworkParams): Props = Props(new Forger(settings, viewHolderRef, mainchainSynchronizer, companion, params))

  def apply(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, params))

  def apply(name: String,
            settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, mainchainSynchronizer, companion, params), name)
}