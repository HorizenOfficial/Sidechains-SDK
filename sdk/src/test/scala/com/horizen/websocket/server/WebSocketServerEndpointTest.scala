package com.horizen.websocket.server

import java.net.URI

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.{GetDataFromCurrentSidechainNodeView}
import com.horizen.api.http.{SidechainApiMockConfiguration, SidechainNodeViewUtilMocks}
import javax.websocket.{ClientEndpointConfig, Endpoint, EndpointConfig, MessageHandler, Session}
import org.glassfish.tyrus.client.ClientManager
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar


import scala.concurrent.{ExecutionContext, Promise}

class WebSocketServerEndpointTest extends JUnitSuite with MockitoSugar{
  private var server: ActorRef = _
  private var mapper : ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
  implicit val ec:ExecutionContext = ExecutionContext.Implicits.global

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-wwebsocket-server")
  val mockedSidechainNodeViewHolder = TestProbe()
  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()
  val utilMocks = new SidechainNodeViewUtilMocks()

  mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetDataFromCurrentSidechainNodeView(f) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply())
            sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

  @Before
  def setUp(): Unit = {
    // start server on default port
    server = WebSocketServerRef(mockedSidechainNodeViewHolderRef, 0)
  }


  @Test
  def badRequestMessage(): Unit = {
    //Verify that processError of WebSocketServerEndpoint is called with wrong message structure
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val endpoint = new WsEndpoint
    var session: Session = client.connectToServer(endpoint, cec, new URI("ws://localhost:8025/"))

    val wrongRequest = mapper.createObjectNode()
        .put("wrong_key", 1)

    session.getBasicRemote.sendText(wrongRequest.toString)
    Thread.sleep(2000)

    var json = mapper.readTree(endpoint.receivedMessage)

    assertTrue(json.has("msgType"))
    assertEquals(3,json.get("msgType").asInt())

    assertTrue(json.has("requestId"))
    assertEquals(-1,json.get("requestId").asInt())

    assertTrue(json.has("answerType"))
    assertEquals(-1,json.get("answerType").asInt())

    assertTrue(json.has("errorCode"))
    assertEquals(5,json.get("errorCode").asInt())

    assertTrue(json.has("responsePayload"))
    assertEquals("WebSocket message error!",json.get("responsePayload").asText())


    //Verify that processError of WebSocketServerEndpoint is called with wrong message structure
    val badMsgTypeRequest = mapper.createObjectNode()
      .put("msgType", 2)
      .put("requestId",0)
      .put("requestType", 5)
      .put("requestPayload", "{}")
    session.getBasicRemote.sendText(badMsgTypeRequest.toString)
    Thread.sleep(2000)

    json = mapper.readTree(endpoint.receivedMessage)

    assertTrue(json.has("msgType"))
    assertEquals(3,json.get("msgType").asInt())

    assertTrue(json.has("requestId"))
    assertEquals(0,json.get("requestId").asInt())

    assertTrue(json.has("answerType"))
    assertEquals(5,json.get("answerType").asInt())

    assertTrue(json.has("errorCode"))
    assertEquals(5,json.get("errorCode").asInt())

    assertTrue(json.has("responsePayload"))
    assertEquals("WebSocket message error!",json.get("responsePayload").asText())

    session.close()
  }

  @Test
  def getRawMempoolTest(): Unit = {
    // Test the getRawMempool request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val endpoint = new WsEndpoint
    var session: Session = client.connectToServer(endpoint, cec, new URI("ws://localhost:8025/"))

    val rawMempoolRequest = mapper.createObjectNode()
      .put("msgType", 1)
      .put("requestId",0)
      .put("requestType", 5)
      .put("requestPayload", "{}")
    session.getBasicRemote.sendText(rawMempoolRequest.toString)
    Thread.sleep(2000)
    val json = mapper.readTree(endpoint.receivedMessage)

    assertTrue(json.has("msgType"))
    assertEquals(2,json.get("msgType").asInt())

    assertTrue(json.has("requestId"))
    assertEquals(0,json.get("requestId").asInt())

    assertTrue(json.has("answerType"))
    assertEquals(5,json.get("answerType").asInt())

    assertTrue(json.has("responsePayload"))

    val responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("size"))
    assertEquals(2, responsePayload.get("size").asInt())
    assertTrue(responsePayload.has("transactions"))
    assertTrue(responsePayload.get("transactions").isArray)
    var nTx = 0
    responsePayload.get("transactions").forEach(tx => {
      nTx += 1
      assertEquals( "9e8d287524000a128f3d936ffdc1df1f2a54fa85a2800bc7b681e934d251efac", tx.asText())
    })
    assertEquals(2, nTx)
    session.close()

  }

  @Test
  def getMempoolTxsTest(): Unit = {
    // Test the getMempoolTxs request
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    val endpoint = new WsEndpoint
    var session: Session = client.connectToServer(endpoint, cec, new URI("ws://localhost:8025/"))

    val rawMempoolRequest = mapper.createObjectNode()
      .put("msgType", 1)
      .put("requestId",0)
      .put("requestType", 4)
    rawMempoolRequest.putObject("requestPayload").putArray("hash").add("9e8d287524000a128f3d936ffdc1df1f2a54fa85a2800bc7b681e934d251efac")

    session.getBasicRemote.sendText(rawMempoolRequest.toString)
    Thread.sleep(3000)

    val json = mapper.readTree(endpoint.receivedMessage)
    assertTrue(json.has("msgType"))
    assertEquals(2, json.get("msgType").asInt())

    assertTrue(json.has("requestId"))
    assertEquals(0, json.get("requestId").asInt())

    assertTrue(json.has("answerType"))
    assertEquals(4, json.get("answerType").asInt())

    assertTrue(json.has("responsePayload"))

    val responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("transactions"))
    assertTrue(responsePayload.get("transactions").isArray)

    var counter: Int = 0
    responsePayload.get("transactions").forEach(tx => {
      counter += 1
      assertEquals("9e8d287524000a128f3d936ffdc1df1f2a54fa85a2800bc7b681e934d251efac", tx.get("id").asText())
    })
    assertEquals(1, counter)

    session.close()

  }

}

private class WsEndpoint extends Endpoint {
  var receivedMessage: String = ""
  override def onOpen(session: Session, config: EndpointConfig): Unit = {
    session.addMessageHandler(new MessageHandler.Whole[String]() {
      override def onMessage(message: String): Unit = {
        receivedMessage = message
      }
    })
  }
}
