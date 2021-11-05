package com.horizen.network

import akka.actor.{ActorRef, ActorSystem}
import scorex.core.ModifierTypeId
import scorex.core.network.ModifiersStatus.{Received, Requested}
import scorex.core.network.{ConnectedPeer, DeliveryTracker, ModifiersStatus}
import scorex.util.ModifierId
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class SidechainDeliveryTracker(system: ActorSystem,
                               deliveryTimeout: FiniteDuration,
                               maxDeliveryChecks: Int,
                               nvsRef: ActorRef)
  extends DeliveryTracker(system, deliveryTimeout, maxDeliveryChecks, nvsRef) {

  private case class CheckSyncProgress(connectedPeer: ConnectedPeer, modIds: Seq[ModifierId])

  protected val beenReceivedFrom: mutable.Map[ModifierId, ConnectedPeer] = mutable.Map()

  def peerInfo(id: ModifierId): Option[ConnectedPeer] = {
    val modifierStatus: ModifiersStatus = status(id)
    modifierStatus match {
      case Requested =>
        requested.get(id).flatMap(_.peer)
      case Received =>
        received.get(id)
      case _ =>
        None
    }
  }

  def modHadBeenReceivedFromPeer(id:ModifierId,typeId: ModifierTypeId ):Option[ConnectedPeer] = {
    log.info(s"modHadBeenReceivedFromPeer: I had Received <$id> from the peer: ${beenReceivedFrom.get(id)}")
    beenReceivedFrom.get(id)
  }

  def setBlockHeld(id: ModifierId): Unit ={
    val peerFromWhichIReceived = received.get(id)
    peerFromWhichIReceived match {
      case Some(peer) =>
        log.info(s"moving to my receivedFrom before to setHeld.. $id from peer: ${peerFromWhichIReceived.get}")
        beenReceivedFrom.put(id, peer)
      case None =>
    }
    super.setHeld(id)
  }

}
