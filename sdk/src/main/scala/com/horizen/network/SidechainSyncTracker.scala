package com.horizen.network

import akka.actor.{ActorContext, ActorRef, Cancellable}
import scorex.core.consensus.History.{Fork, HistoryComparisonResult, Older, Unknown}
import scorex.core.network.NodeViewSynchronizer.Events.{BetterNeighbourAppeared, NoBetterNeighbour}
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SendLocalSyncInfo
import scorex.core.network.{ConnectedPeer, SyncTracker}
import scorex.core.settings.NetworkSettings
import scorex.core.utils.TimeProvider
import scorex.core.utils.TimeProvider.Time

import java.net.InetSocketAddress
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}


class SidechainSyncTracker (nvsRef: ActorRef,
                            context: ActorContext,
                            networkSettings: NetworkSettings,
                            timeProvider: TimeProvider)(implicit ec: ExecutionContext)
  extends SyncTracker(nvsRef: ActorRef,
    context: ActorContext,
    networkSettings: NetworkSettings,
    timeProvider: TimeProvider) {

  private var olderStatusesMap = mutable.Map[ConnectedPeer, SidechainSyncStatus]()
  var betterNeighbourHeight = 0
  var myHeight = 0 // DIRAC TODO update first on processSync after every succesfulModifier applied

  def updateSyncStatus(peer: ConnectedPeer, syncStatus: SidechainSyncStatus): Unit = {
    super.updateStatus(peer,syncStatus.historyCompare)
    if (syncStatus.historyCompare == Older){
      log.info(s"updating syncStatus , height = ${syncStatus.otherNodeDeclaredHeight}")
      olderStatusesMap += peer -> syncStatus
      // DIRAC not sure if correct, just to have the best height we got through the sync phase
      betterNeighbourHeight = if (syncStatus.otherNodeDeclaredHeight > betterNeighbourHeight) syncStatus.otherNodeDeclaredHeight
                              else betterNeighbourHeight
    }
  }

  def updateStatusWithLastSyncTime(peer:ConnectedPeer, time: Time): Unit = {
    olderStatusesMap(peer).lastTipSyncTime = time
  }

  def updateStatusWithNeighbourHeight(peer:ConnectedPeer, height: Int): Unit = {
    olderStatusesMap(peer).otherNodeDeclaredHeight = height
  }

  def updateStatusWithNeighbourHeight(peer:ConnectedPeer, compared: HistoryComparisonResult): Unit = {
    olderStatusesMap(peer).historyCompare = compared
  }
}
