package com.horizen.forge

import java.util.{Timer, TimerTask}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus.{ConsensusEpochAndSlot, ConsensusEpochNumber, ConsensusSlotNumber}
import com.horizen.forge.Forger.ReceivableMessages.{GetForgingInfo, StartForging, StopForging, TryForgeNextBlockForEpochAndSlot}
import com.horizen.params.NetworkParams
import com.horizen.utils.TimeToEpochUtils
import sparkz.core.NodeViewHolder.ReceivableMessages
import sparkz.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import sparkz.core.utils.NetworkTimeProvider
import sparkz.util.SparkzLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class Forger(settings: SidechainSettings,
             viewHolderRef: ActorRef,
             mainchainSynchronizer: MainchainSynchronizer,
             companion: SidechainTransactionsCompanion,
             timeProvider: NetworkTimeProvider,
             val params: NetworkParams) extends Actor with SparkzLogging {
  val forgeMessageBuilder: ForgeMessageBuilder = new ForgeMessageBuilder(mainchainSynchronizer, companion, params, settings.websocket.allowNoConnectionInRegtest)
  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)


  private val consensusMillisecondsInSlot: Int = params.consensusSecondsInSlot * 1000
  private def forgingInitiatorTimerTask: TimerTask = new TimerTask {override def run(): Unit = tryToCreateBlockNow()}
  private var timerOpt: Option[Timer] = None

  private def startTimer(): Unit = {
    this.timerOpt match {
      case Some(_) => log.info("Automatically forging already had been started")
      case None => {
        val newTimer = new Timer()
        val currentTime: Long = timeProvider.time() / 1000
        val delay = TimeToEpochUtils.secondsRemainingInSlot(params, currentTime) * 1000
        newTimer.schedule(forgingInitiatorTimerTask, 0L)
        newTimer.scheduleAtFixedRate(forgingInitiatorTimerTask, delay, consensusMillisecondsInSlot)
        timerOpt = Some(newTimer)
        log.info("Automatically forging had been started")
      }
    }
  }

  private def stopTimer(): Unit = {
    this.timerOpt match {
      case Some(timer) => {
        timer.cancel()
        log.info("Automatically forging had been stopped")
        this.timerOpt = None
      }
      case None => log.info("Automatically forging had been already stopped")
    }
  }

  private def isForgingEnabled: Boolean = {
    timerOpt.isDefined
  }

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, SidechainAppEvents.SidechainApplicationStart.getClass)
  }

  override def postStop(): Unit = {
    log.debug("Forger actor is stopping...")
    super.postStop()
  }

  override def receive: Receive = {
    checkForger orElse
    processStartForgingMessage orElse
    processStopForgingMessage orElse
    processTryForgeNextBlockForEpochAndSlotMessage orElse
    processGetForgeInfo orElse {
      case message: Any => log.error(s"Forger received strange message: ${message} from ${sender().path.name}")
    }
  }

  protected def checkForger: Receive = {
    case SidechainAppEvents.SidechainApplicationStart =>
      if(settings.forger.automaticForging)
        self ! StartForging
  }

  protected def processStartForgingMessage: Receive = {
    case StartForging => {
      log.info("Receive StartForging message")
      startTimer()
      // Don't send answer to itself.
      if(sender() != self)
        sender() ! Success(Unit)
    }
  }

  protected def processStopForgingMessage: Receive = {
    case StopForging => {
      log.info("Receive StopForging message")
      stopTimer()
      sender() ! Success(Unit)
    }
  }

  protected def processTryForgeNextBlockForEpochAndSlotMessage: Receive = {
    case TryForgeNextBlockForEpochAndSlot(epochNumber, slotNumber, forcedTx) => tryToCreateBlockForEpochAndSlot(epochNumber, slotNumber, Some(sender()), timeout, forcedTx)
  }

  protected def tryToCreateBlockNow(): Unit = {
    val currentTime: Long = timeProvider.time() / 1000
    val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params, currentTime)
    log.info(s"Send TryForgeNextBlockForEpochAndSlot message with epoch and slot ${epochAndSlot}")
    tryToCreateBlockForEpochAndSlot(epochAndSlot.epochNumber, epochAndSlot.slotNumber, None, timeout, Seq())
  }

  protected def tryToCreateBlockForEpochAndSlot(epochNumber: ConsensusEpochNumber, slot: ConsensusSlotNumber, respondsToOpt: Option[ActorRef], blockCreationTimeout: Timeout, forcedTx: Iterable[SidechainTypes#SCBT]): Unit = {
    val forgeMessage: ForgeMessageBuilder#ForgeMessageType = forgeMessageBuilder.buildForgeMessageForEpochAndSlot(epochNumber, slot, blockCreationTimeout, forcedTx)
    val forgedBlockAsFuture = (viewHolderRef ? forgeMessage).asInstanceOf[Future[ForgeResult]]
    forgedBlockAsFuture.onComplete{
      case Success(ForgeSuccess(block)) => {
        log.info(s"Got successfully forged block with id ${block.id}")
        viewHolderRef ! LocallyGeneratedModifier[SidechainBlock](block)
        respondsToOpt.map(respondsTo => respondsTo ! Success(block.id))
      }

      case Success(SkipSlot(reason)) => {
        log.info(s"Slot is skipped with reason: $reason")
        respondsToOpt.map(respondsTo => respondsTo ! Failure(new RuntimeException(s"Slot had been skipped with reason: $reason")))
      }

      case Success(NoOwnedForgingStake) => {
        log.info(s"No forging stake.")
        respondsToOpt.map(respondsTo => respondsTo ! Failure(new RuntimeException("Can't forge block, no forging stake is present for epoch.")))
      }

      case Success(ForgeFailed(ex)) => {
        log.error(s"Forging had been failed. Reason: ${ex.getMessage}")
        respondsToOpt.map(respondsTo => respondsTo ! Failure(ex))
      }

      case failure @ Failure(ex) => {
        log.error(s"Forging had been failed. Reason: ${ex.getMessage}")
        respondsToOpt.map(respondsTo => respondsTo ! failure)
      }
    }
  }

  protected def processGetForgeInfo: Receive = {
    case GetForgingInfo => {
      val forgerInfoRequester = sender()

      val getInfoMessage
        = ReceivableMessages.GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ConsensusEpochAndSlot](getEpochAndSlotForBestBlock)
      val epochAndSlotFut = (viewHolderRef ? getInfoMessage).asInstanceOf[Future[ConsensusEpochAndSlot]]
      epochAndSlotFut.onComplete{
        case Success(epochAndSlot: ConsensusEpochAndSlot) => {
          forgerInfoRequester ! Success(ForgingInfo(params.consensusSecondsInSlot, params.consensusSlotsInEpoch, epochAndSlot, isForgingEnabled))
        }
        case failure @ Failure(ex) => {
          forgerInfoRequester ! failure
        }
      }
    }
  }

  def getEpochAndSlotForBestBlock(view: View): ConsensusEpochAndSlot = {
    val history = view.history
    TimeToEpochUtils.timestampToEpochAndSlot(params, history.bestBlockInfo.timestamp)
  }
}

object Forger extends SparkzLogging {
  object ReceivableMessages {
    case object StartForging
    case object StopForging
    case class  TryForgeNextBlockForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber, forcedTx: Iterable[SidechainTypes#SCBT])
    case object GetForgingInfo
  }
}


object ForgerRef {
  def props(settings: SidechainSettings,
            viewHolderRef: ActorRef,
            mainchainSynchronizer: MainchainSynchronizer,
            companion: SidechainTransactionsCompanion,
            timeProvider: NetworkTimeProvider,
            params: NetworkParams): Props = Props(new Forger(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params))

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
