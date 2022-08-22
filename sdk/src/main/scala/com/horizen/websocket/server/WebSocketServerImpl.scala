package com.horizen.websocket.server

import com.horizen.block.SidechainBlock

import jakarta.websocket.ClientEndpoint

@ClientEndpoint
class WebSocketServerImpl(bindPort: Int, configuration: Class[_]) extends WebSocketServerBaseImpl(bindPort, configuration) {

  def onMempoolChanged(): Unit = {
    WebSocketServerEndpoint.notifyMempoolChanged()
  }

  def onSemanticallySuccessfulModifier(block: SidechainBlock): Unit = {
    WebSocketServerEndpoint.notifySemanticallySuccessfulModifier(block)
  }

}
