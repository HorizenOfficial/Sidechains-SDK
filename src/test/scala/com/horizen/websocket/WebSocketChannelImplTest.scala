package com.horizen.websocket

import java.net.InetSocketAddress

import org.glassfish.tyrus.server.Server
import org.junit.{Before, Test}
import org.junit.Assert.{assertEquals, assertFalse, _}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success}

class WebSocketChannelImplTest extends JUnitSuite with MockitoSugar {

  private val serverHost = "localhost"
  private val serverPort = 8080
  private var server : Server = _

  @Before
  def setUp() : Unit = {
    server = new Server(serverHost, serverPort, null, null, classOf[WebSocketServerEchoEndpoint])
  }

  @Test
  def multipleStartConnectionOperations() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)

    server.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 1)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val attempt_1 = connector.start()
    assertEquals(classOf[Success[_]], attempt_1.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    val attempt_2 = connector.start()
    assertEquals(classOf[Failure[_]], attempt_2.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    val attempt_3 = connector.start()
    assertEquals(classOf[Failure[_]], attempt_3.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    assertTrue("Web socket connector is not started.", connector.isStarted())

    server.stop()

  }

  @Test
  def multipleCloseConnectionOperationsWithoutStart() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)

    server.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 1)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    assertFalse("Web socket connector is started.", connector.isStarted())

    assertFalse(connector.stop().isSuccess)
    assertFalse(connector.stop().isSuccess)
    assertFalse(connector.stop().isSuccess)
    assertFalse(connector.stop().isSuccess)

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    assertFalse("Web socket connector is started.", connector.isStarted())

    server.stop()

  }

  @Test
  def multipleCloseConnectionOperationsWithoutServerStarted() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 1)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    assertFalse("Web socket connector is started.", connector.isStarted())

    assertFalse(connector.stop().isSuccess)
    assertFalse(connector.stop().isSuccess)
    assertFalse(connector.stop().isSuccess)
    assertFalse(connector.stop().isSuccess)

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    assertFalse("Web socket connector is started.", connector.isStarted())

  }

  @Test
  def multipleCloseConnectionOperations() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)

    server.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 1)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val startOp = connector.start()
    assertEquals(classOf[Success[_]], startOp.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    assertTrue(connector.stop().isSuccess)
    assertTrue(connector.stop().isSuccess)
    assertTrue(connector.stop().isSuccess)
    assertTrue(connector.stop().isSuccess)

    // The 'onDisconnection' is called once. So, multiple calls of 'connector.close()' have no side effects
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    assertFalse("Web socket connector is started.", connector.isStarted())

    server.stop()

  }

  @Test
  def openAndCloseConnection() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)

    server.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 1,
      reconnectionMaxAttempts = 1)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val startOp = connector.start()
    assertEquals(classOf[Success[_]], startOp.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    val channel : WebSocketChannel = startOp.get

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val newStartOp = connector.start()
    assertEquals(classOf[Failure[_]], newStartOp.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val stopOp = connector.stop()
    assertEquals("Connector already stopped.", classOf[Success[_]], stopOp.getClass)
    assertFalse("Web socket connector is started.", connector.isStarted())

    // The 'onDisconnection' is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    val newStopOp = connector.stop()
    assertEquals("Connector was started.", classOf[Success[_]], newStopOp.getClass)
    assertFalse("Web socket connector is started.", connector.isStarted())

    // The 'onDisconnection' is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    server.stop()

    // The 'onDisconnection' is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

  }

  @Test
  def successEchoMessage() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)

    server.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 1,
      reconnectionMaxAttempts = 3)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val startOp = connector.start()
    assertEquals(classOf[Success[_]], startOp.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    val channel : WebSocketChannel = startOp.get

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val message : String = "the message"

    channel.sendMessage(message)
    Thread.sleep(100)

    // Verify that the mocked handler received from the server the same message sent by the client.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    server.stop()
    Thread.sleep(100)

    /**
      * Post-conditions:
      *   - The mocked handler received from the server the same message sent by the client.
      *   - The 'onDisconnection' method is called once.
      *   - Other mocked methods are not called.
      */
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())

  }

  @Test
  def connectionFailed() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)

    // We try to connect but the server is not started
    // Remember the parameter of the configuration 'reconnectionMaxAttempts = 3'
    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 3)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val startOp = connector.start()

    // Assert connection failed
    assertEquals(classOf[Failure[_]], startOp.getClass)
    assertTrue("Web socket connector is started.", !connector.isStarted)

    // Verify that the 'onConnectFailure' method of the mocked handler is called 3 times (value provided by 'reconnectionMaxAttempts').
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(3)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val stopOp = connector.stop()
    assertEquals(classOf[Failure[_]], stopOp.getClass)
    /**
      * Post-conditions:
      *   - The 'onConnectFailure' method of the mocked handler is called 3 times (value provided by 'reconnectionMaxAttempts').
      *   - Other mocked methods are not called.
      */
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(3)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())
  }

  @Test
  def connectionSuccessAfterConnectionFailed() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)

    // We try to connect but the server is not started
    // Remember the parameter of the configuration 'reconnectionMaxAttempts = 3'
    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 3)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val startOp = connector.start()

    // Assert connection failed
    assertEquals(classOf[Failure[_]], startOp.getClass)
    assertTrue("Web socket connector is started.", !connector.isStarted)

    // Verify that the 'onConnectFailure' method of the mocked handler is called 3 times (value provided by 'reconnectionMaxAttempts').
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(3)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    // now we start the server
    server.start()

    val newStartOp = connector.start()

    // Assert connection not failed
    assertEquals(classOf[Success[_]], newStartOp.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted)

    val message : String = "a message"
    val channel = newStartOp.get
    channel.sendMessage(message)
    Thread.sleep(100)

    // Verify that the 'onConnectFailure' method of the mocked handler is called 3 times (value provided by 'reconnectionMaxAttempts').
    // Verify that the 'onReceivedMessage' method of the mocked handler is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(3)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())


    val stopOp = connector.stop()
    assertEquals(classOf[Success[_]], stopOp.getClass)
    /**
      * Post-conditions:
      *   - The 'onConnectFailure' method of the mocked handler is called 3 times (value provided by 'reconnectionMaxAttempts').
      *   - The 'onReceivedMessage' method of the mocked handler is called once.
      *   - The 'onDisconnection' method of the mocked handler is called once.
      *   - Other mocked methods are not called.
      */
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(3)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    server.stop()
  }

  @Test
  def failEchoMessage() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)

    server.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 1,
      reconnectionMaxAttempts = 2)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val startOp = connector.start()
    assertEquals(classOf[Success[_]], startOp.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    val channel : WebSocketChannel = startOp.get

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val message : String = "the message"

    server.stop()
    Thread.sleep(100)
    channel.sendMessage(message)
    Thread.sleep(100)

    // Verify the 'onDisconnection' method of the mocked handler is called once (value provided by 'reconnectionMaxAttempts').
    // Verify the 'onSendMessageErrorOccurred' method of the mocked handler is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onSendMessageErrorOccurred(ArgumentMatchers.eq(message), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())

    val closeOp = connector.stop()
    assertEquals(classOf[Success[_]], closeOp.getClass)
    /**
      * Post-conditions:
      *   - The 'onDisconnection' method of the mocked handler is called 3 times (value provided by 'reconnectionMaxAttempts').
      *   - The 'onSendMessageErrorOccurred' method of the mocked handler is called once.
      *   - Other mocked methods are not called.
      */
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onSendMessageErrorOccurred(ArgumentMatchers.eq(message), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())
  }

  @Test
  def setMessageHandlerTwice() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    val secondMockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any())).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())).thenAnswer(asw => true)

    server.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 4)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val startOp = connector.start()

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    assertEquals(classOf[Success[_]], startOp.getClass)
    assertTrue("The connector is not started.", connector.isStarted)

    val channel : WebSocketChannel = startOp.get
    val message : String = "the message"
    channel.sendMessage(message)
    Thread.sleep(100)

    // Verify the 'onReceivedMessage' method of 'mockedWebSocketMessageHandler' is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(secondMockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val res = connector.setMessageHandler(secondMockedWebSocketMessageHandler)
    assertFalse("Message handler has benn updated.", res)

    val secondMessage : String = "the message"
    channel.sendMessage(secondMessage)
    Thread.sleep(100)

    // Verify the 'onReceivedMessage' method of 'mockedWebSocketMessageHandler' is called twice.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(2)).onReceivedMessage(ArgumentMatchers.eq(secondMessage))
    Mockito.verify(secondMockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    server.stop()

  }

  @Test
  def asyncConnectorStart() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any())).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())).thenAnswer(asw => true)

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 1,
      reconnectionMaxAttempts = 3)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    server.start()

    val futureOfChannel = connector.asyncStart()
    assertFalse("The connector is started.", connector.isStarted)

    val timeout : FiniteDuration = 4 seconds
    val fut = Await.result(futureOfChannel, timeout)

    assertTrue("The connector is not started.", connector.isStarted)
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    server.stop()
    Thread.sleep(100)

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())

  }

  @Test
  def asyncConnectorRetrySuccess() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any())).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())).thenAnswer(asw => true)

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 1,
      reconnectionMaxAttempts = 3)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val futureOfChannel = connector.asyncStart()
    assertFalse("The connector is started.", connector.isStarted)
    Thread.sleep(1000)

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.atLeastOnce()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    assertFalse("The connector is started.", connector.isStarted)
    server.start()

    try {
      val timeout : FiniteDuration = 5 second
      val fut = Await.result(futureOfChannel, timeout)
    } catch {
      case e : Throwable =>
        server.stop()
        fail(e)
    }

    assertTrue("The connector is not started.", connector.isStarted)
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.atLeastOnce()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.atMost(3)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    server.stop()
    Thread.sleep(100)

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.atLeastOnce()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.atMost(3)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())

  }

  @Test
  def receiveMultipleMessages() : Unit = {
    server.stop()

    val mockedWebSocketMessageHandler : WebSocketMessageHandler = mock[WebSocketMessageHandler]
    val mockedWebSocketReconnectionHandler : WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]

    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw => true)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any[String])).thenAnswer(asw => false)
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any[String])).thenAnswer(asw => true)

    val pushServerHost = "localhost"
    val pushServerPort = 8080
    var pushServer : Server = new Server(pushServerHost, pushServerPort, null, null, classOf[WebSocketServerPushEndpoint])

    pushServer.start()

    val conf = new WebSocketConnectorConfiguration(
      schema = "ws",
      remoteAddress = new InetSocketAddress(serverHost, serverPort),
      connectionTimeout = 10,
      reconnectionDelay = 0,
      reconnectionMaxAttempts = 2)
    val connector = new WebSocketConnectorImpl()

    connector.setConfiguration(conf)
    connector.setReconnectionHandler(mockedWebSocketReconnectionHandler)
    connector.setMessageHandler(mockedWebSocketMessageHandler)

    val pushedMessages = 5

    val startOp = connector.start()
    assertEquals(classOf[Success[_]], startOp.getClass)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    Thread.sleep(100)


    // Verify that the mocked handler received from the server the 'pushedMessages' number of messages.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(pushedMessages)).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val stopOp = connector.stop()
    assertEquals(classOf[Success[_]], stopOp.getClass)
    assertFalse("Web socket connector is started.", connector.isStarted())

    /**
      * Post-conditions:
      *   - The mocked handler received from the server the 'pushedMessages' number of messages.
      *   - The 'onDisconnection' method is called once.
      *   - Other mocked methods are not called.
      */
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(pushedMessages)).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    pushServer.stop()

  }

}
