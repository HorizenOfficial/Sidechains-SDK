package com.horizen

import java.util.{Comparator, Optional, ArrayList => JArrayList, List => JList}

import com.horizen.box.Box
import com.horizen.node.NodeMemoryPool
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction
import scorex.util.ModifierId
import scorex.core.transaction.MempoolReader

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class SidechainMemoryPool(unconfirmed: TrieMap[String, SidechainTypes#SCBT])
  extends scorex.core.transaction.MemoryPool[SidechainTypes#SCBT, SidechainMemoryPool]
  with SidechainTypes
  with NodeMemoryPool
{
  override type NVCT = SidechainMemoryPool
  //type BT = BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]

  // Getters:
  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#SCBT] = {
    unconfirmed.get(modifierId)
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

  override def take(limit: Int): Iterable[SidechainTypes#SCBT] = {
    unconfirmed.values.toSeq.sortBy(-_.fee).take(limit)
  }

  def take(sortFunc: (SidechainTypes#SCBT, SidechainTypes#SCBT) => Boolean,
           limit: Int): Iterable[SidechainTypes#SCBT] = {
    unconfirmed.values.toSeq.sortWith(sortFunc).take(limit)
  }

  override def filter(txs: Seq[SidechainTypes#SCBT]): SidechainMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(condition: SidechainTypes#SCBT => Boolean): SidechainMemoryPool = {
    unconfirmed.retain { (k, v) =>
      condition(v)
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
        tx.incompatibilityChecker().isTransactionCompatible(tx, unconfirmed.values.toList.asJava)) {
      unconfirmed.put(tx.id, tx)
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

    val currentUnconfimed = unconfirmed.values.toList.asJava
    for (t <- txs) {
      if (!t.incompatibilityChecker().isTransactionCompatible(t, currentUnconfimed))
        return Failure(new IllegalArgumentException("There is incompatible transaction - " + t))
    }

    for (t <- txs)
      unconfirmed.put(t.id, t)

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
      if (!t.incompatibilityChecker().isTransactionCompatible(t, unconfirmed.values.toList.asJava))
        return this
    }

    for (t <- txs)
      unconfirmed.put(t.id, t)

    this
  }

  override def remove(tx: SidechainTypes#SCBT): SidechainMemoryPool = {
    unconfirmed.remove(tx.id)
    this
  }

  override def getTransactions: JList[SidechainTypes#SCBT] = {
    unconfirmed.values.toList.asJava
  }

  override def getTransactions(c: Comparator[SidechainTypes#SCBT], limit: Int): JList[SidechainTypes#SCBT] = {
    val txs = unconfirmed.values.toList.asJava
    txs.sort(c)
    txs.subList(0, limit)
  }

  override def getTransactionsSortedByFee(limit: Int): JList[SidechainTypes#SCBT] = {
    unconfirmed.values.toList.sortBy(-_.fee).take(limit).asJava
  }

  override def getSize: Int = unconfirmed.size

  override def getTransactionById(transactionId: String): Optional[BoxTransaction[SCP, Box[SCP]]] = {
    Optional.ofNullable(unconfirmed.getOrElse(transactionId, null))
  }
}

object SidechainMemoryPool
{
  lazy val emptyPool : SidechainMemoryPool = new SidechainMemoryPool(TrieMap())
}

