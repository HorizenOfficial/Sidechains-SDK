package com.horizen.websocket.server

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.SidechainMemoryPool
import com.horizen.api.http.SidechainApiMockConfiguration
import com.horizen.transaction.RegularTransaction
import com.horizen.utils.CountDownLatchController
import org.glassfish.tyrus.client.ClientManager
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{After, Assert, Test}
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

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-wwebsocket-server")
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

    assertTrue(checkStaticResponseFields(json, 3, -1, -1))

    assertTrue(json.has("errorCode"))
    assertEquals(5, json.get("errorCode").asInt())

    assertTrue(json.has("responsePayload"))
    assertEquals("WebSocket message error!", json.get("responsePayload").asText())


    //Verify that processError of WebSocketServerEndpoint is called with wrong message structure
    val badMsgTypeRequest = mapper.createObjectNode()
      .put("msgType", 2)
      .put("requestId", 0)
      .put("requestType", 5)
      .put("requestPayload", "{}")

    countDownController.reset(1)
    session.getBasicRemote.sendText(badMsgTypeRequest.toString)

    assertTrue("No message received.", countDownController.await(3000))

    json = mapper.readTree(endpoint.receivedMessage.get(1))
    assertTrue(checkStaticResponseFields(json, 3, 0, 5))

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
      .put("msgType", 1)
      .put("requestId", 0)
      .put("requestType", 5)
      .put("requestPayload", "{}")
    session.getBasicRemote.sendText(rawMempoolRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    // Check response
    val json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, 2, 0, 5))

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
      .put("msgType", 1)
      .put("requestId", 0)
      .put("requestType", 4)

    // Set the first mempool Tx id to look for
    rawMempoolRequest.putObject("requestPayload")
      .putArray("hash")
      .add(mempoolTxs.head.id())

    session.getBasicRemote.sendText(rawMempoolRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    val json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, 2, 0, 4))

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
      .put("msgType", 1)
      .put("requestId", 0)
      .put("requestType", 0)
    blockByHashtRequest.putObject("requestPayload").put("hash", "21438dfafec6d70317574cc3307bedf801e3f9137835ae8b36d12653c4d26e95")

    session.getBasicRemote.sendText(blockByHashtRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    var json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, 2, 0, 0))

    assertTrue(json.has("responsePayload"))
    var responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("block"))
    assertTrue(responsePayload.has("hash"))
    assertTrue(responsePayload.has("height"))

    assertEquals("21438dfafec6d70317574cc3307bedf801e3f9137835ae8b36d12653c4d26e95", responsePayload.get("hash").asText())

    // Get block by height
    val blockByHeightRequest = mapper.createObjectNode()
      .put("msgType", 1)
      .put("requestId", 0)
      .put("requestType", 0)
    blockByHeightRequest.putObject("requestPayload").put("height", 100)

    countDownController.reset(1)
    session.getBasicRemote.sendText(blockByHeightRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    json = mapper.readTree(endpoint.receivedMessage.get(1))

    assertTrue(checkStaticResponseFields(json, 2, 0, 0))

    assertTrue(json.has("responsePayload"))
    responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("block"))
    assertTrue(responsePayload.has("hash"))
    assertTrue(responsePayload.has("height"))

    session.close()
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
      .put("msgType", 1)
      .put("requestId", 0)
      .put("requestType", 2)
    newBlockHashesRequest.putObject("requestPayload").putArray("locatorHashes").add("some_bock_hash")
    newBlockHashesRequest.findParent("locatorHashes").put("limit", 5)

    session.getBasicRemote.sendText(newBlockHashesRequest.toString)
    assertTrue("No message received.", countDownController.await(3000))

    var json = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(json, 2, 0, 2))

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

    assertTrue(checkStaticResponseFields(json, 3, 0, 2))
    assertTrue(json.has("errorCode"))
    assertEquals(4, json.get("errorCode").asInt())
    assertTrue(json.has("responsePayload"))
    assertEquals(json.get("responsePayload").asText(), "Invalid limit size! Max limit is 50")

    session.close()
  }

  @Test
  def eventsTest(): Unit = {
    // Test the websocket server events

    //Connect to the websocket server with some request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val countDownController: CountDownLatchController = new CountDownLatchController(1)
    val endpoint = new WsEndpoint(countDownController)
    val session: Session = startSession(client, cec, endpoint)


    //Check SemanticallySuccessfulModifier event
    countDownController.reset(1)
    publishNewTipEvent()
    assertTrue("No event message received.", countDownController.await(5000))
    val tipJson = mapper.readTree(endpoint.receivedMessage.get(0))

    assertTrue(checkStaticResponseFields(tipJson, 0, -1, 0))

    assertTrue(tipJson.has("eventPayload"))
    var eventPayload = tipJson.get("eventPayload")
    assertTrue(eventPayload.has("height"))
    assertTrue(eventPayload.has("hash"))
    assertTrue(eventPayload.has("block"))

    //Check ChangedMempool event
    countDownController.reset(1)
    publishMempoolEvent()
    assertTrue("No event message received.", countDownController.await(5000))
    val mempoolJson = mapper.readTree(endpoint.receivedMessage.get(1))

    assertTrue(checkStaticResponseFields(mempoolJson, 0, -1, 2))

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
      .put("msgType", 1)
      .put("requestId", 0)
      .put("requestType", 0)
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
      .put("msgType", 1)
      .put("requestId", 1)
      .put("requestType", 5)
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

    assertTrue(checkStaticResponseFields(request1, 2, 0, 0))
    assertTrue(checkStaticResponseFields(request2, 2, 1, 5))

    val event1Of1 = mapper.readTree(endpoint.receivedMessage.get(1))
    val event1Of2 = mapper.readTree(endpoint2.receivedMessage.get(1))

    assertTrue(checkStaticResponseFields(event1Of1, 0, -1, 0))
    assertTrue(checkStaticResponseFields(event1Of2, 0, -1, 0))

    val event2Of1 = mapper.readTree(endpoint.receivedMessage.get(2))
    val event2Of2 = mapper.readTree(endpoint2.receivedMessage.get(2))

    assertTrue(checkStaticResponseFields(event2Of1, 0, -1, 2))
    assertTrue(checkStaticResponseFields(event2Of2, 0, -1, 2))

    // Disconnect client 2
    session2.close()

    // Resend event only on client 1
    countDownController.reset(2)
    publishAllEvents()
    assertTrue("No event messages received.", countDownController.await(3000))

    assertEquals(3, endpoint2.receivedMessage.size())
    assertEquals(5, endpoint.receivedMessage.size())

    val event4Of1 = mapper.readTree(endpoint.receivedMessage.get(3))
    assertTrue(checkStaticResponseFields(event4Of1, 0, -1, 0))
    val event5Of1 = mapper.readTree(endpoint.receivedMessage.get(4))
    assertTrue(checkStaticResponseFields(event5Of1, 0, -1, 2))

    // Disconnect client 1
    session.close()
  }

  def publishAllEvents(): Unit = {
    publishNewTipEvent()
    publishMempoolEvent()
  }

  def publishNewTipEvent(): Unit = {
    actorSystem.eventStream.publish(SemanticallySuccessfulModifier[scorex.core.PersistentNodeViewModifier](mock[scorex.core.PersistentNodeViewModifier]))
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
