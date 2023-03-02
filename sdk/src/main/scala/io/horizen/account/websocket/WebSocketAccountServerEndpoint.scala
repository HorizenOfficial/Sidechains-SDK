package io.horizen.account.websocket

import com.fasterxml.jackson.databind.JsonNode
import io.horizen.account.api.rpc.request.{RpcId, RpcRequest}
import io.horizen.account.api.rpc.response.{RpcResponseError, RpcResponseSuccess}
import io.horizen.account.api.rpc.service.RpcFilter
import io.horizen.account.api.rpc.types.{EthereumLogView, FilterQuery}
import io.horizen.account.api.rpc.utils.{RpcCode, RpcError}
import io.horizen.account.block.AccountBlock
import io.horizen.account.serialization.EthJsonMapper
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.websocket.data.{Subscription, SubscriptionWithFilter, WebSocketAccountEvent, WebSocketAccountEventParams}
import io.horizen.evm.Address
import jakarta.websocket._
import jakarta.websocket.server.ServerEndpoint
import org.web3j.utils.Numeric
import sparkz.util.SparkzLogging

import java.io.{PrintWriter, StringWriter}
import java.math.BigInteger
import java.util
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable

abstract class WebSocketAccountRequest(val request: String)
case object SUBSCRIBE_REQUEST extends WebSocketAccountRequest("eth_subscribe")
case object UNSUBSCRIBE_REQUEST extends WebSocketAccountRequest("eth_unsubscribe")

abstract class WebSocketAccountSubscription(val method: String)
case object NEW_HEADS_SUBSCRIPTION extends WebSocketAccountSubscription("newHeads")
case object NEW_PENDING_TRANSACTIONS_SUBSCRIPTION extends WebSocketAccountSubscription("newPendingTransactions")
case object LOGS_SUBSCRIPTION extends WebSocketAccountSubscription("logs")

@ServerEndpoint("/")
class WebSocketAccountServerEndpoint() extends SparkzLogging {

  @OnClose
  def onClose(session: Session): Unit = {
    log.debug("Websocket session closed "+session.getId)
    WebSocketAccountServerEndpoint.removeSession(session)
  }

  @OnError
  def onError(session: Session, t:Throwable): Unit = {
    log.error("Error on websocket session "+session.getId+": "+t.toString)
    WebSocketAccountServerEndpoint.removeSession(session)
  }

  @OnMessage
  def onMessageReceived(session: Session, message: String): Unit = {
    try {
      val rpcRequest = new RpcRequest(EthJsonMapper.getMapper.readTree(message))
      validateRpcRequestParams(rpcRequest.params, rpcRequest.id, session)

      rpcRequest.method match {
        case SUBSCRIBE_REQUEST.request =>
          subscribe(session, rpcRequest)
        case UNSUBSCRIBE_REQUEST.request =>
          unsubscribe(session, rpcRequest)
        case unknownMethod =>
          WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
            new RpcError(RpcCode.MethodNotFound, s"Method $unknownMethod not supported.", "")),
            session)
          log.debug(s"Method $unknownMethod not supported.")
      }
    } catch {
      case ex: Throwable =>
        val sw = new StringWriter
        ex.printStackTrace(new PrintWriter(sw))
        WebSocketAccountServerEndpoint.send(new RpcResponseError(null,
          new RpcError(RpcCode.ExecutionError, "Websocket On receive message processing exception occurred", sw.toString)),
          session)
        log.error("Websocket On receive message processing exception occurred = " + ex.getMessage)
    }
  }

  private def subscribe(session: Session, rpcRequest: RpcRequest): Unit = {
    val rpcParams = rpcRequest.params
    val subscribeMethod = rpcParams.get(0)

    subscribeMethod.asText() match {

      case NEW_HEADS_SUBSCRIPTION.method =>
        val subscriptionId = createSubscriptionId()
        WebSocketAccountServerEndpoint.addNewHeadsSubscription(Subscription(session, subscriptionId))
        log.debug("New Subscription on newHeads "+session.getId)
        WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, subscriptionId), session)

      case NEW_PENDING_TRANSACTIONS_SUBSCRIPTION.method =>
        val subscriptionId = createSubscriptionId()
        WebSocketAccountServerEndpoint.addNewPendingTransactionsSubscription(Subscription(session, subscriptionId))
        log.debug("New Subscription on newPendingTransactions "+session.getId)
        WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, subscriptionId), session)

      case LOGS_SUBSCRIPTION.method =>
        if (rpcParams.size() < 2)
          WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
            new RpcError(RpcCode.InvalidParams, "Missing filters (address, topics).", "")),
            session)
        val subscriptionId = createSubscriptionId()
        val filterQuery = EthJsonMapper.deserialize(rpcParams.get(1).toString, classOf[FilterQuery])
        filterQuery.sanitize()
        WebSocketAccountServerEndpoint.addLogsSubscription(SubscriptionWithFilter(session, subscriptionId,
          filterQuery
        ))
        log.debug("New Subscription on logs "+session.getId)
        WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, subscriptionId), session)

      case unkownMethod =>
        WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
          new RpcError(RpcCode.InvalidParams, "unsupported subscription type " + unkownMethod, "")),
          session)
        log.debug("unsupported subscription type " + unkownMethod)
    }

  }

  private def unsubscribe(session: Session, rpcRequest: RpcRequest): Unit = {
    val rpcParams = rpcRequest.params

    val subscriptionIdsToRemove: util.ArrayList[BigInteger] = new util.ArrayList[BigInteger]()
    rpcParams.forEach(subscriptionId => {
      subscriptionIdsToRemove.add(Numeric.decodeQuantity(subscriptionId.asText()))
    })
    val removedSubscription = WebSocketAccountServerEndpoint.removeSubscriptions(subscriptionIdsToRemove.toArray(new Array[BigInteger](0)))
    if (!removedSubscription) {
      WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
        new RpcError(RpcCode.InvalidParams, s"Subscription ID not found.", "")),
        session)
    } else
      WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, true), session)
  }

  private def validateRpcRequestParams(rpcParams: JsonNode, rpcRequest: RpcId, session: Session): Unit = {
    if (rpcParams == null || !rpcParams.isArray || rpcParams.size() < 1) {
      log.debug("Missing or empty field params.")
      WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest,
        new RpcError(RpcCode.InvalidParams, "Missing or empty field params.", "")),
        session)
    }
  }

  private def createSubscriptionId(): BigInteger = {
    BigInteger.valueOf(WebSocketAccountServerEndpoint.subscriptionCounter.incrementAndGet())
  }
}

private object WebSocketAccountServerEndpoint extends SparkzLogging {
  var subscriptionCounter: AtomicInteger = new AtomicInteger(0)
  var newHeadsSubscriptions: util.ArrayList[Subscription] = new util.ArrayList[Subscription]()
  var newPendingTransactionsSubscriptions: util.ArrayList[Subscription] = new util.ArrayList[Subscription]()
  var logsSubscriptions: util.ArrayList[SubscriptionWithFilter] = new util.ArrayList[SubscriptionWithFilter]()

  val webSocketAccountChannelImpl = new WebSocketAccountChannelImpl()
  private var walletAddresses: Set[Address] = webSocketAccountChannelImpl.getWalletAddresses
  private var cachedBlocksReceipts: mutable.Queue[(String, Set[EthereumLogView])] = new mutable.Queue[(String, Set[EthereumLogView])]()
  private val maxCachedBlockReceipts = 100

  def notifySemanticallySuccessfulModifier(block: AccountBlock): Unit = {
    log.info("Websocket received new block: "+block.toString)

    val blockJson = webSocketAccountChannelImpl.accountBlockToWebsocketJson(block)

    newHeadsSubscriptions.forEach(subscription => {
      send(new WebSocketAccountEvent("eth_subscription", new WebSocketAccountEventParams(subscription.subscriptionId, blockJson)), subscription.session)
    })

    //We have a chain reorganization
    while (cachedBlocksReceipts.nonEmpty && !cachedBlocksReceipts.last._1.equals(block.parentId)) {
      val oldTip = cachedBlocksReceipts.last
      logsSubscriptions.forEach(subscription => {
        val logsToSend = oldTip._2.filter(RpcFilter.testLog(subscription.filter.address, subscription.filter.topics))
        sendTransactionLog(logsToSend.toSeq, subscription)
      })
      cachedBlocksReceipts = cachedBlocksReceipts.dropRight(cachedBlocksReceipts.size)
    }
    processBlockReceipt(block)
  }

  def notifyNewPendingTransaction(tx: EthereumTransaction): Unit = {
    log.info("Websocket received new tx: "+tx.id())

    if (walletAddresses.contains(tx.getFromAddress)) {
      newPendingTransactionsSubscriptions.forEach(subscription => {
        send(new WebSocketAccountEvent("eth_subscription", new WebSocketAccountEventParams(subscription.subscriptionId, Numeric.prependHexPrefix(tx.id()))), subscription.session)
      })
    }
  }

  def notifyMempoolReaddedTransactions(readdedTxs: Seq[EthereumTransaction]): Unit = {
    readdedTxs.foreach( tx =>
      notifyNewPendingTransaction(tx)
    )
  }

  private def processBlockReceipt(block: AccountBlock): Unit = {
    var relevantBlockReceipt: Seq[EthereumLogView] = Seq()
    logsSubscriptions.forEach(subscription => {
      val logs = webSocketAccountChannelImpl.getEthereumLogsFromBlock(block, subscription)
      sendTransactionLog(logs, subscription)
      relevantBlockReceipt = relevantBlockReceipt ++ logs
    })
    cachedBlocksReceipts.enqueue((block.id, relevantBlockReceipt.toSet.map((log: EthereumLogView) => {
      log.updateRemoved(true)
      log})
    ))
    if (cachedBlocksReceipts.size > maxCachedBlockReceipts) {
      cachedBlocksReceipts.dequeue()
    }
  }

  private def sendTransactionLog(txLogs: Seq[EthereumLogView], subscription: SubscriptionWithFilter): Unit = {
    txLogs.foreach(txLog => {
      send(new WebSocketAccountEvent( "eth_subscription", new WebSocketAccountEventParams(subscription.subscriptionId, txLog)), subscription.session)
    })
  }

  def onVaultChanged(): Unit = {
    walletAddresses = webSocketAccountChannelImpl.getWalletAddresses
  }

  def addNewHeadsSubscription(subscription: Subscription): Unit = {
    synchronized{
      WebSocketAccountServerEndpoint.newHeadsSubscriptions.add(subscription)
    }
  }

  def addNewPendingTransactionsSubscription(subscription: Subscription): Unit = {
    synchronized{
      WebSocketAccountServerEndpoint.newPendingTransactionsSubscriptions.add(subscription)
    }
  }

  def addLogsSubscription(subscription: SubscriptionWithFilter): Unit = {
    synchronized{
      WebSocketAccountServerEndpoint.logsSubscriptions.add(subscription)
    }
  }

  def removeSubscriptions(subscriptionIdsToRemove: Array[BigInteger]): Boolean = {
    var removedNewHeadSubscription = false
    var removedNewPendingTransactionsSubcription = false
    var removedLogsSubcription = false
    synchronized {
      removedNewHeadSubscription = WebSocketAccountServerEndpoint.newHeadsSubscriptions.removeIf(subscription => subscriptionIdsToRemove.contains(subscription.subscriptionId))
      removedNewPendingTransactionsSubcription = WebSocketAccountServerEndpoint.newPendingTransactionsSubscriptions.removeIf(subscription => subscriptionIdsToRemove.contains(subscription.subscriptionId))
      removedLogsSubcription = WebSocketAccountServerEndpoint.logsSubscriptions.removeIf(subscription => subscriptionIdsToRemove.contains(subscription.subscriptionId))
    }
    removedNewHeadSubscription || removedNewPendingTransactionsSubcription || removedLogsSubcription
  }

  def removeSession(session: Session): Unit = {
    synchronized {
      newHeadsSubscriptions.removeIf(subscription => subscription.session.getId.equals(session.getId))
      newPendingTransactionsSubscriptions.removeIf(subscription => subscription.session.getId.equals(session.getId))
      logsSubscriptions.removeIf(subscription => subscription.session.getId.equals(session.getId))
    }
  }

  def send(websocketResponse: Object, session: Session): Unit = {
    session.getAsyncRemote.sendText(EthJsonMapper.serialize(websocketResponse), new SendHandler {
      override def onResult(sendResult: SendResult): Unit = {
        if (!sendResult.isOK) {
          log.debug("Websocket send message failed. "+session.getId)
        }
      }
    })
  }

}
