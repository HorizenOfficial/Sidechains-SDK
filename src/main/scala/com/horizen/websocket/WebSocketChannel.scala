package com.horizen.websocket

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.horizen.WebSocketClientSettings
import com.horizen.websocket.WebSocketChannel.ReceivableMessages.{ConnectionError, OpenChannel, ReceiveMessage, SendMessage}
import scorex.util.ScorexLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WebSocketChannel(webSocketClient :ActorRef, webSocketConfiguration : WebSocketClientSettings)
                          (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext) extends Actor with ScorexLogging {

  private var webSocketRequest : WebSocketRequest = null

  private def manageMessageRequest: Receive = {
    case OpenChannel =>
      openWebSocketChannel

    case SendMessage(message) =>
      sendMessage(message)
  }

  override def receive: Receive = {
    manageMessageRequest orElse
      {
        case a : Any => log.error(getClass.getName + " has received a strange input: " + a)
      }
  }

  val incoming: Sink[Message, Future[Done]] =
    Sink.foreach[Message] {
      case tm: TextMessage =>
        webSocketClient ! ReceiveMessage(tm.getStrictText)
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
      case _ => log.error("Web socket channel: Unexpected message type")
    }

  def openWebSocketChannel = {
    val future : Future[Unit] = createAndOpenWebSocketChannel(webSocketConfiguration)
    val timeout = FiniteDuration(
      webSocketConfiguration.connectionTimeout,
      TimeUnit.valueOf(webSocketConfiguration.connectionTimeUnit))
    future.onComplete{
      case Success(value) =>
      case Failure(exception) =>
        webSocketClient ! ConnectionError(exception)
    }
    /**
      * Note : if we don't use a barrier, we can have a scenario in which the stream is not ready to process incoming messages.
      * Therefore the message is enqueued but processed later, when the stream will be ready.
     */
    Await.result(future, timeout)
  }

  private def createChannel(conf : WebSocketClientSettings) : Try[WebSocketRequest] = {
    var socketAddress = conf.bindingAddress
    var uri  = Uri.from(
      scheme = "ws",
      host = socketAddress.getHostName,
      port = socketAddress.getPort)
    Try(WebSocketRequest.fromTargetUri(uri))
  }

  private def startQueue (webSocketRequest : WebSocketRequest) =
    Source.queue[Message](Int.MaxValue, OverflowStrategy.backpressure)
      .viaMat(Http().webSocketClientFlow(webSocketRequest))(Keep.both)
      .toMat(incoming)(Keep.both)
      .run()

  private def createAndOpenWebSocketChannel(conf : WebSocketClientSettings): Future[Unit]  = {
    createChannel(conf) match {
      case Success(channel) =>
        try{

          webSocketRequest = channel
          val (upgradeResponse, closed) = startQueue(webSocketRequest)

          upgradeResponse._2.flatMap { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
              log.info(s"Connection established: ${webSocketRequest.uri.toString()}")

              closed.onComplete{
                case Success(value) =>
                  log.info(s"Connection closed: ${webSocketRequest.uri.toString()}")
                case Failure(exception) =>
                  Future.failed(exception)
              }

              Future.successful()

            } else {
              var exception = new RuntimeException(s"Connection failed: ${webSocketRequest.uri.toString()}")
              Future.failed(exception)
            }
          }


        }catch {
          case e : Throwable =>
            Future.failed(e)
        }

      case Failure(exception) =>
        Future.failed(exception)
    }
  }

  private def sendMessage (msg : String) =
    try {
      startQueue(webSocketRequest)._1._1.offer(TextMessage(msg))
    }catch{
      case e : Throwable => webSocketClient ! ReceiveMessage(msg, e)
    }

}

object WebSocketChannel{
  object ReceivableMessages{
    case class SendMessage(message : String)
    case class ReceiveMessage(message : String, throwable : Throwable = null)
    case class OpenChannel()
    case class ConnectionError(throwable : Throwable)
  }
}

object WebSocketChannelRef{
  def props(webSocketClient :ActorRef, webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): Props =
    Props(new WebSocketChannel(webSocketClient, webSocketConfiguration))

  def apply(webSocketClient :ActorRef, webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): ActorRef =
    system.actorOf(props(webSocketClient, webSocketConfiguration))
}

