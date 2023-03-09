package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.{AccountStateReaderProvider, BaseStateReaderProvider}
import com.horizen.account.transaction.EthereumTransaction
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Try

class MempoolMap(
                  accountStateReaderProvider: AccountStateReaderProvider,
                  baseStateReaderProvider: BaseStateReaderProvider) extends SparkzLogging {
  type TxIdByNonceMap = mutable.SortedMap[BigInteger, ModifierId]
  type TxByNonceMap = mutable.SortedMap[BigInteger, SidechainTypes#SCAT]

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

      val expectedNonce = nonces.getOrElseUpdate(
        account,
        accountStateReaderProvider.getAccountStateReader().getNonce(account.asInstanceOf[AddressProposition].address())
      )
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
          val nonExecTxsPerAccount = {
            nonExecutableTxs.getOrElseUpdate(account, new mutable.TreeMap[BigInteger, ModifierId]())
          }
          val existingTxWithSameNonceIdOpt = nonExecTxsPerAccount.get(ethTransaction.getNonce)
          if (existingTxWithSameNonceIdOpt.isDefined) {
            val existingTxWithSameNonceId = existingTxWithSameNonceIdOpt.get
            replaceIfCanPayHigherFee(existingTxWithSameNonceId, ethTransaction, nonExecTxsPerAccount)
          } else {
            all.put(ethTransaction.id, ethTransaction)
            nonExecTxsPerAccount.put(ethTransaction.getNonce, ethTransaction.id)
          }
        case 1 =>
          // This case means there is already an executable tx with the same nonce in the mem pool
          val executableTxsPerAccount = executableTxs(account)
          val existingTxWithSameNonceId = executableTxsPerAccount(ethTransaction.getNonce)
          replaceIfCanPayHigherFee(existingTxWithSameNonceId, ethTransaction, executableTxsPerAccount)
      }
    }
    this
  }

  def replaceIfCanPayHigherFee(existingTxId: ModifierId, newTx: SidechainTypes#SCAT, mapOfTxsByNonce: TxIdByNonceMap) = {
    val existingTxWithSameNonce = all(existingTxId)
    if (canPayHigherFee(newTx, existingTxWithSameNonce)) {
      log.trace(s"Replacing transaction $existingTxWithSameNonce with $newTx")
      all.remove(existingTxId)
      all.put(newTx.id, newTx)
      mapOfTxsByNonce.put(newTx.getNonce, newTx.id)
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

  def getAccountNonce(account: SidechainTypes#SCP): Option[BigInteger] = nonces.get(account)

  def size: Int = all.size

  def getTransaction(txId: ModifierId): Option[SidechainTypes#SCAT] = all.get(txId)

  def contains(txId: ModifierId): Boolean = all.contains(txId)

  def values: Iterable[SidechainTypes#SCAT] = all.values

  def mempoolTransactions(executable: Boolean): Iterable[ModifierId] = {
    val txsList = new ListBuffer[ModifierId]
    var mempoolIdsMap = TrieMap.empty[SidechainTypes#SCP, TxIdByNonceMap]
    if (executable) mempoolIdsMap = executableTxs
    else mempoolIdsMap = nonExecutableTxs
    for ((_, v) <- mempoolIdsMap) {
      for ((_, innerV) <- v) {
        txsList += innerV
      }
    }
    txsList
  }

  def mempoolTransactionsMap(executable: Boolean): TrieMap[SidechainTypes#SCP, TxByNonceMap] = {
    val txsMap = TrieMap.empty[SidechainTypes#SCP, TxByNonceMap]
    var mempoolIdsMap = TrieMap.empty[SidechainTypes#SCP, TxIdByNonceMap]
    if (executable) mempoolIdsMap = executableTxs
    else mempoolIdsMap = nonExecutableTxs
    for ((from, nonceIdsMap) <- mempoolIdsMap) {
      val nonceTxsMap: mutable.TreeMap[BigInteger, SidechainTypes#SCAT] = new mutable.TreeMap[BigInteger, SidechainTypes#SCAT]()
      for ((txNonce, txId) <- nonceIdsMap) {
        nonceTxsMap.put(txNonce, getTransaction(txId).get)
      }
      txsMap.put(from,nonceTxsMap)
    }
    txsMap
  }

  /**
   * Returns executable transactions sorted by gas tip (descending) and nonce. The ordering is performed in a semi-lazy
   * way.
   */
  def takeExecutableTxs(forcedTx: Iterable[SidechainTypes#SCAT] = Seq()): TransactionsByPriceAndNonce = {

    val baseFee = baseStateReaderProvider.getBaseStateReader().getNextBaseFee

    new TransactionsByPriceAndNonce(baseFee, forcedTx)
  }

  def canPayHigherFee(newTx: SidechainTypes#SCAT, oldTx: SidechainTypes#SCAT): Boolean = {
    (newTx.getMaxFeePerGas.compareTo(oldTx.getMaxFeePerGas) > 0) &&
    (newTx.getMaxPriorityFeePerGas.compareTo(oldTx.getMaxPriorityFeePerGas) > 0)
  }

  def updateMemPool(rejectedBlocks: Seq[AccountBlock], appliedBlocks: Seq[AccountBlock]): Unit = {
    /* Mem pool needs to be updated after state modifications. Transactions that have become invalid
    (or for a nonce too low or for insufficient balance), should be removed. Txs
    from blocks rejected due to a switch of the active chain, that are still valid, should be re-added
    to the mem pool instead.
    For efficiency, mem pool is updated account per account and only accounts whose state was modified
    are considered.
     */

    //Creates a map with with the max nonce for each account. The txs in a block are ordered by nonce,
    //so there is no need to check if the nonce already in the map is greater or not => the last one is
    //always the greatest.
    val appliedTxNoncesByAccount = TrieMap.empty[SidechainTypes#SCP, BigInteger]
    appliedBlocks.foreach(block => {
      block.transactions.foreach(tx => appliedTxNoncesByAccount.put(tx.getFrom, tx.getNonce))
    })

    val listOfRejectedBlocksTxs = rejectedBlocks.flatMap(_.transactions)
    val rejectedTransactionsByAccount = listOfRejectedBlocksTxs.groupBy(_.getFrom)

    rejectedTransactionsByAccount.foreach { case (account, rejectedTxs) =>
      val latestNonceAfterAppliedTxs = appliedTxNoncesByAccount.remove(account)
      if (latestNonceAfterAppliedTxs.isDefined)
        updateAccount(account, latestNonceAfterAppliedTxs.get, rejectedTxs)
      else
        restoreRejectedTransactions(account, rejectedTxs)
    }
    appliedTxNoncesByAccount.foreach { case (account, nonce) =>
      updateAccount(account, nonce)

    }
  }

  def restoreRejectedTransactions(
      account: SidechainTypes#SCP,
      txsFromRejectedBlocks: Seq[SidechainTypes#SCAT]
  ): Unit = {
    // In this case, the current account state wasn't modified by new blocks
    // but it was just reverted to an older state. So there is no need to check for nonce, because the txs were already
    // verified for it. However we still need to check for balance because it is possible that both txs in mem pool and
    // txs from reverted blocks were verified with a different balance respect the one restored. The only exception are
    // txs from the oldest reverted block but for simplicity they are checked the same.

    val fromAddress = account.asInstanceOf[AddressProposition].address()
    val balance = accountStateReaderProvider.getAccountStateReader().getBalance(fromAddress)

    val newExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()
    val newNonExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()

    // Recreates from scratch the account's nonExecTxs and execTxs maps, starting from txs from rejected blocks.
    // They are by default directly added to the execTxs map, because the nonce is surely correct. If a tx is found invalid
    // for balance, it is discarded. All the subsequent txs, if valid, will become not executable and they will be added
    // to the nonExec map.
    var destMap = newExecTxs
    var haveBecomeNonExecutable = false

    txsFromRejectedBlocks
      .foreach { tx =>
        if (balance.compareTo(tx.maxCost) >= 0) {
          all.put(tx.id, tx)
          destMap.put(tx.getNonce, tx.id)
        } else {
          if (!haveBecomeNonExecutable) {
            destMap = newNonExecTxs
            haveBecomeNonExecutable = true
          }
        }
      }

    val execTxsOpt = executableTxs.remove(account)

    if (execTxsOpt.nonEmpty) {
      val execTxs = execTxsOpt.get
      execTxs.foreach { case (nonce, id) =>
        if (balance.compareTo(all(id).maxCost) >= 0) {
          destMap.put(nonce, id)
        } else {
          all.remove(id)
          if (!haveBecomeNonExecutable) {
            destMap = newNonExecTxs
            haveBecomeNonExecutable = true
          }
        }
      }
    }

    val nonExecTxsOpt = nonExecutableTxs.remove(account)
    if (nonExecTxsOpt.nonEmpty) {
      val nonExecTxs = nonExecTxsOpt.get
      nonExecTxs.foreach { case (nonce, id) =>
        if (balance.compareTo(all(id).maxCost) >= 0) {
          newNonExecTxs.put(nonce, id)
        } else {
          all.remove(id)
        }
      }
    }

    nonces.put(account, newExecTxs.lastKey.add(BigInteger.ONE))
    executableTxs.put(account, newExecTxs)
    if (newNonExecTxs.nonEmpty) {
      nonExecutableTxs.put(account, newNonExecTxs)
    }

  }

  def existRejectedTxsWithValidNonce(rejectedTxs: Seq[SidechainTypes#SCAT], expectedNonce: BigInteger): Boolean = {
    rejectedTxs.nonEmpty && rejectedTxs.last.getNonce.compareTo(expectedNonce) >= 0
  }

  def updateAccount(
      account: SidechainTypes#SCP,
      nonceOfTheLatestAppliedTx: BigInteger,
      txsFromRejectedBlocks: Seq[SidechainTypes#SCAT] = Seq.empty[SidechainTypes#SCAT]
  ): Unit = {
    var newExpectedNonce = nonceOfTheLatestAppliedTx.add(BigInteger.ONE)
    val fromAddress = account.asInstanceOf[AddressProposition].address()
    val balance = accountStateReaderProvider.getAccountStateReader().getBalance(fromAddress)

    val newExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()
    val newNonExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()

    // Recreates from scratch the account's nonExecTxs and execTxs maps, starting from txs from rejected blocks.
    // First all the txs with nonce too low are discarded. The subsequent txs don't need to be checked for nonce
    // because they are ordered by increasing nonce. They are candidate to be added by default to the execTxs map, because
    // they come from reverted blocks so it is impossible to have nonce gaps. They still need to be checked for balance.
    // If a tx is found invalid for balance, it is discarded. All the subsequent txs, if valid, will become not executable
    // and they will be added to the nonExec map.
    var destMap = newExecTxs
    var haveBecomeNonExecutable = false

    if (existRejectedTxsWithValidNonce(txsFromRejectedBlocks, newExpectedNonce)) {
      txsFromRejectedBlocks.withFilter(tx => tx.getNonce.compareTo(newExpectedNonce) >= 0).foreach { tx =>
        if (balance.compareTo(tx.maxCost) >= 0) {
          all.put(tx.id, tx)
          destMap.put(tx.getNonce, tx.id)
        } else {
          if (!haveBecomeNonExecutable) {
            destMap = newNonExecTxs
            haveBecomeNonExecutable = true
          }
        }
      }

    }

    // First all the txs with nonce too low are discarded. The subsequent txs don't need to be checked for nonce
    // because they are ordered by increasing nonce. They are candidate to be added by default to the execTxs map, unless
    // a previous tx was found invalid. They remaining txs are checked for balance.
    // If a tx is found invalid, it is removed from the mem pool. All the subsequent txs, if valid, will become not executable
    // and they will be added to the nonExec map.
    val execTxsOpt = executableTxs.remove(account)
    if (execTxsOpt.nonEmpty) {
      execTxsOpt.get
        .withFilter {
          case (nonce, id) => {
            if (nonce.compareTo(newExpectedNonce) < 0) {
              all.remove(id)
              false
            } else
              true
          }
        }.foreach { case (nonce, id) =>
          if (balance.compareTo(all(id).maxCost) >= 0) {
            destMap.put(nonce, id)
          } else {
            all.remove(id)
            if (!haveBecomeNonExecutable) {
              destMap = newNonExecTxs
              haveBecomeNonExecutable = true
            }
          }
        }
    }

    if (newExecTxs.nonEmpty)
      newExpectedNonce = newExecTxs.lastKey.add(BigInteger.ONE)

    // Last we need to check txs in nonExec map. The checks are the same as the other txs with the only difference that
    // some of them could have become executable, thanks to the txs in the applied block.
    val nonExecTxs = nonExecutableTxs.remove(account)
    if (nonExecTxs.nonEmpty) {
      nonExecTxs.get
        .withFilter {
          case (nonce, id) => {
            if (nonce.compareTo(newExpectedNonce) < 0) {
              all.remove(id)
              false
            } else
              true
          }
        }
        .foreach { case (nonce, id) =>
          if (balance.compareTo(all(id).maxCost) >= 0) {
            if (nonce.compareTo(newExpectedNonce) == 0) {
              newExecTxs.put(nonce, id)
              newExpectedNonce = newExpectedNonce.add(BigInteger.ONE)
            } else {
              newNonExecTxs.put(nonce, id)
            }
          } else {
            all.remove(id)
          }
        }

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

  class TransactionsByPriceAndNonce(baseFee: BigInteger, forcedTx: Iterable[SidechainTypes#SCAT]) extends Iterable[SidechainTypes#SCAT] {

    class Iter extends TransactionsByPriceAndNonceIter {

      def txOrder(tx: SidechainTypes#SCAT): BigInteger = {
        tx.getMaxFeePerGas.subtract(baseFee).min(tx.getMaxPriorityFeePerGas)
      }

      // used in some scenario (only regtest) where use is made of http api 'generate' setting explicitly some
      // transactions to be included in a forged block
      val forcedTxQueue = new mutable.Queue[SidechainTypes#SCAT]()

      val orderedQueue = new mutable.PriorityQueue[SidechainTypes#SCAT]()(Ordering.by(txOrder))

      executableTxs.foreach { case (_, mapOfTxsPerAccount) =>
        val tx = getTransaction(mapOfTxsPerAccount.values.head).get
        orderedQueue.enqueue(tx)
      }
      forcedTx.foreach(
        forcedTxQueue.enqueue(_)
      )

      override def hasNext: Boolean = forcedTxQueue.nonEmpty || orderedQueue.nonEmpty

      override def next(): SidechainTypes#SCAT = {
        if (forcedTxQueue.nonEmpty) {
          forcedTxQueue.dequeue()
        } else {
          val bestTx = orderedQueue.dequeue()
          val nextTxIdOpt = executableTxs(bestTx.getFrom).get(bestTx.getNonce.add(BigInteger.ONE))
          if (nextTxIdOpt.nonEmpty) {
            val tx = getTransaction(nextTxIdOpt.get).get
            orderedQueue.enqueue(tx)
          }
          bestTx
        }
      }

      def peek: SidechainTypes#SCAT = {
        if (forcedTxQueue.nonEmpty) {
          forcedTxQueue.head
        } else {
          orderedQueue.head
        }
      }

      def removeAndSkipAccount(): SidechainTypes#SCAT = {
        if (forcedTxQueue.nonEmpty) {
          forcedTxQueue.dequeue()
        } else {
          orderedQueue.dequeue()
        }
      }
    }

    override def iterator: TransactionsByPriceAndNonceIter = {
      new Iter()
    }
  }
}

trait TransactionsByPriceAndNonceIter extends Iterator[SidechainTypes#SCAT] {
  def peek: SidechainTypes#SCAT
  def removeAndSkipAccount(): SidechainTypes#SCAT
}
