package com.horizen.websocket.client

import jakarta.websocket.server.ServerEndpoint
import jakarta.websocket.{OnMessage, Session}

@ServerEndpoint("/")
class WebSocketServerEchoEndpoint {

  @OnMessage
  def echo(session: Session, message: String): Unit = {
    session.getAsyncRemote.sendText(message)
  }

}
