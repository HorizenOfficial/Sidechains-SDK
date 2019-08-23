package com.horizen.websocket

import akka.actor.Actor
import com.horizen.websocket.WebSocketEventActor.ReceivableMessages.{Subscribe, UnSubscribe}
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext

class WebSocketEventActor(f : ChannelMessageEvent => Unit)(implicit ec : ExecutionContext) extends Actor with ScorexLogging {

  protected final def onEvent : Receive =
  {
    case event : ChannelMessageEvent =>
      f(event)

    case Subscribe =>
      context.system.eventStream.subscribe(self, classOf[ChannelMessageEvent])

    case UnSubscribe =>
      context.system.eventStream.unsubscribe(self, classOf[ChannelMessageEvent])
  }

  override final def receive: Receive =
  {
    onEvent orElse
      {
        case a : Any => log.error(getClass.getName + " has received a strange input: " + a)
      }
  }
}

object WebSocketEventActor{
  object ReceivableMessages{
    case class Subscribe()
    case class UnSubscribe()
  }
}
