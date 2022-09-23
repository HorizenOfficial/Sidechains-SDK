package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.AccountStateReader
import com.horizen.account.transaction.EthereumTransaction
import scorex.util.{ModifierId, ScorexLogging}

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Try

class MempoolMap(stateReader: AccountStateReader) extends ScorexLogging {
  type TxIdByNonceMap = mutable.SortedMap[BigInteger, ModifierId]

  // All transactions currently in the mempool
  private val all: TrieMap[ModifierId, SidechainTypes#SCAT] = TrieMap[ModifierId, SidechainTypes#SCAT]()
  // Transaction ids of all currently processable transactions, grouped by account and ordered by nonce
  private val executableTxs: TrieMap[SidechainTypes#SCP, TxIdByNonceMap] = TrieMap[SidechainTypes#SCP, TxIdByNonceMap]()
  // Transaction ids of all transactions that cannot be executed because they are "orphans", grouped by account and ordered by nonce
  private val nonExecutableTxs: TrieMap[SidechainTypes#SCP, TxIdByNonceMap] = TrieMap[SidechainTypes#SCP, TxIdByNonceMap]()
  // Next expected nonce for each account. It is the max nonce of the executable txs plus 1, if any, otherwise it has the same value of the statedb nonce.
  private val nonces: TrieMap[SidechainTypes#SCP, BigInteger] = TrieMap[SidechainTypes#SCP, BigInteger]()

  def add(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    require(ethTransaction.isInstanceOf[EthereumTransaction], "Transaction is not EthereumTransaction")
    val account = ethTransaction.getFrom
    if (!nonces.contains(account)) {
      nonces.put(account, stateReader.getNonce(account.asInstanceOf[AddressProposition].address()))
    }

    if (!contains(ethTransaction.id)) {

      val expectedNonce = nonces(account)
      if (expectedNonce.equals(ethTransaction.getNonce)) {
        all.put(ethTransaction.id, ethTransaction)
        val executableTxsPerAccount = executableTxs.getOrElse(account, {
          val txsPerAccountMap = new mutable.TreeMap[BigInteger, ModifierId]()
          executableTxs.put(account, txsPerAccountMap)
          txsPerAccountMap
        })
        executableTxsPerAccount.put(ethTransaction.getNonce, ethTransaction.id)
        var nextNonce = expectedNonce.add(BigInteger.ONE)
        nonExecutableTxs.get(account).foreach(txs => {
          while (txs.contains(nextNonce)) {
            val promotedTxId = txs.remove(nextNonce).get
            executableTxsPerAccount.put(nextNonce, promotedTxId)
            nextNonce = nextNonce.add(BigInteger.ONE)
          }
          if (txs.isEmpty) {
            nonExecutableTxs.remove(account)
          }
        }

        )
        nonces.put(account, nextNonce)

      }
      else if (expectedNonce.compareTo(ethTransaction.getNonce) < 0) {
        val listOfTxs = nonExecutableTxs.getOrElse(account, {
          val txsPerAccountMap = new mutable.TreeMap[BigInteger, ModifierId]()
          nonExecutableTxs.put(account, txsPerAccountMap)
          txsPerAccountMap
        })
        if (listOfTxs.contains(ethTransaction.getNonce)) {
          val oldTxId = listOfTxs(ethTransaction.getNonce)
          val oldTx = all(oldTxId)
          if (ethTransaction.canPayHigherFee(oldTx)) {
            log.trace(s"Replacing transaction $oldTx with $ethTransaction")
            all.remove(oldTxId)
            all.put(ethTransaction.id, ethTransaction)
            listOfTxs.put(ethTransaction.getNonce, ethTransaction.id)
          }
        }
        else {
          all.put(ethTransaction.id, ethTransaction)
          listOfTxs.put(ethTransaction.getNonce, ethTransaction.id)
        }
      }
      else {
        val listOfTxs = executableTxs(account)
        val oldTxId = listOfTxs(ethTransaction.getNonce)
        val oldTx = all(oldTxId)
        if (ethTransaction.canPayHigherFee(oldTx)) {
          log.trace(s"Replacing transaction $oldTx with $ethTransaction")
          all.remove(oldTxId)
          all.put(ethTransaction.id, ethTransaction)
          listOfTxs.put(ethTransaction.getNonce, ethTransaction.id)
        }

      }
    }
    this
  }

  def remove(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    if (all.remove(ethTransaction.id).isDefined) {
      if (nonces(ethTransaction.getFrom).compareTo(ethTransaction.getNonce) < 0){
        nonExecutableTxs.get(ethTransaction.getFrom).foreach(nonExecTxsPerAccount => {
            nonExecTxsPerAccount.remove(ethTransaction.getNonce)
            if (nonExecTxsPerAccount.isEmpty) {
              nonExecutableTxs.remove(ethTransaction.getFrom)
              if (!executableTxs.contains(ethTransaction.getFrom)) {
                nonces.remove(ethTransaction.getFrom)
              }
            }
        })
       }
      else {
        executableTxs.get(ethTransaction.getFrom).foreach( execTxsPerAccount => {
          execTxsPerAccount.remove(ethTransaction.getNonce)
          nonces.put(ethTransaction.getFrom, ethTransaction.getNonce)
          var demotedTxId = execTxsPerAccount.remove(ethTransaction.getNonce.add(BigInteger.ONE))
          while (demotedTxId.isDefined) {
            val demotedTx = all(demotedTxId.get)
            val nonExecTxsPerAccount = nonExecutableTxs.getOrElse(ethTransaction.getFrom, {
              val txsPerAccountMap = new mutable.TreeMap[BigInteger, ModifierId]()
              nonExecutableTxs.put(ethTransaction.getFrom, txsPerAccountMap)
              txsPerAccountMap
            })
            nonExecTxsPerAccount.put(demotedTx.getNonce, demotedTxId.get)
            demotedTxId = execTxsPerAccount.remove(demotedTx.getNonce.add(BigInteger.ONE))
          }
          if (execTxsPerAccount.isEmpty) {
            executableTxs.remove(ethTransaction.getFrom)
            if (!nonExecutableTxs.contains(ethTransaction.getFrom)) {
              nonces.remove(ethTransaction.getFrom)
            }
          }
        })
       }
    }
    this
  }

  def size: Int = all.size

  def getTransaction(txId: ModifierId): Option[SidechainTypes#SCAT] = all.get(txId)

  def contains(txId: ModifierId): Boolean = all.contains(txId)

  def values: Iterable[SidechainTypes#SCAT] = all.values

  /**
   * Take n executable transactions sorted by gas tip (descending)
   */
  def takeExecutableTxs(limit: Int): Iterable[SidechainTypes#SCAT] = {

    def txOrder(tx: SidechainTypes#SCAT) = {
      if (tx.isInstanceOf[EthereumTransaction]){
        val ethTx = tx.asInstanceOf[EthereumTransaction]
        ethTx.getMaxFeePerGas.subtract(stateReader.baseFee).min(ethTx.getMaxPriorityFeePerGas)
      }
      else
        tx.getGasPrice.subtract(stateReader.baseFee)
    }

    val orderedQueue = new mutable.PriorityQueue[SidechainTypes#SCAT]()(Ordering.by(txOrder))
    executableTxs.foreach { case (_, listOfTxsPerAccount) =>
      val tx = getTransaction(listOfTxsPerAccount.values.head).get
      orderedQueue.enqueue(tx)
    }
    val txs = new ListBuffer[SidechainTypes#SCAT]()

    var i = 1
    while (i <= limit && orderedQueue.nonEmpty) {
      val bestTx = orderedQueue.dequeue()
      txs.append(bestTx)
      val nextTxIdOpt = executableTxs(bestTx.getFrom).get(bestTx.getNonce.add(BigInteger.ONE))
      if (nextTxIdOpt.isDefined) {
        val tx = getTransaction(nextTxIdOpt.get).get
        orderedQueue.enqueue(tx)
      }
      i += 1
    }
    txs
  }

}
