package com.horizen.websocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.horizen.WebSocketClientSettings
import com.horizen.websocket.WebSocketEventActor.ReceivableMessages.{Subscribe, UnSubscribe}
import com.horizen.websocket.WebSocketChannel.ReceivableMessages.{OpenChannel, ReceiveMessage, SendMessage}
import com.horizen.websocket.WebSocketClient.ReceivableMessages.{SubscribeForEvent, UnSubscribeForEvent}
import io.circe.{Json, parser}
import scorex.util.ScorexLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class WebSocketClient(webSocketConfiguration : WebSocketClientSettings)
                     (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext) extends Actor with ScorexLogging{

  private var webSocketChannel : ActorRef = null
  private var requestPool : TrieMap[String, Promise[ChannelMessage]] = TrieMap()
  private var eventActorPool : TrieMap[String, ActorRef] = TrieMap()

  override def preStart(): Unit = {
    webSocketChannel = WebSocketChannelRef(self, webSocketConfiguration)

    webSocketChannel ! OpenChannel
  }

  protected def manageRequest : Receive = {

    case req : WebSocketRequestMessage =>
      val promise = Promise[ChannelMessage]
      requestPool += (req.correlationId -> promise)
      sender() ! promise.future
      log.info(s"Sending request to web socket channel: "+req.toString)
      webSocketChannel ! SendMessage(req.toJson.toString())

    case ReceiveMessage(message, throwable) =>
      if(throwable == null)
        log.info(s"Response from web socket channel: "+message)
      else
        log.error("Error from web socket channel", throwable)

      var response : Try[Either[(String, ChannelMessage), ChannelMessageEvent]] = tryToDecodeChannelMessage(message, throwable)
      response match {
        case Success(value) =>
          value match {
            case Right(event) =>
              context.system.eventStream.publish(event)
            case Left(resp) =>
              var correlationId = resp._1
              requestPool.get(correlationId) match {
                case Some(promise) =>
                  if(throwable != null)
                    promise.failure(throwable)
                  else
                    promise.success(resp._2)
                  requestPool -= correlationId
                case None =>
                  log.error(resp+" --> It cannot be parsed")
              }
          }
        case Failure(exception) =>
          log.error(exception.getMessage)
      }

    case SubscribeForEvent(f) =>
      try{
        var eventActor = createWebSocketEventActor(f)
        eventActor ! Subscribe
        sender() ! Future.successful(eventActor.path.name)
      }catch {
        case e : Throwable => Future.failed(e)
      }

    case UnSubscribeForEvent(actorName : String) =>
      eventActorPool.get(actorName) match {
        case Some(actoreRef) =>
          actoreRef ! UnSubscribe
          eventActorPool -= actorName
        case None =>
      }

  }

  private def tryToParseCorrIdAndRequestType(rawResponse : String) : Try[(Try[String], Try[Int])] = {
    parser.decode[Map[String, Json]](rawResponse) match {
      case Right(map) =>
        var tryMsgType = map.get("type") match {
          case Some(msgType) =>
            try {
              Success(msgType.asNumber.get.toInt.get)
            } catch {
              case e: Throwable => Failure(e)
            }
          case None => Failure(new IllegalStateException("Request type not found"))
        }

        var tryCorrId = map.get("msgId") match {
          case Some(msgId) =>
            try {
              Success(msgId.asString.getOrElse(""))
            } catch {
              case e: Throwable => Failure(e)
            }
          case None => Failure(new IllegalStateException("Correlation id not found"))
        }

        Success((tryCorrId, tryMsgType))
      case Left(value) => Failure(value)
    }
  }

  private def tryToDecodeChannelMessage(rawResponse : String, throwable: Throwable = null) : Try[Either[(String, ChannelMessage), ChannelMessageEvent]] =
  {
      tryToParseCorrIdAndRequestType(rawResponse) match {
        case Success(value) =>
          value._1 match {
            case Success(id) if !id.isEmpty => Success(Left((id, ChannelMessage(rawResponse))))
            case Failure(exception) =>
              value._2 match {
                case Success(reqType) => Success(Right(ChannelMessageEvent(rawResponse)))
                case Failure(exception) => Failure(exception)
              }
          }
        case Failure(exception) => Failure(exception)
      }
  }

  override def receive: Receive = {
    manageRequest orElse
    {
      case a : Any => log.error(getClass.getName + " has received a strange input: " + a)
    }
  }

  def createWebSocketEventActor(f : ChannelMessageEvent => Unit) =
  {
    var newEventActor = system.actorOf(Props(new WebSocketEventActor(f)), String.valueOf(System.currentTimeMillis()))
    var name = newEventActor.path.name
    eventActorPool += (name ->  newEventActor)
    newEventActor
  }
}

object WebSocketClient{
  object ReceivableMessages{
    case class SubscribeForEvent(f : ChannelMessageEvent => Unit)
    case class UnSubscribeForEvent(actorName : String)
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