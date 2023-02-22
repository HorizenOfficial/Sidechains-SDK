package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.mempool.MempoolMap.txSizeInSlot
import sparkz.util.ModifierId

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}

class TxCache(txLifetime: FiniteDuration = 3.hours) {
  // All transactions currently in the mempool
  private val all: TrieMap[ModifierId, TxMetaInfo] = TrieMap.empty[ModifierId, TxMetaInfo]
  private var sizeInSlots: Int = 0
  private var nonExecSizeInSlots: Int = 0

  private var oldestTx: Option[TxMetaInfo] = None
  private var youngestTx: Option[TxMetaInfo] = None

  def add(tx: SidechainTypes#SCAT, isNonExec: Boolean): Unit = {
    val txInf0 = TxMetaInfo(tx, isNonExec)
    all.put(tx.id, txInf0)
    val txSize = txSizeInSlot(tx)
    sizeInSlots += txSize
    if (isNonExec){
      nonExecSizeInSlots += txSize
    }
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
    val txInfoOpt = all.remove(txId)
    txInfoOpt.map { txInfo =>
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

  def getOldestTransactionInfo(): Option[TxMetaInfo] = oldestTx

  def getNonExecIterator(): NonExecTransactionIterator = new NonExecTransactionIterator

  case class TxMetaInfo(tx: SidechainTypes#SCAT, var isNotExecutable: Boolean) {
    val deadline: Deadline = txLifetime.fromNow
    var next: Option[TxMetaInfo] = None
    var previous: Option[TxMetaInfo] = None

    def hasTimedOut: Boolean = deadline.isOverdue()
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
