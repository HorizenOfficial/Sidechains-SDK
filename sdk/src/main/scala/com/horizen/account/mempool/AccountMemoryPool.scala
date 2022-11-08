package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.node.NodeAccountMemoryPool
import sparkz.core.transaction.MempoolReader
import scorex.util.{ModifierId, ScorexLogging}
import com.horizen.account.state.AccountStateReader

import java.util
import java.util.{Comparator, Optional}
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success, Try}

class AccountMemoryPool(
                         unconfirmed: MempoolMap,
                         stateReaderProvider: AccountStateReaderProvider
                       ) extends sparkz.core.transaction.MemoryPool[
  SidechainTypes#SCAT,
  AccountMemoryPool
]
  with SidechainTypes
  with NodeAccountMemoryPool
  with ScorexLogging {
  override type NVCT = AccountMemoryPool

  // Getters:
  override def modifierById(
                             modifierId: ModifierId
                           ): Option[SidechainTypes#SCAT] = {
    unconfirmed.getTransaction(modifierId)
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
    unconfirmed.takeExecutableTxs(limit)
  }

  override def filter(txs: Seq[SidechainTypes#SCAT]): AccountMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(
      condition: SidechainTypes#SCAT => Boolean
  ): AccountMemoryPool = {
    val filteredTxs = unconfirmed.values.filter(tx => condition(tx))
    //Reset everything
    val newMemPool = AccountMemoryPool.createEmptyMempool(stateReaderProvider)
    filteredTxs.foreach(tx => newMemPool.put(tx))
    newMemPool
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#SCAT] = this

  // Setters:

  override def put(tx: SidechainTypes#SCAT): Try[AccountMemoryPool] = {
    Try {
      new AccountMemoryPool(unconfirmed.add(tx).get, stateReaderProvider)
    }
  }

  override def put(
      txs: Iterable[SidechainTypes#SCAT]
  ): Try[AccountMemoryPool] = {
    Try {
      for (t <- txs) {
        put(t)
      }
      this
    }
  }

  override def putWithoutCheck(
      txs: Iterable[SidechainTypes#SCAT]
  ): AccountMemoryPool = ???

  override def remove(tx: SidechainTypes#SCAT): AccountMemoryPool = {
    unconfirmed.remove(tx) match {
      case Success(mempoolMap) => new AccountMemoryPool(mempoolMap, stateReaderProvider)
      case Failure(e) =>
        log.error(s"Exception while removing transaction $tx from MemPool", e)
        throw e
    }
  }

  override def getTransactions: util.List[SidechainTypes#SCAT] =
    unconfirmed.values.toList.asJava

  override def getTransactions(
      c: Comparator[SidechainTypes#SCAT],
      limit: Int
  ): util.List[SidechainTypes#SCAT] = {
    val txs = getTransactions
    txs.sort(c)
    txs.subList(0, limit)
  }

  override def getSize: Int = size

  override def getTransactionById(
      transactionId: String
  ): Optional[SidechainTypes#SCAT] = {
    Optional.ofNullable(
      unconfirmed.getTransaction(ModifierId @@ transactionId).orNull
    )
  }

  def updateMemPool(removedBlocks: Seq[AccountBlock], appliedBlocks: Seq[AccountBlock]): AccountMemoryPool = {
    unconfirmed.updateMemPool(removedBlocks, appliedBlocks)
    new AccountMemoryPool(unconfirmed, stateReaderProvider)
  }
}

object AccountMemoryPool {
  def createEmptyMempool(stateReaderProvider: AccountStateReaderProvider): AccountMemoryPool = {
    new AccountMemoryPool(new MempoolMap(stateReaderProvider), stateReaderProvider)
  }
}

trait AccountStateReaderProvider {
  def getAccountStateReader(): AccountStateReader

}
