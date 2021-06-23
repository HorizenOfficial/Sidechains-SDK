package com.horizen.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.{Before, Test}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import org.junit.Assert._
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.{Failure, Success, Try}

class WebSocketCommunicationClientTest extends JUnitSuite with MockitoSugar {

  // No matter about real information (height, block hashes etc)
  private var mapper : ObjectMapper = null

  private def getServerResponse(message : String) : String = {
    var resp = ""
    try {
      val json = mapper.readTree(message)
      val reqId = Try(json.get("requestId").asText())
      if(reqId.isSuccess){
        val reqtype = json.get("requestType").asText() match {
          case "0" | "1" if reqId.get.length > 0 =>
            resp =
              mapper.createObjectNode()
                .put("msgType", 2)
                .put("requestId", reqId.get)
                .set("responsePayload", mapper.createObjectNode()
                  .put("height", 50)
                  .put("hash", "0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf")
                  .put("block", "the block hex"))
                .toString
          case "2" if reqId.get.length > 0 =>
            resp =
              mapper.createObjectNode()
                .put("msgType", 2)
                .put("requestId", reqId.get)
                .set("responsePayload", mapper.createObjectNode()
                  .put("height", 201)
                  .set("hashes", mapper.createArrayNode()
                    .add("08d34596dd7e137f603d6661d867eb083c0592e8333f838478de2ebd3efd8e5a")
                    .add("0e4d418de14c9aeaba0714bc23626bab1fe12001758dd6d4908029ad588b5861")
                    .add("0e812bd816810df31b1cbfdacec59768e3dc668dbd9186c421bc44730e61ecfa")
                    .add("0e760f0f0e47b5155905e155c26ee5af680bca459d1273cf2ba4eaaad4c1ca7d")
                    .add("001c2bca61711e2f74a001283eb3cb60645d0d42586ccb14879afcd68c2ed2f9")
                    .add("028db24dc6985679cdff9ce4648d110236123729760affd48836d26ca5cca7f4")))
                .toString
          case "5" if reqId.get.length > 0 =>
            resp =
              mapper.createObjectNode()
                .put("msgType", 2)
                .put("requestId", reqId.get)
                .set("responsePayload", mapper.createObjectNode()
                  .set("mempoolTopQualityCert", mapper.createObjectNode()
                    .put("quality", 20L)
                    .put("epoch", 5)
                    .put("certHash", "5df6a3f3603d19384435c9d54feb9dea1f4ea340bf4b60869889fcbf60843f82")
                    .put("rawCertificateHex", "fbffffff9ea21362e472c7c60becaf131209e247f211f3f351248fa231cb0eaf19cfe41f000000001400000000000000d0a064c71b6b54d5de6c2e233aa604bc5d2011ab3b48f41732e56adec4f8b80d0256b289505ef240df5fb278d8479a4f59ddb2a01ea2b71712bcfd345e6fb12026f0aa8ceec75208df838d0506d0f3c49d166f17f1f7f038d87edd32c3f301dd1c297020e6f24a9b528b09eba2db6a96eafe146ee1e8d4634a50134af43f010049d8b466f9c08a2bca3b418b63ce01f64852a4c585adb88fd9a36e803c228f4af1e0d53f0ebb2ea172715e9826008f370420409c73faf98ff199f628702320c490aeb04dab0e464403288c6b2c2f4283bf406c2242da99fe656416ea982600000058d7b03e5d11b1ab9db1b0aabd0c6e21193e5810887267e5e006adc8a507dfe4820a3016c34f606d5b63b09bcf2004a8b896c593a7e9f488a42e1351f496420e3c9771f775534a124a2fb119f795394cb66cc9e07be5bee4ebc71619167f000022dc545f369a5e2ec885539d820c76867d1edecd7a2b92ac91045f7de3d81c6b13cde936a4edc7ebc6fb1d20c3fd5a87af1bccac2a77f0ee0ec4b06d2edbb4b9093d9a48bc7d6e31f8abc246e73e80e876444447dd6913b0a8e1da56c9c90000475dec97c8d427d3406e1f584023969025de0b5c3d45d6da568c4c4ae399e043b3611816a91cffd269c48733745ac0e9912d48f1c007b90d331bbc7808b7d6d5329ca732ee8b40c86f01e580faa4e17eee2d3aff41510897fd82814c35e30000ecb1aa8437b07b205073b249663b1432980f15fb2feb1d4972f873ec55dec259785ae624e6f7a8ac92e448c969adc6a2a381874023b6119ceda76dc0b030482b6072dbc85fc4e1dd4b68ac7d5191f1")
                    .put("fee", 0.005)))
                .toString
          case _ =>
            resp = mapper.createObjectNode()
              .put("msgType", 3)
              .put("requestId", reqId.get)
              .put("errorCode", 2)
              .put("message", "INVALID_COMMAND").toString
        }
      }else{
        resp = mapper.createObjectNode()
          .put("msgType", 3)
          .put("requestId", "")
          .put("errorCode", 5)
          .put("message", "MISSING_REQUESTID").toString
      }


    } catch {
      case ex: Throwable =>
        resp = mapper.createObjectNode()
          .put("msgType", 3)
          .put("requestId", "")
          .put("errorCode", 3)
          .put("message", "INVALID_JSON_FORMAT").toString
    }

    resp
  }

  @Before
  def setUp() : Unit = {
    mapper = new ObjectMapper().registerModule(DefaultScalaModule)
  }

  @Test
  def sendRequestWithoutChannel() : Unit = {

    val webSocketClient = new WebSocketCommunicationClient()

    val mockedChannel = mock[WebSocketChannel]
    val mockedConnector : WebSocketConnector = mock[WebSocketConnector]

    Mockito.when(mockedConnector.isStarted()).thenAnswer(asw => true)
    Mockito.when(mockedConnector.stop()).thenAnswer(asw => Success(Unit))
    Mockito.when(mockedConnector.start()).thenAnswer(asw => Try(mockedChannel))

    val fut = webSocketClient.sendRequest(GET_SINGLE_BLOCK_REQUEST_TYPE, null, null)
    assertTrue(fut.isCompleted)
    assertEquals(classOf[Failure[_]], fut.value.get.getClass)

  }

  @Test
  def sendWellFormedRequest() : Unit = {

    val webSocketClient = new WebSocketCommunicationClient()

    val mockedChannel = mock[WebSocketChannel]
    val mockedConnector : WebSocketConnector = mock[WebSocketConnector]

    Mockito.when(mockedConnector.isStarted()).thenAnswer(asw => true)
    Mockito.when(mockedConnector.stop()).thenAnswer(asw => Success(Unit))
    Mockito.when(mockedConnector.start()).thenAnswer(asw => Try(mockedChannel))

    webSocketClient.setWebSocketChannel(mockedChannel)

    val wellFormedGetBlockByHeightRequestPayload : RequestPayload = GetBlockByHeightRequestPayload(30) // request type -> 0
    val wellFormedGetBlockByHashRequestPayload : RequestPayload = GetBlockByHashRequestPayload("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf") // request type -> 0
    val wellFormedGetNewBlocksRequestPayload : RequestPayload = GetNewBlocksRequestPayload(Seq[String]("hash_1", "hash_2", "hash_3"), 30) // request type -> 2
    val wellFormedGetTopQualityCertificatesRequestPayload : RequestPayload = TopQualityCertificatePayload(("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf")) // request type -> 5

    Mockito.when(mockedChannel.sendMessage(ArgumentMatchers.any())).thenAnswer(
      asw =>
      {
        val req = asw.getArgument(0).asInstanceOf[String]
        // get response from server and send reply to the registered message handler
        webSocketClient.onReceivedMessage(getServerResponse(req))
      }
    )

    try {
      val future_1 : Future[BlockResponsePayload]= webSocketClient.sendRequest[RequestPayload, BlockResponsePayload](GET_SINGLE_BLOCK_REQUEST_TYPE, wellFormedGetBlockByHeightRequestPayload, classOf[BlockResponsePayload])
      val welFormedBlockResponsePayload_1 : BlockResponsePayload = Await.result(future_1, webSocketClient.requestTimeoutDuration())
      assertTrue(future_1.value.get.isSuccess)
      assertEquals(welFormedBlockResponsePayload_1.hash, "0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf")
      assertEquals(welFormedBlockResponsePayload_1.block, "the block hex")
      assertEquals(welFormedBlockResponsePayload_1.height, 50)

      val future_2 : Future[BlockResponsePayload] = webSocketClient.sendRequest[RequestPayload, BlockResponsePayload](GET_SINGLE_BLOCK_REQUEST_TYPE, wellFormedGetBlockByHashRequestPayload, classOf[BlockResponsePayload])
      val welFormedBlockResponsePayload_2 : BlockResponsePayload = Await.result(future_2, webSocketClient.requestTimeoutDuration())
      assertTrue(future_2.value.get.isSuccess)
      assertEquals(welFormedBlockResponsePayload_2.hash, "0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf")
      assertEquals(welFormedBlockResponsePayload_2.block, "the block hex")
      assertEquals(welFormedBlockResponsePayload_2.height, 50)

      val future_3 : Future[NewBlocksResponsePayload] = webSocketClient.sendRequest[RequestPayload, NewBlocksResponsePayload](GET_NEW_BLOCK_HASHES_REQUEST_TYPE, wellFormedGetNewBlocksRequestPayload, classOf[NewBlocksResponsePayload])
      val welFormedNewBlocksResponsePayload : NewBlocksResponsePayload = Await.result(future_3, webSocketClient.requestTimeoutDuration())
      assertTrue(future_3.value.get.isSuccess)
      assertEquals(welFormedNewBlocksResponsePayload.hashes, Seq[String](
         "08d34596dd7e137f603d6661d867eb083c0592e8333f838478de2ebd3efd8e5a",
                "0e4d418de14c9aeaba0714bc23626bab1fe12001758dd6d4908029ad588b5861",
                "0e812bd816810df31b1cbfdacec59768e3dc668dbd9186c421bc44730e61ecfa",
                "0e760f0f0e47b5155905e155c26ee5af680bca459d1273cf2ba4eaaad4c1ca7d",
                "001c2bca61711e2f74a001283eb3cb60645d0d42586ccb14879afcd68c2ed2f9",
                "028db24dc6985679cdff9ce4648d110236123729760affd48836d26ca5cca7f4"
      ))
      assertEquals(welFormedNewBlocksResponsePayload.height, 201)

      val future_4 : Future[TopQualityCertificateResponsePayload] = webSocketClient.sendRequest[RequestPayload, TopQualityCertificateResponsePayload](GET_TOP_QUALITY_CERTIFICATES_TYPE, wellFormedGetTopQualityCertificatesRequestPayload, classOf[TopQualityCertificateResponsePayload])
      val wellFormedTopQualityCertificateResponsePayload : TopQualityCertificateResponsePayload = Await.result(future_4, webSocketClient.requestTimeoutDuration())
      assertTrue(future_4.value.get.isSuccess)
      assertTrue(wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.quality.nonEmpty)
      assertEquals(wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.quality.get, 20L)
      assertTrue(wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.epoch.nonEmpty)
      assertEquals(wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.epoch.get, 5)
      assertTrue(wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.certHash.nonEmpty)
      assertEquals("5df6a3f3603d19384435c9d54feb9dea1f4ea340bf4b60869889fcbf60843f82", wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.certHash.get)
      assertTrue(wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.rawCertificateHex.nonEmpty)
      assertEquals("fbffffff9ea21362e472c7c60becaf131209e247f211f3f351248fa231cb0eaf19cfe41f000000001400000000000000d0a064c71b6b54d5de6c2e233aa604bc5d2011ab3b48f41732e56adec4f8b80d0256b289505ef240df5fb278d8479a4f59ddb2a01ea2b71712bcfd345e6fb12026f0aa8ceec75208df838d0506d0f3c49d166f17f1f7f038d87edd32c3f301dd1c297020e6f24a9b528b09eba2db6a96eafe146ee1e8d4634a50134af43f010049d8b466f9c08a2bca3b418b63ce01f64852a4c585adb88fd9a36e803c228f4af1e0d53f0ebb2ea172715e9826008f370420409c73faf98ff199f628702320c490aeb04dab0e464403288c6b2c2f4283bf406c2242da99fe656416ea982600000058d7b03e5d11b1ab9db1b0aabd0c6e21193e5810887267e5e006adc8a507dfe4820a3016c34f606d5b63b09bcf2004a8b896c593a7e9f488a42e1351f496420e3c9771f775534a124a2fb119f795394cb66cc9e07be5bee4ebc71619167f000022dc545f369a5e2ec885539d820c76867d1edecd7a2b92ac91045f7de3d81c6b13cde936a4edc7ebc6fb1d20c3fd5a87af1bccac2a77f0ee0ec4b06d2edbb4b9093d9a48bc7d6e31f8abc246e73e80e876444447dd6913b0a8e1da56c9c90000475dec97c8d427d3406e1f584023969025de0b5c3d45d6da568c4c4ae399e043b3611816a91cffd269c48733745ac0e9912d48f1c007b90d331bbc7808b7d6d5329ca732ee8b40c86f01e580faa4e17eee2d3aff41510897fd82814c35e30000ecb1aa8437b07b205073b249663b1432980f15fb2feb1d4972f873ec55dec259785ae624e6f7a8ac92e448c969adc6a2a381874023b6119ceda76dc0b030482b6072dbc85fc4e1dd4b68ac7d5191f1",
        wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.rawCertificateHex.get)
      assertTrue(wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.fee.nonEmpty)
      assertEquals(0.005, wellFormedTopQualityCertificateResponsePayload.mempoolTopQualityCert.fee.get, 0.0001)

    } catch {
      case e : Throwable => fail(e)
    }
  }

  @Test
  def sendRequestWithMalformedType() : Unit = {

    val webSocketClient = new WebSocketCommunicationClient()

    val mockedChannel = mock[WebSocketChannel]
    val mockedConnector : WebSocketConnector = mock[WebSocketConnector]

    Mockito.when(mockedConnector.isStarted()).thenAnswer(asw => true)
    Mockito.when(mockedConnector.stop()).thenAnswer(asw => Success(Unit))
    Mockito.when(mockedConnector.start()).thenAnswer(asw => Try(mockedChannel))

    webSocketClient.setWebSocketChannel(mockedChannel)

    val wellFormedGetBlockByHeightRequestPayload : RequestPayload = GetBlockByHeightRequestPayload(30) // request type -> 0

    Mockito.when(mockedChannel.sendMessage(ArgumentMatchers.any())).thenAnswer(
      asw =>
      {
        val req = asw.getArgument(0).asInstanceOf[String]
        // get response from server and send reply to the registered message handler
        webSocketClient.onReceivedMessage(getServerResponse(req))
      }
    )

    try {
      case object UNEXISTED_REQUEST_TYPE extends RequestType(10)
      val future_1 : Future[BlockResponsePayload]= webSocketClient.sendRequest[RequestPayload, BlockResponsePayload](UNEXISTED_REQUEST_TYPE, wellFormedGetBlockByHeightRequestPayload, classOf[BlockResponsePayload])
      Await.result(future_1, webSocketClient.requestTimeoutDuration())
      fail("The response must be an error.")
    } catch {
      case e : Throwable =>
        assertEquals(classOf[RuntimeException], e.getClass)
    }
  }

  @Test
  def sendWellFormedRequestReceiveMalformedResponse() : Unit = {

    val webSocketClient = new WebSocketCommunicationClient()

    val mockedChannel = mock[WebSocketChannel]
    val mockedConnector : WebSocketConnector = mock[WebSocketConnector]

    Mockito.when(mockedConnector.isStarted()).thenAnswer(asw => true)
    Mockito.when(mockedConnector.stop()).thenAnswer(asw => Success(Unit))
    Mockito.when(mockedConnector.start()).thenAnswer(asw => Try(mockedChannel))

    webSocketClient.setWebSocketChannel(mockedChannel)

    val wellFormedGetBlockByHeightRequestPayload : RequestPayload = GetBlockByHeightRequestPayload(30) // request type -> 0

    Mockito.when(mockedChannel.sendMessage(ArgumentMatchers.any())).thenAnswer(
      asw =>
      {
        val req = asw.getArgument(0).asInstanceOf[String]
        // get response from server and send reply to the registered message handler
        webSocketClient.onReceivedMessage("just a simple response...")
      }
    )

    try {
      case object UNEXISTED_REQUEST_TYPE extends RequestType(10)
      val future_1 : Future[BlockResponsePayload]= webSocketClient.sendRequest[RequestPayload, BlockResponsePayload](UNEXISTED_REQUEST_TYPE, wellFormedGetBlockByHeightRequestPayload, classOf[BlockResponsePayload])
      Await.result(future_1, webSocketClient.requestTimeoutDuration())
      fail("The response must be an error.")
    } catch {
      case e : Throwable =>
        assertEquals(classOf[TimeoutException], e.getClass)
    }
  }

  @Test
  def receiveEvent() : Unit = {

    val webSocketClient = new WebSocketCommunicationClient()

    val mockedChannel = mock[WebSocketChannel]
    val mockedConnector : WebSocketConnector = mock[WebSocketConnector]
    val mockedEventHandler : EventHandler[OnUpdateTipEventPayload] = mock[OnUpdateTipEventHandler]

    Mockito.when(mockedConnector.isStarted()).thenAnswer(asw => true)
    Mockito.when(mockedConnector.stop()).thenAnswer(asw => Success(Unit))
    Mockito.when(mockedConnector.start()).thenAnswer(asw => Try(mockedChannel))

    webSocketClient.setWebSocketChannel(mockedChannel)

    val registration = webSocketClient.registerEventHandler[OnUpdateTipEventPayload](0, mockedEventHandler, classOf[OnUpdateTipEventPayload])
    assertTrue(registration.isSuccess)

    val eventJson = mapper.createObjectNode()
      .put("height", 265)
      .put("hash", "012eca4e7c57850527716dbbfa63798260c16c8d022d57e86499f4d845ed46a9")
      .put("block", "the block hex")

    val eventFromServer = mapper.createObjectNode()
      .put("msgType", 0)
      .put("eventType", 0)
      .set("eventPayload", eventJson).toString

    webSocketClient.onReceivedMessage(eventFromServer)
    webSocketClient.onReceivedMessage(eventFromServer)
    webSocketClient.onReceivedMessage(eventFromServer)

    val event = OnUpdateTipEventPayload(265, "012eca4e7c57850527716dbbfa63798260c16c8d022d57e86499f4d845ed46a9", "the block hex")
    Mockito.verify(mockedEventHandler, Mockito.times(3)).onEvent(ArgumentMatchers.eq(event))

    webSocketClient.unregisterEventHandler[OnUpdateTipEventPayload](0, mockedEventHandler)

    // the event handler 'onEvent' must not be called anymore
    webSocketClient.onReceivedMessage(eventFromServer)
    webSocketClient.onReceivedMessage(eventFromServer)
    Mockito.verify(mockedEventHandler, Mockito.times(3)).onEvent(ArgumentMatchers.eq(event))
  }
}
