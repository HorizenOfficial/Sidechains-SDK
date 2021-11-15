package com.horizen.network

import akka.actor.{ActorRef, ActorSystem}
import com.horizen.fixtures.SidechainBlockInfoFixture
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.PersistentNodeViewModifier
import scorex.core.network.{ConnectedPeer, ModifiersStatus}
import scorex.util.ModifierId

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class SidechainDeliveryTrackerTest extends JUnitSuite
  with MockitoSugar
  with SidechainBlockInfoFixture {


  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvhs_mocked")
  protected val deliveryTimeout: FiniteDuration = new FiniteDuration(3,SECONDS)
  protected val maxDeliveryChecks = 2
  var dlvTracker = new SidechainDeliveryTracker(actorSystem , deliveryTimeout,maxDeliveryChecks,mock[ActorRef])

  val (peer,pmod,wrongPmod) = prepareData()
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")



  @Test
  def testSetBlockHeldWithValidModAndPeer() {
    dlvTracker.setRequested(pmod.id, pmod.modifierTypeId, Option(peer))
    var status = dlvTracker.status(pmod.id)
    assertTrue(s"status should be Requested but it is $status", status== ModifiersStatus.Requested)
    dlvTracker.setReceived(pmod.id, peer)
    status = dlvTracker.status(pmod.id)
    assertTrue(s"status should be Received but it is $status", status== ModifiersStatus.Received)
    dlvTracker.setBlockHeld(pmod.id)
    status = dlvTracker.status(pmod.id)
    val beenReceivedPeer = dlvTracker.modHadBeenReceivedFromPeer(pmod.id, pmod.modifierTypeId)
    assertTrue(s"the peer set: <$peer> should be the same of beenReceived: <$beenReceivedPeer> ",beenReceivedPeer.get==peer)
  }

  protected def prepareData(): ( ConnectedPeer, PersistentNodeViewModifier,PersistentNodeViewModifier) = {

    val modifierId: ModifierId = getRandomModifier()

    val pmod =mock[PersistentNodeViewModifier]
    Mockito.when(pmod.id).thenReturn(getRandomModifier())
    Mockito.when(pmod.modifierTypeId).thenReturn(scorex.core.ModifierTypeId @@ 3.toByte)

    val wrongPmod =mock[PersistentNodeViewModifier]
    Mockito.when(pmod.id).thenReturn(getRandomModifier())
    Mockito.when(pmod.modifierTypeId).thenReturn(scorex.core.ModifierTypeId @@ 2.toByte)

    (peer,pmod,wrongPmod)
  }




}
