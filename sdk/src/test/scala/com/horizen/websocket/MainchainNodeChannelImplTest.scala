package com.horizen.websocket

import com.horizen.params.MainNetParams
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
    val expectedReqType = 0

    Mockito.when(mockedCommunicationClient.requestTimeoutDuration()).thenReturn(timeoutDuration)
    Mockito.when(mockedCommunicationClient.sendRequest[RequestPayload, ResponsePayload](
      ArgumentMatchers.any[Int], ArgumentMatchers.any[RequestPayload], ArgumentMatchers.any[Class[ResponsePayload]]
    )).thenAnswer( answer => {
        val reqType = answer.getArgument(0).asInstanceOf[Int]
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
    val expectedReqType = 0

    Mockito.when(mockedCommunicationClient.requestTimeoutDuration()).thenReturn(timeoutDuration)
    Mockito.when(mockedCommunicationClient.sendRequest[RequestPayload, ResponsePayload](
      ArgumentMatchers.any[Int], ArgumentMatchers.any[RequestPayload], ArgumentMatchers.any[Class[ResponsePayload]]
    )).thenAnswer( answer => {
        val reqType = answer.getArgument(0).asInstanceOf[Int]
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
    val expectedReqType = 2

    val respHeight = 1000
    val respHashes = Seq(
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb662",
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb663",
      "0000000024ebb5c6d558daa34ad9b9a4c5503b057e14815a48e241612b1eb664"
    )

    Mockito.when(mockedCommunicationClient.requestTimeoutDuration()).thenReturn(timeoutDuration)
    Mockito.when(mockedCommunicationClient.sendRequest[RequestPayload, ResponsePayload](
      ArgumentMatchers.any[Int], ArgumentMatchers.any[RequestPayload], ArgumentMatchers.any[Class[ResponsePayload]]
    )).thenAnswer( answer => {
      val reqType = answer.getArgument(0).asInstanceOf[Int]
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
