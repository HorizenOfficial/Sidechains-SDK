package io.horizen.account.websocket

import io.horizen.account.block.AccountBlock
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.network.SyncStatus
import io.horizen.websocket.server.WebSocketServerBaseImpl
import jakarta.websocket._

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

  def onNewExecTransactionsEvent(newExecTxs: Seq[EthereumTransaction]): Unit = {
    WebSocketAccountServerEndpoint.notifyNewExecTransactions(newExecTxs)
  }

  def onSyncStart(syncStatus: SyncStatus): Unit = {
    WebSocketAccountServerEndpoint.notifySyncStarted(syncStatus)
  }

  def onSyncStop(): Unit = {
    WebSocketAccountServerEndpoint.notifySyncStopped()
  }

}
