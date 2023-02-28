package com.horizen.account.websocket

import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import sparkz.util.SparkzLogging
import com.fasterxml.jackson.databind.node.ObjectNode
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.wallet.AccountWallet
import sparkz.core.NodeViewHolder
import sparkz.core.NodeViewHolder.CurrentView
import com.horizen.account.websocket.WebSocketAccountServerRef.sidechainNodeViewHolderRef
import akka.pattern.ask
import com.horizen.account.api.rpc.types.EthereumBlockView
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.websocket.data.SubscriptionWithFilter
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.web3j.utils.Numeric

import java.math.BigInteger
import java.util
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WebSocketAccountChannelImpl extends SparkzLogging{
  implicit val duration: Timeout = 20 seconds
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]


  private def applyOnAccountView[R](functionToBeApplied: NV => R): R = {
    val res = sidechainNodeViewHolderRef
      .ask {
        NodeViewHolder.ReceivableMessages.GetDataFromCurrentView { (nodeview: NV) =>
          // wrap any exceptions
          Try(functionToBeApplied(nodeview))
        }
      }
      .asInstanceOf[Future[Try[R]]]
    // return result or rethrow potential exceptions
    Await.result(res, 5000 millis) match {
      case Success(value) => value
      case Failure(exception) =>
        log.error("Failed to apply function on account view ")
        throw exception
      }
  }

  def getWalletAddresses(): Set[Address] = {
    applyOnAccountView { nodeView =>
      nodeView.vault.publicKeys().filter(key => key.isInstanceOf[AddressProposition]).map(addressProposition => addressProposition.asInstanceOf[AddressProposition].address())
    }
  }

  def getTransactionReceipt(txHash: String): Option[EthereumReceipt] = {
    applyOnAccountView { nodeView =>
      nodeView.state.getTransactionReceipt(BytesUtils.fromHexString(txHash))
    }
  }

  def createWsLogEventFromEthereumReceipt(txReceipt: EthereumReceipt, subscriptionWithFilter: SubscriptionWithFilter): Array[ObjectNode] = {
    val txLogs: java.util.ArrayList[ObjectNode] = new util.ArrayList[ObjectNode]()
    if (subscriptionWithFilter.checkSubscriptionInBloom(txReceipt.consensusDataReceipt.logsBloom)) {
      txReceipt.consensusDataReceipt.logs.zipWithIndex.foreach {
        case (log, index) =>
          if (subscriptionWithFilter.filterTransactionLogs(log)) {
            val logJson = mapper.createObjectNode()

            logJson.put("blockHash", Numeric.prependHexPrefix(BytesUtils.toHexString(txReceipt.blockHash)))
            logJson.put("blockNumber", Numeric.toHexStringWithPrefix(BigInteger.valueOf(txReceipt.blockNumber)))
            logJson.put("transactionHash", Numeric.prependHexPrefix(BytesUtils.toHexString(txReceipt.transactionHash)))
            logJson.put("transactionIndex", Numeric.toHexStringWithPrefix(BigInteger.valueOf(txReceipt.transactionIndex)))
            logJson.put("address", log.address.toString)
            logJson.put("data", Numeric.prependHexPrefix(BytesUtils.toHexString(log.data)))
            logJson.put("logIndex", Numeric.toHexStringWithPrefix(BigInteger.valueOf(index)))
            logJson.set("topics", mapper.readTree(SerializationUtil.serialize(log.topics.map(topic => Numeric.prependHexPrefix(BytesUtils.toHexString(topic.toBytes))))))
            txLogs.add(logJson)
          }
      }
    }
    txLogs.toArray(new Array[ObjectNode](0))
  }


  def accountBlockToWebsocketJson(block: AccountBlock): ObjectNode = {
    applyOnAccountView { nodeView =>
      val blockNumber = nodeView.history.getBlockHeightById(block.id).get().toLong
      val blockHash = new Hash(block.id.toBytes)
      val ethereumBlockView = EthereumBlockView.withoutTransactions(blockNumber, blockHash, block)

      val blockJson = mapper.createObjectNode()
      blockJson.put("difficulty", "0x0")
      blockJson.put("extraData", ethereumBlockView.extraData)
      blockJson.put("gasLimit", ethereumBlockView.gasLimit)
      blockJson.put("gasUsed", ethereumBlockView.gasUsed)
      blockJson.put("logsBloom", ethereumBlockView.logsBloom)
      blockJson.put("miner", ethereumBlockView.miner.toString)
      blockJson.put("nonce", ethereumBlockView.nonce)
      blockJson.put("number", ethereumBlockView.number)
      blockJson.put("parentHash", ethereumBlockView.parentHash.toString)
      blockJson.put("receiptRoot", ethereumBlockView.receiptsRoot.toString)
      blockJson.put("sha3Uncles", ethereumBlockView.sha3Uncles)
      blockJson.put("stateRoot", ethereumBlockView.stateRoot.toString)
      blockJson.put("timestamp", ethereumBlockView.timestamp)
      blockJson.put("transactionsRoot", ethereumBlockView.transactionsRoot.toString)

      blockJson
    }
  }
}
