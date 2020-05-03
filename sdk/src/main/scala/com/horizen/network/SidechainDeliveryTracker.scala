package com.horizen.network

import akka.actor.{ActorRef, ActorSystem}
import scorex.core.network.ModifiersStatus.{Received, Requested}
import scorex.core.network.{ConnectedPeer, DeliveryTracker, ModifiersStatus}
import scorex.util.ModifierId

import scala.concurrent.duration.FiniteDuration

class SidechainDeliveryTracker(system: ActorSystem,
                               deliveryTimeout: FiniteDuration,
                               maxDeliveryChecks: Int,
                               nvsRef: ActorRef)
  extends DeliveryTracker(system, deliveryTimeout, maxDeliveryChecks, nvsRef) {

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
}
