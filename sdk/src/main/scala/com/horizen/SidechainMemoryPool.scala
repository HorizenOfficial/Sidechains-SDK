package com.horizen

import java.util.{Comparator, Optional, List => JList}
import com.horizen.box.Box
import com.horizen.node.NodeMemoryPool
import com.horizen.transaction.BoxTransaction
import com.horizen.utils.FeeRate
import scorex.util.ModifierId
import scorex.util.{ModifierId, ScorexLogging}
import scorex.core.transaction.MempoolReader

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class SidechainMemoryPool(unconfirmed: TrieMap[String, SidechainMemoryPoolEntry], mempoolSettings: MempoolSettings)
  extends scorex.core.transaction.MemoryPool[SidechainTypes#SCBT, SidechainMemoryPool]
  with SidechainTypes
  with NodeMemoryPool
  with ScorexLogging
{
  var maxPoolSizeBytes : Long =  mempoolSettings.maxSize * 1024 * 1024
  var usedPoolSizeBytes : Long = 0
  val minFeeRate : Long = mempoolSettings.minFeeRate

  override type NVCT = SidechainMemoryPool
  //type BT = BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]

  // Getters:
  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#SCBT] = {
    unconfirmed.get(modifierId) match {
      case Some(tx) => Some(tx.getUnconfirmedTx())
      case None => None
    }
  }

  override def contains(id: ModifierId): Boolean = {
    unconfirmed.contains(id)
  }

  override def getAll(ids: Seq[ModifierId]): Seq[SidechainTypes#SCBT] = {
    ids.flatMap(modifierById)
  }

  override def size: Int = {
    unconfirmed.size
  }

  // define a custom sorting func based on fee rate
  def getTransactionsSortedByFeeRate: (SidechainMemoryPoolEntry, SidechainMemoryPoolEntry) => Boolean = (a: SidechainMemoryPoolEntry, b: SidechainMemoryPoolEntry) =>
  {
    a.feeRate > b.feeRate
  }


  override def getTransactionsSortedByFee(limit: Int): JList[SidechainTypes#SCBT] = {
    unconfirmed.values.toList.sortBy(-_.getUnconfirmedTx().fee).take(limit).map(tx => tx.getUnconfirmedTx()).asJava
  }

  override def take(limit: Int): Iterable[SidechainTypes#SCBT] = {
    unconfirmed.values.toSeq.sortWith(getTransactionsSortedByFeeRate).take(limit).map(tx => tx.getUnconfirmedTx())
  }

  def take(sortFunc: (SidechainMemoryPoolEntry, SidechainMemoryPoolEntry) => Boolean,
           limit: Int): Iterable[SidechainTypes#SCBT] = {
    unconfirmed.values.toSeq.sortWith(sortFunc).take(limit).map(tx => tx.getUnconfirmedTx())
  }

  override def filter(txs: Seq[SidechainTypes#SCBT]): SidechainMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  /**
   * Filters the txs cointained in the mempool, recalculates the used bytes, and return an instance of itself.
   * @param condition to use to filter the transactions
   */
  override def filter(condition: SidechainTypes#SCBT => Boolean): SidechainMemoryPool = {
    usedPoolSizeBytes = 0
    unconfirmed.retain { (k, v) =>
      condition(v.getUnconfirmedTx())
      if (condition(v.getUnconfirmedTx())){
        usedPoolSizeBytes = usedPoolSizeBytes + v.getTxFeeRate().getSize()
        true
      }else{
        false
      }
    }
    this
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#SCBT] = {
    this
  }

  // Setters:
  override def put(tx: SidechainTypes#SCBT): Try[SidechainMemoryPool] = {
    // check if tx is not colliding with unconfirmed using
    // tx.incompatibilityChecker().hasIncompatibleTransactions(tx, unconfirmed)
    val txFeeRate = new FeeRate(tx.fee(), tx.size())
    if (txFeeRate.getFeeRate() < minFeeRate) {
       Failure(new IllegalArgumentException("Transaction fee is less than mempool.minFeeRate - " + tx))
    } else if (tx.incompatibilityChecker().isMemoryPoolCompatible &&
        tx.incompatibilityChecker().isTransactionCompatible(tx, unconfirmed.values.toList.map(t => t.getUnconfirmedTx()).asJava)) {
      if (addWithSizeCheck(SidechainMemoryPoolEntry(tx, txFeeRate)))
        Success[SidechainMemoryPool](this)
      else
        Failure(new IllegalArgumentException("Mempool full and tx feeRate too low, unable to add transaction - " + tx))
    } else {
        Failure(new IllegalArgumentException("Transaction is incompatible - " + tx))
    }
  }


  override def put(txs: Iterable[SidechainTypes#SCBT]): Try[SidechainMemoryPool] = {
    // for each tx in txs call "put"
    // rollback to initial state if "put(tx)" failed
    for (t <- txs.tails) {
      if (t != Nil &&
          (!t.head.incompatibilityChecker().isMemoryPoolCompatible ||
           !t.head.incompatibilityChecker().isTransactionCompatible(t.head, t.tail.toList.asJava)))
        return Failure(new IllegalArgumentException("There is incompatible transaction - " + t.head))
    }

    val currentUnconfimed = unconfirmed.values.toList.map(t => t.getUnconfirmedTx()).asJava
    for (t <- txs) {
      if (!t.incompatibilityChecker().isTransactionCompatible(t, currentUnconfimed))
        return Failure(new IllegalArgumentException("There is incompatible transaction - " + t))
    }

    for (t <- txs) {
      val txFeeRate = new FeeRate(t.fee(), t.size())
      if (txFeeRate.getFeeRate() >= minFeeRate) {
        addWithSizeCheck(SidechainMemoryPoolEntry(t, txFeeRate))
      }
    }
    new Success[SidechainMemoryPool](this)
  }

  /**
   * Add tx to the mempool.
   * Lowest fee-rate txs are removed in case max mempool size is reached.
   * @return true if the transaction has been inserted succesfully
   */
  def addWithSizeCheck(entry: SidechainMemoryPoolEntry) : Boolean = {
    val orderedEntries = unconfirmed.values.toSeq.sortWith((a,b) => {
      a.getTxFeeRate().getFeeRate() < b.getTxFeeRate().getFeeRate()
    })
    while (usedPoolSizeBytes + entry.getTxFeeRate().getSize() > maxPoolSizeBytes){
      val lastEntry = orderedEntries.take(1).last
      if (lastEntry.getTxFeeRate().getFeeRate() > entry.getTxFeeRate().getFeeRate()){
        //the pool is full, and the entry we are trying to add has feerate lower than the miminum in pool,
        //so the insert will fail
        return false;
      }
      remove(lastEntry.getUnconfirmedTx())
    }
    unconfirmed.put(entry.getUnconfirmedTx().id(), entry)
    usedPoolSizeBytes = usedPoolSizeBytes + entry.getTxFeeRate().getSize()
    true
  }

  // TO DO: check usage in Scorex core
  // Probably, we need to do a Global check inside for both new and existing transactions.
  override def putWithoutCheck(txs: Iterable[SidechainTypes#SCBT]): SidechainMemoryPool = {
    for (t <- txs.tails) {
      if (t != Nil && !t.head.incompatibilityChecker().isTransactionCompatible(t.head, t.tail.toList.asJava))
        return this
    }

    for (t <- txs) {
      if (!t.incompatibilityChecker().isTransactionCompatible(t, unconfirmed.values.toList.map(t => t.getUnconfirmedTx()).asJava))
        return this
    }

    for (t <- txs) {
      val txFeeRate = new FeeRate(t.fee(), t.size())
      if (txFeeRate.getFeeRate() >= minFeeRate) {
        addWithSizeCheck(SidechainMemoryPoolEntry(t, txFeeRate))
      }
    }

    this
  }

  override def remove(tx: SidechainTypes#SCBT): SidechainMemoryPool = {
    unconfirmed.remove(tx.id)
    usedPoolSizeBytes = usedPoolSizeBytes - tx.size()
    this
  }

  override def getTransactions: JList[SidechainTypes#SCBT] = {
    unconfirmed.values.map(el => el.getUnconfirmedTx()).toList.asJava
  }

  override def getTransactions(c: Comparator[SidechainTypes#SCBT], limit: Int): JList[SidechainTypes#SCBT] = {
    val txs = unconfirmed.values.toList.map(tx => tx.getUnconfirmedTx()).asJava
    txs.sort(c)
    txs.subList(0, limit)
  }

  override def getSize: Int = unconfirmed.size

  def getUsedSizeKb: Int = {
    Math.round(usedPoolSizeBytes/1024)
  }

  def getUsedPercentage: Int = {
    Math.round((usedPoolSizeBytes*100)/maxPoolSizeBytes)
  }

  override def getTransactionById(transactionId: String): Optional[BoxTransaction[SCP, Box[SCP]]] = {
    Optional.ofNullable(unconfirmed.get(transactionId) match {
      case Some(mempoolEntry) => mempoolEntry.getUnconfirmedTx()
      case None => null
    })
  }
}

object SidechainMemoryPool
{
  def createEmptyMempool(mempoolSettings: MempoolSettings) : SidechainMemoryPool = {
    new SidechainMemoryPool(TrieMap(), mempoolSettings)
  }
}

