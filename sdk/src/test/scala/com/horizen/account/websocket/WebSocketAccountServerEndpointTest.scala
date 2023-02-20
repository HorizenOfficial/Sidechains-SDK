package com.horizen.account.websocket

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.MempoolReAddedTransactions
import com.horizen.SidechainTypes
import com.horizen.account.api.rpc.types.EthereumBlockView
import com.horizen.account.api.rpc.utils.RpcCode
import com.horizen.account.block.AccountBlock
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.api.http.SidechainApiMockConfiguration
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.Hash
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.{BytesUtils, CountDownLatchController}
import org.glassfish.tyrus.client.ClientManager
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.{After, Assert, Test}
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.utils.Numeric
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.{SemanticallySuccessfulModifier, SuccessfulTransaction}

import java.math.BigInteger
import java.net.URI
import java.util
import jakarta.websocket.{ClientEndpointConfig, Endpoint, EndpointConfig, MessageHandler, Session}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class WebSocketAccountServerEndpointTest extends JUnitSuite with MockitoSugar with BeforeAndAfterAll{
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-websocket-account-server")
  val mockedSidechainNodeViewHolder: TestProbe = TestProbe()
  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()
  val utilMocks = new NodeViewHolderUtilMocks()

  case object INVALID_REQUEST extends WebSocketAccountRequest("eth_unknown")
  case object INVALID_SUBSCRIPTION extends WebSocketAccountSubscription("unknown")

  mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
    msg match {
      case GetDataFromCurrentView(f) =>
        sender ! f(utilMocks.getNodeView)
    }
    TestActor.KeepRunning
  })
  val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref
  private val server: ActorRef = WebSocketAccountServerRef(mockedSidechainNodeViewHolderRef, 9035)

  @After
  def after(): Unit = {
    actorSystem.stop(server)
  }

  def startSession(client: ClientManager, cec: ClientEndpointConfig, endpoint: WsEndpoint): Session = {
    var attempts = 30
    while(attempts > 0) {
      Try {
        client.connectToServer(endpoint, cec, new URI("ws://localhost:9035/"))
      } match {
        case Success(session) =>
          return session
        case Failure(_) =>
          // server is instantiating => try again in a while
          attempts -= 1
          Thread.sleep(100)
      }
    }
    Assert.fail("Not able to connect to server")
    null
  }

  @Test
  def sessionTest(): Unit = {
    // Test the handle of multiple client connections

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    //Client 1 subscribe to the websocket
    val clientId1 = 1
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, NEW_HEADS_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)

    // Add client 2
    val client2 = ClientManager.createClient

    val countDownController2: CountDownLatchController = new CountDownLatchController(1)
    val endpoint2 = new WsEndpoint(countDownController2)
    val session2: Session = startSession(client2, cec, endpoint2)

    //Client 2 subscribe to the websocket
    val clientId2 = 2
    sendWebsocketRequest(clientId2, SUBSCRIBE_REQUEST, NEW_HEADS_SUBSCRIPTION.method, Option.empty, session2)
    assertTrue("No event messages received.", countDownController2.await(5000))

    //Verify that we received the websocket response
    assertEquals(1, endpoint2.receivedMessage.size())
    response = mapper.readTree(endpoint2.receivedMessage.get(0))
    checkResponseMessage(response, clientId2)

    //Publish a new block
    countDownController.reset(1)
    countDownController2.reset(1)

    publishNewBlockEvent(utilMocks.genesisBlock)
    assertTrue("No event message received.", countDownController.await(5000))
    assertTrue("No event message received.", countDownController2.await(5000))

    assertEquals(2, endpoint.receivedMessage.size())
    assertEquals(2, endpoint2.receivedMessage.size())

    // Disconnect client 1
    session.close()
    // Disconnect client 2
    session2.close()
  }

  @Test
  def unsbuscriptionTest(): Unit = {
    // Test the handle of subscription and unsubscription

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    //Client 1 subscribe to the websocket method newHeads
    val clientId1 = 1
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, NEW_HEADS_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    val newHeadsSubscriptionId = response.get("result").asText()
    endpoint.receivedMessage.remove(0)

    //Client 1 subscribe to the websocket method newPendingTransactions
    countDownController.reset(1)
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, NEW_PENDING_TRANSACTIONS_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    val pendingTransactionSubscriptionId = response.get("result").asText()
    endpoint.receivedMessage.remove(0)

    //Publish a new block
    countDownController.reset(1)
    publishNewBlockEvent(utilMocks.genesisBlock)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    endpoint.receivedMessage.remove(0)

    //Unsubscribe to the newPendingTransaction method and verify that we still receive the newHeads events
    countDownController.reset(1)
    sendWebsocketRequest(clientId1, UNSUBSCRIBE_REQUEST, pendingTransactionSubscriptionId, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    assertEquals(response.get("result").asBoolean, true)
    endpoint.receivedMessage.remove(0)

    //Publish a new block
    countDownController.reset(1)
    publishNewBlockEvent(utilMocks.genesisBlock)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    endpoint.receivedMessage.remove(0)

    //Unsubscribe to the newHeads method
    countDownController.reset(1)
    sendWebsocketRequest(clientId1, UNSUBSCRIBE_REQUEST, newHeadsSubscriptionId, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    assertEquals(response.get("result").asBoolean, true)
    endpoint.receivedMessage.remove(0)

    //Publish a new block and verify that we didn't receive a new message
    countDownController.reset(1)
    publishNewBlockEvent(utilMocks.genesisBlock)
    assertFalse("No event message received.", countDownController.await(5000))
    assertEquals(0, endpoint.receivedMessage.size())

    //Try to unsubscribe to a non existing subscription
    countDownController.reset(1)
    val invalidSubscriptionId = "0x10"
    sendWebsocketRequest(clientId1, UNSUBSCRIBE_REQUEST, invalidSubscriptionId, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket error response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, clientId1, RpcCode.InvalidParams.code, "Subscription ID not found.")

    // Disconnect client 1
    session.close()
  }

  @Test
  def invalidSubscriptionTest(): Unit = {
    // Test the handle of invalid websocket requests

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    //Client 1 try to send an invalid websocket request
    val clientId1 = 1
    sendWebsocketRequest(clientId1, INVALID_REQUEST, NEW_HEADS_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we send back an error
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, clientId1, RpcCode.MethodNotFound.code, s"Method ${INVALID_REQUEST.request} not supported.")
    endpoint.receivedMessage.remove(0)

    //Client 1 try to subscribe to an invalid websocket method
    countDownController.reset(1)
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, INVALID_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we send back an error
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, clientId1, RpcCode.InvalidParams.code, s"unsupported subscription type ${INVALID_SUBSCRIPTION.method}")
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()
  }

  @Test
  def newHeadsSubscriptionTest(): Unit = {
    // Test the handle of newHeads websocket subscription

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    //Client 1 subscribe to newHeads method
    val clientId1 = 1
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, NEW_HEADS_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block
    countDownController.reset(1)

    publishNewBlockEvent(utilMocks.genesisBlock)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkBlock(response)
    endpoint.receivedMessage.remove(0)

    //Publish a new block
    countDownController.reset(1)

    publishNewBlockEvent(utilMocks.genesisBlock)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkBlock(response)
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()
  }

  @Test
  def newPendingTransactionsSubscriptionTest(): Unit = {
    // Test the handle of newPendingTransactions websocket subscription

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    //Client 1 subscribe to newHeads method
    val clientId1 = 1
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, NEW_PENDING_TRANSACTIONS_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new transaction
    countDownController.reset(1)

    publishNewTransactionEvent(utilMocks.exampleTransaction1)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkTransaction(response, utilMocks.exampleTransaction1)
    endpoint.receivedMessage.remove(0)

    //Publish a new transaction
    countDownController.reset(1)

    publishNewTransactionEvent(utilMocks.exampleTransaction2)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkTransaction(response, utilMocks.exampleTransaction2)
    endpoint.receivedMessage.remove(0)

    //Publish a new transaction that is not signed by the node
    countDownController.reset(1)

    publishNewTransactionEvent(utilMocks.nonIncludedTransaction)
    assertFalse("No event message received.", countDownController.await(5000))
    assertEquals(0, endpoint.receivedMessage.size())

    //Simulate the removal of these transactions from a block and the re insertion into the mempool
    countDownController.reset(1)
    publishNewReAddedTransactionEvent(Seq(utilMocks.exampleTransaction1, utilMocks.exampleTransaction2))
    assertTrue("No event message received.", countDownController.await(5000))
    countDownController.reset(1)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(2, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkTransaction(response, utilMocks.exampleTransaction1)
    response = mapper.readTree(endpoint.receivedMessage.get(1))
    checkTransaction(response, utilMocks.exampleTransaction2)

    // Disconnect client 1
    session.close()
  }

  @Test
  def logsSubscriptionTest(): Unit = {
    // Test the handle of logs websocket subscription

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    var session: Session = startSession(client, cec, endpoint)

    //Client 1 subscribe to logs method with one address filter
    val clientId1 = 1
    val logFilters = mapper.createObjectNode()
    val logAddress = "0x90dc4f6c07c2ecb76768a70276206436e77a6645"
    logFilters.put("address", logAddress)
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, LOGS_SUBSCRIPTION.method, Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    publishNewBlockEvent(utilMocks.blockWithTransaction)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transacionLog, Option.apply(Array[String]{logAddress}), Option.empty)
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Client 1 subscribe to logs method with two address filter (one contained and one not)
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    val logAddressNotIncluded = "0x1234567890123456789012345678901234567890"
    logFilters.removeAll()
    logFilters.set("address", mapper.readTree(SerializationUtil.serialize(Array(logAddress, logAddressNotIncluded))))
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, LOGS_SUBSCRIPTION.method, Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    publishNewBlockEvent(utilMocks.blockWithTransaction)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transacionLog, Option.apply(Array[String]{logAddress}), Option.empty)
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Client 1 subscribe to logs method with one address filter that is not included in the tx
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.put("address", logAddressNotIncluded)
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, LOGS_SUBSCRIPTION.method, Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    publishNewBlockEvent(utilMocks.blockWithTransaction)
    assertFalse("No event message received.", countDownController.await(5000))
    assertEquals(0, endpoint.receivedMessage.size())

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Client 1 subscribe to logs method with one topic filter
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    val topics0 = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
    logFilters.removeAll()
    logFilters.set("topics", mapper.readTree(SerializationUtil.serialize(Array(topics0))))
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, LOGS_SUBSCRIPTION.method, Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    publishNewBlockEvent(utilMocks.blockWithTransaction)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transacionLog, Option.apply(Array[String]{logAddress}), Option.empty)
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Client 1 subscribe to logs method with one topic filter and one address filter
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.set("topics", mapper.readTree(SerializationUtil.serialize(Array(topics0))))
    logFilters.put("address", logAddress)
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, LOGS_SUBSCRIPTION.method, Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    publishNewBlockEvent(utilMocks.blockWithTransaction)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transacionLog, Option.apply(Array[String]{logAddress}), Option.empty)
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Client 1 subscribe to logs method with no filters
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    sendWebsocketRequest(clientId1, SUBSCRIBE_REQUEST, LOGS_SUBSCRIPTION.method, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, clientId1, RpcCode.InvalidParams.code, "Missing filters (address, topcis).")
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()
  }



  private def sendWebsocketRequest(id: Int, request: WebSocketAccountRequest, method: String, additional_arguments: Option[JsonNode] = Option.empty, session: Session): Unit = {
    val rpcRequest = mapper.createObjectNode()
    rpcRequest.put("jsonrpc", "2.0")
    rpcRequest.put("id", id)
    rpcRequest.put("method", request.request)
    var params = Array[Object]{method}
    if (additional_arguments.isDefined)
      params = params :+additional_arguments.get
    rpcRequest.set("params", mapper.readTree(SerializationUtil.serialize(params)))
    session.getBasicRemote.sendObject(rpcRequest)

  }

  private def checkResponseMessage(wsResponse: JsonNode, expectedId: Int): Unit = {
    assertTrue("Missing field jsonrpc.", wsResponse.has("jsonrpc"))
    assertTrue("Missing field id.", wsResponse.has("id"))
    assertTrue("Missing field result.", wsResponse.has("result"))

    assertEquals("Wrong jsonrpc version", wsResponse.get("jsonrpc").asText(), "2.0")
    assertEquals("Wrong id", wsResponse.get("id").asInt(), expectedId)
  }

  private def checkErrorResponse(wsResponse: JsonNode, expectedId: Int, expectedError: Int, expectedErrorMessage: String): Unit = {
    assertTrue("Missing field jsonrpc.", wsResponse.has("jsonrpc"))
    assertTrue("Missing field id.", wsResponse.has("id"))
    assertTrue("Missing field error.", wsResponse.has("error"))

    assertEquals("Wrong jsonrpc version", wsResponse.get("jsonrpc").asText(), "2.0")
    assertEquals("Wrong id", wsResponse.get("id").asInt(), expectedId)
    val error = wsResponse.get("error")
    assertEquals("Wrong error code", error.get("code").asInt(), expectedError)
    assertEquals("Wrong error message", error.get("message").asText(), expectedErrorMessage)

  }

  private def checkBlock(wsResponse: JsonNode): Unit = {
    checkWsEventStaticFields(wsResponse)

    val blockJson = wsResponse.get("params").get("result")
    val ethereumBlockView = EthereumBlockView.withoutTransactions(0, new Hash(utilMocks.genesisBlock.id.toBytes), utilMocks.genesisBlock)

    assertEquals("Wrong block difficulty", blockJson.get("difficulty").asText(), "0x0")
    assertEquals("Wrong block extraData", blockJson.get("extraData").asText(), ethereumBlockView.extraData)
    assertEquals("Wrong block gasLimit", blockJson.get("gasLimit").asText(), ethereumBlockView.gasLimit)
    assertEquals("Wrong block gasUsed", blockJson.get("gasUsed").asText(), ethereumBlockView.gasUsed)
    assertEquals("Wrong block logsBloom", blockJson.get("logsBloom").asText(), ethereumBlockView.logsBloom)
    assertEquals("Wrong block miner", blockJson.get("miner").asText(), ethereumBlockView.miner.toString)
    assertEquals("Wrong block nonce", blockJson.get("nonce").asText(), ethereumBlockView.nonce)
    assertEquals("Wrong block number", blockJson.get("number").asText(), ethereumBlockView.number)
    assertEquals("Wrong block parentHash", blockJson.get("parentHash").asText(), ethereumBlockView.parentHash.toString)
    assertEquals("Wrong block receiptRoot", blockJson.get("receiptRoot").asText(), ethereumBlockView.receiptsRoot.toString)
    assertEquals("Wrong block sha3Uncles", blockJson.get("sha3Uncles").asText(), ethereumBlockView.sha3Uncles)
    assertEquals("Wrong block stateRoot", blockJson.get("stateRoot").asText(), ethereumBlockView.stateRoot.toString)
    assertEquals("Wrong block timestamp", blockJson.get("timestamp").asText(), ethereumBlockView.timestamp)
    assertEquals("Wrong block transactionsRoot", blockJson.get("transactionsRoot").asText(), ethereumBlockView.transactionsRoot.toString)
  }

  private def checkTransaction(wsResponse: JsonNode, tx: EthereumTransaction): Unit = {
    checkWsEventStaticFields(wsResponse)

    val txHashJson = wsResponse.get("params").get("result").asText()
    assertEquals("Wrong transaction hash", txHashJson, Numeric.prependHexPrefix(tx.id()))
  }

  private def checkLogs(wsResponse: JsonNode, transactionReceipt: EthereumReceipt, transactionLog: EvmLog, addressFilter: Option[Array[String]], topicsFilter: Option[Array[String]]): Unit = {
    checkWsEventStaticFields(wsResponse)

    val logJson = wsResponse.get("params").get("result")
    if (addressFilter.isDefined && addressFilter.get.length > 0)
      assertTrue("Wrong log address", addressFilter.get.contains(logJson.get("address").asText()))
    val jsonTopics = logJson.get("topics")
    if (topicsFilter.isDefined && topicsFilter.get.length > 0) {
      val jsonTopics = logJson.get("topics")
      jsonTopics.forEach(topic => assertTrue("Wrong log topics", topicsFilter.get.contains(topic.asText())))
    } else {
      jsonTopics.forEach(topic => assertTrue("Wrong log topics", transactionLog.topics.contains(new Hash(Numeric.prependHexPrefix(topic.asText())))))
    }

    assertEquals("Wrong log data", logJson.get("data").asText(), Numeric.prependHexPrefix(BytesUtils.toHexString(transactionLog.data)))
    assertEquals("Wrong log logIndex", logJson.get("logIndex").asText(), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    assertEquals("Wrong log blockHash", logJson.get("blockHash").asText(), Numeric.prependHexPrefix(BytesUtils.toHexString(transactionReceipt.blockHash)))
    assertEquals("Wrong log blockNumber", logJson.get("blockNumber").asText(), Numeric.toHexStringWithPrefix(BigInteger.valueOf(transactionReceipt.blockNumber)))
    assertEquals("Wrong log transactionHash", logJson.get("transactionHash").asText(), Numeric.prependHexPrefix(BytesUtils.toHexString(transactionReceipt.transactionHash)))
    assertEquals("Wrong log transactionIndex", logJson.get("transactionIndex").asText(), Numeric.toHexStringWithPrefix(BigInteger.valueOf(transactionReceipt.transactionIndex)))
  }

  private def checkWsEventStaticFields(wsResponse: JsonNode): Unit = {
    assertTrue("Missing field jsonrpc.", wsResponse.has("jsonrpc"))
    assertTrue("Missing field method.", wsResponse.has("method"))
    assertTrue("Missing field params.", wsResponse.has("params"))
    assertTrue("Missing field result in params.", wsResponse.get("params").has("result"))
    assertTrue("Missing field subscription in params.", wsResponse.get("params").has("subscription"))

    assertEquals("Wrong jsonrpc version", wsResponse.get("jsonrpc").asText(), "2.0")
    assertEquals("Wrong method", wsResponse.get("method").asText(), "eth_subscription")
  }

  def publishNewBlockEvent(block: AccountBlock): Unit = {
    actorSystem.eventStream.publish(SemanticallySuccessfulModifier[sparkz.core.PersistentNodeViewModifier](block))
  }

  def publishNewTransactionEvent(tx: EthereumTransaction): Unit = {
    actorSystem.eventStream.publish(SuccessfulTransaction[EthereumTransaction](tx))
  }

  def publishNewReAddedTransactionEvent(txs: Seq[EthereumTransaction]): Unit = {
    actorSystem.eventStream.publish(MempoolReAddedTransactions[SidechainTypes#SCAT](txs.asInstanceOf[Seq[SidechainTypes#SCAT]]))
  }
}

private class WsEndpoint(countDownLatchController: CountDownLatchController) extends Endpoint {
  var receivedMessage: util.ArrayList[String] = new util.ArrayList[String]()

  override def onOpen(session: Session, config: EndpointConfig): Unit = {
    session.addMessageHandler(new MessageHandler.Whole[String]() {
      override def onMessage(message: String): Unit = {
        receivedMessage.add(message)
        // notify the message was received
        countDownLatchController.countDown()
      }
    })
  }
}