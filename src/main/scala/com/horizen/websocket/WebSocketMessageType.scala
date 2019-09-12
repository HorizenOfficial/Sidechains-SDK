package com.horizen.websocket

object WebSocketMessageType {

  case class Request_1 (correlationId : String, message : String)

  case class Request_2 (correlationId : String, message : String)

  case class WebSocketResponseMessage(correlationId : String, message : String)

}
