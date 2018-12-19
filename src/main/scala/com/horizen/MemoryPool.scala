package com.horizen

import com.horizen.box.Box
import com.horizen.proposition.ProofOfKnowledgeProposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction
import scorex.core.ModifierId
import scorex.core.transaction.MempoolReader

import scala.collection.concurrent.TrieMap
import scala.util.Try

case class MemoryPool(unconfirmed: TrieMap[ModifierId, BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]])
  extends scorex.core.transaction.MemoryPool[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]], MemoryPool]
{
  override type NVCT <: MemoryPool

  // Getters:
  override def getById(id: ModifierId): Option[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]] = {
    unconfirmed.get(id)
  }

  override def contains(id: ModifierId): Boolean = {
    unconfirmed.contains(id)
  }

  override def getAll(ids: Seq[ModifierId]): Seq[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]] = {
    ids.flatMap(getById)
  }

  override def size: Int = {
    unconfirmed.size
  }

  override def take(limit: Int): Iterable[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]] = {
    unconfirmed.values.toSeq.sortBy(-_.fee).take(limit)
  }

  override def filter(txs: Seq[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): MemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(condition: (BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]) => Boolean): MemoryPool = {
    unconfirmed.retain { (k, v) =>
      condition(v)
    }
    this
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]] = {
    this
  }

  // Setters:
  override def put(tx: BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]): Try[MemoryPool] = {
    // check if tx is not colliding with unconfirmed using
    // tx.incompatibilityChecker().hasIncompatibleTransactions(tx, unconfirmed)
    null
  }

  override def put(txs: Iterable[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): Try[MemoryPool] = {
    // for each tx in txs call "put"
    // rollback to initial state if "put(tx)" failed
    null
  }

  // TO DO: check usage in Scorex core
  // Probably, we need to do a Global check inside for both new and existing transactions.
  override def putWithoutCheck(txs: Iterable[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): MemoryPool = ???

  override def remove(tx: BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]): MemoryPool = {
    unconfirmed.remove(tx.id)
    this
  }
}
