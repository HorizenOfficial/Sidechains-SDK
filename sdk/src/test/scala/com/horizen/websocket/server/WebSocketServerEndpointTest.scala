package com.horizen.websocket.server

import java.net.URI

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, GetDataFromCurrentSidechainNodeView, LocallyGeneratedSecret}
import com.horizen.WebSocketSettings
import com.horizen.api.http.{SidechainApiMockConfiguration, SidechainNodeViewUtilMocks}
import com.horizen.node.SidechainNodeView
import com.horizen.websocket.client.{BlockResponsePayload, CommunicationClient, DefaultWebSocketReconnectionHandler, DisconnectionCode, GetBlockByHashRequestPayload, ResponsePayload, WebSocketConnectorImpl, WebSocketMessageHandler, WebSocketReconnectionHandler}
import javax.websocket.{ClientEndpointConfig, Endpoint, EndpointConfig, MessageHandler, Session}
import org.glassfish.tyrus.client.ClientManager
import org.glassfish.tyrus.server.Server
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration.{FiniteDuration, _}
import com.horizen.websocket.server.SidechainNodeChannelImpl
import javax.websocket.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success, Try}

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
    //server = new WebSocketServerImpl(8025, classOf[WebSocketServerEndpoint])
    server = WebSocketServerRef(mockedSidechainNodeViewHolderRef, 0)

  }


  @Test
  def badRequestMessage(): Unit = {

    //Verify that processError of WebSocketServerEndpoint is called with wrong message structure
    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    var messageReceived = ""
    var session: Session = client.connectToServer(new Endpoint() {

      override def onOpen(session: Session, config: EndpointConfig): Unit = {
          session.addMessageHandler(new MessageHandler.Whole[String]() {

            override def onMessage(message: String): Unit = {
              messageReceived = message
            }

          })
      }
    }, cec, new URI("ws://localhost:8025/"))

    val wrongRequest = mapper.createObjectNode()
        .put("wrong_key", 1)

    session.getBasicRemote.sendText(wrongRequest.toString)
    Thread.sleep(2000)

    var json = mapper.readTree(messageReceived)

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

    json = mapper.readTree(messageReceived)

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

    val cec = ClientEndpointConfig.Builder.create.build
    val client = ClientManager.createClient

    var messageReceived = ""
    var session: Session = client.connectToServer(new Endpoint() {

      override def onOpen(session: Session, config: EndpointConfig): Unit = {
        session.addMessageHandler(new MessageHandler.Whole[String]() {

          override def onMessage(message: String): Unit = {
            messageReceived = message
          }

        })
      }
    }, cec, new URI("ws://localhost:8025/"))

    val rawMempoolRequest = mapper.createObjectNode()
      .put("msgType", 1)
      .put("requestId",0)
      .put("requestType", 5)
      .put("requestPayload", "{}")
    session.getBasicRemote.sendText(rawMempoolRequest.toString)
    Thread.sleep(2000)

    val json = mapper.readTree(messageReceived)
    System.out.println(messageReceived)
    assertTrue(json.has("msgType"))
    assertEquals(2,json.get("msgType").asInt())

    assertTrue(json.has("requestId"))
    assertEquals(0,json.get("requestId").asInt())

    assertTrue(json.has("answerType"))
    assertEquals(5,json.get("answerType").asInt())

    assertTrue(json.has("responsePayload"))

    val responsePayload = json.get("responsePayload")
    assertTrue(responsePayload.has("size"))
    assertEquals(responsePayload.get("size").asInt(),2)
    assertTrue(responsePayload.has("transactions"))
    assertTrue(responsePayload.get("transactions").isArray)
    var nTx = 0
    responsePayload.get("transactions").forEach(tx => {
      nTx += 1
      assertEquals(tx.asText(), "9e8d287524000a128f3d936ffdc1df1f2a54fa85a2800bc7b681e934d251efac")
    })
    assertEquals(nTx, 2)
    session.close()

  }

}
