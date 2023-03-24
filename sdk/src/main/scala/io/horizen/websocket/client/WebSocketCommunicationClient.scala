package io.horizen.websocket.client

import java.util.concurrent.atomic.AtomicInteger
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import sparkz.util.SparkzLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

case class WebSocketServerError(msgType: Int, requestId: String, errorCode: Int, message: String)

class WebSocketCommunicationClient(requestTimeOut: FiniteDuration = 5 seconds)
  extends WebSocketChannelCommunicationClient with WebSocketMessageHandler with SparkzLogging {

  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private var requestsPool: TrieMap[String, (Promise[ResponsePayload], Class[ResponsePayload])] = TrieMap()
  private var eventHandlersPool: TrieMap[Int, Seq[(EventHandler[EventPayload], Class[EventPayload])]] = TrieMap()
  private var webSocketChannel: WebSocketChannel = _
  private val counter = new AtomicInteger()

  override def setWebSocketChannel(channel: WebSocketChannel): Unit =
    webSocketChannel = channel

  override def sendRequest[Req <: RequestPayload, Resp <: ResponsePayload](requestType: RequestType, request: Req, responseClazz: Class[Resp]): Future[Resp] = {
    // to-do. Check if the channel is not null
    if (webSocketChannel != null) {
      val requestId = generateRequestId
      var json = mapper.createObjectNode()
      json.put("msgType", 1)
      json.put("requestType", requestType.code)
      json.put("requestId", requestId)
      json.set("requestPayload", mapper.valueToTree[JsonNode](request))

      val message = json.toString
      val promise = Promise[Resp]
      requestsPool += (requestId -> (promise.asInstanceOf[Promise[ResponsePayload]], responseClazz.asInstanceOf[Class[ResponsePayload]]))

      webSocketChannel.sendMessage(message)
      promise.future
    } else
      Promise[Resp].failure(new IllegalStateException("The web socket channel must be not null.")).future
  }

  override def registerEventHandler[E <: EventPayload](eventType: Int, handler: EventHandler[E], eventClazz: Class[E]): Try[Unit] = Try {
    synchronized {
      val eventHandlers = eventHandlersPool.getOrElse(eventType, Seq())

      if (eventHandlers.exists(h => h._1.equals(handler)))
        throw new IllegalArgumentException(s"Handler is already registered.")

      val updatedHandlers = eventHandlers :+ (handler.asInstanceOf[EventHandler[EventPayload]], eventClazz.asInstanceOf[Class[EventPayload]])
      eventHandlersPool += (eventType -> updatedHandlers)
    }
  }

  override def unregisterEventHandler[E <: EventPayload](eventType: Int, handler: EventHandler[E]): Unit = {
    synchronized {
      eventHandlersPool.get(eventType) match {
        case Some(handlers) =>
          val updatedHandlers = handlers.filter(h => !h._1.equals(handler))
          eventHandlersPool += (eventType -> updatedHandlers)
        case None =>
      }
    }
  }

  override def onReceivedMessage(message: String): Unit = {
    try {
      val json = mapper.readTree(message)
      json.get("msgType").asInt() match {
        case 0 => // Event
          processEvent(json)
        case 2 => // Response
          processResponse(json)
        case 3 =>
          processError(json)
        case msgType =>
          log.error("Unknown message received with type = " + msgType)
      }
    } catch {
      case ex: Throwable =>
        log.error("On receive message processing exception occurred = " + ex.getMessage)
    }
  }

  private def processError(json: JsonNode): Unit = {
    val requestId = json.get("requestId").asText("")
    requestsPool.remove(requestId) match {
      case Some((promise, _)) =>
        Try {
          mapper.convertValue(json, classOf[WebSocketServerError])
        } match {
          case Success(errorResponse) =>
            promise.failure(new WebsocketErrorResponseException(errorResponse.message))
          case Failure(_) =>
            // Unexpected error structure received from the server
            promise.failure(new WebsocketInvalidErrorMessageException(json.toString))
        }
      case None =>
        log.error("Unknown response received: " + json.toString)
    }
  }

  private def processEvent(json: JsonNode): Unit = {
    val eventType = json.get("eventType").asInt(-1)
    eventHandlersPool.get(eventType) match {
      case Some(handlers) =>
        val eventPayload = json.get("eventPayload")
        for (h <- handlers) {
          try {
            val resp = mapper.convertValue(eventPayload, h._2)
            h._1.onEvent(resp)
          }
          catch {
            case ex: Throwable => log.error("Event parsing was failed by handler: " + ex.getMessage)
          }
        }

      case _ =>
        log.debug("Event without defined handler received. Event type = " + eventType)
    }

  }

  private def processResponse(json: JsonNode): Unit = {
    val requestId = json.get("requestId").asText("")
    requestsPool.remove(requestId) match {
      case Some((promise, responseClazz)) =>
        try {
          val resp = mapper.convertValue(json.get("responsePayload"), responseClazz)
          promise.success(resp)
        } catch {
          case ex: Throwable => promise.failure(ex)
        }

      case None =>
        log.error("Unknown response received with requested id = " + requestId)
    }

  }

  override def onSendMessageErrorOccurred(message: String, cause: Throwable): Unit = Try {
    log.error("Error from web socket channel", cause)

    val json = mapper.readTree(message)
    val requestId = json.get("requestId").asText("")
    requestsPool.remove(requestId) match {
      case Some((promise, _)) =>
        promise.failure(cause)
      case _ => {}
    }
  }

  private def generateRequestId: String = {
    String.valueOf(counter.addAndGet(1))
  }

  override def requestTimeoutDuration(): FiniteDuration = requestTimeOut
}
