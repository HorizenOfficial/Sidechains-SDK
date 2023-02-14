package io.horizen.utxo.websocket.server

import com.horizen.websocket.server.WebSocketServerBaseImpl
import io.horizen.utxo.block.SidechainBlock

import javax.websocket._

@ClientEndpoint
class WebSocketServerImpl(bindPort: Int, configuration: Class[_]) extends WebSocketServerBaseImpl(bindPort, configuration) {

  def onMempoolChanged(): Unit = {
    WebSocketServerEndpoint.notifyMempoolChanged()
  }

  def onSemanticallySuccessfulModifier(block: SidechainBlock): Unit = {
    WebSocketServerEndpoint.notifySemanticallySuccessfulModifier(block)
  }

}
