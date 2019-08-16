package com.horizen.websocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.horizen.WebSocketClientSettings
import com.horizen.websocket.WebSocketClient.ReceivableMessages.UpdateTipEvent
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext

class UpdateTipEventActor(f : UpdateTipEvent => Unit)(implicit ec : ExecutionContext) extends Actor with ScorexLogging {

  override final def preStart(): Unit =
  {
    context.system.eventStream.subscribe(self, classOf[UpdateTipEvent])
  }

  protected final def onEvent : Receive =
  {
    case event :UpdateTipEvent =>
      log.info(s"Received event...")
      f(event)
  }

  override final def receive: Receive =
  {
    onEvent orElse
    {
      case a : Any => log.error("Strange input: " + a)
    }
  }
}

object UpdateTipEventActorRef{
  def props(f : UpdateTipEvent => Unit)(implicit system: ActorSystem, ec: ExecutionContext): Props =
    Props(new UpdateTipEventActor(f))

  def apply(f : UpdateTipEvent => Unit)(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(f))
}