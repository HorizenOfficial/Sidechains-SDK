package com.horizen

import java.util.{List => JList, ArrayList => JArrayList}

import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction
import scorex.util.ModifierId
import scorex.core.transaction.MempoolReader

import scala.collection.concurrent.TrieMap
import scala.util.{Try, Success, Failure}
import scala.collection.JavaConverters._
import com.horizen.node.NodeMemoryPool

class SidechainMemoryPool(unconfirmed: TrieMap[String, SidechainTypes#BT])
  extends scorex.core.transaction.MemoryPool[SidechainTypes#BT, SidechainMemoryPool]
 with NodeMemoryPool {
  override type NVCT = SidechainMemoryPool
  //type BT = BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]

  // Getters:
  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#BT] = {
    unconfirmed.get(modifierId)
  }

  override def contains(id: ModifierId): Boolean = {
    unconfirmed.contains(id)
  }

  override def getAll(ids: Seq[ModifierId]): Seq[SidechainTypes#BT] = {
    ids.flatMap(modifierById)
  }

  override def size: Int = {
    unconfirmed.size
  }

  override def take(limit: Int): Iterable[SidechainTypes#BT] = {
    unconfirmed.values.toSeq.sortBy(-_.fee).take(limit)
  }

  def take(sortFunc: (SidechainTypes#BT, SidechainTypes#BT) => Boolean,
           limit: Int): Iterable[SidechainTypes#BT] = {
    unconfirmed.values.toSeq.sortWith(sortFunc).take(limit)
  }

  override def filter(txs: Seq[SidechainTypes#BT]): SidechainMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(condition: (SidechainTypes#BT) => Boolean): SidechainMemoryPool = {
    unconfirmed.retain { (k, v) =>
      condition(v)
    }
    this
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#BT] = {
    this
  }

  // Setters:
  override def put(tx: SidechainTypes#BT): Try[SidechainMemoryPool] = {
    // check if tx is not colliding with unconfirmed using
    // tx.incompatibilityChecker().hasIncompatibleTransactions(tx, unconfirmed)
    if (tx.incompatibilityChecker().isMemoryPoolCompatible &&
        tx.incompatibilityChecker().isTransactionCompatible(tx, unconfirmed.values.toList.asJava)) {
      unconfirmed.put(tx.id(), tx)
      new Success[SidechainMemoryPool](this)
    }
    else
        new Failure(new IllegalArgumentException("Transaction is incompatible - " + tx))
  }

  override def put(txs: Iterable[SidechainTypes#BT]): Try[SidechainMemoryPool] = {
    // for each tx in txs call "put"
    // rollback to initial state if "put(tx)" failed
    for (t <- txs.tails) {
      if (t != Nil &&
          (!t.head.incompatibilityChecker().isMemoryPoolCompatible ||
           !t.head.incompatibilityChecker().isTransactionCompatible(t.head, t.tail.toList.asJava)))
        return new Failure(new IllegalArgumentException("There is incompatible transaction - " + t.head))
    }

    for (t <- txs) {
      if (!t.incompatibilityChecker().isTransactionCompatible(t, unconfirmed.values.toList.asJava))
        return new Failure(new IllegalArgumentException("There is incompatible transaction - " + t))
    }

    for (t <- txs)
      unconfirmed.put(t.id(), t)

    new Success[SidechainMemoryPool](this)
  }

  // TO DO: check usage in Scorex core
  // Probably, we need to do a Global check inside for both new and existing transactions.
  override def putWithoutCheck(txs: Iterable[SidechainTypes#BT]): SidechainMemoryPool = {
    for (t <- txs.tails) {
      if (t != Nil && !t.head.incompatibilityChecker().isTransactionCompatible(t.head, t.tail.toList.asJava))
        return this
    }

    for (t <- txs) {
      if (!t.incompatibilityChecker().isTransactionCompatible(t, unconfirmed.values.toList.asJava))
        return this
    }

    for (t <- txs)
      unconfirmed.put(t.id(), t)

    this
  }

  override def remove(tx: SidechainTypes#BT): SidechainMemoryPool = {
    unconfirmed.remove(tx.id)
    this
  }
}

