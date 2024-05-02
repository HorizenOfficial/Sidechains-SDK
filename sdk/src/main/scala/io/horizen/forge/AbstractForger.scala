package io.horizen.forge

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import io.horizen._
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.consensus.{ConsensusEpochAndSlot, ConsensusEpochNumber, ConsensusParamsUtil, ConsensusSlotNumber}
import io.horizen.forge.AbstractForger.ReceivableMessages.{GetForgingInfo, StartForging, StopForging, TryForgeNextBlockForEpochAndSlot}
import io.horizen.history.AbstractHistory
import io.horizen.metrics.MetricsManager
import io.horizen.params.NetworkParams
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.Transaction
import io.horizen.utils.TimeToEpochUtils
import io.horizen.wallet.Wallet
import sparkz.util.SparkzLogging
import sparkz.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import sparkz.core.NodeViewHolder.{CurrentView, ReceivableMessages}
import sparkz.core.transaction.MemoryPool
import sparkz.core.utils.NetworkTimeProvider

import java.util.{Timer, TimerTask}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.{Failure, Success}

abstract class AbstractForger[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H]
](settings: SidechainSettings,
             viewHolderRef: ActorRef,
             forgeMessageBuilder: AbstractForgeMessageBuilder[TX, H, PM],
             timeProvider: NetworkTimeProvider,
             val params: NetworkParams) extends Actor with SparkzLogging
{
  type FPI <: AbstractFeePaymentsInfo
  type HSTOR <: AbstractHistoryStorage[PM, FPI, HSTOR]
  type HIS <: AbstractHistory[TX, H, PM, FPI, HSTOR, HIS]
  type MS <: AbstractState[TX, H, PM, MS]
  type VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PM, VL]
  type MP <: MemoryPool[TX, MP]

  type View = CurrentView[HIS, MS, VL, MP]

  private val restApiTimeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  private var slotsDurationInSeconds: Int = -1

  // implicit timeout for akka msg
  implicit private val timeout: Timeout = Timeout(restApiTimeoutDuration)

  private def forgingInitiatorTimerTask: TimerTask = new TimerTask {override def run(): Unit = tryToCreateBlockNow()}
  private var timerOpt: Option[Timer] = None

  private val metricsManager = MetricsManager.getInstance()

  private def startTimer(): Unit = {
    this.timerOpt match {
      case Some(_) => log.info("Automatically forging already had been started")
      case None =>
        val newTimer = new Timer()
        val currentTime: Long = timeProvider.time() / 1000
        val delay = TimeToEpochUtils.secondsRemainingInSlot(params.sidechainGenesisBlockTimestamp, currentTime) * 1000
        if (slotsDurationInSeconds == -1) {
          slotsDurationInSeconds = ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(params.sidechainGenesisBlockTimestamp, timeProvider.time() / 1000) * 1000
        }
        newTimer.schedule(forgingInitiatorTimerTask, 0L)
        newTimer.scheduleAtFixedRate(forgingInitiatorTimerTask, delay, slotsDurationInSeconds)
        timerOpt = Some(newTimer)
        log.info("Automatically forging had been started")
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

  protected def isForgingEnabled: Boolean = {
    timerOpt.isDefined
  }

  override def preStart(): Unit = {
    log.debug(" Forger actor is starting")
    super.preStart()
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
      case message: Any => log.error(s"Forger received strange message: $message from ${sender().path.name}")
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
    case obj: TryForgeNextBlockForEpochAndSlot[TX] =>
      tryToCreateBlockForEpochAndSlot(obj.consensusEpochNumber, obj.consensusSlotNumber, Some(sender()), obj.forcedTx)  }


  protected def tryToCreateBlockNow(): Unit = {
    // we are in the timer func, triggered periodically for automatic forging attempt
    val currentTime: Long = timeProvider.time() / 1000
    val epochAndSlot = TimeToEpochUtils.timestampToEpochAndSlot(params.sidechainGenesisBlockTimestamp, currentTime)
    log.info(s"Send TryForgeNextBlockForEpochAndSlot message with epoch and slot $epochAndSlot")
    tryToCreateBlockForEpochAndSlot(epochAndSlot.epochNumber, epochAndSlot.slotNumber, None, Seq())
    recalculateSlotDuration()
  }

  protected def recalculateSlotDuration(): Unit = {
    val consensusSecondsInSlot = ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(params.sidechainGenesisBlockTimestamp, timeProvider.time() / 1000) * 1000
    if (consensusSecondsInSlot != slotsDurationInSeconds) {
      log.info(s"Detected a change in slot duration, previous: $slotsDurationInSeconds actual: $consensusSecondsInSlot - restarting Forger Actor...")
      slotsDurationInSeconds = consensusSecondsInSlot
      stopTimer()
      startTimer()
    }
  }

  def getForgedBlockAsFuture(epochNumber: ConsensusEpochNumber, slot: ConsensusSlotNumber, forcedTx: Iterable[TX]) : Future[ForgeResult] = {
    // we should not take more time than a slot duration for forging a block.
    val mcRefDataRetrievalTimeout = Timeout((restApiTimeoutDuration.min(FiniteDuration(ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(epochNumber), SECONDS)))/2)

    val forgeMessage: AbstractForgeMessageBuilder[TX, H, PM]#ForgeMessageType = forgeMessageBuilder.buildForgeMessageForEpochAndSlot(epochNumber, slot, mcRefDataRetrievalTimeout, forcedTx)
    val forgedBlockAsFuture = (viewHolderRef ? forgeMessage).asInstanceOf[Future[ForgeResult]]
    forgedBlockAsFuture
  }

  protected def tryToCreateBlockForEpochAndSlot(epochNumber: ConsensusEpochNumber, slot: ConsensusSlotNumber, respondsToOpt: Option[ActorRef], forcedTx: Iterable[TX]): Unit = {
    val forgedBlockAsFuture = getForgedBlockAsFuture(epochNumber, slot, forcedTx)

    forgedBlockAsFuture.onComplete{
      case Success(ForgeSuccess(block)) => {
        log.info(s"Got successfully forged block with id ${block.id}")
        metricsManager.forgedBlock(metricsManager.currentMillis() - (block.timestamp * 1000))
        viewHolderRef ! LocallyGeneratedModifier(block)
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

      case Success(ForgingStakeListEmpty) => {
        log.info(s"No forging stakes available for this sidechain.")
        respondsToOpt.map(respondsTo => respondsTo ! Failure(new RuntimeException("Can't forge block, no forging stakes can be found for this sidechain")))
      }

      case Success(ForgeFailed(ex)) => {
        log.error(s"Forging had been failed. Reason: ${ex.getMessage}", ex)
        respondsToOpt.map(respondsTo => respondsTo ! Failure(ex))
      }

      case failure @ Failure(ex) => {
        log.error(s"Forging had been failed. Reason: ${ex.getMessage}", ex)
        respondsToOpt.map(respondsTo => respondsTo ! failure)
      }
    }
  }

  //protected def processGetForgeInfo: Receive
  protected def processGetForgeInfo: Receive = {
    case GetForgingInfo =>
      val forgerInfoRequester = sender()

      val getInfoMessage
      = ReceivableMessages.GetDataFromCurrentView[HIS, MS, VL, MP, ConsensusEpochAndSlot](getEpochAndSlotForBestBlock)
      val epochAndSlotFut = (viewHolderRef ? getInfoMessage).asInstanceOf[Future[ConsensusEpochAndSlot]]
      epochAndSlotFut.onComplete {
        case Success(epochAndSlot: ConsensusEpochAndSlot) =>
          forgerInfoRequester ! Success(ForgingInfo(ConsensusParamsUtil.getConsensusSecondsInSlotsPerEpoch(epochAndSlot.epochNumber), ConsensusParamsUtil.getConsensusSlotsPerEpoch(epochAndSlot.epochNumber), epochAndSlot, isForgingEnabled))

        case failure@Failure(_) =>
          forgerInfoRequester ! failure

      }
  }

  def getEpochAndSlotForBestBlock(view: View): ConsensusEpochAndSlot = {
    val history = view.history
    TimeToEpochUtils.timestampToEpochAndSlot(params.sidechainGenesisBlockTimestamp, history.bestBlockInfo.timestamp)
  }
}

object AbstractForger extends SparkzLogging {
  object ReceivableMessages {
    case object StartForging
    case object StopForging
    case class TryForgeNextBlockForEpochAndSlot[TX](consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber, forcedTx: Iterable[TX])
    case object GetForgingInfo
  }
}
