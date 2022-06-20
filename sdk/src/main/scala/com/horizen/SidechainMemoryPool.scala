package com.horizen

import java.util.{Comparator, Optional, List => JList}
import com.horizen.box.Box
import com.horizen.node.NodeMemoryPool
import com.horizen.transaction.BoxTransaction
import com.horizen.utils.FeeRate
import scorex.util.ModifierId
import scorex.core.transaction.MempoolReader

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class SidechainMemoryPool(unconfirmed: TrieMap[String, SidechainMemoryPoolEntry])
  extends scorex.core.transaction.MemoryPool[SidechainTypes#SCBT, SidechainMemoryPool]
  with SidechainTypes
  with NodeMemoryPool
{
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

  override def filter(condition: SidechainTypes#SCBT => Boolean): SidechainMemoryPool = {
    unconfirmed.retain { (k, v) =>
      condition(v.getUnconfirmedTx())
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
    if (tx.incompatibilityChecker().isMemoryPoolCompatible &&
        tx.incompatibilityChecker().isTransactionCompatible(tx, unconfirmed.values.toList.map(t => t.getUnconfirmedTx()).asJava)) {
      unconfirmed.put(tx.id, SidechainMemoryPoolEntry(tx))
      Success[SidechainMemoryPool](this)
    }
    else
        Failure(new IllegalArgumentException("Transaction is incompatible - " + tx))
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
      unconfirmed.put(t.id, SidechainMemoryPoolEntry(t))
    }

    new Success[SidechainMemoryPool](this)
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
      unconfirmed.put(t.id, SidechainMemoryPoolEntry(t))
    }

    this
  }

  override def remove(tx: SidechainTypes#SCBT): SidechainMemoryPool = {
    unconfirmed.remove(tx.id)
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

  override def getTransactionById(transactionId: String): Optional[BoxTransaction[SCP, Box[SCP]]] = {
    Optional.ofNullable(unconfirmed.get(transactionId) match {
      case Some(mempoolEntry) => mempoolEntry.getUnconfirmedTx()
      case None => null
    })
  }
}

object SidechainMemoryPool
{
  lazy val emptyPool : SidechainMemoryPool = new SidechainMemoryPool(TrieMap())
}

