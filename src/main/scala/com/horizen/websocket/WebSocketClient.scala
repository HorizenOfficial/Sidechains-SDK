package com.horizen.websocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.horizen.WebSocketClientSettings
import com.horizen.websocket.WebSocketChannel.ReceivableMessages.{ReceiveMessage, SendMessage}
import com.horizen.websocket.WebSocketClient.ReceivableMessages.{SubscribeForUpdateTipEvent, UnSubscribeForUpdateTipEvent, UpdateTipEvent}
import com.horizen.websocket.WebSocketMessageType.{Request_1, Request_2, WebSocketResponseMessage}
import scorex.util.ScorexLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Promise}

class WebSocketClient(webSocketConfiguration : WebSocketClientSettings)
                     (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext) extends Actor with ScorexLogging{

  private var webSocketChannel : ActorRef = null
  private var requestPool : TrieMap[String, Promise[WebSocketResponseMessage]] = TrieMap()


  override def preStart(): Unit = {
    webSocketChannel = WebSocketChannelRef(self, webSocketConfiguration)
  }

  protected def manageRequest : Receive = {
    case req : Request_1 =>
      val promise = Promise[WebSocketResponseMessage]
      val future = promise.future
      requestPool(req.correlationId) = promise
      sender() ! future
      log.info(s"Sending request to web socket channel: "+req.toString)
      webSocketChannel ! SendMessage(req.toString)

    case req : Request_2 =>
      val promise = Promise[WebSocketResponseMessage]
      val future = promise.future
      requestPool(req.correlationId) = promise
      sender() ! future
      log.info(s"Sending request to web socket channel: "+req.toString)
      webSocketChannel ! SendMessage(req.toString)

    case ReceiveMessage(message) =>
      log.info(s"Response from web socket channel: "+message)
      var response : Either[WebSocketResponseMessage, UpdateTipEvent] = getResponseFromRawResponse(message)
      response match {
        case Right(event) =>
          context.system.eventStream.publish(UpdateTipEvent(event.message))
        case Left(resp) =>
          var correlationId = resp.correlationId
          requestPool.remove(correlationId) match {
            case Some(promise) => promise.success(resp)
            case None =>
          }
      }

    case SubscribeForUpdateTipEvent(f) =>

      subscribeToUpdateEvent(f)

    case UnSubscribeForUpdateTipEvent(actor : Actor) =>
  }

  private def getResponseFromRawResponse(rawResponse : String) : Either[WebSocketResponseMessage, UpdateTipEvent] =
    {
      // TO-DO: implement real parser
      if(rawResponse.equals("UpdateTip request"))
        Right(UpdateTipEvent(rawResponse))
      else
        Left(WebSocketResponseMessage("corrId", "response"))
    }

  override def receive: Receive = {
    manageRequest orElse
    {
      case a : Any => log.error("Strange input: " + a)
    }
  }

  def subscribeToUpdateEvent(f : UpdateTipEvent => Unit) =
  {
    system.actorOf(Props(new UpdateTipEventActor(f) ))
  }
}

object WebSocketClient{
  object ReceivableMessages{
    case class UpdateTipEvent(message : String)
    case class SubscribeForUpdateTipEvent(f : UpdateTipEvent => Unit)
    case class UnSubscribeForUpdateTipEvent(actor : ActorRef)
  }
}

object WebSocketClientRef{
  def props(webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): Props =
    Props(new WebSocketClient(webSocketConfiguration))

  def apply(webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): ActorRef =
    system.actorOf(props(webSocketConfiguration))
}

