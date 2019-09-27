package com.horizen.websocket

import scala.util.Try


trait WebSocketHandler {
  def onReceivedMessage(message: String): Unit
  def onSendMessageErrorOccurred(message: String, cause: Throwable): Unit
  def onConnectionError(cause: Throwable): Unit // to do: think about this
}


trait WebSocketChannel {
  def isOpened: Boolean
  def open(): Try[Unit]
  def close(): Try[Unit] // to do: think about usage of this
  def sendMessage(message: String): Unit
  def setWebSocketHandler(handler: WebSocketHandler)
}
