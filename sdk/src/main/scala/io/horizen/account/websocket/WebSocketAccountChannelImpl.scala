package io.horizen.account.websocket

import akka.pattern.ask
import akka.util.Timeout
import io.horizen.account.api.rpc.service.RpcFilter
import io.horizen.account.api.rpc.types.{EthereumBlockView, EthereumLogView}
import io.horizen.account.block.AccountBlock
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.AccountState
import io.horizen.account.wallet.AccountWallet
import io.horizen.account.websocket.WebSocketAccountServerRef.sidechainNodeViewHolderRef
import io.horizen.account.websocket.data.{SubscriptionWithFilter, WebSocketEthereumBlockView}
import io.horizen.evm.{Address, Hash}
import sparkz.core.NodeViewHolder
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.util.SparkzLogging

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
