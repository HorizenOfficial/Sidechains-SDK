package com.horizen.network

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.fixtures.SidechainBlockInfoFixture
import com.horizen.validation.{BlockInFutureException, InconsistentDataException, InvalidBlockException, InvalidSidechainBlockHeaderException}
import org.junit.{After, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SyntacticallyFailedModification
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ModifierId
import org.junit.Assert.{assertEquals, assertTrue}
import scorex.core.network.{ConnectedPeer, ConnectionId, Incoming}
import scorex.core.network.NetworkController.ReceivableMessages.{PenalizePeer, RegisterMessageSpecs}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.language.postfixOps

class SidechainNodeViewSynchronizerTest extends JUnitSuite
  with MockitoSugar
  with SidechainBlockInfoFixture {

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvhs_mocked")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")

  val (nodeViewSynchronizerRef, deliveryTracker, block, peer, networkControllerProbe) = prepareData()

  @Test
  def onSyntacticallyFailedModification(): Unit = {
    var setUnknownExecuted: Boolean = false


    // Test 1: BlockInFutureException exception processing
    val blockInFutureException = new BlockInFutureException("block in future exception")

    val promise = Promise[Unit]()
    Mockito.when(deliveryTracker.setUnknown(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      setUnknownExecuted = true
      promise.success(Unit)
    })

    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, blockInFutureException)

    Await.result(promise.future, 2 seconds)

    // Check that sender was not penalize
    networkControllerProbe.expectNoMessage()
    // Check that block was set to Unknown -> no ban
    assertTrue("Delivery tracker expected to set block id as Unknown.", setUnknownExecuted)



    // Test 2: InconsistentDataException exception processing
    setUnknownExecuted = false
    val inconsistentDataException = new InconsistentDataException("inconsistent data exception")

    Mockito.reset(deliveryTracker)
    Mockito.when(deliveryTracker.peerInfo(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      Some(peer)
    })

    Mockito.when(deliveryTracker.setUnknown(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      setUnknownExecuted = true
    })

    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, inconsistentDataException)

    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Unknown -> no ban
    assertTrue("Delivery tracker expected to set block id as Unknown.", setUnknownExecuted)


    // Test 3: Any other exception processing
    var setInvalidExecuted = false
    Mockito.reset(deliveryTracker)
    Mockito.when(deliveryTracker.setInvalid(ArgumentMatchers.any[ModifierId])).thenAnswer(answer => {
      val receivedId: ModifierId = answer.getArgument(0)
      assertEquals("Different block id expected.", block.id, receivedId)
      setInvalidExecuted = true
      Some(peer)
    })

    // Test on InvalidSidechainBlockHeaderException
    val sidechainBlockHeaderInvalidException = new InvalidSidechainBlockHeaderException("block header invalid exception")
    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, sidechainBlockHeaderInvalidException)
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Invalid -> ban
    assertTrue("Delivery tracker expected to set block id as Invalid.", setInvalidExecuted)

    // Test on InvalidBlockException
    setInvalidExecuted = false
    val invalidDataException = new InvalidBlockException("invalid data exception")
    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, invalidDataException)
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Invalid -> ban
    assertTrue("Delivery tracker expected to set block id as Invalid.", setInvalidExecuted)

    // Test on IllegalArgumentException
    setInvalidExecuted = false
    val otherException = new IllegalArgumentException("other exception")
    nodeViewSynchronizerRef ! SyntacticallyFailedModification(block, otherException)
    // Check that sender was penalize
    networkControllerProbe.expectMsgType[PenalizePeer]
    // Check that block was set to Invalid -> ban
    assertTrue("Delivery tracker expected to set block id as Invalid.", setInvalidExecuted)
  }



  @After
  def afterAll(): Unit = {
    actorSystem.terminate()
  }


  protected def prepareData(): (ActorRef, SidechainDeliveryTracker, SidechainBlock, ConnectedPeer, TestProbe) = {
    val networkControllerProbe = TestProbe()
    val viewHolderProbe = TestProbe()
    val scorexSettings: ScorexSettings = ScorexSettings.read(Some(getClass.getClassLoader.getResource("sc_node_holder_fixter_settings.conf").getFile))
    val timeProvider = new NetworkTimeProvider(scorexSettings.ntp)


    val peer = ConnectedPeer(ConnectionId(new InetSocketAddress(10), new InetSocketAddress(11), Incoming), mock[ActorRef], None)
    val tracker: SidechainDeliveryTracker = mock[SidechainDeliveryTracker]

    val nodeViewSynchronizerRef = actorSystem.actorOf(Props(
      new SidechainNodeViewSynchronizer(networkControllerProbe.ref, viewHolderProbe.ref, SidechainSyncInfoMessageSpec, scorexSettings.network, timeProvider, Map()) {
        override protected val deliveryTracker: SidechainDeliveryTracker = tracker
      }))

    networkControllerProbe.expectMsgType[RegisterMessageSpecs]

    val modifierId: ModifierId = getRandomModifier()
    val block = mock[SidechainBlock]
    Mockito.when(block.id).thenReturn(modifierId)

    (nodeViewSynchronizerRef, tracker, block, peer, networkControllerProbe)
  }
}
