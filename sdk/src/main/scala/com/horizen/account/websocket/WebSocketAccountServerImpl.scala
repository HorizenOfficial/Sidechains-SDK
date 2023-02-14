package com.horizen.account.websocket

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.websocket.server.WebSocketServerBaseImpl

import javax.websocket._

@ClientEndpoint
class WebSocketAccountServerImpl(bindPort: Int, configuration: Class[_]) extends WebSocketServerBaseImpl(bindPort, configuration) {

  def onSemanticallySuccessfulModifier(block: AccountBlock): Unit = {
    WebSocketAccountServerEndpoint.notifySemanticallySuccessfulModifier(block)
  }

  def onSuccessfulTransaction(tx: EthereumTransaction): Unit = {
    WebSocketAccountServerEndpoint.notifyNewPendingTransaction(tx)
  }

  def onChangedVault(): Unit = {
    WebSocketAccountServerEndpoint.onVaultChanged()
  }

  def onMempoolReaddedTransaction(readdedTxs: Seq[EthereumTransaction]): Unit = {
    WebSocketAccountServerEndpoint.notifyMempoolReaddedTransactions(readdedTxs)
  }

  def onRemovedMempoolTransactions(removedTxs: Seq[SidechainTypes#SCAT]): Unit = {
    WebSocketAccountServerEndpoint.notifyRemovedTransactions(removedTxs)
  }

}
