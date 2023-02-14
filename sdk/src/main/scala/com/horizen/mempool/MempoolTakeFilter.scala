package com.horizen.mempool
import com.horizen.SidechainTypes

/**
 * Filter that can be used to take tx from the mempool.
 * For every tx in the mempool evaluateTx is called.
 * If we have multiple filters applied, all of them are called.
 * If every filter returns true, the tx is taken, and accumulateTx is called
 * @see SidechainMemoryPool.takeWithFilterLimit
 */
trait MempoolTakeFilter {
  def evaluateTx(tx: SidechainTypes#SCBT): Boolean
  def accumulateTx(tx: SidechainTypes#SCBT)
}
