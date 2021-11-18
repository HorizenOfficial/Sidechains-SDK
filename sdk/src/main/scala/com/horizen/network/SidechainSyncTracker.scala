package com.horizen.network

import akka.actor.{ActorContext, ActorRef}
import scorex.core.consensus.History.Older
import scorex.core.network.{ConnectedPeer, SyncTracker}
import scorex.core.settings.NetworkSettings
import scorex.core.utils.TimeProvider
import scorex.core.utils.TimeProvider.Time

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext


class SidechainSyncTracker (nvsRef: ActorRef,
                            context: ActorContext,
                            networkSettings: NetworkSettings,
                            timeProvider: TimeProvider)(implicit ec: ExecutionContext)
  extends SyncTracker(nvsRef: ActorRef,
    context: ActorContext,
    networkSettings: NetworkSettings,
    timeProvider: TimeProvider) {

  private[network] val NO_VALUE = -1
  private[network] var olderStatusesMap = mutable.Map[ConnectedPeer, SidechainSyncStatus]()
  private[network] var failedStatusesMap = mutable.Map[ConnectedPeer,ListBuffer[SidechainFailedSync]]()

  def updateSyncStatus(peer: ConnectedPeer, syncStatus: SidechainSyncStatus): Unit = {
    updateStatus(peer,syncStatus.historyCompare)
    if (syncStatus.historyCompare == Older){
      if(olderStatusesMap.contains(peer)){ // update only the heights
        olderStatusesMap(peer).otherNodeDeclaredHeight = syncStatus.otherNodeDeclaredHeight
      }
      else{
        olderStatusesMap += peer -> syncStatus
      }
    }else{
      if(olderStatusesMap.contains(peer)){
        olderStatusesMap.remove(peer)
      }
    }
  }

  def updateForFailing(peer: ConnectedPeer, sidechainFailedSync: SidechainFailedSync):Unit ={
      if(!failedStatusesMap.contains(peer)){
        failedStatusesMap += peer -> ListBuffer(sidechainFailedSync)
      }else{
        failedStatusesMap(peer).append(sidechainFailedSync)
      }
  }

  def updateStatusWithLastSyncTime(peer:ConnectedPeer, time: Time): Unit = {
    if(olderStatusesMap.contains(peer)){
      olderStatusesMap(peer).lastTipSyncTime = time
    }
  }

  def lastTipTimeOfThe(peer: ConnectedPeer): Time ={
    if(olderStatusesMap.contains(peer)){
      olderStatusesMap(peer).lastTipSyncTime
    }else{
      NO_VALUE
    }
  }

  def getTimeFromlastPeerGivingTip():Time = {
    if(olderStatusesMap.size == 0) return NO_VALUE
    olderStatusesMap.map(elem => elem._2.lastTipSyncTime ).max
  }

  def isRecent(lastTipSyncTime: Time): Boolean = {
    // let's make 15 seconds
    if((timeProvider.time() - lastTipSyncTime) > 15000)
      false
    else
      true
  }

  def getMaxHeightFromBetterNeighbours:Int = {
    if(olderStatusesMap.size == 0) return NO_VALUE
    olderStatusesMap.filter(elem => elem._2.lastTipSyncTime != NO_VALUE && isRecent(elem._2.lastTipSyncTime)).map(_._2.otherNodeDeclaredHeight).max
  }

}


object SidechainSyncTracker {


}