package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.transaction.EthereumTransaction
import scorex.util.{ModifierId, ScorexLogging}

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Try

class MempoolMap(stateReaderProvider: AccountStateReaderProvider) extends ScorexLogging {
  type TxIdByNonceMap = mutable.SortedMap[BigInteger, ModifierId]

  // All transactions currently in the mempool
  private val all: TrieMap[ModifierId, SidechainTypes#SCAT] = TrieMap.empty[ModifierId, SidechainTypes#SCAT]
  // Transaction ids of all currently processable transactions, grouped by account and ordered by nonce
  private val executableTxs: TrieMap[SidechainTypes#SCP, TxIdByNonceMap] =
    TrieMap.empty[SidechainTypes#SCP, TxIdByNonceMap]
  // Transaction ids of all transactions that cannot be executed because they are "orphans", grouped by account and ordered by nonce
  private val nonExecutableTxs: TrieMap[SidechainTypes#SCP, TxIdByNonceMap] =
    TrieMap.empty[SidechainTypes#SCP, TxIdByNonceMap]
  // Next expected nonce for each account. It is the max nonce of the executable txs plus 1, if any, otherwise it has the same value of the statedb nonce.
  private val nonces: TrieMap[SidechainTypes#SCP, BigInteger] = TrieMap.empty[SidechainTypes#SCP, BigInteger]

  def add(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    require(ethTransaction.isInstanceOf[EthereumTransaction], "Transaction is not EthereumTransaction")
    val account = ethTransaction.getFrom
    if (!contains(ethTransaction.id)) {

      val expectedNonce = nonces.getOrElseUpdate(account,
        stateReaderProvider.getAccountStateReader().getNonce(account.asInstanceOf[AddressProposition].address()))
      expectedNonce.compareTo(ethTransaction.getNonce) match {
        case 0 =>
          all.put(ethTransaction.id, ethTransaction)
          val executableTxsPerAccount =
            executableTxs.getOrElseUpdate(account, new mutable.TreeMap[BigInteger, ModifierId]())
          executableTxsPerAccount.put(ethTransaction.getNonce, ethTransaction.id)
          var nextNonce = expectedNonce.add(BigInteger.ONE)
          nonExecutableTxs
            .get(account)
            .foreach(nonExecTxsPerAccount => {
              var candidateToPromotionTx = nonExecTxsPerAccount.remove(nextNonce)
              while (candidateToPromotionTx.isDefined) {
                val promotedTxId = candidateToPromotionTx.get
                executableTxsPerAccount.put(nextNonce, promotedTxId)
                nextNonce = nextNonce.add(BigInteger.ONE)
                candidateToPromotionTx = nonExecTxsPerAccount.remove(nextNonce)
              }
              if (nonExecTxsPerAccount.isEmpty) {
                nonExecutableTxs.remove(account)
              }
            })
          nonces.put(account, nextNonce)
        case -1 =>
          val nonExecTxsPerAccount = nonExecutableTxs.getOrElseUpdate(account, new mutable.TreeMap[BigInteger, ModifierId]())
          val existingTxWithSameNonceIdOpt = nonExecTxsPerAccount.get(ethTransaction.getNonce)
          if (existingTxWithSameNonceIdOpt.isDefined) {
            val existingTxWithSameNonceId = existingTxWithSameNonceIdOpt.get
            replaceIfCanPayHigherFee(existingTxWithSameNonceId, ethTransaction, nonExecTxsPerAccount)
          } else {
            all.put(ethTransaction.id, ethTransaction)
            nonExecTxsPerAccount.put(ethTransaction.getNonce, ethTransaction.id)
          }
        case 1 =>
          //This case means there is already an executable tx with the same nonce in the mem pool
          val executableTxsPerAccount = executableTxs(account)
          val existingTxWithSameNonceId = executableTxsPerAccount(ethTransaction.getNonce)
          replaceIfCanPayHigherFee(existingTxWithSameNonceId, ethTransaction, executableTxsPerAccount)
      }
    }
    this
  }

  def replaceIfCanPayHigherFee(existingTxId: ModifierId, newTx: SidechainTypes#SCAT, listOfTxs: TxIdByNonceMap) = {
    val existingTxWithSameNonce = all(existingTxId)
    if (canPayHigherFee(newTx, existingTxWithSameNonce)) {
      log.trace(s"Replacing transaction $existingTxWithSameNonce with $newTx")
      all.remove(existingTxId)
      all.put(newTx.id, newTx)
      listOfTxs.put(newTx.getNonce, newTx.id)
    }

  }

  def remove(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    if (all.remove(ethTransaction.id).isDefined) {
      if (nonces(ethTransaction.getFrom).compareTo(ethTransaction.getNonce) < 0) {
        nonExecutableTxs
          .get(ethTransaction.getFrom)
          .foreach(nonExecTxsPerAccount => {
            nonExecTxsPerAccount.remove(ethTransaction.getNonce)
            if (nonExecTxsPerAccount.isEmpty) {
              nonExecutableTxs.remove(ethTransaction.getFrom)
              if (!executableTxs.contains(ethTransaction.getFrom)) {
                nonces.remove(ethTransaction.getFrom)
              }
            }
          })
      } else {
        executableTxs
          .get(ethTransaction.getFrom)
          .foreach(execTxsPerAccount => {
            execTxsPerAccount.remove(ethTransaction.getNonce)
            nonces.put(ethTransaction.getFrom, ethTransaction.getNonce)
            var demotedTxId = execTxsPerAccount.remove(ethTransaction.getNonce.add(BigInteger.ONE))
            while (demotedTxId.isDefined) {
              val demotedTx = all(demotedTxId.get)
              val nonExecTxsPerAccount =
                nonExecutableTxs.getOrElseUpdate(ethTransaction.getFrom, new mutable.TreeMap[BigInteger, ModifierId]())
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

    val baseFee = stateReaderProvider.getAccountStateReader().baseFee

    def txOrder(tx: SidechainTypes#SCAT) = {
      tx.getMaxFeePerGas.subtract(baseFee).min(tx.getMaxPriorityFeePerGas)
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

  def canPayHigherFee(newTx: SidechainTypes#SCAT, oldTx: SidechainTypes#SCAT): Boolean = {
    (newTx.getMaxFeePerGas.compareTo(oldTx.getMaxFeePerGas) > 0) &&
    (newTx.getMaxPriorityFeePerGas.compareTo(oldTx.getMaxPriorityFeePerGas) > 0)
  }

  def updateMemPool(listOfTxsToReAdd: Seq[SidechainTypes#SCAT], listOfTxsToRemove: Seq[SidechainTypes#SCAT]): Unit = {
    val accountsOfTxsToRemove = TrieMap.empty[SidechainTypes#SCP, BigInteger]
    val NonceForMissingAccount = BigInteger.valueOf(-1)
    listOfTxsToRemove.foreach(tx =>
      if (accountsOfTxsToRemove.getOrElse(tx.getFrom, NonceForMissingAccount).compareTo(tx.getNonce) < 0)
        accountsOfTxsToRemove.put(tx.getFrom, tx.getNonce)
    )

    val txsToAddPerAccounts = listOfTxsToReAdd.groupBy(_.getFrom)

    txsToAddPerAccounts.foreach { case (account, revertedTxs) =>
      val newExpectedNonce = accountsOfTxsToRemove.remove(account)
      updateAccount(account, newExpectedNonce, revertedTxs)
    }
    accountsOfTxsToRemove.foreach { case (account, nonce) =>
      updateAccount(account, Some(nonce), Seq.empty[SidechainTypes#SCAT])
    }
  }

  def updateAccount(
      account: SidechainTypes#SCP,
      expectedNonceOpt: Option[BigInteger],
      revertedTxs: Seq[SidechainTypes#SCAT]
  ): Any = {
    val fromAddress = account.asInstanceOf[AddressProposition].address()
    var newExpectedNonce = if (expectedNonceOpt.isDefined) expectedNonceOpt.get.add(BigInteger.ONE) else stateReaderProvider.getAccountStateReader().getNonce(fromAddress)
    val balance = stateReaderProvider.getAccountStateReader().getBalance(fromAddress)
    val oldExpectedNonce = nonces.get(account)

    val newExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()
    val newNonExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()

    if (oldExpectedNonce.isEmpty) {
      revertedTxs
        .withFilter(tx => tx.getNonce.compareTo(newExpectedNonce) >= 0 &&
          balance.compareTo(tx.maxCost) >= 0)
        .foreach { tx =>
          all.put(tx.id, tx)
          if (newExpectedNonce.compareTo(tx.getNonce) == 0) {
            newExecTxs.put(tx.getNonce, tx.id)
            newExpectedNonce = tx.getNonce.add(BigInteger.ONE)
            while (newNonExecTxs.contains(newExpectedNonce)) {
              val promotedTxId = newNonExecTxs.remove(newExpectedNonce).get
              newExecTxs.put(newExpectedNonce, promotedTxId)
              newExpectedNonce = newExpectedNonce.add(BigInteger.ONE)
            }
          } else {
            newNonExecTxs.put(tx.getNonce, tx.id)
          }
        }
    } else if (oldExpectedNonce.get.compareTo(newExpectedNonce) <= 0) {
      // In this case the reverted txs and the execTxs are all dropped. I just need to check the non exec txs
      executableTxs.remove(account).foreach { execTxs => execTxs.foreach { case (_, id) => all.remove(id) } }
      val nonExecTxs = nonExecutableTxs.remove(account)
      nonExecTxs.foreach {
        _.foreach { case (nonce, id) =>
          if (nonce.compareTo(newExpectedNonce) < 0) {
            all.remove(id)
          } else {
            val tx = all(id)
            val maxTxCost = tx.maxCost
            if (balance.compareTo(maxTxCost) >= 0) {
              if (nonce.compareTo(newExpectedNonce) == 0) {
                newExecTxs.put(nonce, id)
                newExpectedNonce = newExpectedNonce.add(BigInteger.ONE)
              } else {
                newNonExecTxs.put(nonce, id)
              }
            } else
              all.remove(id)
          }
        }
      }
    } else {
      // In this case non exec txs remain non exec, but reverted and exec could become non exec
      revertedTxs
        .withFilter(tx =>
          tx.getNonce.compareTo(newExpectedNonce) >= 0 &&
            balance.compareTo(tx.maxCost) >= 0
        )
        .foreach { tx =>
          all.put(tx.id, tx)
          if (newExpectedNonce.compareTo(tx.getNonce) == 0) {
            newExecTxs.put(tx.getNonce, tx.id)
            newExpectedNonce = tx.getNonce.add(BigInteger.ONE)
            while (newNonExecTxs.contains(newExpectedNonce)) {
              val promotedTxId = newNonExecTxs.remove(newExpectedNonce).get
              newExecTxs.put(newExpectedNonce, promotedTxId)
              newExpectedNonce = newExpectedNonce.add(BigInteger.ONE)
            }
          } else {
            newNonExecTxs.put(tx.getNonce, tx.id)
          }
        }

      executableTxs
        .remove(account)
        .foreach(_.foreach { case (nonce, id) =>
          if (newExpectedNonce.compareTo(nonce) > 0) {
            all.remove(id)
          } else {
            val tx = all(id)
            val maxTxCost = tx.maxCost
            if (balance.compareTo(maxTxCost) >= 0) {
              if (nonce.compareTo(newExpectedNonce) == 0) {
                newExecTxs.put(nonce, id)
                newExpectedNonce = newExpectedNonce.add(BigInteger.ONE)
              } else {
                newNonExecTxs.put(nonce, id)
              }
            } else
              all.remove(id)
          }
        })

      val nonExecTxsOpt = nonExecutableTxs.remove(account)

      nonExecTxsOpt.foreach(_.foreach { case (nonce, id) =>
        val tx = all(id)
        val maxTxCost = tx.maxCost
        if (balance.compareTo(maxTxCost) >= 0) {
          newNonExecTxs.put(nonce, id)
        } else
          all.remove(id)
      })

    }

    if (newExecTxs.isEmpty && newNonExecTxs.isEmpty) {
      nonces.remove(account)
    } else {
      nonces.put(account, newExpectedNonce)
      if (newExecTxs.nonEmpty) {
        executableTxs.put(account, newExecTxs)
      }
      if (newNonExecTxs.nonEmpty) {
        nonExecutableTxs.put(account, newNonExecTxs)
      }
    }
  }

}
