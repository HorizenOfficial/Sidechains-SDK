package com.horizen.websocket.client

import com.horizen.WebSocketSettings
import org.glassfish.tyrus.server.Server
import org.junit.{After, Before, Test}
import org.junit.Assert.{assertEquals, assertFalse, _}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.language.postfixOps

class WebSocketChannelImplTest extends JUnitSuite with MockitoSugar {

  private val serverHost = "localhost"
  private var serverPort: Int = _
  private var server: Server = _

  private val pushServer: Server = new Server(serverHost, 0, null, null, classOf[WebSocketServerPushEndpoint])
  val pushServerPort = pushServer.getPort

  @Before
  def setUp(): Unit = {
    var isStarted: Boolean = false
    var attemptsLeft = 10
    while(!isStarted && attemptsLeft > 0) {
      try {
        // start server on available port
        server = new Server(serverHost, 0, null, null, classOf[WebSocketServerEchoEndpoint])
        // get real port value
        serverPort = server.getPort

        server.start()
        isStarted = true;

      } catch {
        case _: Throwable =>  {
          attemptsLeft -= 1
          println(s"Server was not started on port ${server.getPort}, attempts left $attemptsLeft");
          Thread.sleep(100)
        }
      }
    }
  }

  @After
  def afterAllTests(): Unit = {
    server.stop()
    // stop pushServer ro prevent tests collisions even if pushServer was not used in a particular test to prevent
    pushServer.stop()
  }

  @Test
  def multipleStartConnectionOperations(): Unit = {
    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 0 seconds,
      reconnectionMaxAttempts = 1,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val attempt_1 = connector.start()
    assertTrue(attempt_1.isSuccess)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    val already_started_attempt_1 = connector.start()
    assertTrue(already_started_attempt_1.isFailure)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    val already_started_attempt_2 = connector.start()
    assertTrue(already_started_attempt_2.isFailure)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    assertTrue("Web socket connector is not started.", connector.isStarted())
  }

  @Test
  def multipleCloseConnectionOperationsWithoutStart(): Unit = {
    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 0 seconds,
      reconnectionMaxAttempts = 1,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

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
  def multipleCloseConnectionOperationsWithoutServerStarted(): Unit = {
    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 0 seconds,
      reconnectionMaxAttempts = 1,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

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
  def multipleCloseConnectionOperations(): Unit = {
    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 0 seconds,
      reconnectionMaxAttempts = 1,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val startOp = connector.start()
    assertTrue(startOp.isSuccess)
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
  }

  @Test
  def openAndCloseConnection(): Unit = {
    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 1 seconds,
      reconnectionMaxAttempts = 1,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val startOp = connector.start()
    assertTrue(startOp.isSuccess)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val newStartOp = connector.start()
    assertTrue(newStartOp.isFailure)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val stopOp = connector.stop()
    assertTrue("Connector was not stopped.", stopOp.isSuccess)
    assertFalse("Web socket connector was not stopped.", connector.isStarted())

    // The 'onDisconnection' is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    val newStopOp = connector.stop()
    assertTrue("Connector was not stopped.", newStopOp.isSuccess)
    assertFalse("Web socket connector was not stopped.", connector.isStarted())

    // The 'onDisconnection' is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())

    server.stop()
    Thread.sleep(100)

    // The 'onDisconnection' is called once.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())
  }

  @Test
  def successEchoMessage(): Unit = {
    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 1 seconds,
      reconnectionMaxAttempts = 3,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val startOp = connector.start()
    assertTrue(startOp.isSuccess)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    // Post-conditions. All mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val message: String = "the message"

    connector.sendMessage(message)
    Thread.sleep( 500)

    // Verify that the mocked handler received from the server the same message sent by the client.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    server.stop()
    Thread.sleep(500)

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
  def connectionFailed(): Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    // We try to connect but the server is not started
    // Remember the parameter of the configuration 'reconnectionMaxAttempts = 3'
    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 0 seconds,
      reconnectionMaxAttempts = 3,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val startOp = connector.start()

    // Assert connection failed
    assertTrue(startOp.isFailure)
    assertTrue("Web socket connector is started.", !connector.isStarted)
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(4)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val stopOp = connector.stop()
    assertTrue(startOp.isFailure)
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(4)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())
  }

  @Test
  def connectionSuccessAfterConnectionFailed(): Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    // We try to connect but the server is not started
    // Remember the parameter of the configuration 'reconnectionMaxAttempts = 3'
    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 0 seconds,
      reconnectionMaxAttempts = 3,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val startOp = connector.start()

    // Assert connection failed
    assertTrue(startOp.isFailure)
    assertTrue("Web socket connector is started.", !connector.isStarted)

    // Verify that the 'onConnectFailure' method of the mocked handler is called 3 times (value provided by 'reconnectionMaxAttempts').
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(4)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    // now we start the server
    server.start()

    val newStartOp = connector.start()

    // Assert connection not failed
    assertTrue(newStartOp.isSuccess)
    assertTrue("Web socket connector is not started.", connector.isStarted)

    val message: String = "a message"
    connector.sendMessage(message)
    Thread.sleep(100)

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(4)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())


    val stopOp = connector.stop()
    assertTrue(stopOp.isSuccess)
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onReceivedMessage(ArgumentMatchers.eq(message))
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(4)).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.ON_SUCCESS), ArgumentMatchers.any())
  }

  @Test
  def failEchoMessage(): Unit = {
    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 1 seconds,
      reconnectionMaxAttempts = 2,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val startOp = connector.start()
    assertTrue(startOp.isSuccess)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val message: String = "the message"

    server.stop()
    Thread.sleep(100)
    connector.sendMessage(message)
    Thread.sleep(100)

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onSendMessageErrorOccurred(ArgumentMatchers.eq(message), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())

    val closeOp = connector.stop()
    assertTrue(closeOp.isSuccess)
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(1)).onSendMessageErrorOccurred(ArgumentMatchers.eq(message), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())
  }

  @Test
  def asyncConnectorStart(): Unit = {

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 1 seconds,
      reconnectionMaxAttempts = 3,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val futureOfChannel = connector.asyncStart()
    assertFalse("The connector is started.", connector.isStarted)

    val timeout: FiniteDuration = 4 seconds
    val fut = Await.result(futureOfChannel, timeout)

    assertTrue("The connector is not started.", connector.isStarted)
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    server.stop()
    Thread.sleep(500)

    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.times(1)).onDisconnection(ArgumentMatchers.eq(DisconnectionCode.UNEXPECTED), ArgumentMatchers.any())
  }

  //@Test
  // NOTE: Doesn't work pon Windows as expected. Seems, that conf.connectionTimeout value is ignored.
  // TO DO: verify why and restore the test.
  def asyncConnectorRetrySuccess(): Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + serverPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 1 seconds,
      reconnectionMaxAttempts = 3,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

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
      val timeout: FiniteDuration = 5 seconds
      val fut = Await.result(futureOfChannel, timeout)
    } catch {
      case e: Throwable =>
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
  def receiveMultipleMessages(): Unit = {
    server.stop()

    val mockedWebSocketMessageHandler: WebSocketMessageHandler = mock[WebSocketMessageHandler]

    pushServer.start()

    val conf = WebSocketSettings(
      address = "ws://" + serverHost + ":" + pushServerPort,
      connectionTimeout = 10 milliseconds,
      reconnectionDelay = 0 seconds,
      reconnectionMaxAttempts = 2,
      wsServer = false,
      wsServerPort = 0
    )

    val defaultReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(conf)

    val mockedWebSocketReconnectionHandler: WebSocketReconnectionHandler = mock[WebSocketReconnectionHandler]
    Mockito.when(mockedWebSocketReconnectionHandler.onDisconnection(ArgumentMatchers.any[DisconnectionCode.Value], ArgumentMatchers.any[String])).thenAnswer(asw =>
      defaultReconnectionHandler.onDisconnection(asw.getArgument(0), asw.getArgument(1)))
    Mockito.when(mockedWebSocketReconnectionHandler.onConnectionFailed(ArgumentMatchers.any[Throwable])).thenAnswer(asw =>
      defaultReconnectionHandler.onConnectionFailed(asw.getArgument(0)))
    Mockito.when(mockedWebSocketReconnectionHandler.getDelay).thenAnswer(_ => defaultReconnectionHandler.getDelay)

    val connector = new WebSocketConnectorImpl(conf.address, conf.connectionTimeout, mockedWebSocketMessageHandler, mockedWebSocketReconnectionHandler)

    val pushedMessages = 5

    val startOp = connector.start()
    assertTrue(startOp.isSuccess)
    assertTrue("Web socket connector is not started.", connector.isStarted())

    Thread.sleep(500)


    // Verify that the mocked handler received from the server the 'pushedMessages' number of messages.
    // Other mocked methods are not called.
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.times(pushedMessages)).onReceivedMessage(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onConnectionFailed(ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketMessageHandler, Mockito.never()).onSendMessageErrorOccurred(ArgumentMatchers.any(), ArgumentMatchers.any())
    Mockito.verify(mockedWebSocketReconnectionHandler, Mockito.never()).onDisconnection(ArgumentMatchers.any(), ArgumentMatchers.any())

    val stopOp = connector.stop()
    assertTrue(stopOp.isSuccess)
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
