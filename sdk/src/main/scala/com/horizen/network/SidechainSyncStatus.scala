package com.horizen.network

import scorex.core.consensus.History.HistoryComparisonResult
import scorex.core.utils.TimeProvider.Time

case class SidechainSyncStatus(var historyCompare: HistoryComparisonResult,
                               var otherNodeDeclaredHeight:Int,
                               var myOwnHeight: Int,
                               var lastTipSyncTime:Time = 0L)

case class SidechainFailedSync( var reasonToFail: Throwable,
                                var failedSyncTime:Time = 0L)