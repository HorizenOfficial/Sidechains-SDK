package com.horizen.account.websocket

import java.util
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.SidechainTypes
import com.horizen.account.api.rpc.request.{RpcId, RpcRequest}
import com.horizen.account.api.rpc.response.{RpcResponseError, RpcResponseSuccess}
import com.horizen.account.api.rpc.utils.{RpcCode, RpcError}
import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.websocket.data.{Subscription, SubscriptionWithFilter, WebsocketAccountResponse}
import com.horizen.serialization.SerializationUtil

import jakarta.websocket.{OnClose, OnError, OnMessage, SendHandler, SendResult, Session}
import jakarta.websocket.server.ServerEndpoint
import sparkz.util.SparkzLogging

import java.io.{PrintWriter, StringWriter}
import java.util.concurrent.atomic.AtomicInteger
import org.web3j.utils.Numeric

import java.math.BigInteger

abstract class WebSocketAccountRequest(val request: String)
case object SUBSCRIBE_REQUEST extends WebSocketAccountRequest("eth_subscribe")
case object UNSUBSCRIBE_REQUEST extends WebSocketAccountRequest("eth_unsubscribe")

abstract class WebSocketAccountSubscription(val method: String)
case object NEW_HEADS_SUBSCRIPTION extends WebSocketAccountSubscription("newHeads")
case object NEW_PENDING_TRANSACTIONS_SUBSCRIPTION extends WebSocketAccountSubscription("newPendingTransactions")
case object LOGS_SUBSCRIPTION extends WebSocketAccountSubscription("logs")

@ServerEndpoint("/")
class WebSocketAccountServerEndpoint() extends SparkzLogging {
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

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
      val json = mapper.readTree(message)
      val rpcRequest = new RpcRequest(json)

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
        WebSocketAccountServerEndpoint.send(new RpcResponseError(new RpcId("1"),
          new RpcError(RpcCode.ExecutionError, "Websocket On receive message processing exception occurred", sw.toString)),
          session)
        log.debug("Websocket On receive message processing exception occurred = " + ex.getMessage)
    }
  }

  private def subscribe(session: Session, rpcRequest: RpcRequest): Unit = {
    val rpcParams = rpcRequest.params
    val subscribeMethod = rpcParams.get(0)
    val subscriptionId = createSubscriptionId()

    subscribeMethod.asText() match {

      case NEW_HEADS_SUBSCRIPTION.method =>
        WebSocketAccountServerEndpoint.addNewHeadsSubscription(Subscription(session, subscriptionId))
        log.debug("New Subscription on newHeads "+session.getId)
        WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, subscriptionId), session)

      case NEW_PENDING_TRANSACTIONS_SUBSCRIPTION.method =>
        WebSocketAccountServerEndpoint.addNewPendingTransactionsSubscription(Subscription(session, subscriptionId))
        log.debug("New Subscription on newPendingTransactions "+session.getId)
        WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, subscriptionId), session)

      case LOGS_SUBSCRIPTION.method =>
        if (rpcParams.size() < 2)
          WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
            new RpcError(RpcCode.InvalidParams, "Missing filters (address, topcis).", "")),
            session)
        val logs = rpcParams.get(1)
        val addressFilter = getLogAddresses(logs)
        val topicFilter = getLogTopics(logs)
        WebSocketAccountServerEndpoint.addLogsSubscription(SubscriptionWithFilter(session, subscriptionId,
          addressFilter,
          topicFilter
        ))
        log.debug("New Subscription on logs "+session.getId)
        WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, subscriptionId), session)

      case uknownMethod =>
        WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
          new RpcError(RpcCode.InvalidParams, "unsupported subscription type " + uknownMethod, "")),
          session)
        log.debug("unsupported subscription type " + uknownMethod)
    }

  }

  private def unsubscribe(session: Session, rpcRequest: RpcRequest): Unit = {
    val rpcParams = rpcRequest.params

    val subscriptionIdsToRemove: util.ArrayList[String] = new util.ArrayList[String]()
    rpcParams.forEach(subscriptionId => subscriptionIdsToRemove.add(subscriptionId.asText()))
    val removedSubscription = WebSocketAccountServerEndpoint.removeSubscriptions(subscriptionIdsToRemove.toArray(new Array[String](0)))
    if (!removedSubscription) {
      WebSocketAccountServerEndpoint.send(new RpcResponseError(rpcRequest.id,
        new RpcError(RpcCode.InvalidParams, s"Subscription ID not found.", "")),
        session)
    } else
      WebSocketAccountServerEndpoint.send(new RpcResponseSuccess(rpcRequest.id, true), session)
  }

  private def createSubscriptionId(): String = {
    Numeric.toHexStringWithPrefix(BigInteger.valueOf(WebSocketAccountServerEndpoint.subscriptionCounter.incrementAndGet()))
  }

  private def getLogAddresses(logParams: JsonNode): Option[Array[String]] = {
    var address: Option[Array[String]] = Option.empty
    if (logParams.has("address")) {
      val addressParams = logParams.get("address")
      if (addressParams.isArray) {
        val addresses: util.ArrayList[String] = new util.ArrayList[String]()
        addressParams.forEach(addr => addresses.add(addr.asText()))
        address = Some(addresses.toArray(new Array[String](0)))
      } else {
        address = Some(Array[String]{addressParams.asText()})
      }
    }
    address
  }

  private def getLogTopics(logParams: JsonNode): Option[Array[String]] = {
    var topics: Option[Array[String]] = Option.empty
    if (logParams.has("topics")) {
      val topicArray: util.ArrayList[String] = new util.ArrayList[String]()
      logParams.get("topics").forEach(topic => topicArray.add(topic.asText()))
      topics = Some(topicArray.toArray(new Array[String](0)))
    }
    topics
  }
}

private object WebSocketAccountServerEndpoint extends SparkzLogging {
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
  var subscriptionCounter: AtomicInteger = new AtomicInteger(0)
  var newHeadsSubscriptions: util.ArrayList[Subscription] = new util.ArrayList[Subscription]()
  var newPendingTransactionsSubscriptions: util.ArrayList[Subscription] = new util.ArrayList[Subscription]()
  var logsSubscriptions: util.ArrayList[SubscriptionWithFilter] = new util.ArrayList[SubscriptionWithFilter]()

  val webSocketAccountChannelImpl = new WebSocketAccountChannelImpl()
  private var walletKeys: Set[String] = webSocketAccountChannelImpl.getWalletKeys()

  def notifySemanticallySuccessfulModifier(block: AccountBlock): Unit = {
    log.info("Websocket received new block: "+block.toString)

    val responsePayload = mapper.createObjectNode()

    val blockJson = webSocketAccountChannelImpl.accountBlockToWebsocketJson(block)
    responsePayload.set("result", blockJson)

    newHeadsSubscriptions.forEach(subscription => {
      responsePayload.put("subscription", subscription.subscriptionId)
      send(new WebsocketAccountResponse("eth_subscription", responsePayload), subscription.session)
    })

    logsSubscriptions.forEach(subscription => {
      if (subscription.checkSubscriptionInBloom(block.header.logsBloom)) {
        block.transactions.foreach(tx => getTransactionsLogData(tx, subscription))
      }
    })
  }

  def notifyNewPendingTransaction(tx: EthereumTransaction): Unit = {
    log.info("Websocket received new tx: "+tx.id())

    if (walletKeys.contains(tx.getFromAddress.toString)) {
      val responsePayload = mapper.createObjectNode()
      responsePayload.put("result", Numeric.prependHexPrefix(tx.id()))

      newPendingTransactionsSubscriptions.forEach(subscription => {
        responsePayload.put("subscription", subscription.subscriptionId)
        send(new WebsocketAccountResponse("eth_subscription", responsePayload), subscription.session)
      })
    }
  }

  def notifyMempoolReaddedTransactions(readdedTxs: Seq[EthereumTransaction]): Unit = {
    readdedTxs.foreach( tx =>
      notifyNewPendingTransaction(tx)
    )
  }

  def notifyRemovedTransactions(removedTxs: Seq[SidechainTypes#SCAT]): Unit = {
    logsSubscriptions.forEach(subscription => {
      removedTxs.foreach(tx => getTransactionsLogData(tx, subscription, removed = true))
    })
  }

  private def getTransactionsLogData(tx: SidechainTypes#SCAT, subscription: SubscriptionWithFilter, removed: Boolean = false): Unit = {

    val responsePayload = mapper.createObjectNode()

    val txReceipt = webSocketAccountChannelImpl.getTransactionLogData(tx.id, subscription)
    if (txReceipt.isDefined) {
      responsePayload.set("result", txReceipt.get)
      responsePayload.put("subscription", subscription.subscriptionId)
      if (removed)
        responsePayload.put("removed", true)
      send(new WebsocketAccountResponse("eth_subscription", responsePayload), subscription.session)
    }
  }

  def onVaultChanged(): Unit = {
    walletKeys = webSocketAccountChannelImpl.getWalletKeys()
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

  def removeSubscriptions(subscriptionIdsToRemove: Array[String]): Boolean = {
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
    session.getAsyncRemote.sendObject(SerializationUtil.serialize(websocketResponse), new SendHandler {
      override def onResult(sendResult: SendResult): Unit = {
        if (!sendResult.isOK) {
          log.debug("Websocket send message failed. "+session.getId)
        }
      }
    })
  }

}
