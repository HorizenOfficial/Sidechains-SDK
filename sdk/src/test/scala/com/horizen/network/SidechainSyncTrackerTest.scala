package com.horizen.network


import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.fixtures.SidechainBlockInfoFixture
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{After, Test}
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.PersistentNodeViewModifier
import scorex.core.consensus.History.{Older, Younger}
import scorex.core.network.NetworkController.ReceivableMessages.RegisterMessageSpecs
import scorex.core.network.{ConnectedPeer, ConnectionId, Incoming}
import scorex.core.settings.{NetworkSettings, ScorexSettings}
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ModifierId

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class SidechainSyncTrackerTest extends JUnitSuite
  with MockitoSugar
  with SidechainBlockInfoFixture {


  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvhs_mocked")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")

  protected val deliveryTimeout: FiniteDuration = new FiniteDuration(3, SECONDS)
  protected val maxDeliveryChecks = 2
  val (nodeViewSynchronizerRef, timeProvider, deliveryTracker, block, peer, peer_2, networkControllerProbe, pmod, spySync) = prepareData()

  @Test
  def syncTrackerUpdateWithOneOlderTest(): Unit = {
    var anOlder = SidechainSyncStatus(Older, 32, 12)
    spySync.updateSyncStatus(peer, anOlder)
    var olderMap = spySync.olderStatusesMap
    var stat = olderMap.get(peer)
    assertEquals(s"the status $stat should be equal to $anOlder", stat.get, anOlder)
    assertEquals("the declared height of the other nodeshould be 32", olderMap(peer).otherNodeDeclaredHeight, 32)
    assertEquals("myownHeight should be set to 12", olderMap(peer).myOwnHeight, 12)
    assertEquals("betterNeighbour height is 32", spySync.betterNeighbourHeight, 32)
    assertEquals("my height is 12", spySync.myHeight, 12)
    anOlder = SidechainSyncStatus(Older, 27, 15)
    spySync.updateSyncStatus(peer_2, anOlder)
    olderMap = spySync.olderStatusesMap
    stat = olderMap.get(peer_2)
    println(stat.toList.toString())
    assertEquals("Myheight should be 15", spySync.myHeight, 15)
    assertEquals("betterNeighoburheight should be  32", spySync.betterNeighbourHeight, 32)
    anOlder = SidechainSyncStatus(Older, 42, 18)
    spySync.updateSyncStatus(peer_2, anOlder)
    assertEquals("myheight should be 18", spySync.myHeight, 18)
    assertEquals("betterNeighoburheight should be  42", spySync.betterNeighbourHeight, 42)
  }

  @Test
  def syncTrackerUpdateWithOneYoungerTest(): Unit = {
    val younger = SidechainSyncStatus(Younger, 32, 37)
    var olderMap = spySync.olderStatusesMap
    spySync.updateSyncStatus(peer, younger)
    var stat = olderMap.get(peer)
    olderMap = spySync.olderStatusesMap
    stat = olderMap.get(peer)
    assertTrue(s"the status $stat should be equal to None", stat == None)
    assertEquals("", spySync.myHeight, -1)
    assertEquals("", spySync.betterNeighbourHeight, -1)
  }

  @Test
  def testUpdateStatusWithMyHeight() = {
    var anOlder = SidechainSyncStatus(Older, 32, 12)
    spySync.updateSyncStatus(peer, anOlder)
    spySync.updateStatusWithMyHeight(peer)
    assertEquals("my height should be increased at 13 now", spySync.myHeight, 13)
    assertEquals("in the map myOwnHeight should be increased at 13 now", spySync.olderStatusesMap(peer).myOwnHeight, 13)

  }


  @Test
  def testUpdateForFailing() {
    val failTime = timeProvider.time
    val failedSync = SidechainFailedSync(mock[Throwable], failTime)
    spySync.updateForFailing(peer, failedSync)
    val failMap = spySync.failedStatusesMap
    assertEquals("failedStatusesMap should contain the fail of that peer", failMap(peer), failedSync)
    assertEquals("the fail should have the time of the fail", failMap(peer).failedSyncTime, failTime)
  }

  @Test
  def testUpdateStatusWithLastSyncTime() {
    var anOlder = SidechainSyncStatus(Older, 32, 12)
    spySync.updateSyncStatus(peer, anOlder)
    val syncTime = timeProvider.time
    spySync.updateStatusWithLastSyncTime(peer, syncTime)
    assertEquals("", spySync.olderStatusesMap(peer).lastTipSyncTime, syncTime)

  }


  @After
  def afterAll(): Unit = {
    actorSystem.terminate()
  }

  protected def prepareData(): (ActorRef, NetworkTimeProvider, SidechainDeliveryTracker, SidechainBlock, ConnectedPeer, ConnectedPeer, TestProbe, PersistentNodeViewModifier, SidechainSyncTracker) = {
    val networkControllerProbe = TestProbe()
    val viewHolderProbe = TestProbe()
    val scorexSettings: ScorexSettings = ScorexSettings.read(Some(getClass.getClassLoader.getResource("sc_node_holder_fixter_settings.conf").getFile))
    val timeProvider = new NetworkTimeProvider(scorexSettings.ntp)

    val peer = ConnectedPeer(ConnectionId(new InetSocketAddress(10), new InetSocketAddress(11), Incoming), mock[ActorRef], None)
    val peer_2 = ConnectedPeer(ConnectionId(new InetSocketAddress(110), new InetSocketAddress(111), Incoming), mock[ActorRef], None)
    val tracker: SidechainDeliveryTracker = mock[SidechainDeliveryTracker]

    val nodeViewSynchronizerRef = actorSystem.actorOf(Props(
      new SidechainNodeViewSynchronizer(networkControllerProbe.ref, viewHolderProbe.ref, SidechainSyncInfoMessageSpec, scorexSettings.network, timeProvider, Map()) {
        override protected val deliveryTracker: SidechainDeliveryTracker = tracker
      }))
    networkControllerProbe.expectMsgType[RegisterMessageSpecs]


    val actorCtx = mock[ActorContext]
    Mockito.when(actorCtx.system).thenReturn(actorSystem)

    val syncTracker = new SidechainSyncTracker(nodeViewSynchronizerRef, actorCtx, mock[NetworkSettings], mock[NetworkTimeProvider])
    val spySync = spy(syncTracker)
    val modifierId: ModifierId = getRandomModifier()
    val block = mock[SidechainBlock]
    Mockito.when(block.id).thenReturn(modifierId)

    val pmod = mock[PersistentNodeViewModifier]
    Mockito.when(pmod.id).thenReturn(modifierId)
    Mockito.when(pmod.modifierTypeId).thenReturn(scorex.core.ModifierTypeId @@ 3.toByte)

    (nodeViewSynchronizerRef, timeProvider, tracker, block, peer, peer_2, networkControllerProbe, pmod, spySync)
  }


}
