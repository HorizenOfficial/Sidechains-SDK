package com.horizen.websocket

import scorex.util.ScorexLogging

class DefaultWebSocketReconnectionHandler(conf: WebSocketConnectorConfiguration) extends WebSocketReconnectionHandler with ScorexLogging {

  var onDisconnectCounter = 0
  var onConnectFailureCounter = 0

  override def onConnectionFailed(cause: Throwable): Boolean = {
    onConnectFailureCounter = onConnectFailureCounter + 1
    if (onConnectFailureCounter <= conf.reconnectionMaxAttempts)
      {
        log.info("onConnectFailure. Reconnecting... (attempt " + onConnectFailureCounter + ") " + cause.getMessage)
        true
      }
    else false
  }

  override def onDisconnection(code: DisconnectionCode.Value, reason: String): Boolean = {
    onDisconnectCounter = onDisconnectCounter + 1
    if (onDisconnectCounter <= conf.reconnectionMaxAttempts && code != DisconnectionCode.ON_SUCCESS) {
      log.info("onDisconnect. Reconnecting... (attempt " + onDisconnectCounter + ")")
      true
    } else false
  }

  override def getDelay: Long = {
    val c = conf.reconnectionDelay.toSeconds
    c
  }
}
