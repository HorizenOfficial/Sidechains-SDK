package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.mempool.MempoolMap.txSizeInSlot
import sparkz.util.ModifierId

import scala.collection.concurrent.TrieMap

/*
Thsi class contains all the transactions accepted in the mempool.
Transactions can be retrieved from the cache or by transaction id or by arrival order.
 */
class TxCache {
  // All transactions currently in the mempool
  private val all: TrieMap[ModifierId, TxMetaInfo] = TrieMap.empty[ModifierId, TxMetaInfo]
  private var sizeInSlots: Int = 0

  private var oldestTx: Option[TxMetaInfo] = None
  private var youngestTx: Option[TxMetaInfo] = None

  def add(tx: SidechainTypes#SCAT): Unit = {
    val txInfo = new TxMetaInfo(tx)
    all.put(tx.id, txInfo)
    sizeInSlots += txSizeInSlot(tx)
    if (oldestTx.isEmpty) {
      oldestTx = Some(txInfo)
      youngestTx = oldestTx
    }
    else {
      val tmp = youngestTx
      youngestTx = Some(txInfo)
      youngestTx.get.older = tmp
      tmp.get.younger = youngestTx
    }
  }

  def remove(txId: ModifierId): Option[SidechainTypes#SCAT] = {
    val txInfoOpt = all.remove(txId)
    txInfoOpt.map { txInfo =>
      (txInfo.older, txInfo.younger) match {
        case (None, None) =>
          oldestTx = None
          youngestTx = None
        case (None, younger) =>
          oldestTx = younger
          younger.get.older = None
        case (older, None) =>
          youngestTx = older
          older.get.younger = None
        case (older, younger) =>
          older.get.younger = younger
          younger.get.older = older

      }
      sizeInSlots -= txSizeInSlot(txInfo.tx)
      txInfo.tx
    }

  }

  def getTransaction(txId: ModifierId): Option[SidechainTypes#SCAT] = all.get(txId).map(_.tx)

  def apply(txId: ModifierId): SidechainTypes#SCAT = all(txId).tx

  def values: Iterable[SidechainTypes#SCAT] = all.values.map(_.tx)

  def size: Int = all.size

  def contains(txId: ModifierId): Boolean = all.contains(txId)

  def getSizeInSlots: Int = sizeInSlots

  def getOldestTransaction(): Option[SidechainTypes#SCAT] = oldestTx.map(_.tx)

  def getYoungestTransaction(): Option[SidechainTypes#SCAT] = youngestTx.map(_.tx)



}
