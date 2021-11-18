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
import scorex.core.network.NodeViewSynchronizer.Events.BetterNeighbourAppeared
import scorex.core.network.{ConnectedPeer, ConnectionId, Incoming, Outgoing}
import scorex.core.settings.{NetworkSettings, ScorexSettings}
import scorex.core.utils.{NetworkTimeProvider, TimeProvider}
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
  val (nodeViewSynchronizerRef, timeProvider, deliveryTracker, block, peer, peer_2, peer_3,networkControllerProbe, pmod, spySync) = prepareData()

  @Test
  def syncTrackerUpdateWithAnotherOlderPeerTest(): Unit = {
    var anOlder = SidechainSyncStatus(Older, 32, 12)
    spySync.updateSyncStatus(peer, anOlder)
    var olderMap = spySync.olderStatusesMap
    var stat = olderMap.get(peer)
    assertEquals(s"the status $stat should be equal to $anOlder", stat.get, anOlder)
    assertEquals("the declared height of the other nodeshould be 32", olderMap(peer).otherNodeDeclaredHeight, 32)
    assertEquals("myownHeight should be set to 12", olderMap(peer).myOwnHeight, 12)
    anOlder = SidechainSyncStatus(Older, 27, 15)
    spySync.updateSyncStatus(peer_2, anOlder)
    olderMap = spySync.olderStatusesMap
    stat = olderMap.get(peer_2)
    println(stat.toList.toString())
    anOlder = SidechainSyncStatus(Older, 42, 18)
    spySync.updateSyncStatus(peer_2, anOlder)
    assertEquals("the declared height of the other nodeshould be 42", olderMap(peer_2).otherNodeDeclaredHeight, 42)
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
  }


  @Test
  def testUpdateForFailing() {
    val track = new SidechainSyncTracker(mock[ActorRef],mock[ActorContext],mock[NetworkSettings],mock[TimeProvider])

    val failTime = timeProvider.time
    val failedSync = SidechainFailedSync(mock[Throwable], failTime)
    val failedSync_2 = SidechainFailedSync(mock[Throwable], failTime + 1345)
    val failedSync_3 = SidechainFailedSync(mock[Throwable], failTime + 5448)
    track.updateForFailing(peer, failedSync)
    track.updateForFailing(peer, failedSync_2)
    track.updateForFailing(peer, failedSync_3)
    track.updateForFailing(peer_2, failedSync)
    track.updateForFailing(peer_2, failedSync_2)
    track.updateForFailing(peer_3, failedSync_3)
    val failMap = track.failedStatusesMap
    //assertEquals("failedStatusesMap should contain the fail of that peer", failMap(peer), failedSync)
    assertEquals("the size should be 3 but is found as failMap(peer).size", 3, failMap(peer).size)
    assertEquals(s"the failTime should be ${failedSync_2.failedSyncTime} be but it is ${failMap(peer_2)(1).failedSyncTime}",
                  failedSync_2.failedSyncTime, failMap(peer_2)(1).failedSyncTime)
  }

  @Test
  def testUpdateStatusWithLastSyncTime() {
    var anOlder = SidechainSyncStatus(Older, 32, 12)
    spySync.updateSyncStatus(peer, anOlder)
    val syncTime = timeProvider.time
    spySync.updateStatusWithLastSyncTime(peer, syncTime)
    assertEquals("", spySync.olderStatusesMap(peer).lastTipSyncTime, syncTime)
  }


  @Test
  def lastTipFromPeerTimeTest(): Unit = {

    val track = new SidechainSyncTracker(mock[ActorRef],mock[ActorContext],mock[NetworkSettings],mock[TimeProvider])
    val anOlder = SidechainSyncStatus(Older, 32, 12)
    val anOlder_2 = SidechainSyncStatus(Older, 52, 12)
    val anOlder_3 = SidechainSyncStatus(Older, 62, 12)

    // #### TEST 1 - EMPTY ####
    var lastTip = track.getTimeFromlastPeerGivingTip()
    assertEquals(s"lastTip should be -1L but is $lastTip",-1L,lastTip)

    // FILLING- put directly the peers to avoid Mock issues :)
    track.olderStatusesMap += peer -> anOlder
    track.olderStatusesMap += peer_2 -> anOlder_2
    track.olderStatusesMap += peer_3 -> anOlder_3
    track.updateStatusWithLastSyncTime(peer,1637144585490L)
    track.updateStatusWithLastSyncTime(peer_2,1637144585522L)
    track.updateStatusWithLastSyncTime(peer_3,1637144585380L)

    // #### TEST 2 - FILLED MAP ####
    lastTip = track.getTimeFromlastPeerGivingTip()
    assertEquals(s"lastTip should be 1637144585522L but is $lastTip",1637144585522L,lastTip)
  }

  @Test
  def getMaxHeightFromBetterNeighboursTest:Unit = {
    val track = new SidechainSyncTracker(mock[ActorRef],mock[ActorContext],mock[NetworkSettings],mock[TimeProvider])
    val anOlder = SidechainSyncStatus(Older, 32, 12)
    val anOlder_2 = SidechainSyncStatus(Older, 52, 12)
    val anOlder_3 = SidechainSyncStatus(Older, 62, 12)

    track.olderStatusesMap += peer -> anOlder
    track.olderStatusesMap += peer_2 -> anOlder_2
    track.olderStatusesMap += peer_3 -> anOlder_3
    track.updateStatusWithLastSyncTime(peer,1637144585490L)
    track.updateStatusWithLastSyncTime(peer_2,1637144585522L)
    track.updateStatusWithLastSyncTime(peer_3,1637144585380L)

    val betterHeight = track.getMaxHeightFromBetterNeighbours

    assertEquals(s"betterHeight should be 62 but is $betterHeight",62,betterHeight)



  }




  @After
  def afterAll(): Unit = {
    actorSystem.terminate()
  }

  protected def prepareData(): (ActorRef, NetworkTimeProvider, SidechainDeliveryTracker, SidechainBlock, ConnectedPeer, ConnectedPeer,ConnectedPeer, TestProbe, PersistentNodeViewModifier, SidechainSyncTracker) = {
    val networkControllerProbe = TestProbe()
    val viewHolderProbe = TestProbe()
    val scorexSettings: ScorexSettings = ScorexSettings.read(Some(getClass.getClassLoader.getResource("sc_node_holder_fixter_settings.conf").getFile))
    val timeProvider = new NetworkTimeProvider(scorexSettings.ntp)

    val peer = ConnectedPeer(ConnectionId(new InetSocketAddress(10), new InetSocketAddress(11), Incoming), mock[ActorRef], None)
    val peer_2 = ConnectedPeer(ConnectionId(new InetSocketAddress(110), new InetSocketAddress(111), Outgoing), mock[ActorRef], None)
    val peer_3 = ConnectedPeer(ConnectionId(new InetSocketAddress(210), new InetSocketAddress(211), Incoming), mock[ActorRef], None)

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

    (nodeViewSynchronizerRef, timeProvider, tracker, block, peer, peer_2, peer_3, networkControllerProbe, pmod, spySync)
  }


}
