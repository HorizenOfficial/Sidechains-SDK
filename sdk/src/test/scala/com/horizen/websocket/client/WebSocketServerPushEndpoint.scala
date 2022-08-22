package com.horizen.websocket.client

import jakarta.websocket.server.ServerEndpoint
import jakarta.websocket.{OnOpen, Session}

@ServerEndpoint("/")
class WebSocketServerPushEndpoint {

  @OnOpen
  def onOpen(session: Session): Unit = {
    // Wait a bit to be sure that client has defined a message handler for the just added session.
    Thread.sleep(200)
    for (i <- 1 to 5){
      session.getAsyncRemote.sendText("Message #"+i)
    }
  }

}
