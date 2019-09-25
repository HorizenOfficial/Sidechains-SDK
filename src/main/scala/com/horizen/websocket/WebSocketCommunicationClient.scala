package com.horizen.websocket
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.horizen.utils.BytesUtils
import scorex.util.ScorexLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.concurrent.duration._

class WebSocketCommunicationClient(webSocketChannel: WebSocketChannel)
                                   extends CommunicationClient with WebSocketHandler with ScorexLogging {

  webSocketChannel.setWebSocketHandler(this)

  private var requestsPool : TrieMap[String, (Promise[ResponsePayload], Class[ResponsePayload])] = TrieMap()
  private var eventHandlersPool : TrieMap[Int, Seq[(EventHandler[EventPayload], Class[EventPayload])]] = TrieMap()


  override def sendRequest[Req <: RequestPayload, Resp <: ResponsePayload](requestType: Int, request: Req, responseClazz: Class[Resp]): Future[Resp] = {
    if(!webSocketChannel.isOpen) {
      webSocketChannel.open() // to do: manage failure situation
    }
    val requestId = generateRequestId
    val mapper = new ObjectMapper()
    var json = mapper.createObjectNode()
    json.put("msgType", 1)
    json.put("requestType", requestType)
    json.put("requestId", requestId)
    json.put("requestPayload", mapper.valueToTree[JsonNode](request))

    val message = json.toString
    val promise = Promise[Resp]
    requestsPool += (requestId -> (promise.asInstanceOf[Promise[ResponsePayload]], responseClazz.asInstanceOf[Class[ResponsePayload]]))

    webSocketChannel.sendMessage(message)
    promise.future
  }

  override def registerEventHandler[E <: EventPayload](eventType: Int, handler: EventHandler[E], eventClazz: Class[E]): Try[Unit] = Try {
    val eventHandlers = eventHandlersPool.getOrElse(eventType, Seq())

    if(eventHandlers.exists(h => h._1.equals(handler)))
      throw new IllegalArgumentException(s"Handler is already registered.")

    val updatedHandlers = eventHandlers :+ (handler.asInstanceOf[EventHandler[EventPayload]], eventClazz.asInstanceOf[Class[EventPayload]])
    eventHandlersPool += (eventType -> updatedHandlers)
  }

  override def unregisterEventHandler[E <: EventPayload](eventType: Int, handler: EventHandler[E]): Unit = {
    eventHandlersPool.get(eventType) match {
      case Some(handlers) =>
        val updatedHandlers = handlers.filter(h => !h._1.equals(handler))
        eventHandlersPool += (eventType -> updatedHandlers)
    }
  }

  override def onReceivedMessage(message: String): Unit = {
    // to do: lock
    try {
      val mapper = new ObjectMapper()
      val json = mapper.readTree(message)
      json.get("msgType").asInt() match {
        case 0 => // Event
          processEvent(json)
        case 2 => // Response
          processResponse(json)
        case msgType =>
          log.error("Unknown message received with type = %d", msgType)
      }
    } catch {
      case ex: Throwable =>
        log.error("On receive message processing exception occurred = %s", ex.getMessage)
    }
    // to do: unlock
  }

  private def processEvent(json: JsonNode): Unit = {
    val eventType = json.get("eventType").asInt(-1)
    eventHandlersPool.get(eventType) match {
      case Some(handlers) =>
        val mapper = new ObjectMapper()
        val eventPayload = json.get("eventPayload")
        for(h <- handlers) {
          try {
            val resp = mapper.convertValue(eventPayload, h._2)
            h._1.onEvent(resp)
          }
          catch {
            case ex =>  log.error("Event parsing was failed by handler: %s", ex.getMessage)
          }
        }

      case _ =>
        log.error("Event without defined handler received. Event type = %d", eventType)
    }

  }

  private def processResponse(json: JsonNode): Unit = {
      val requestId = json.get("requestId").asText("")
      requestsPool.get(requestId) match {
        case Some((promise, responseClazz)) =>
          try {
            val mapper = new ObjectMapper()
            val resp = mapper.convertValue(json.get("responsePayload"), responseClazz)
            promise.success(resp)
          } catch {
            case ex => promise.failure(ex)
          }
          requestsPool -= requestId

        case None =>
          log.error("Unknown response received with requested id = %d", requestId)
      }

  }

  override def onSendMessageErrorOccurred(message: String, cause: Throwable): Unit = Try {
    log.error("Error from web socket channel", cause)

    val mapper = new ObjectMapper()
    val json = mapper.readTree(message)
    val requestId = json.get("requestId").asText("")
    requestsPool.get(requestId) match {
      case Some((promise, _)) =>
        promise.failure(cause)
        requestsPool -= requestId
    }
  }

  override def onConnectionError(cause: Throwable): Unit = {
    // to do: reconnect or else
  }

  private def generateRequestId: String = {
    val bytes: Array[Byte] = new Array[Byte](16)
    scala.util.Random.nextBytes(bytes)
    BytesUtils.toHexString(bytes)
  }

  override def requestTimeoutDuration(): FiniteDuration = 2 seconds
}
