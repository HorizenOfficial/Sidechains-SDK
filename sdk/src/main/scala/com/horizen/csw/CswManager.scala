package com.horizen.csw

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.SidechainSettings
import com.horizen.params.NetworkParams
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext

class CswManager(settings: SidechainSettings,
                 params: NetworkParams,
                 sidechainNodeViewHolderRef: ActorRef) (implicit ec: ExecutionContext)
  extends Actor with ScorexLogging {

  override def receive: Receive = {
    reportStrangeInput
  }

  private def reportStrangeInput: Receive = {
    case nonsense =>
      log.warn(s"Strange input for CswManager: $nonsense")
  }
}


object CswManagerRef {
  def props(settings: SidechainSettings, params: NetworkParams, sidechainNodeViewHolderRef: ActorRef)
           (implicit ec: ExecutionContext) : Props =
    Props(new CswManager(settings, params, sidechainNodeViewHolderRef))

  def apply(settings: SidechainSettings, params: NetworkParams, sidechainNodeViewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, params, sidechainNodeViewHolderRef))

  def apply(name: String, settings: SidechainSettings,params: NetworkParams, sidechainNodeViewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, params, sidechainNodeViewHolderRef), name)
}