package com.horizen.websocket.server

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.SidechainMemoryPool
import com.horizen.api.http.SidechainApiMockConfiguration
import com.horizen.block.SidechainBlock
import com.horizen.transaction.RegularTransaction
import com.horizen.utils.CountDownLatchController
import org.glassfish.tyrus.client.ClientManager
import org.junit.Assert.{assertArrayEquals, assertEquals, assertFalse, assertTrue}
import org.junit.{After, Assert, Test}
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedMempool, SemanticallySuccessfulModifier}

import java.net.URI
import java.util
import javax.websocket._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class WebSocketServerEndpointTest extends JUnitSuite with MockitoSugar with BeforeAndAfterAll {
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-websocket-server")
  val mockedSidechainNodeViewHolder: TestProbe = TestProbe()
  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()
  val utilMocks = new NodeViewHolderUtilMocks()
  val mempoolTxs: Seq[RegularTransaction] = utilMocks.transactionList.asScala

  mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetDataFromCurrentView(f) =>
          sender ! f(utilMocks.getNodeView(sidechainApiMockConfiguration))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref
  private val server: ActorRef = WebSocketServerRef(mockedSidechainNodeViewHolderRef, 9025)

  @After
  def after(): Unit = {
    actorSystem.stop(server)
  }

  def startSession(client: ClientManager, cec: ClientEndpointConfig, endpoint: WsEndpoint): Session = {
    var attempts = 30
    while(attempts > 0) {
      Try {
        client.connectToServer(endpoint, cec, new URI("ws://localhost:9025/"))
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
  def badRequestMessage(): Unit = {
    //Verify that processError of WebSocketServerEndpoint is called with wrong message structure
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    val wrongRequest = mapper.createObjectNode()
      .put("wrong_key", 1)

    session.getBasicRemote.sendText(wrongRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    var json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, ERROR_MESSAGE.code, -1, -1))

    assertTrue(json.has("errorCode"))
    assertEquals(5, json.get("errorCode").asInt())

    assertTrue(json.has("responsePayload"))
    assertEquals("WebSocket message error!", json.get("responsePayload").asText())


    //Verify that processError of WebSocketServerEndpoint is called with wrong message structure
    val badMsgTypeRequest = mapper.createObjectNode()
      .put("msgType", RESPONSE_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_RAW_MEMPOOL.code)
      .put("requestPayload", "{}")

    countDownController.reset(1)
    session.getBasicRemote.sendText(badMsgTypeRequest.toString)

    assertTrue("No message received.", countDownController.await(3000))

    json = mapper.readTree(endpoint.receivedMessage.get(1))
    assertTrue(checkStaticResponseFields(json, ERROR_MESSAGE.code, 0, GET_RAW_MEMPOOL.code))

    assertTrue(json.has("errorCode"))
    assertEquals(5, json.get("errorCode").asInt())

    assertTrue(json.has("responsePayload"))
    assertEquals("WebSocket message error!", json.get("responsePayload").asText())

    session.close()
  }

  @Test
  def getRawMempoolTest(): Unit = {
    // Test the getRawMempool request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    // Get raw mempool
    val rawMempoolRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_RAW_MEMPOOL.code)
      .put("requestPayload", "{}")
    session.getBasicRemote.sendText(rawMempoolRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    // Check response
    val json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, RESPONSE_MESSAGE.code, 0, GET_RAW_MEMPOOL.code))

    assertTrue(json.has("responsePayload"))
    val responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("size"))
    assertEquals(mempoolTxs.size, responsePayload.get("size").asInt())
    assertTrue(responsePayload.has("transactions"))
    assertTrue(responsePayload.get("transactions").isArray)

    val resTxs = responsePayload.get("transactions")
    for(i <- mempoolTxs.indices) {
      assertEquals(s"Different transaction id returned by server for tx idx = $i.",
        mempoolTxs(i).id(), resTxs.get(i).asText())
    }

    session.close()
  }

  @Test
  def getMempoolTxsTest(): Unit = {
    // Test the getMempoolTxs request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    val rawMempoolRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_MEMPOOL_TXS.code)

    // Set the first mempool Tx id to look for
    rawMempoolRequest.putObject("requestPayload")
      .putArray("hash")
      .add(mempoolTxs.head.id())

    session.getBasicRemote.sendText(rawMempoolRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    val json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, RESPONSE_MESSAGE.code, 0, GET_MEMPOOL_TXS.code))

    assertTrue(json.has("responsePayload"))
    val responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("transactions"))

    val resTxs = responsePayload.get("transactions")
    assertTrue(resTxs.isArray)
    assertEquals("Different txs size found", 1, resTxs.size())

    assertEquals(s"Different transaction id returned by server.",
      mempoolTxs.head.id(), resTxs.get(0).get("id").asText())

    session.close()
  }

  @Test
  def getSingleBlockTest(): Unit = {
    // Test the getSingleBlock request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    // Get block by hash
    val blockByHashtRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_SINGLE_BLOCK_REQUEST_TYPE.code)
    blockByHashtRequest.putObject("requestPayload").put("hash", utilMocks.genesisBlock.id)

    session.getBasicRemote.sendText(blockByHashtRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    var json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, RESPONSE_MESSAGE.code, 0, GET_SINGLE_BLOCK_REQUEST_TYPE.code))

    assertTrue(json.has("responsePayload"))
    var responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("block"))
    assertTrue(responsePayload.has("hash"))
    assertTrue(responsePayload.has("height"))
    assertFalse(responsePayload.has("feePayments"))

    assertEquals(utilMocks.genesisBlock.id, responsePayload.get("hash").asText())

    // Get block by height
    val blockByHeightRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_SINGLE_BLOCK_REQUEST_TYPE.code)
    blockByHeightRequest.putObject("requestPayload").put("height", 100)

    countDownController.reset(1)
    session.getBasicRemote.sendText(blockByHeightRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    json = mapper.readTree(endpoint.receivedMessage.get(1))

    assertTrue(checkStaticResponseFields(json, RESPONSE_MESSAGE.code, 0, GET_SINGLE_BLOCK_REQUEST_TYPE.code))

    assertTrue(json.has("responsePayload"))
    responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("block"))
    assertTrue(responsePayload.has("hash"))
    assertTrue(responsePayload.has("height"))
    assertFalse(responsePayload.has("feePayments"))

    session.close()
  }

  @Test
  def getSingleBlockWithFeePayments(): Unit = {
    // Test the getSingleBlock request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    // Get block by hash
    val blockByHashtRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_SINGLE_BLOCK_REQUEST_TYPE.code)
    blockByHashtRequest.putObject("requestPayload").put("hash", utilMocks.feePaymentsBlockId)

    session.getBasicRemote.sendText(blockByHashtRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    var json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, RESPONSE_MESSAGE.code, 0, GET_SINGLE_BLOCK_REQUEST_TYPE.code))

    assertTrue(json.has("responsePayload"))
    val responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("block"))
    assertTrue(responsePayload.has("hash"))
    assertTrue(responsePayload.has("height"))
    assertTrue(responsePayload.has("feePayments"))

    val feePaymentsJson = responsePayload.get("feePayments")
    assertTrue(feePaymentsJson.has("unlockers"))
    assertTrue(feePaymentsJson.get("unlockers").isArray)
    assertEquals(0, feePaymentsJson.get("unlockers").size())
    assertTrue(feePaymentsJson.has("newBoxes"))
    assertTrue(feePaymentsJson.get("newBoxes").isArray)
    assertEquals(utilMocks.feePaymentsInfo.transaction.newBoxes().size(), feePaymentsJson.get("newBoxes").size())
  }

  @Test
  def getNewBlockHashes(): Unit = {
    // Test the getNewBlockHashes request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)

    // Get block by hash
    val newBlockHashesRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_NEW_BLOCK_HASHES_REQUEST_TYPE.code)
    newBlockHashesRequest.putObject("requestPayload").putArray("locatorHashes").add("some_bock_hash")
    newBlockHashesRequest.findParent("locatorHashes").put("limit", 5)

    session.getBasicRemote.sendText(newBlockHashesRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    var json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, RESPONSE_MESSAGE.code, 0, GET_NEW_BLOCK_HASHES_REQUEST_TYPE.code))

    assertTrue(json.has("responsePayload"))
    val responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("height"))
    assertTrue(responsePayload.has("hashes"))
    assertTrue(responsePayload.get("hashes").isArray)

    // Test with limit greater than max
    newBlockHashesRequest.findParent("locatorHashes").put("limit", 100)
    countDownController.reset(1)
    session.getBasicRemote.sendText(newBlockHashesRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))
    json = mapper.readTree(endpoint.receivedMessage.get(1))

    assertTrue(checkStaticResponseFields(json, ERROR_MESSAGE.code, 0, GET_NEW_BLOCK_HASHES_REQUEST_TYPE.code))
    assertTrue(json.has("errorCode"))
    assertEquals(4, json.get("errorCode").asInt())
    assertTrue(json.has("responsePayload"))
    assertEquals(json.get("responsePayload").asText(), "Invalid limit size! Max limit is 50")

    session.close()
  }

  @Test
  def eventsTest(): Unit = {
    // Test the websocket server events

    // Connect to the websocket server with some request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)


    // Test 1: Check SemanticallySuccessfulModifier event
    countDownController.reset(1)
    publishNewTipEvent()
    assertTrue("No event message received.", countDownController.await(5000))
    val tipJson = mapper.readTree(endpoint.receivedMessage.get(endpoint.receivedMessage.size() - 1))

    assertTrue(checkStaticResponseFields(tipJson, EVENT_MESSAGE.code, -1, 0))

    assertTrue(tipJson.has("eventPayload"))
    var eventPayload = tipJson.get("eventPayload")
    assertTrue(eventPayload.has("height"))
    assertTrue(eventPayload.has("hash"))
    assertTrue(eventPayload.has("block"))
    assertFalse(eventPayload.has("feePayments"))


    // Test 2: Check SemanticallySuccessfulModifier event with FeePayments
    countDownController.reset(1)
    publishNewTipEventWithFeePayments()
    assertTrue("No event message received.", countDownController.await(5000))
    val tipWithFeePaymentsJson = mapper.readTree(endpoint.receivedMessage.get(endpoint.receivedMessage.size() - 1))

    assertTrue(checkStaticResponseFields(tipWithFeePaymentsJson, EVENT_MESSAGE.code, -1, 0))

    assertTrue(tipWithFeePaymentsJson.has("eventPayload"))
    eventPayload = tipWithFeePaymentsJson.get("eventPayload")
    assertTrue(eventPayload.has("height"))
    assertTrue(eventPayload.has("hash"))
    assertTrue(eventPayload.has("block"))
    assertTrue(eventPayload.has("feePayments"))

    val feePaymentsJson = eventPayload.get("feePayments")
    assertTrue(feePaymentsJson.has("unlockers"))
    assertTrue(feePaymentsJson.get("unlockers").isArray)
    assertEquals(0, feePaymentsJson.get("unlockers").size())
    assertTrue(feePaymentsJson.has("newBoxes"))
    assertTrue(feePaymentsJson.get("newBoxes").isArray)
    assertEquals(utilMocks.feePaymentsInfo.transaction.newBoxes().size(), feePaymentsJson.get("newBoxes").size())


    // Test 3: Check ChangedMempool event
    countDownController.reset(1)
    publishMempoolEvent()
    assertTrue("No event message received.", countDownController.await(5000))
    val mempoolJson = mapper.readTree(endpoint.receivedMessage.get(endpoint.receivedMessage.size() - 1))

    assertTrue(checkStaticResponseFields(mempoolJson, EVENT_MESSAGE.code, -1, 2))

    assertTrue(mempoolJson.has("eventPayload"))
    eventPayload = mempoolJson.get("eventPayload")
    assertTrue(eventPayload.has("size"))
    assertEquals(2, eventPayload.get("size").asInt())
    assertTrue(eventPayload.has("transactions"))

    val resTxs = eventPayload.get("transactions")
    assertTrue(resTxs.isArray)

    for(i <- mempoolTxs.indices) {
      assertEquals(s"Different transaction id returned by server for tx idx = $i.",
        mempoolTxs(i).id(), resTxs.get(i).asText())
    }

    session.close()
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

    // Get block by hash
    val blockByHashRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 0)
      .put("requestType", GET_SINGLE_BLOCK_REQUEST_TYPE.code)
    blockByHashRequest.putObject("requestPayload").put("hash", "some_block_hash")

    session.getBasicRemote.sendText(blockByHashRequest.toString)
    assertTrue("No message received.", countDownController.await(5000))

    // Add client 2
    val cec2 = ClientEndpointConfig.Builder.create.build
    val client2 = ClientManager.createClient

    val countDownController2: CountDownLatchController = new CountDownLatchController(1)
    val endpoint2 = new WsEndpoint(countDownController2)
    val session2: Session = startSession(client2, cec2, endpoint2)

    // Get raw mempool
    val rawMempoolRequest = mapper.createObjectNode()
      .put("msgType", REQUEST_MESSAGE.code)
      .put("requestId", 1)
      .put("requestType", GET_RAW_MEMPOOL.code)
      .put("requestPayload", "{}")

    session2.getBasicRemote.sendText(rawMempoolRequest.toString)
    assertTrue("No message received.", countDownController2.await(3000))

    // Send events
    countDownController.reset(2)
    countDownController2.reset(2)
    publishAllEvents()

    assertTrue("No event messages received.", countDownController.await(3000))
    assertTrue("No event messages received.", countDownController2.await(3000))

    //Both client1 and client2 have 3 message (1 request and 2 events)
    assertEquals(3, endpoint.receivedMessage.size())
    assertEquals(3, endpoint2.receivedMessage.size())

    val request1 = mapper.readTree(endpoint.receivedMessage.get(0))
    val request2 = mapper.readTree(endpoint2.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(request1, RESPONSE_MESSAGE.code, 0, 0))
    assertTrue(checkStaticResponseFields(request2, RESPONSE_MESSAGE.code, 1, 5))

    val event1Of1 = mapper.readTree(endpoint.receivedMessage.get(1))
    val event1Of2 = mapper.readTree(endpoint2.receivedMessage.get(1))

    assertTrue(checkStaticResponseFields(event1Of1, EVENT_MESSAGE.code, -1, 0))
    assertTrue(checkStaticResponseFields(event1Of2, EVENT_MESSAGE.code, -1, 0))

    val event2Of1 = mapper.readTree(endpoint.receivedMessage.get(2))
    val event2Of2 = mapper.readTree(endpoint2.receivedMessage.get(2))

    assertTrue(checkStaticResponseFields(event2Of1, EVENT_MESSAGE.code, -1, 2))
    assertTrue(checkStaticResponseFields(event2Of2, EVENT_MESSAGE.code, -1, 2))

    // Disconnect client 2
    session2.close()

    // Resend event only on client 1
    countDownController.reset(2)
    publishAllEvents()
    assertTrue("No event messages received.", countDownController.await(3000))

    assertEquals(3, endpoint2.receivedMessage.size())
    assertEquals(5, endpoint.receivedMessage.size())

    val event4Of1 = mapper.readTree(endpoint.receivedMessage.get(3))
    assertTrue(checkStaticResponseFields(event4Of1, EVENT_MESSAGE.code, -1, 0))
    val event5Of1 = mapper.readTree(endpoint.receivedMessage.get(4))
    assertTrue(checkStaticResponseFields(event5Of1, EVENT_MESSAGE.code, -1, 2))

    // Disconnect client 1
    session.close()
  }

  def publishAllEvents(): Unit = {
    publishNewTipEvent()
    publishMempoolEvent()
  }

  def publishNewTipEvent(): Unit = {
    val block: SidechainBlock = mock[SidechainBlock]
    Mockito.when(block.id).thenReturn(utilMocks.genesisBlock.id)
    actorSystem.eventStream.publish(SemanticallySuccessfulModifier[scorex.core.PersistentNodeViewModifier](block))
  }

  def publishNewTipEventWithFeePayments(): Unit = {
    val block: SidechainBlock = mock[SidechainBlock]
    Mockito.when(block.id).thenReturn(utilMocks.feePaymentsBlockId)
    actorSystem.eventStream.publish(SemanticallySuccessfulModifier[scorex.core.PersistentNodeViewModifier](block))
  }

  def publishMempoolEvent(): Unit = {
    actorSystem.eventStream.publish(ChangedMempool[SidechainMemoryPool](mock[SidechainMemoryPool]))
  }

  private def checkStaticResponseFields(json: JsonNode, msgType: Int, requestId: Int, answerType: Int): Boolean = {
    if (requestId == -1)
      json.has("msgType") &&
        msgType == json.get("msgType").asInt() &&
        json.has("answerType") &&
        answerType == json.get("answerType").asInt()
    else
      json.has("msgType") &&
        msgType == json.get("msgType").asInt() &&
        json.has("requestId") &&
        requestId == json.get("requestId").asInt() &&
        json.has("answerType") &&
        answerType == json.get("answerType").asInt()
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
