package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.mempool.MempoolMap.txSizeInSlot
import sparkz.util.ModifierId

import scala.collection.concurrent.TrieMap

class TxCache {
  // All transactions currently in the mempool
  private val all: TrieMap[ModifierId, TxMetaInfo] = TrieMap.empty[ModifierId, TxMetaInfo]
  private var sizeInSlots: Int = 0

  private var oldestTx: Option[TxMetaInfo] = None
  private var youngestTx: Option[TxMetaInfo] = None

  def add(tx: SidechainTypes#SCAT): Unit = {
    val txInf0 = TxMetaInfo(tx)
    all.put(tx.id, txInf0)
    sizeInSlots += txSizeInSlot(tx)
    if (oldestTx.isEmpty) {
      oldestTx = Some(txInf0)
      youngestTx = oldestTx
    }
    else {
      val tmp = youngestTx
      youngestTx = Some(txInf0)
      youngestTx.get.previous = tmp
      tmp.get.next = youngestTx
    }
  }

  def remove(txId: ModifierId): Option[SidechainTypes#SCAT] = {
    all.remove(txId).map { txInfo =>
      val prev = txInfo.previous
      val next = txInfo.next
      if (prev.isEmpty && next.isEmpty){
        oldestTx = None
        youngestTx = None
      }
      else if (prev.isEmpty) {
        oldestTx = next
        next.get.previous = None
      }
      else if (next.isEmpty) {
        youngestTx = prev
        prev.get.next = None
      }
      else {
        prev.get.next = next
        next.get.previous = prev
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


  case class TxMetaInfo(tx: SidechainTypes#SCAT) {
    var next: Option[TxMetaInfo] = None
    var previous: Option[TxMetaInfo] = None
  }

}
