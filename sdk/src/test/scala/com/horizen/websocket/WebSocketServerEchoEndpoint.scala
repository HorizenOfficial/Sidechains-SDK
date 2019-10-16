package com.horizen.websocket


import javax.websocket.OnMessage
import javax.websocket.Session
import javax.websocket.server.ServerEndpoint

@ServerEndpoint("/")
class WebSocketServerEchoEndpoint {

  @OnMessage
  def echo(session: Session, message: String): Unit = {
    session.getAsyncRemote.sendText(message)
  }

}
