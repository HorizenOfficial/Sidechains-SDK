package com.horizen.websocket

import scala.concurrent.Future
import scala.util.Try

object DisconnectionCode extends Enumeration {

  val ON_SUCCESS = Value(0)
  val UNEXPECTED = Value(1)

}

trait WebSocketMessageHandler {
  def onReceivedMessage(message: String): Unit
  def onSendMessageErrorOccurred(message: String, cause: Throwable): Unit
}

trait WebSocketReconnectionHandler {

  // when client fails to connect to remote endpoint
  def onConnectionFailed(cause: Throwable) : Boolean

  // when established connection is lost for any reason
  def onDisconnection(code : DisconnectionCode.Value, reason : String) : Boolean
}

trait WebSocketConnector[C <: WebSocketChannel] {

  def isStarted() : Boolean
  def start() : Try[C]
  def asyncStart() : Future[Try[C]]
  def stop() : Try[Unit]

  def setConfiguration(configuration : WebSocketConnectorConfiguration) : Boolean
  def setReconnectionHandler(handler : WebSocketReconnectionHandler) : Boolean
  def setMessageHandler(handler: WebSocketMessageHandler) : Boolean

}

trait WebSocketChannel {
  def sendMessage(message: String): Unit
}
