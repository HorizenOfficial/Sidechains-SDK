package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.mempool.MempoolMap.txSizeInSlot
import sparkz.util.ModifierId

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap

/*
This class contains all the transactions accepted in the mempool.
Transactions can be retrieved from the cache or by transaction id or by arrival order.
 */
class TxCache {
  // All transactions currently in the mempool
  private val all: TrieMap[ModifierId, TxMetaInfo] = TrieMap.empty[ModifierId, TxMetaInfo]
  private var sizeInSlots: Int = 0
  private var nonExecSizeInSlots: Int = 0

  private var oldestTx: Option[TxMetaInfo] = None
  private var youngestTx: Option[TxMetaInfo] = None

  def add(tx: SidechainTypes#SCAT, isNonExec: Boolean): Unit = {
    val txInfo = new TxMetaInfo(tx, isNonExec)
    all.put(tx.id, txInfo)
    val txSize = txSizeInSlot(tx)
    sizeInSlots += txSize
    if (isNonExec){
      nonExecSizeInSlots += txSize
    }
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
    all.remove(txId).map { txInfo =>
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
      val txSize = txSizeInSlot(txInfo.tx)
      sizeInSlots -= txSize
      if (txInfo.isNotExecutable){
        nonExecSizeInSlots -= txSize
      }
      txInfo.tx
    }

  }


  def promoteTransaction(txId: ModifierId): Unit = {
    all.get(txId).foreach { txInfo =>
      txInfo.isNotExecutable = false
      nonExecSizeInSlots -= txSizeInSlot(txInfo.tx)
    }
  }

  def demoteTransaction(txId: ModifierId): Unit = {
    all.get(txId).foreach { txInfo =>
      txInfo.isNotExecutable = true
      nonExecSizeInSlots += txSizeInSlot(txInfo.tx)
    }
  }

  def getTransaction(txId: ModifierId): Option[SidechainTypes#SCAT] = all.get(txId).map(_.tx)

  def apply(txId: ModifierId): SidechainTypes#SCAT = all(txId).tx

  def values: Iterable[SidechainTypes#SCAT] = all.values.map(_.tx)

  def size: Int = all.size

  def contains(txId: ModifierId): Boolean = all.contains(txId)

  def getSizeInSlots: Int = sizeInSlots

  def getNonExecSizeInSlots: Int = nonExecSizeInSlots

  // Returns the transaction that has been in the mempool for the longest time
  def getOldestTransaction(): Option[SidechainTypes#SCAT] = oldestTx.map(_.tx)

  // Returns the latest transaction added to the mempool
  def getYoungestTransaction(): Option[SidechainTypes#SCAT] = youngestTx.map(_.tx)

  def getNonExecIterator(): NonExecTransactionIterator = new NonExecTransactionIterator

  case class TxMetaInfo(tx: SidechainTypes#SCAT, var isNotExecutable: Boolean) {
    var next: Option[TxMetaInfo] = None
    var previous: Option[TxMetaInfo] = None
  }

  class NonExecTransactionIterator {

    private var nextElem: Option[TxMetaInfo] = oldestTx

    @tailrec
    final private[mempool] def findNext(txInfo: Option[TxMetaInfo]): Option[TxMetaInfo] = {
      if (txInfo.isEmpty || txInfo.get.isNotExecutable)
        txInfo
      else {
        findNext(txInfo.get.next)
      }
    }

    def hasNext: Boolean = {
      findNext(nextElem).isDefined
    }

    def next: SidechainTypes#SCAT = {
      val tmp = findNext(nextElem)
      if (tmp.isEmpty) {
        nextElem = tmp
        throw new NoSuchElementException()
      } else {
        val txInfo = tmp.get
        nextElem = txInfo.next
        txInfo.tx
      }
    }

  }

}
