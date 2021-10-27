package com.horizen.network

import scorex.core.consensus.History.HistoryComparisonResult
import scorex.core.network.ConnectedPeer
import scorex.core.utils.TimeProvider.Time

case class SidechainSyncStatus(var historyCompare: HistoryComparisonResult, var otherNodeDeclaredHeight:Int, var lastTipSyncTime:Time = 0L) {

}
