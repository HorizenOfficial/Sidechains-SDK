package io.horizen.account.mempool

import io.horizen.SidechainTypes
import io.horizen.account.mempool.MempoolMap.txSizeInSlot
import io.horizen.account.mempool.TxExecutableStatus.TxExecutableStatus
import sparkz.util.ModifierId

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

/*
This class contains all the transactions accepted in the mempool.
Transactions can be retrieved from the cache or by transaction id or by arrival order.
 */
class TxCache(txLifetime: FiniteDuration) {
  // All transactions currently in the mempool
  private val all: TrieMap[ModifierId, TxMetaInfo] = TrieMap.empty[ModifierId, TxMetaInfo]
  private var sizeInSlots: Int = 0
  private var nonExecSizeInSlots: Int = 0

  private var oldestTx: Option[TxMetaInfo] = None
  private var youngestTx: Option[TxMetaInfo] = None

  def add(tx: SidechainTypes#SCAT, execStatus: TxExecutableStatus): Unit = {
    val txInfo = new TxMetaInfo(tx, execStatus, txLifetime)
    all.put(tx.id, txInfo)
    val txSize = txSizeInSlot(tx)
    sizeInSlots += txSize
    if (execStatus == TxExecutableStatus.NON_EXEC){
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
      if (txInfo.executableStatus == TxExecutableStatus.NON_EXEC){
        nonExecSizeInSlots -= txSize
      }
      txInfo.tx
    }

  }


  def promoteTransaction(txId: ModifierId): SidechainTypes#SCAT = {
    all.get(txId) match {
      case Some(txInfo) =>
        txInfo.executableStatus = TxExecutableStatus.EXEC
        nonExecSizeInSlots -= txSizeInSlot(txInfo.tx)
        txInfo.tx
      case None => throw new IllegalStateException("Trying to promote a non existent transaction")
    }
  }

  def demoteTransaction(txId: ModifierId): Unit = {
    all.get(txId).foreach { txInfo =>
      txInfo.executableStatus = TxExecutableStatus.NON_EXEC
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

  class NonExecTransactionIterator {

    private var nextElem: Option[TxMetaInfo] = oldestTx

    @tailrec
    final private[mempool] def findNext(txInfo: Option[TxMetaInfo]): Option[TxMetaInfo] = {
      if (txInfo.isEmpty || txInfo.get.executableStatus == TxExecutableStatus.NON_EXEC)
        txInfo
      else {
        findNext(txInfo.get.younger)
      }
    }

    def hasNext: Boolean = {
      findNext(nextElem).isDefined
    }

    def next: SidechainTypes#SCAT = {
      findNext(nextElem) match {
        case None =>
          nextElem = None
          throw new NoSuchElementException()
        case Some(txInfo) =>
          nextElem = txInfo.younger
          txInfo.tx
      }

    }

  }

}
