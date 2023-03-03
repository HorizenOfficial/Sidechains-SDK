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
import sparkz.util.{ModifierId, SparkzLogging}

import java.io.{PrintWriter, StringWriter}
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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
      if (badRpcRequestParams(rpcRequest.params)) {
        log.debug("Missing or empty field params.")
        WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
          new RpcError(RpcCode.InvalidParams, "Missing or empty field params.", "")),
          session)
      } else {
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
      }
    } catch {
      case ex: Throwable =>
        val sw = new StringWriter
        ex.printStackTrace(new PrintWriter(sw))
        WebSocketAccountServerEndpoint.send(new RpcResponseError(tryGetRpcRequestId(message).orNull,
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
        val filterQuery = EthJsonMapper.deserialize(rpcParams.get(1).toString, classOf[FilterQuery])
        filterQuery.sanitize()
        val subscriptionId = createSubscriptionId()
        WebSocketAccountServerEndpoint.addLogsSubscription(SubscriptionWithFilter(session, subscriptionId,
          filterQuery
        ))
        log.debug("New Subscription on logs "+session.getId)
        WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, subscriptionId), session)

      case unknownMethod =>
        WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
          new RpcError(RpcCode.InvalidParams, "unsupported subscription type " + unknownMethod, "")),
          session)
        log.debug("unsupported subscription type " + unknownMethod)
    }

  }

  private def unsubscribe(session: Session, rpcRequest: RpcRequest): Unit = {
    val subscriptionIdsToRemove = EthJsonMapper.deserialize(rpcRequest.params.toString, classOf[Array[BigInteger]])

    // The RPC format allows to put multiple subscription Ids, but accordingly to the GETH implementation, only the first one is processed
    val removedSubscription = WebSocketAccountServerEndpoint.removeSubscriptions(subscriptionIdsToRemove(0))
    if (!removedSubscription) {
      WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
        new RpcError(RpcCode.InvalidParams, s"Subscription ID not found.", "")),
        session)
    } else
      WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, true), session)
  }

  private def badRpcRequestParams(rpcParams: JsonNode): Boolean = {
    rpcParams == null || !rpcParams.isArray || rpcParams.size() < 1
  }

  private def createSubscriptionId(): BigInteger = {
    BigInteger.valueOf(WebSocketAccountServerEndpoint.subscriptionCounter.incrementAndGet())
  }

  private def tryGetRpcRequestId(message: String): Option[RpcId] = {
    var rpcId: Option[RpcId] = Option.empty
    try {
      val rpcRequest = new RpcRequest(EthJsonMapper.getMapper.readTree(message))
      rpcId = Option.apply(rpcRequest.id)
    } catch {
      case ex: Throwable =>
        log.error("Missing id field in websocket request")
    }
    rpcId
  }
}

private object WebSocketAccountServerEndpoint extends SparkzLogging {
  var subscriptionCounter: AtomicInteger = new AtomicInteger(0)
  var newHeadsSubscriptions: ArrayBuffer[Subscription] = ArrayBuffer()
  var newPendingTransactionsSubscriptions: ArrayBuffer[Subscription] = ArrayBuffer()
  var logsSubscriptions: ArrayBuffer[SubscriptionWithFilter] = ArrayBuffer()

  val webSocketAccountChannelImpl = new WebSocketAccountChannelImpl()
  private var walletAddresses: Set[Address] = webSocketAccountChannelImpl.getWalletAddresses
  private var cachedBlocksReceipts: List[(ModifierId, Set[EthereumLogView])] = List[(ModifierId, Set[EthereumLogView])]()
  private val maxCachedBlockReceipts = 100

  def notifySemanticallySuccessfulModifier(block: AccountBlock): Unit = {
    log.debug("Websocket received new block: "+block.toString)

    val blockJson = webSocketAccountChannelImpl.accountBlockToWebsocketJson(block)

    for(subscription <- newHeadsSubscriptions) {
        send(new WebSocketAccountEvent(params = new WebSocketAccountEventParams(subscription.subscriptionId, blockJson)), subscription.session)
    }

    while (cachedBlocksReceipts.nonEmpty && !cachedBlocksReceipts.head._1.equals(block.parentId)) {
      //We have a chain reorganization
      val oldTip: (ModifierId, Set[EthereumLogView]) = cachedBlocksReceipts.head
      for (subscription <- logsSubscriptions) {
        val logsToSend = oldTip._2.filter(RpcFilter.testLog(subscription.filter.address, subscription.filter.topics))
        sendTransactionLog(logsToSend.toSeq, subscription)
      }
      cachedBlocksReceipts = cachedBlocksReceipts.drop(1)
    }
    processBlockReceipt(block)
  }

  def notifyNewPendingTransaction(tx: EthereumTransaction): Unit = {
    log.debug("Websocket received new tx: "+tx.id())

    if (walletAddresses.contains(tx.getFromAddress)) {
      for (subscription <- newPendingTransactionsSubscriptions) {
        send(new WebSocketAccountEvent(params = new WebSocketAccountEventParams(subscription.subscriptionId, Numeric.prependHexPrefix(tx.id()))), subscription.session)
      }
    }
  }

  def notifyMempoolReaddedTransactions(readdedTxs: Seq[EthereumTransaction]): Unit = {
    readdedTxs.foreach( tx =>
      notifyNewPendingTransaction(tx)
    )
  }

  private def processBlockReceipt(block: AccountBlock): Unit = {
    var relevantBlockReceipt: Seq[EthereumLogView] = Seq()
    for (subscription <- logsSubscriptions) {
      val logs = webSocketAccountChannelImpl.getEthereumLogsFromBlock(block, subscription)
      sendTransactionLog(logs, subscription)
      relevantBlockReceipt = logs ++: relevantBlockReceipt
    }
    cachedBlocksReceipts = (block.id, relevantBlockReceipt.toSet.map((log: EthereumLogView) => {
      log.updateRemoved(true)
      log})) +: cachedBlocksReceipts
    if (cachedBlocksReceipts.size > maxCachedBlockReceipts) {
      cachedBlocksReceipts = cachedBlocksReceipts.dropRight(1)
    }
  }

  private def sendTransactionLog(txLogs: Seq[EthereumLogView], subscription: SubscriptionWithFilter): Unit = {
    txLogs.foreach(txLog => {
      send(new WebSocketAccountEvent( params =  new WebSocketAccountEventParams(subscription.subscriptionId, txLog)), subscription.session)
    })
  }

  def onVaultChanged(): Unit = {
    synchronized{
      walletAddresses = webSocketAccountChannelImpl.getWalletAddresses
    }
  }

  def addNewHeadsSubscription(subscription: Subscription): Unit = {
    synchronized{
      WebSocketAccountServerEndpoint.newHeadsSubscriptions += subscription
    }
  }

  def addNewPendingTransactionsSubscription(subscription: Subscription): Unit = {
    synchronized{
      WebSocketAccountServerEndpoint.newPendingTransactionsSubscriptions += subscription
    }
  }

  def addLogsSubscription(subscription: SubscriptionWithFilter): Unit = {
    synchronized{
      WebSocketAccountServerEndpoint.logsSubscriptions += subscription
    }
  }

  def removeSubscriptions(subscriptionIdToRemove: BigInteger): Boolean = {
    val foundNewHeadsSubscriptionToRemove = newHeadsSubscriptions.indexWhere(subscription => subscription.subscriptionId.equals(subscriptionIdToRemove))
    if (foundNewHeadsSubscriptionToRemove != -1) {
      newHeadsSubscriptions.remove(foundNewHeadsSubscriptionToRemove)
      return true
    }
    val foundNewPendingTransactionsSubscriptionToRemove = newPendingTransactionsSubscriptions.indexWhere(subscription => subscription.subscriptionId.equals(subscriptionIdToRemove))
    if (foundNewPendingTransactionsSubscriptionToRemove != -1) {
      newPendingTransactionsSubscriptions.remove(foundNewPendingTransactionsSubscriptionToRemove)
      return true
    }
    val foundLogSubscriptionToRemove = logsSubscriptions.indexWhere(subscription => subscription.subscriptionId.equals(subscriptionIdToRemove))
    if (foundLogSubscriptionToRemove != -1) {
      logsSubscriptions.remove(foundLogSubscriptionToRemove)
      return true
    }
    false
  }

  def removeSession(session: Session): Unit = {
    synchronized {
      newHeadsSubscriptions = newHeadsSubscriptions.filterNot(subscription => subscription.session.getId.equals(session.getId))
      newPendingTransactionsSubscriptions = newPendingTransactionsSubscriptions.filterNot(subscription => subscription.session.getId.equals(session.getId))
      logsSubscriptions = logsSubscriptions.filterNot(subscription => subscription.session.getId.equals(session.getId))
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
