package com.horizen.websocket

import javax.websocket.{ClientEndpoint, SendHandler, SendResult, Session}
import scorex.util.ScorexLogging

@ClientEndpoint
class WebSocketChannelImpl extends WebSocketChannel with ScorexLogging {

  private var webSocketHandler : WebSocketMessageHandler = _
  private var userSession : Session = _

  override def sendMessage(message: String): Unit = {
    try {
      userSession.getAsyncRemote().sendText(message, new SendHandler {
        override def onResult(sendResult: SendResult): Unit = {
          if(!sendResult.isOK)
          {
            log.info("Send message failed.")
            if(webSocketHandler != null)
              webSocketHandler.onSendMessageErrorOccurred(message, sendResult.getException)
          }
          else log.info(String.format("Message sent: %s", message))
        }}
      )
    } catch {
      case e : Throwable =>
        if(webSocketHandler != null)
          webSocketHandler.onSendMessageErrorOccurred(message, e)
    }

  }

  def setWebSocketMessageHandler(handler: WebSocketMessageHandler): Unit =
    webSocketHandler = handler

  def setSession(session : Session) : Unit =
    userSession = session

}

