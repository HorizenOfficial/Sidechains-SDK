package com.horizen.websocket

import com.horizen.block.MainchainHeader
import com.horizen.params.MainNetParams
import com.horizen.utils.BytesUtils
import org.scalatest.junit.JUnitSuite
import org.junit.Test
import org.junit.Assert._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.mockito._
import org.mockito._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Promise
import scala.io.Source
import scala.util.Success

class MainchainNodeChannelImplTest extends JUnitSuite with MockitoSugar {

  @Test
  def getBlockByHeight(): Unit = {
    val mockedCommunicationClient: CommunicationClient = mock[CommunicationClient]

    val timeoutDuration: FiniteDuration = new FiniteDuration(100, MILLISECONDS)
    val height = 473173
    val hash = "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660"
    val mcBlockHex = Source.fromResource("mcblock473173_mainnet").getLines().next()
    val expectedReqType = GET_SINGLE_BLOCK_REQUEST_TYPE

    Mockito.when(mockedCommunicationClient.requestTimeoutDuration()).thenReturn(timeoutDuration)
    Mockito.when(mockedCommunicationClient.sendRequest[RequestPayload, ResponsePayload](
      ArgumentMatchers.any[RequestType], ArgumentMatchers.any[RequestPayload], ArgumentMatchers.any[Class[ResponsePayload]]
    )).thenAnswer( answer => {
        val reqType = answer.getArgument(0).asInstanceOf[RequestType]
        assertEquals("Get block by height request type is wrong.", expectedReqType, reqType)
        val req = answer.getArgument(1).asInstanceOf[GetBlockByHeightRequestPayload]
        assertEquals("Get block by height request data (height) is wrong.", height, req.height)
        assertTrue("Get block by height response payload type is wrong", answer.getArgument(2).isInstanceOf[Class[BlockResponsePayload]])

        val p = Promise[ResponsePayload]
        val thread = new Thread {
          override def run() {
            Thread.sleep(timeoutDuration.div(2L).toMillis)
            p.complete(Success(BlockResponsePayload(height, hash, mcBlockHex)))
          }
        }
        thread.start()
        p.future
      }
    )

    val params = MainNetParams()
    val mcnode = new MainchainNodeChannelImpl(mockedCommunicationClient, params)

    val mcRefTry = mcnode.getBlockByHeight(height)
    assertTrue("MCBlock ref expected to be retrieved.", mcRefTry.isSuccess)
    assertEquals("MCBlock ref hash is different.", hash, mcRefTry.get.header.hashHex)
  }


  @Test
  def getBlockByHash(): Unit = {
    val mockedCommunicationClient: CommunicationClient = mock[CommunicationClient]

    val timeoutDuration: FiniteDuration = new FiniteDuration(100, MILLISECONDS)
    val height = 473173
    val hash = "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660"
    val mcBlockHex = Source.fromResource("mcblock473173_mainnet").getLines().next()
    val expectedReqType = GET_SINGLE_BLOCK_REQUEST_TYPE

    Mockito.when(mockedCommunicationClient.requestTimeoutDuration()).thenReturn(timeoutDuration)
    Mockito.when(mockedCommunicationClient.sendRequest[RequestPayload, ResponsePayload](
      ArgumentMatchers.any[RequestType], ArgumentMatchers.any[RequestPayload], ArgumentMatchers.any[Class[ResponsePayload]]
    )).thenAnswer( answer => {
        val reqType = answer.getArgument(0).asInstanceOf[RequestType]
        assertEquals("Get block by hash request type is wrong.", expectedReqType, reqType)
        val req = answer.getArgument(1).asInstanceOf[GetBlockByHashRequestPayload]
        assertEquals("Get block by hash request data (hash) is wrong.", hash, req.hash)
        assertTrue("Get block by hash response payload type is wrong", answer.getArgument(2).isInstanceOf[Class[BlockResponsePayload]])

        val p = Promise[ResponsePayload]
        val thread = new Thread {
          override def run() {
            Thread.sleep(timeoutDuration.div(2L).toMillis)
            p.complete(Success(BlockResponsePayload(height, hash, mcBlockHex)))
          }
        }
        thread.start()
        p.future
      }
    )

    val params = MainNetParams()
    val mcnode = new MainchainNodeChannelImpl(mockedCommunicationClient, params)

    val mcRefTry = mcnode.getBlockByHash(hash)
    assertTrue("MCBlock ref expected to be retrieved.", mcRefTry.isSuccess)
    assertEquals("MCBlock ref hash is different.",
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660", mcRefTry.get.header.hashHex)
  }

  @Test
  def getNewBlockHashes(): Unit = {
    val mockedCommunicationClient: CommunicationClient = mock[CommunicationClient]

    val timeoutDuration: FiniteDuration = new FiniteDuration(100, MILLISECONDS)

    val reqHashes = Seq(
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660",
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb661",
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb662"
    )
    val limit = 10
    val expectedReqType = GET_NEW_BLOCK_HASHES_REQUEST_TYPE

    val respHeight = 1000
    val respHashes = Seq(
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb662",
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb663",
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb664"
    )

    Mockito.when(mockedCommunicationClient.requestTimeoutDuration()).thenReturn(timeoutDuration)
    Mockito.when(mockedCommunicationClient.sendRequest[RequestPayload, ResponsePayload](
      ArgumentMatchers.any[RequestType], ArgumentMatchers.any[RequestPayload], ArgumentMatchers.any[Class[ResponsePayload]]
    )).thenAnswer( answer => {
      val reqType = answer.getArgument(0).asInstanceOf[RequestType]
      assertEquals("Get new block hashes request type is wrong.", expectedReqType, reqType)
      val req = answer.getArgument(1).asInstanceOf[GetNewBlocksRequestPayload]
      assertEquals("Get new block hashes request data (locatorHashes) is wrong.", reqHashes, req.locatorHashes)
      assertEquals("Get new block hashes request data (limit) is wrong.", limit, req.limit)
      assertTrue("Get bnew block hashes response payload type is wrong", answer.getArgument(2).isInstanceOf[Class[NewBlocksResponsePayload]])

      val p = Promise[ResponsePayload]
      val thread = new Thread {
        override def run() {
          Thread.sleep(timeoutDuration.div(2L).toMillis)
          p.complete(Success(NewBlocksResponsePayload(respHeight, respHashes)))
        }
      }
      thread.start()
      p.future
    }
    )

    val params = MainNetParams()
    val mcnode = new MainchainNodeChannelImpl(mockedCommunicationClient, params)

    val hashesTry = mcnode.getNewBlockHashes(reqHashes, limit)
    assertTrue("Result expected to be successful.", hashesTry.isSuccess)
    assertEquals("Result hashes is different.", (respHeight, respHashes), hashesTry.get)
  }

  @Test
  def getMultipleBlockHeaders(): Unit = {
    val mockedCommunicationClient: CommunicationClient = mock[CommunicationClient]

    val timeoutDuration: FiniteDuration = new FiniteDuration(100, MILLISECONDS)

    val reqHashes = Seq(
      "08d34596dd7e137f603d6661d867eb083c0592e8333f838478de2ebd3efd8e5a",
      "0e4d418de14c9aeaba0714bc23626bab1fe12001758dd6d4908029ad588b5861",
      "0e812bd816810df31b1cbfdacec59768e3dc668dbd9186c421bc44730e61ecfa"
    )
    val expectedReqType = GET_MULTIPLE_HEADERS_REQUEST_TYPE

    val respHeaders = Seq(
      "030000001eb0e14a6f84def4b35995c6aa36029d73886c69c07fd81d6ac5bc6ba068c3015fa736f8e7a7075c3ce9c5905a4e8e027d35d8083c647358b9a710f4bee77a2c0000000000000000000000000000000000000000000000000000000000000000c6a97c5ffd0e0f2003000ef89a1e64d88f72ad123adf9dfd3698af8fc997712756390811ef2e0000240019f11bdacd9ebbfa362b966d48ef1a55e00758aa7f92cbbcb4f417ebe07a02b69b59c7",
      "03000000ebd8fa630305ff6ba46f096cf215fe5b4c6512623abdf0d9d52939fd6ef4c00cda4c481ca56dcceb1b695705e1405f66d61f8de18481c20fc02afa939407f1880000000000000000000000000000000000000000000000000000000000000000c7a97c5ffd0e0f203400c0dcae0cc415801d74e3db37a51f941b165c308d92b4d609643546960000240a5993ac4b0703c5f87a5de05fa7dca7b1f31510c936f63e55caef44e7e3d545eb3ad9c8",
      "03000000c73838b61b78ae715e6affd05bd9512943a3778bce0388df8494331c0dbefc02caef2e90a648db25893a2ce9c55fe829f71f55e8fa44c1bd11a4ed265552d6360000000000000000000000000000000000000000000000000000000000000000c7a97c5ffd0e0f202400366c90e7e4c6f98890c0f0b2a20bc8ff5f188b82ffe61be8d78dad4c00002404ee9bf9b05bc821004322d3fd37dfaa4b2e1c35d7ac4202fd77ef247dcc1b8386551cc9"
    )

    val expectedHeaders: Seq[MainchainHeader] = respHeaders.map(strHeader => MainchainHeader.create(BytesUtils.fromHexString(strHeader), 0).get)

    Mockito.when(mockedCommunicationClient.requestTimeoutDuration()).thenReturn(timeoutDuration)
    Mockito.when(mockedCommunicationClient.sendRequest[RequestPayload, ResponsePayload](
      ArgumentMatchers.any[RequestType], ArgumentMatchers.any[RequestPayload], ArgumentMatchers.any[Class[ResponsePayload]]
    )).thenAnswer( answer => {
      val reqType = answer.getArgument(0).asInstanceOf[RequestType]
      assertEquals("Get block headers request type is wrong.", expectedReqType, reqType)
      val req = answer.getArgument(1).asInstanceOf[GetBlockHeadersRequestPayload]
      assertEquals("Get block headers request data (hashes) is wrong.", reqHashes, req.hashes)
      assertTrue("Get block headers response payload type is wrong", answer.getArgument(2).isInstanceOf[Class[BlockHeadersResponsePayload]])

      val p = Promise[ResponsePayload]
      val thread = new Thread {
        override def run() {
          Thread.sleep(timeoutDuration.div(2L).toMillis)
          p.complete(Success(BlockHeadersResponsePayload(respHeaders)))
        }
      }
      thread.start()
      p.future
    }
    )

    val params = MainNetParams()
    val mcnode = new MainchainNodeChannelImpl(mockedCommunicationClient, params)

    val headersTry = mcnode.getBlockHeaders(reqHashes)
    assertTrue("Result expected to be successful.", headersTry.isSuccess)
    assertEquals("Result headers is different.", expectedHeaders, headersTry.get)
  }


  @Test
  def onUpdateTipEvent(): Unit = {
    val mockedCommunicationClient: CommunicationClient = mock[CommunicationClient]

    val height = 473173
    val hash = "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb660"
    val mcBlockHex = Source.fromResource("mcblock473173_mainnet").getLines().next()
    val expectedEventType = 0

    class OnUpdateEventHandlerImpl extends OnUpdateTipEventHandler {
      override def onEvent(eventPayload: OnUpdateTipEventPayload): Unit = {

      }
    }
    val handler = new OnUpdateEventHandlerImpl()


    // Test 1: subscribing
    Mockito.when(mockedCommunicationClient.registerEventHandler[EventPayload](
      ArgumentMatchers.any[Int], ArgumentMatchers.any[EventHandler[EventPayload]], ArgumentMatchers.any[Class[EventPayload]]
    )).thenAnswer( answer => {
        val eventType = answer.getArgument(0).asInstanceOf[Int]
        assertEquals("Subscribe on tip event type is wrong.", expectedEventType, eventType)
        assertEquals("Subscribe on tip handler is different", handler, answer.getArgument(1))
        assertEquals("Subscribe on tip event payload type is different", classOf[OnUpdateTipEventPayload], answer.getArgument(2))
        Success(Unit)
      }
    )

    val params = MainNetParams()
    val mcnode = new MainchainNodeChannelImpl(mockedCommunicationClient, params)

    val res = mcnode.subscribeOnUpdateTipEvent(handler)
    assertTrue("SubscribeOnUpdateTipEvent result expected to be successful", res.isSuccess)


    // Test 2: unsubscribing
    Mockito.when(mockedCommunicationClient.unregisterEventHandler[EventPayload](
      ArgumentMatchers.any[Int], ArgumentMatchers.any[EventHandler[EventPayload]]
    )).thenAnswer( answer => {
        val eventType = answer.getArgument(0).asInstanceOf[Int]
        assertEquals("Subscribe on tip event type is wrong.", expectedEventType, eventType)
        assertEquals("Subscribe on tip handler is different", handler, answer.getArgument(1))
      }
    )
    mcnode.unsubscribeOnUpdateTipEvent(handler)
  }
}
