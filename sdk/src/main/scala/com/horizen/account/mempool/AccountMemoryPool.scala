package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.node.NodeAccountMemoryPool
import scorex.core.transaction.MempoolReader
import scorex.util.ModifierId

import java.util.{Comparator, Optional, List => JList}
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

class AccountMemoryPool(unconfirmed: TrieMap[String, SidechainTypes#SCAT])
  extends scorex.core.transaction.MemoryPool[SidechainTypes#SCAT, AccountMemoryPool]
  with SidechainTypes
  with NodeAccountMemoryPool
{
  override type NVCT = AccountMemoryPool

  // Getters:
  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#SCAT] = {
    unconfirmed.get(modifierId)
  }

  override def contains(id: ModifierId): Boolean = {
    unconfirmed.contains(id)
  }

  override def getAll(ids: Seq[ModifierId]): Seq[SidechainTypes#SCAT] = {
    ids.flatMap(modifierById)
  }

  override def size: Int = {
    unconfirmed.size
  }

  override def take(limit: Int): Iterable[SidechainTypes#SCAT] = {
    // we have no fee to sort it with
    unconfirmed.values.toSeq.take(limit)
  }

  def take(sortFunc: (SidechainTypes#SCAT, SidechainTypes#SCAT) => Boolean,
           limit: Int): Iterable[SidechainTypes#SCAT] = {
    unconfirmed.values.toSeq.sortWith(sortFunc).take(limit)
  }

  override def filter(txs: Seq[SidechainTypes#SCAT]): AccountMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(condition: SidechainTypes#SCAT => Boolean): AccountMemoryPool = {
    unconfirmed.retain { (k, v) =>
      condition(v)
    }
    this
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#SCAT] = {
    this
  }

  def isTransactionCompatible(tx: SidechainTypes#SCAT, txList: Seq[SidechainTypes#SCAT]) : Boolean = {
    val from = tx.getFrom
    val nonce = tx.getNonce

    // we must not have txes with the same from-account-address and nonce
    !unconfirmed.values.toList.exists(x => {
      x.getFrom == from && x.getNonce == nonce
    })
  }

  // Setters:
  override def put(tx: SidechainTypes#SCAT): Try[AccountMemoryPool] = {
    // check if tx is not colliding with unconfirmed
    if (!isTransactionCompatible(tx, unconfirmed.values.toList)) {
      Failure(new IllegalArgumentException("Transaction is incompatible - " + tx))
    } else {
      unconfirmed.put(tx.id, tx)
      Success[AccountMemoryPool](this)
    }
  }

  override def put(txs: Iterable[SidechainTypes#SCAT]): Try[AccountMemoryPool] = {
    // for each tx in txs check compatibility with the trailers elements
    for (tx <- txs.tails) {
      if (tx != Nil) {

        if (!isTransactionCompatible(tx.head, unconfirmed.values.toList))
          return Failure(new IllegalArgumentException("There is an incompatible transaction - " + tx.head))
      }
    }

    // ok, no incompatibility among input elements, add them to mempool and stop if any incompatibility is there
    for (t <- txs) {
      put(t)
    }

    new Success[AccountMemoryPool](this)
  }

  // TO DO: check usage in Scorex core
  // Probably, we need to do a Global check inside for both new and existing transactions.
  override def putWithoutCheck(txs: Iterable[SidechainTypes#SCAT]): AccountMemoryPool = {
    for (t <- txs.tails) {
      if (t != Nil && !isTransactionCompatible(t.head, t.tail.toList))
        return this
    }

    for (t <- txs) {
      if (!isTransactionCompatible(t, unconfirmed.values.toList))
        return this
    }

    for (t <- txs)
      unconfirmed.put(t.id, t)

    this
  }

  override def remove(tx: SidechainTypes#SCAT): AccountMemoryPool = {
    unconfirmed.remove(tx.id)
    this
  }

  override def getTransactions: JList[SidechainTypes#SCAT] = {
    unconfirmed.values.toList.asJava
  }

  override def getTransactions(c: Comparator[SidechainTypes#SCAT], limit: Int): JList[SidechainTypes#SCAT] = {
    val txs = unconfirmed.values.toList.asJava
    txs.sort(c)
    txs.subList(0, limit)
  }

   override def getSize: Int = unconfirmed.size

  override def getTransactionById(transactionId: String): Optional[SidechainTypes#SCAT] = {
    Optional.ofNullable(unconfirmed.getOrElse(transactionId, null))
  }
}

object AccountMemoryPool
{
  lazy val emptyPool : AccountMemoryPool = new AccountMemoryPool(TrieMap())
}

