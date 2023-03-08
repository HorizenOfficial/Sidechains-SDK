package io.horizen.account.websocket

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.horizen.SidechainTypes
import io.horizen.account.AccountSidechainNodeViewHolder.NewExecTransactionsEvent
import io.horizen.account.api.rpc.types.EthereumBlockView
import io.horizen.account.api.rpc.utils.RpcCode
import io.horizen.account.block.AccountBlock
import io.horizen.account.serialization.EthJsonMapper
import io.horizen.account.state.receipt.{EthereumConsensusDataLog, EthereumReceipt}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.api.http.SidechainApiMockConfiguration
import io.horizen.evm.Hash
import io.horizen.json.SerializationUtil
import io.horizen.network.SyncStatus
import io.horizen.network.SyncStatusActor.{NotifySyncStart, NotifySyncStop, NotifySyncUpdate}
import io.horizen.utils.{BytesUtils, CountDownLatchController}
import jakarta.websocket._
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
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(NEW_HEADS_SUBSCRIPTION.method), Option.empty, session)
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
    sendWebsocketRequest(Option.apply(clientId2), Option.apply(SUBSCRIBE_REQUEST), Option.apply(NEW_HEADS_SUBSCRIPTION.method), Option.empty, session2)
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
  def unsubscriptionTest(): Unit = {
    // Test the handle of subscription and unsubscription

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    //Client 1 subscribe to the websocket method newHeads
    val clientId1 = 1
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(NEW_HEADS_SUBSCRIPTION.method), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    val newHeadsSubscriptionId = response.get("result").asText()
    endpoint.receivedMessage.remove(0)

    //Client 1 subscribe to the websocket method newPendingTransactions
    countDownController.reset(1)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(NEW_PENDING_TRANSACTIONS_SUBSCRIPTION.method), Option.empty, session)
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
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(UNSUBSCRIBE_REQUEST), Option.apply(pendingTransactionSubscriptionId), Option.empty, session)
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
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(UNSUBSCRIBE_REQUEST), Option.apply(newHeadsSubscriptionId), Option.empty, session)
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
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(UNSUBSCRIBE_REQUEST), Option.apply(invalidSubscriptionId), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we received the websocket error response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, Option.apply(clientId1), RpcCode.InvalidParams.code, "Subscription ID not found.")

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
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(INVALID_REQUEST), Option.apply(NEW_HEADS_SUBSCRIPTION.method), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we send back an error
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, Option.apply(clientId1), RpcCode.MethodNotFound.code, s"Method ${INVALID_REQUEST.request} not supported.")
    endpoint.receivedMessage.remove(0)

    //Client 1 try to subscribe to an invalid websocket method
    countDownController.reset(1)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(INVALID_SUBSCRIPTION.method), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we send back an error
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, Option.apply(clientId1), RpcCode.InvalidParams.code, s"unsupported subscription type ${INVALID_SUBSCRIPTION.method}")
    endpoint.receivedMessage.remove(0)

    //Client 1 try to subscribe with no params field
    countDownController.reset(1)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.empty, Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we send back an error
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, Option.apply(clientId1), RpcCode.InvalidParams.code, "Missing or empty field params.")
    endpoint.receivedMessage.remove(0)

    //Client 1 try to subscribe with no field id
    countDownController.reset(1)
    assertEquals(0, endpoint.receivedMessage.size())
    sendWebsocketRequest(Option.empty, Option.apply(SUBSCRIBE_REQUEST), Option.apply(NEW_HEADS_SUBSCRIPTION.method), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we send back an error
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, Option.empty, RpcCode.ExecutionError.code, "Websocket On receive message processing exception occurred")
    endpoint.receivedMessage.remove(0)

    //Client 1 try to subscribe with no field method
    countDownController.reset(1)
    assertEquals(0, endpoint.receivedMessage.size())
    sendWebsocketRequest(Option.apply(clientId1), Option.empty, Option.apply(NEW_HEADS_SUBSCRIPTION.method), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we send back an error
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, Option.apply(clientId1), RpcCode.ExecutionError.code, "Websocket On receive message processing exception occurred")
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
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(NEW_HEADS_SUBSCRIPTION.method), Option.empty, session)
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
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(NEW_PENDING_TRANSACTIONS_SUBSCRIPTION.method), Option.empty, session)
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
    publishNewExecTransactionEvent(Seq(utilMocks.exampleTransaction1, utilMocks.exampleTransaction2))
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

    // Client 1 subscribe to logs method with one address filter contained in the transaction
    // Topic filter: []

    val clientId1 = 1
    val logFilters = mapper.createObjectNode()
    logFilters.put("address", utilMocks.transactionAddress.toString)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    // Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    // Publish a new block containing a transaction
    countDownController.reset(1)
    var lastBlock = utilMocks.getNextBlockWithTransaction()
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    countDownController.reset(1)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(2, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog2, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ONE))
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Client 1 subscribe to logs method with two address filter (one contained and one not)
    // Topic filter: []
    // We also verify that we check that the block received is not coming from a chain reorg (parentId is inside the Websocket cache)

    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    val logAddressNotIncluded = "0x1234567890123456789012345678901234567890"
    logFilters.removeAll()
    logFilters.set("address", mapper.readTree(SerializationUtil.serialize(Array(utilMocks.transactionAddress.toString, logAddressNotIncluded))))
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    // Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    // Publish a new block containing a transaction
    countDownController.reset(1)
    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    countDownController.reset(1)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(2, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog2, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ONE))
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Client 1 subscribe to logs method with one address filter that is not included in the tx
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.put("address", logAddressNotIncluded)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    // Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    // Publish a new block containing a transaction
    countDownController.reset(1)

    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertFalse("No event message received.", countDownController.await(5000))
    assertEquals(0, endpoint.receivedMessage.size())

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Client 1 subscribe to logs method with one topic filter
    // Tx log:
    //  Log0 => [topic0, topic1, topic2]
    //  Log1 => [topic0]
    // Topic filter:
    // [[topic0]]
    // We expect to receive both log because they have both topic0 in the first position

    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.set("topics", mapper.readTree(SerializationUtil.serialize(Array(utilMocks.transactionTopic0))))
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    // Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    // Publish a new block containing a transaction
    countDownController.reset(1)

    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    countDownController.reset(1)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(2, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog2, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ONE))
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Client 1 subscribe to logs method with one topic filter and one address filter
    // Tx log:
    //  Log0 => [topic0, topic1, topic2]
    //  Log1 => [topic0]
    // Topic filter:
    // [[topic0]]
    // We expect to receive both log because they have both topic0 in the first position

    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.set("topics", mapper.readTree(SerializationUtil.serialize(Array(utilMocks.transactionTopic0))))
    logFilters.put("address", utilMocks.transactionAddress.toString)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    // Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    // Publish a new block containing a transaction
    countDownController.reset(1)

    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    countDownController.reset(1)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(2, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog2, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ONE))
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Client 1 subscribe to logs method with one topic filter included in the first tx logs and one address filter
    // Tx log:
    //  Log0 => [topic0, topic1, topic2]
    //  Log1 => [topic0]
    // Topic filter:
    // [[topic0], [topic1], [topic2]]
    // We expect to receive only the first log

    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.set("topics", mapper.readTree(SerializationUtil.serialize(Array(utilMocks.transactionTopic0, utilMocks.transactionTopic1, utilMocks.transactionTopic2))))
    logFilters.put("address", utilMocks.transactionAddress.toString)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Client 1 subscribe to logs method with one topic filter included in the first tx log, one topic filter not included in the tx logs and one address filter
    // Tx log:
    //  Log0 => [topic0, topic1, topic2]
    //  Log1 => [topic0]
    // Topic filter:
    // [[topic0, topic1, topic2], [topic1]]
    // We expect to receive only the first log

    endpoint.receivedMessage.clear()
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.set("topics", mapper.readTree(SerializationUtil.serialize(Array(Array(utilMocks.transactionTopic0, utilMocks.transactionTopic1, utilMocks.transactionTopic2), utilMocks.transactionTopic1))))
    logFilters.put("address", utilMocks.transactionAddress.toString)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Client 1 subscribe to logs method with one topic filter included in the first tx log and one address filter
    // Tx log:
    //  Log0 => [topic0, topic1, topic2]
    //  Log1 => [topic0]
    // Topic filter:
    // [[], [], [topic2]]
    // We expect to receive only the first log

    endpoint.receivedMessage.clear()
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.set("topics", mapper.readTree(SerializationUtil.serialize(Array(), Array(), Array(utilMocks.transactionTopic2))))
    logFilters.put("address", utilMocks.transactionAddress.toString)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new block containing a transaction
    countDownController.reset(1)

    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Client 1 subscribe to logs method with no filters
    countDownController.reset(1)
    session = startSession(client, cec, endpoint)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a negative response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkErrorResponse(response, Option.apply(clientId1), RpcCode.InvalidParams.code, "Missing filters (address, topics).")
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()

    ///////////////// Chain reorganization ///////////////////////////////
    countDownController.reset(1)

    //Client 1 subscribe to logs method with one address filter
    session = startSession(client, cec, endpoint)
    logFilters.removeAll()
    logFilters.put("address", utilMocks.transactionAddress.toString)
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters), session)
    assertTrue("No event messages received.", countDownController.await(10000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    countDownController.reset(1)
    lastBlock = utilMocks.getNextBlockWithTransaction(Some(lastBlock.id))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    countDownController.reset(1)
    assertTrue("No event message received.", countDownController.await(5000))

    assertEquals(2, endpoint.receivedMessage.size())
    endpoint.receivedMessage.remove(0)
    endpoint.receivedMessage.remove(0)
    assertEquals(0, endpoint.receivedMessage.size())

    // Generate one other block
    countDownController.reset(1)
    val secondLastBlockId = lastBlock.id
    lastBlock = utilMocks.getNextBlockWithTransaction(Some(secondLastBlockId))
    publishNewBlockEvent(lastBlock)
    assertTrue("No event message received.", countDownController.await(5000))

    countDownController.reset(1)
    assertTrue("No event message received.", countDownController.await(5000))

    assertEquals(2, endpoint.receivedMessage.size())
    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))
    endpoint.receivedMessage.remove(0)

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ONE))
    endpoint.receivedMessage.remove(0)
    assertEquals(0, endpoint.receivedMessage.size())

    countDownController.reset(1)
    assertFalse("Event message received.", countDownController.await(5000))
    assertEquals(0, endpoint.receivedMessage.size())

    // Connect another client 2

    val client2 = ClientManager.createClient

    val countDownController2: CountDownLatchController = new CountDownLatchController(1)
    val endpoint2 = new WsEndpoint(countDownController)
    val session2: Session = startSession(client2, cec, endpoint2)

    // Client 2subscribe to logs method with one address filter NOT contained in the transaction
    // Topic filter: []

    val clientId2 = 2
    val logFilters2 = mapper.createObjectNode()
    logFilters2.put("address", logAddressNotIncluded)
    sendWebsocketRequest(Option.apply(clientId2), Option.apply(SUBSCRIBE_REQUEST), Option.apply(LOGS_SUBSCRIPTION.method), Option.apply(logFilters2), session2)
    assertTrue("No event messages received.", countDownController.await(10000))

    // Verify that we receive a positive response
    assertEquals(1, endpoint2.receivedMessage.size())
    val response2 = mapper.readTree(endpoint2.receivedMessage.get(0))
    checkResponseMessage(response2, clientId2)
    endpoint2.receivedMessage.remove(0)
    countDownController2.reset(1)


    // Create another blocks, the first 1 block generated should be removed and these one should be added
    lastBlock = utilMocks.getNextBlockWithTransaction(Some(secondLastBlockId))
    publishNewBlockEvent(lastBlock)

    Thread.sleep(10000)
    assertEquals(4, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO), removed = true)

    response = mapper.readTree(endpoint.receivedMessage.get(1))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ONE), removed = true)

    response = mapper.readTree(endpoint.receivedMessage.get(2))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ZERO))

    response = mapper.readTree(endpoint.receivedMessage.get(3))
    checkLogs(response, utilMocks.transactionReceipt, utilMocks.transactionLog, Option.apply(Array[String]{utilMocks.transactionAddress.toString}), Numeric.toHexStringWithPrefix(BigInteger.ONE))

    assertEquals(4, endpoint.receivedMessage.size())

    countDownController.reset(1)
    assertFalse("Event message received.", countDownController.await(5000))
    assertEquals(4, endpoint.receivedMessage.size())
    endpoint.receivedMessage.clear()

    // Verify that the client didn't received any message since its filters don't match any tx log
    assertFalse("Event message received.", countDownController2.await(5000))
    assertEquals(0, endpoint2.receivedMessage.size())
    endpoint2.receivedMessage.clear()

    // Disconnect client 1
    session.close()

    // Disconnect client2
    session2.close()
  }

  @Test
  def syncingSubscriptionTest(): Unit = {
    // Test the handle of syncing websocket subscription

    // Add client 1
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    //Client 1 subscribe to syncing method
    val clientId1 = 1
    sendWebsocketRequest(Option.apply(clientId1), Option.apply(SUBSCRIBE_REQUEST), Option.apply(SYNCING_SUBSCRIPTION.method), Option.empty, session)
    assertTrue("No event messages received.", countDownController.await(5000))

    //Verify that we receive a positive response
    assertEquals(1, endpoint.receivedMessage.size())
    var response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkResponseMessage(response, clientId1)
    endpoint.receivedMessage.remove(0)

    //Publish a new start sync event
    countDownController.reset(1)

    publishNewSyncStartEVent(utilMocks.syncStatus)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkSyncStartStatus(response, utilMocks.syncStatus)
    endpoint.receivedMessage.remove(0)

    //Publish a new stop sync event
    countDownController.reset(1)

    publishNewSyncStopEvent()
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkSyncStopStatus(response)
    endpoint.receivedMessage.remove(0)

    //Publish a new update sync event
    countDownController.reset(1)

    publishNewSyncUpdateEvent(utilMocks.syncStatus)
    assertTrue("No event message received.", countDownController.await(5000))
    assertEquals(1, endpoint.receivedMessage.size())

    response = mapper.readTree(endpoint.receivedMessage.get(0))
    checkSyncStartStatus(response, utilMocks.syncStatus)
    endpoint.receivedMessage.remove(0)

    // Disconnect client 1
    session.close()
  }


  private def sendWebsocketRequest(id: Option[Int], request: Option[WebSocketAccountRequest], method: Option[String], additional_arguments: Option[JsonNode] = Option.empty, session: Session): Unit = {
    val rpcRequest = mapper.createObjectNode()
    rpcRequest.put("jsonrpc", "2.0")
    if (id.isDefined) {
      rpcRequest.put("id", id.get)
    }
    if (request.isDefined) {
      rpcRequest.put("method", request.get.request)
    }
    if (method.isDefined) {
      var params = Array[Object]{method.get}
      if (additional_arguments.isDefined)
        params = params :+additional_arguments.get
      rpcRequest.set("params", mapper.readTree(SerializationUtil.serialize(params)))
    }
    session.getBasicRemote.sendObject(rpcRequest)

  }

  private def checkResponseMessage(wsResponse: JsonNode, expectedId: Int): Unit = {
    assertTrue("Missing field jsonrpc.", wsResponse.has("jsonrpc"))
    assertTrue("Missing field id.", wsResponse.has("id"))
    assertTrue("Missing field result.", wsResponse.has("result"))

    assertEquals("Wrong jsonrpc version", "2.0", wsResponse.get("jsonrpc").asText())
    assertEquals("Wrong id", expectedId,  wsResponse.get("id").asInt())
  }

  private def checkErrorResponse(wsResponse: JsonNode, expectedId: Option[Int], expectedError: Int, expectedErrorMessage: String): Unit = {
    assertTrue("Missing field jsonrpc.", wsResponse.has("jsonrpc"))
    assertTrue("Missing field id.", wsResponse.has("id"))
    assertTrue("Missing field error.", wsResponse.has("error"))

    assertEquals("Wrong jsonrpc version","2.0",  wsResponse.get("jsonrpc").asText())
    if (expectedId.isDefined)
      assertEquals("Wrong id", expectedId.get, wsResponse.get("id").asInt())
    else
      assertTrue("Wrong id", wsResponse.get("id").isNull)
    val error = wsResponse.get("error")
    assertEquals("Wrong error code", expectedError, error.get("code").asInt())
    assertEquals("Wrong error message", expectedErrorMessage, error.get("message").asText())

  }

  private def checkBlock(wsResponse: JsonNode): Unit = {
    checkWsEventStaticFields(wsResponse)

    val blockJson = wsResponse.get("params").get("result")
    val ethereumBlockView = EthereumBlockView.withoutTransactions(0, new Hash(utilMocks.genesisBlock.id.toBytes), utilMocks.genesisBlock)

    assertEquals("Wrong block difficulty", "0x0", blockJson.get("difficulty").asText())
    assertEquals("Wrong block extraData", ethereumBlockView.extraData, blockJson.get("extraData").asText())
    assertEquals("Wrong block gasLimit", Numeric.toHexStringWithPrefix(ethereumBlockView.gasLimit), blockJson.get("gasLimit").asText())
    assertEquals("Wrong block gasUsed", Numeric.toHexStringWithPrefix(ethereumBlockView.gasUsed), blockJson.get("gasUsed").asText())
    assertEquals("Wrong block logsBloom", Numeric.prependHexPrefix(BytesUtils.toHexString(ethereumBlockView.logsBloom)), blockJson.get("logsBloom").asText())
    assertEquals("Wrong block miner", ethereumBlockView.miner.toString, blockJson.get("miner").asText())
    assertEquals("Wrong block nonce", ethereumBlockView.nonce, blockJson.get("nonce").asText())
    assertEquals("Wrong block number", Numeric.toHexStringWithPrefix(ethereumBlockView.number), blockJson.get("number").asText())
    assertEquals("Wrong block parentHash", ethereumBlockView.parentHash.toString, blockJson.get("parentHash").asText())
    assertEquals("Wrong block receiptsRoot", ethereumBlockView.receiptsRoot.toString, blockJson.get("receiptsRoot").asText())
    assertEquals("Wrong block sha3Uncles", ethereumBlockView.sha3Uncles, blockJson.get("sha3Uncles").asText())
    assertEquals("Wrong block stateRoot", ethereumBlockView.stateRoot.toString, blockJson.get("stateRoot").asText())
    assertEquals("Wrong block timestamp", Numeric.toHexStringWithPrefix(ethereumBlockView.timestamp), blockJson.get("timestamp").asText())
    assertEquals("Wrong block transactionsRoot", ethereumBlockView.transactionsRoot.toString, blockJson.get("transactionsRoot").asText())
  }

  private def checkTransaction(wsResponse: JsonNode, tx: EthereumTransaction): Unit = {
    checkWsEventStaticFields(wsResponse)

    val txHashJson = wsResponse.get("params").get("result").asText()
    assertEquals("Wrong transaction hash", Numeric.prependHexPrefix(tx.id()), txHashJson)
  }

  private def checkLogs(wsResponse: JsonNode, transactionReceipt: EthereumReceipt, transactionLog: EthereumConsensusDataLog, addressFilter: Option[Array[String]], logIndex: String, removed: Boolean = false): Unit = {
    checkWsEventStaticFields(wsResponse)

    val logJson = wsResponse.get("params").get("result")
    if (addressFilter.isDefined && addressFilter.get.length > 0)
      assertTrue("Wrong log address", addressFilter.get.contains(logJson.get("address").asText()))
    val jsonTopics = logJson.get("topics")
    val topics: util.ArrayList[String] = new util.ArrayList[String]()
    for (i <- 0 until jsonTopics.size())
      topics.add(jsonTopics.get(i).asText())
    assertTrue(topics.toArray(new Array[String](0)).zip(transactionLog.topics).forall({ case (sub, topic) => sub.isEmpty || sub.contains(topic.toString) }))

    assertEquals("Wrong log data", Numeric.prependHexPrefix(BytesUtils.toHexString(transactionLog.data)), logJson.get("data").asText())
    assertEquals("Wrong log logIndex", logIndex, logJson.get("logIndex").asText())
    assertEquals("Wrong log blockHash", Numeric.prependHexPrefix(BytesUtils.toHexString(transactionReceipt.blockHash)), logJson.get("blockHash").asText())
    assertEquals("Wrong log blockNumber", Numeric.toHexStringWithPrefix(BigInteger.valueOf(transactionReceipt.blockNumber)), logJson.get("blockNumber").asText())
    assertEquals("Wrong log transactionHash", Numeric.prependHexPrefix(BytesUtils.toHexString(transactionReceipt.transactionHash)), logJson.get("transactionHash").asText())
    assertEquals("Wrong log transactionIndex", Numeric.toHexStringWithPrefix(BigInteger.valueOf(transactionReceipt.transactionIndex)), logJson.get("transactionIndex").asText())
    assertEquals("Wrong log removed property", removed, logJson.get("removed").asBoolean())
  }

  private def checkSyncStartStatus(wsResponse: JsonNode, expectedSyncStatus: SyncStatus): Unit = {
    assertTrue("Missing field subscription in response.", wsResponse.has("subscription"))
    assertTrue("Missing field result in response.", wsResponse.has("result"))

    val result = wsResponse.get("result")
    assertTrue("Missing field syncing in result.", result.has("syncing"))
    assertEquals("Wrong syncing status", true, result.get("syncing").asBoolean())
    assertTrue("Missing field status in result.", result.has("status"))

    val status = result.get("status")
    assertTrue("Missing field currentBlock in status.", status.has("currentBlock"))
    assertEquals("Wrong currentBlock in status", Numeric.toHexStringWithPrefix(expectedSyncStatus.currentBlock), status.get("currentBlock").asText())
    assertTrue("Missing field highestBlock in status.", status.has("highestBlock"))
    assertEquals("Wrong highestBlock in status", Numeric.toHexStringWithPrefix(expectedSyncStatus.highestBlock), status.get("highestBlock").asText())
    assertTrue("Missing field startingBlock in status.", status.has("startingBlock"))
    assertEquals("Wrong startingBlock in status", Numeric.toHexStringWithPrefix(expectedSyncStatus.startingBlock), status.get("startingBlock").asText())
  }

  private def checkSyncStopStatus(wsResponse: JsonNode): Unit = {
    assertTrue("Missing field subscription in response.", wsResponse.has("subscription"))
    assertTrue("Missing field result in response.", wsResponse.has("result"))

    val result = wsResponse.get("result")
    assertTrue("Missing field syncing in result.", result.has("syncing"))
    assertEquals("Wrong syncing status", false, result.get("syncing").asBoolean())
  }

  private def checkWsEventStaticFields(wsResponse: JsonNode): Unit = {
    assertTrue("Missing field jsonrpc.", wsResponse.has("jsonrpc"))
    assertTrue("Missing field method.", wsResponse.has("method"))
    assertTrue("Missing field params.", wsResponse.has("params"))
    assertTrue("Missing field result in params.", wsResponse.get("params").has("result"))
    assertTrue("Missing field subscription in params.", wsResponse.get("params").has("subscription"))

    assertEquals("Wrong jsonrpc version", "2.0", wsResponse.get("jsonrpc").asText())
    assertEquals("Wrong method", "eth_subscription", wsResponse.get("method").asText())
  }

  def publishNewBlockEvent(block: AccountBlock): Unit = {
    actorSystem.eventStream.publish(SemanticallySuccessfulModifier[sparkz.core.PersistentNodeViewModifier](block))
  }

  def publishNewTransactionEvent(tx: EthereumTransaction): Unit = {
    actorSystem.eventStream.publish(SuccessfulTransaction[EthereumTransaction](tx))
  }

  def publishNewExecTransactionEvent(txs: Seq[EthereumTransaction]): Unit = {
    actorSystem.eventStream.publish(NewExecTransactionsEvent(txs.asInstanceOf[Seq[SidechainTypes#SCAT]]))
  }

  def publishNewSyncStartEVent(syncStatus: SyncStatus): Unit = {
    actorSystem.eventStream.publish(NotifySyncStart(syncStatus))
  }

  def publishNewSyncStopEvent(): Unit = {
    actorSystem.eventStream.publish(NotifySyncStop())
  }

  def publishNewSyncUpdateEvent(syncStatus: SyncStatus): Unit = {
    actorSystem.eventStream.publish(NotifySyncUpdate(syncStatus))
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