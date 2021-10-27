package com.horizen.network

import akka.actor.{ActorRef, ActorSystem, Props, Timers}
import com.horizen.SidechainSyncInfoMessageSpec
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.core.consensus.History.HistoryComparisonResult
import scorex.core.network.ModifiersStatus.{Held, Invalid, Received, Requested, Unknown}
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.CheckDelivery
import scorex.core.network.{ConnectedPeer, DeliveryTracker, ModifiersStatus}
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.NetworkSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ModifierId

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.{Failure, Try}

class SidechainDeliveryTracker(system: ActorSystem,
                               deliveryTimeout: FiniteDuration,
                               maxDeliveryChecks: Int,
                               nvsRef: ActorRef)
  extends DeliveryTracker(system, deliveryTimeout, maxDeliveryChecks, nvsRef) {


  private case class CheckSyncProgress(connectedPeer: ConnectedPeer, modIds: Seq[ModifierId])

  protected val beenRequestedTo: mutable.Map[ModifierId, ConnectedPeer] = mutable.Map()
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

// **********  these methods are to be called from SC_NVS for checking the origin of the applied mod

  def modHadBeenRequestedFromPeer( id:ModifierId,typeId: ModifierTypeId ):Option[ConnectedPeer] = {
    log.info(s"modHadBeenRequestedFromPeer: I had Received <$id> from the peer: ${beenReceivedFrom.get(id)}")
    beenRequestedTo.get(id)
  }

  def modHadBeenReceivedFromPeer(id:ModifierId,typeId: ModifierTypeId ):Option[ConnectedPeer] = {
    log.info(s"modHadBeenReceivedFromPeer: I had Received <$id> from the peer: ${beenReceivedFrom.get(id)}")
    beenReceivedFrom.get(id)
  }

// **********  these methods are Overridden for keeping info before they disappear

  override def setReceived(id: ModifierId, sender: ConnectedPeer): Unit = {
    log.info(s"moving to my requestedTo before to setReceived $id from peer: $sender")
    beenRequestedTo.put(id,sender)
    // work as before
    super.setReceived(id,sender)
  }

  override def setHeld(id: ModifierId): Unit ={
    val peerFromWhichIReceived = received.get(id).get
    log.info(s"moving to my receivedFrom before to setHeld.. $id from peer: $peerFromWhichIReceived")
    beenReceivedFrom.put(id, peerFromWhichIReceived)
    // work as before
    super.setHeld(id)
  }

}





















/*
  1) I could send the [0] elements of the sequence to startSyncStatusTimer and checkin that is applied after 5 seconds

  



 */