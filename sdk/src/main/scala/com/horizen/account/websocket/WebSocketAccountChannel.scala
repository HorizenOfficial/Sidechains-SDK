package com.horizen.account.websocket

import com.fasterxml.jackson.databind.node.ObjectNode
import com.horizen.account.block.AccountBlock
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.websocket.data.SubscriptionWithFilter

trait WebSocketAccountChannel {

  def getWalletKeys: Set[String]

  def getTransactionReceipt(txHash: String): Option[EthereumReceipt]

  def createWsLogEventFromEthereumReceipt(txReceipt: EthereumReceipt, subscriptionWithFilter: SubscriptionWithFilter): Array[ObjectNode]

  def accountBlockToWebsocketJson(block: AccountBlock): ObjectNode

}
