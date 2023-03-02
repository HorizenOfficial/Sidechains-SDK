package com.horizen.account.websocket

import akka.util.Timeout
import sparkz.util.SparkzLogging
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.wallet.AccountWallet
import sparkz.core.NodeViewHolder
import sparkz.core.NodeViewHolder.CurrentView
import com.horizen.account.websocket.WebSocketAccountServerRef.sidechainNodeViewHolderRef
import akka.pattern.ask
import com.horizen.account.api.rpc.service.RpcFilter
import com.horizen.account.api.rpc.types.{EthereumBlockView, EthereumLogView}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.websocket.data.{SubscriptionWithFilter, WebSocketEthereumBlockView}
import io.horizen.evm.{Address, Hash}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WebSocketAccountChannelImpl extends SparkzLogging{
  implicit val duration: Timeout = 20 seconds
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

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

  def getWalletAddresses: Set[Address] = {
    applyOnAccountView { nodeView =>
      nodeView.vault.publicKeys().filter(key => key.isInstanceOf[AddressProposition]).map(addressProposition => addressProposition.asInstanceOf[AddressProposition].address())
    }
  }

  def getEthereumLogsFromBlock(block: AccountBlock, subscriptionWithFilter: SubscriptionWithFilter): Seq[EthereumLogView] = {
    applyOnAccountView { nodeView =>
      RpcFilter.getBlockLogs(nodeView.state.getView, block, subscriptionWithFilter.filter)
    }
  }


  def accountBlockToWebsocketJson(block: AccountBlock): WebSocketEthereumBlockView = {
    applyOnAccountView { nodeView =>
      val blockNumber = nodeView.history.getBlockHeightById(block.id).get().toLong
      val blockHash = new Hash(block.id.toBytes)
      new WebSocketEthereumBlockView(EthereumBlockView.withoutTransactions(blockNumber, blockHash, block))
    }
  }
}
