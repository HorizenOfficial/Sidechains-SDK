package com.horizen.forge

import java.time.Instant
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.consensus.TimeToEpochSlotConverter
import com.horizen.params.NetworkParams
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class ForgingControl(settings: SidechainSettings, forgerRef: ActorRef, viewHolderRef: ActorRef, val params: NetworkParams)
  extends Actor with ScorexLogging with TimeToEpochSlotConverter {

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
    processStartForgingMessage orElse processStopForgingMessage orElse processForgeResultMessage orElse {
      case message: Any => log.error(s"ForgerControl received strange message: ${message} from ${sender().path.name}")
    }
  }

  protected def processStartForgingMessage: Receive = {
    case ForgingControl.ReceivableMessages.StartForging => {
      log.info("Receive StartForging message")
      forgingIsActive = true
      tryToCreateBlockNow()
      sender() ! Success()
    }
  }

  protected def processStopForgingMessage: Receive = {
    case ForgingControl.ReceivableMessages.StopForging => {
      log.info("Receive StopForging message")
      forgingIsActive = false
      sender() ! Success()
    }
  }

  //We will receive ForgeResultMessage due auto forging
  protected def processForgeResultMessage: Receive = {
    case ForgeSuccess(block) => {
      log.info(s"Got successfully forged block with id ${block.id}")
      val forgeProvider = sender()
      viewHolderRef ! LocallyGeneratedModifier[SidechainBlock](block)
      forgeProvider ! Success(block.id)
    }

    case SkipSlot => {
      log.info(s"Slot is skipped")
      sender() ! Failure(new RuntimeException("Slot had been skipped"))
    }

    case ForgeFailed(ex) => {
      log.info("Forging had been failed")
      sender() ! Failure(ex)
    }
  }

  protected def tryToCreateBlockNow(): Unit = {
    val currentTime: Long = Instant.now.getEpochSecond
    log.info(s"Send TryForgeNextBlockForEpochAndSlot message where epoch number is ${timeStampToEpochNumber(currentTime)} and slot number is ${timeStampToSlotNumber(currentTime)}")
    forgerRef ! Forger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot(timeStampToEpochNumber(currentTime), timeStampToSlotNumber(currentTime))
  }

}

object ForgingControl extends ScorexLogging {
  object ReceivableMessages {
    case object StartForging
    case object StopForging
  }
}


object ForgingControlRef {
  def props(settings: SidechainSettings, forgerRef: ActorRef, viewHolderRef: ActorRef, params: NetworkParams): Props = Props(new ForgingControl(settings, forgerRef, viewHolderRef, params))

  def apply(settings: SidechainSettings, forgerRef: ActorRef, viewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, forgerRef, viewHolderRef, params))

  def apply(name: String, settings: SidechainSettings, forgerRef: ActorRef, viewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, forgerRef, viewHolderRef, params), name)
}