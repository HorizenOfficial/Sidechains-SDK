package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.node.NodeAccountMemoryPool
import com.horizen.account.state.AccountStateReader
import com.horizen.account.transaction.EthereumTransaction
import scorex.core.transaction.MempoolReader
import scorex.util.{ModifierId, ScorexLogging}

import java.math.BigInteger
import java.util.{Comparator, Optional, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class AccountMemoryPool(unconfirmed: MempoolMap, stateReader: AccountStateReader)
  extends scorex.core.transaction.MemoryPool[SidechainTypes#SCAT, AccountMemoryPool]
    with SidechainTypes
    with NodeAccountMemoryPool
    with ScorexLogging{
  override type NVCT = AccountMemoryPool

  // Getters:
  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#SCAT] = {
    unconfirmed.all.get(modifierId)
  }

  override def contains(id: ModifierId): Boolean = {
    unconfirmed.all.contains(id)
  }

  override def getAll(ids: Seq[ModifierId]): Seq[SidechainTypes#SCAT] = {
    ids.flatMap(modifierById)
  }

  override def size: Int = {
    unconfirmed.all.size
  }

  override def take(limit: Int): Iterable[SidechainTypes#SCAT] = {

    def txOrder(tx: SidechainTypes#SCAT) = tx.getGasPrice //TODO it should be effectiveGasTip

    val orderedQueue = new mutable.PriorityQueue[SidechainTypes#SCAT]()(Ordering.by(txOrder))
    unconfirmed.executableTxs.foreach(p => {
      val tx = unconfirmed.all(p._2.values.head)
      orderedQueue.enqueue(tx)
    })
    val txs = new ListBuffer[SidechainTypes#SCAT]()

    var i = 1
    while (i <= limit && !orderedQueue.isEmpty) {
      val bestTx = orderedQueue.dequeue()
      txs.append(bestTx)
      val nextTxIdOpt = unconfirmed.executableTxs(bestTx.getFrom).get(bestTx.getNonce.add(BigInteger.ONE))
      if (nextTxIdOpt.isDefined) {
        val tx = unconfirmed.all(nextTxIdOpt.get)
        orderedQueue.enqueue(tx)
      }
      i += 1
    }
    txs
  }


  override def filter(txs: Seq[SidechainTypes#SCAT]): AccountMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(condition: SidechainTypes#SCAT => Boolean): AccountMemoryPool = {
    unconfirmed.all.retain { (k, v) =>
      condition(v)
    }
    //Reset everything
    val newMemPool = AccountMemoryPool.createEmptyMempool(stateReader)
    unconfirmed.all.values.foreach(tx => newMemPool.put(tx))
    newMemPool
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#SCAT] = {
    this
  }

  // Setters:

  override def put(tx: SidechainTypes#SCAT): Try[AccountMemoryPool] = {
    Try {

      if (tx.isInstanceOf[EthereumTransaction]) {
        val ethTx = tx.asInstanceOf[EthereumTransaction]
        if (!unconfirmed.containsAccountInfo(ethTx.getFrom)) {
          val stateNonce = stateReader.getNonce(ethTx.getFrom.address())
          unconfirmed.initializeAccount(stateNonce, ethTx.getFrom)
        }
        new AccountMemoryPool(unconfirmed.add(ethTx).get, stateReader)
      }
      else
        this
    }

  }

  override def put(txs: Iterable[SidechainTypes#SCAT]): Try[AccountMemoryPool] = {
    Try {
      for (t <- txs) {
        put(t)
      }

      this
    }
  }

  override def putWithoutCheck(txs: Iterable[SidechainTypes#SCAT]): AccountMemoryPool = {
    for (t <- txs)
      unconfirmed.all.put(t.id, t)

    this
  }

  override def remove(tx: SidechainTypes#SCAT): AccountMemoryPool = {
    unconfirmed.remove(tx) match {
      case Success(mempoolMap) => new AccountMemoryPool(mempoolMap, stateReader)
      case Failure(e) =>
        log.error(s"Exception while removing transaction ${tx} from MemPool", e)
        throw e
    }
  }

  override def getTransactions: JList[SidechainTypes#SCAT] = {
    unconfirmed.all.values.toList.asJava
  }

  override def getTransactions(c: Comparator[SidechainTypes#SCAT], limit: Int): JList[SidechainTypes#SCAT] = {
    val txs = unconfirmed.all.values.toList.asJava
    txs.sort(c)
    txs.subList(0, limit)
  }

  override def getSize: Int = unconfirmed.all.size

  override def getTransactionById(transactionId: String): Optional[SidechainTypes#SCAT] = {
    Optional.ofNullable(unconfirmed.all.getOrElse(ModifierId @@ transactionId, null))
  }
}

object AccountMemoryPool {
  def createEmptyMempool(stateReader: AccountStateReader): AccountMemoryPool = {
    new AccountMemoryPool(MempoolMap(), stateReader)
  }
}

