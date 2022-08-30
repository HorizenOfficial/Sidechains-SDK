package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.node.NodeAccountMemoryPool
import com.horizen.account.state.AccountStateReader
import scorex.core.transaction.MempoolReader
import scorex.util.ModifierId

import java.math.BigInteger
import java.util.{Comparator, Optional, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Try

class AccountMemoryPool(unconfirmed: MempoolMap, stateReader: AccountStateReader)
  extends scorex.core.transaction.MemoryPool[SidechainTypes#SCAT, AccountMemoryPool]
    with SidechainTypes
    with NodeAccountMemoryPool {
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
      val tx = unconfirmed.all.get(p._2.values.head)
      orderedQueue.enqueue(tx.get)
    })
    val txs = new ListBuffer[SidechainTypes#SCAT]()

    var i = 1
    while (i <= limit && !orderedQueue.isEmpty) {
      val bestTx = orderedQueue.dequeue()
      txs.append(bestTx)
      val nextTxIdOpt = unconfirmed.executableTxs.get(bestTx.getFrom).get.get(bestTx.getNonce.add(BigInteger.ONE))
      if (nextTxIdOpt.isDefined) {
        val tx = unconfirmed.all.get(nextTxIdOpt.get)
        orderedQueue.enqueue(tx.get)
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
      //Check if the same tx is already present
      if (unconfirmed.all.contains(tx.id)) {
        this
      }
      else {

        var isRejected = false
        if (!unconfirmed.nonces.contains(tx.getFrom)) {
          val stateNonce = stateReader.getNonce(tx.getFrom.bytes())
          val txsPerAccountMap = new mutable.TreeMap[BigInteger, ModifierId]()
          txsPerAccountMap.put(tx.getNonce, tx.id)
          if (stateNonce.equals(tx.getNonce)) {
            unconfirmed.executableTxs.put(tx.getFrom, txsPerAccountMap)
            unconfirmed.nonces.put(tx.getFrom, stateNonce.add(BigInteger.ONE))
          }
          else {
            unconfirmed.nonExecutableTxs.put(tx.getFrom, txsPerAccountMap)
            unconfirmed.nonces.put(tx.getFrom, stateNonce)
          }
        }
        else {
          val expectedNonce = unconfirmed.nonces.get(tx.getFrom).get
          if (expectedNonce.equals(tx.getNonce)) {
            val executableTxsMap = unconfirmed.executableTxs.get(tx.getFrom).getOrElse(new mutable.TreeMap[BigInteger, ModifierId]())
            executableTxsMap.put(tx.getNonce, tx.id)
            var nextNonce = expectedNonce.add(BigInteger.ONE)
            //Check if some non executable tx can be promoted
            unconfirmed.nonExecutableTxs.get(tx.getFrom).foreach(txs => {
              while (txs.contains(nextNonce)) {
                val promotedTxId = txs.remove(nextNonce).get
                executableTxsMap.put(nextNonce, promotedTxId)
                nextNonce = nextNonce.add(BigInteger.ONE)
              }
              if (txs.isEmpty) {
                unconfirmed.nonExecutableTxs.remove(tx.getFrom)
              }
            }
            )
            unconfirmed.executableTxs.put(tx.getFrom, executableTxsMap)
            unconfirmed.nonces.put(tx.getFrom, nextNonce)

          }
          else if (tx.getNonce.compareTo(expectedNonce) >= 0) {

            val map = unconfirmed.nonExecutableTxs.get(tx.getFrom).getOrElse(new mutable.TreeMap[BigInteger, ModifierId]())
            val oldTxIdOpt = map.get(tx.getNonce)
            if (!oldTxIdOpt.isDefined || (unconfirmed.all.get(oldTxIdOpt.get).get.getGasPrice.compareTo(tx.getGasPrice) < 0)) {
              map.put(tx.getNonce, tx.id)
              unconfirmed.nonExecutableTxs.put(tx.getFrom, map)
              unconfirmed.all.remove(oldTxIdOpt.get)
            }
            else {
              //There is already a better tx, so this one is rejected
              isRejected = true
            }

          }
          else { //Another tx with the same nonce is already in the executable list
            val map = unconfirmed.executableTxs.get(tx.getFrom).get
            val oldTx = unconfirmed.all.get(map.get(tx.getNonce).get).get
            if (oldTx.getGasPrice.compareTo(tx.getGasPrice) < 0) {
              map.put(tx.getNonce, tx.id)
              unconfirmed.all.remove(oldTx.id)
            }
            else
              isRejected = true
          }

        }

        if (!isRejected)
          unconfirmed.all.put(tx.id, tx)

        this
      }


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
    if (unconfirmed.all.remove(tx.id).isDefined) {
      if (!unconfirmed.nonExecutableTxs.get(tx.getFrom).forall(txsMap => txsMap.remove(tx.getNonce).isDefined)) {
        val executableMap = unconfirmed.executableTxs.get(tx.getFrom).get
        executableMap.remove(tx.getNonce)
        unconfirmed.nonces.put(tx.getFrom, tx.getNonce)
        var demotedTxId = executableMap.remove(tx.getNonce.add(BigInteger.ONE))
        while (demotedTxId.isDefined) {
          val demotedTx = unconfirmed.all.get(demotedTxId.get).get
          unconfirmed.nonExecutableTxs.get(tx.getFrom).get.put(demotedTx.getNonce, demotedTxId.get)
          demotedTxId = executableMap.remove(demotedTx.getNonce.add(BigInteger.ONE))
        }
      }
    }
    this
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

