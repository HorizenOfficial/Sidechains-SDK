package com.horizen.forge

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.SidechainSettings
import com.horizen.forge.SidechainBlockForger.ReceivableMessages.{StartSidechainBlockForging, StopSidechainBlockForging}
import scorex.util.ScorexLogging

// Implementation not completed
class SidechainBlockForger(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef) extends Actor with ScorexLogging {

  protected def startSidechainBlockForging : Receive = {
    case StartSidechainBlockForging => None
  }

  protected def stopSidechainBlockForging : Receive = {
    case StopSidechainBlockForging => None
  }

  override def receive: Receive = {
    startSidechainBlockForging orElse stopSidechainBlockForging orElse
      {
        case a : Any => log.error("Strange input: " + a)
      }
  }
}

object SidechainBlockForger extends ScorexLogging{

  object ReceivableMessages{

    case object StartSidechainBlockForging
    case object StopSidechainBlockForging
  }
}

object SidechainBlockForgerRef {
  def props(settings: SidechainSettings, viewHolderRef: ActorRef): Props = Props(new SidechainBlockForger(settings, viewHolderRef))

  def apply(settings: SidechainSettings, viewHolderRef: ActorRef)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef))

  def apply(name: String, settings: SidechainSettings, viewHolderRef: ActorRef)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef), name)
}
