package com.horizen.websocket.server

import java.util

import akka.actor.ActorRef
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.inject.Inject
import javax.websocket.{OnMessage, Session}
import javax.websocket.server.ServerEndpoint

case object GET_SINGLE_BLOCK_REQUEST_TYPE extends RequestType(0)
case object GET_NEW_BLOCK_HASHES_REQUEST_TYPE extends RequestType(2)
case object GET_MEMPOOL_TXS extends RequestType(4)
case object GET_RAW_MEMPOOL extends RequestType(5)

@ServerEndpoint("/")
class WebSocketServerEndpoint() {
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  @Inject val bean: ActorRef = null;

  @OnMessage
  def onMessageReceived(session: Session, message: String): Unit = {
    if (!WebSocketServerEndpoint.sessions.contains(session)) {
      WebSocketServerEndpoint.addSession(session);
    }
    try {
      val json = mapper.readTree(message)
      if (json.has("msgType")) {
        json.get("msgType").asInt() match {
          case 1 => // Event
            processRequest(json, session)
          case 2 => // Error
            processError(json, session)
          case msgType =>
            processError(json, session)
            System.out.println("Unknown message received with type = " + msgType)
        }
      } else {
        processError(json, session)
        System.out.println("Unknown message received!")
      }
    } catch {
      case ex: Throwable =>
        processError( mapper.createObjectNode(), session)
        System.out.println("On receive message processing exception occurred = " + ex.getMessage)
    }
  }

  def processRequest(json: JsonNode,session: Session): Unit = {
    val sidechainNodeChannelImpl: SidechainNodeChannelImpl = new SidechainNodeChannelImpl(session)
    val requestPayload = json.get("requestPayload")
    val requestId  = json.get("requestId").asInt()
    val requestType = json.get("requestType").asInt()

    requestType match {
      case GET_SINGLE_BLOCK_REQUEST_TYPE.code => // Get single block
        if (requestPayload.has("hash")) {
          val hash = requestPayload.get("hash").asText()
          sidechainNodeChannelImpl.sendBlockByHash(hash, requestId, requestType )
        }
        else if (requestPayload.has("height")) {
          val height = requestPayload.get("height").asInt()
          sidechainNodeChannelImpl.sendBlockByHeight(height, requestId, requestType)
        }
        else processError(json, session)

      case GET_NEW_BLOCK_HASHES_REQUEST_TYPE.code => // Get new block hashes
        val afterHash = requestPayload.get("locatorHashes")
        val limit = requestPayload.get("limit").asInt()
        sidechainNodeChannelImpl.sendNewBlockHashes(afterHash, limit, requestId, requestType)

      case GET_MEMPOOL_TXS.code => // Get mempool txes
        val txids = requestPayload.get("hash")
        sidechainNodeChannelImpl.sendMempoolTxs(txids, requestId, requestType)

      case GET_RAW_MEMPOOL.code => // Get raw mempool
        sidechainNodeChannelImpl.sendRawMempool(requestId, requestType)

      case msgType =>
        System.out.println("Unknown message received with type = " + msgType)
    }

  }

  def processError(json: JsonNode, session: Session): Unit = {
    val sidechainNodeChannelImpl: SidechainNodeChannelImpl = new SidechainNodeChannelImpl(session)

    var requestId = -1
    if (json.has("requestId")) {
      requestId = json.get("requestId").asInt()
    }
    var requestType = -1
    if (json.has("requestType")) {
      requestType = json.get("requestType").asInt()
    }

    sidechainNodeChannelImpl.sendError(requestId, requestType, 5, "WebSocket message error!")
  }
}

object WebSocketServerEndpoint {
  var sessions: util.ArrayList[Session] = new util.ArrayList[Session]()

  def addSession (session: Session): Unit = {
    this.sessions.add(session)
  }
  def removeSession (session: Session) : Unit = {
    this.sessions.remove(session)
  }
}
