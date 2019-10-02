package com.horizen.websocket

class DefaultWebSocketReconnectionHandler extends WebSocketReconnectionHandler {

  override def onConnectionFailed(cause: Throwable): Boolean = true

  override def onDisconnection(code: DisconnectionCode.Value, reason: String): Boolean = {
    if(code == DisconnectionCode.ON_SUCCESS)
      false
    else true
  }
}
