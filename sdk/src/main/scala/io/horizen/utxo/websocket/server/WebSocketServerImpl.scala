package io.horizen.utxo.websocket.server

import io.horizen.utxo.block.SidechainBlock
import io.horizen.websocket.server.WebSocketServerBaseImpl
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
