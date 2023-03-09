package io.horizen.account.mempool

import io.horizen.account.block.AccountBlock
import io.horizen.account.mempool.MempoolMap._
import io.horizen.account.mempool.TxExecutableStatus.TxExecutableStatus
import io.horizen.account.mempool.exception.{AccountMemPoolOutOfBoundException, NonceGapTooWideException, TransactionReplaceUnderpricedException, TxOversizedException}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.{AccountStateReaderProvider, BaseStateReaderProvider}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.evm.Address
import io.horizen.{AccountMempoolSettings, SidechainTypes}
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class MempoolMap(
                  accountStateReaderProvider: AccountStateReaderProvider,
                  baseStateReaderProvider: BaseStateReaderProvider,
                  mempoolSettings: AccountMempoolSettings) extends SparkzLogging {

  type TxIdByNonceMap = mutable.SortedMap[BigInteger, ModifierId]
  type TxByNonceMap = mutable.SortedMap[BigInteger, SidechainTypes#SCAT]

  // This constant added to the state nonce gives the maximum nonce that a tx can have to be accepted in the mempool
  val maxAllowedNonceGap: BigInteger = BigInteger.valueOf(mempoolSettings.maxNonceGap).subtract(BigInteger.ONE)
  val MaxSlotsPerAccount: Int = mempoolSettings.maxAccountSlots
  val MaxMemPoolSlots: Int = mempoolSettings.maxMemPoolSlots
  val MaxNonExecSubPoolSlots: Int = mempoolSettings.maxNonExecMemPoolSlots
  val TxLifetime: FiniteDuration = mempoolSettings.txLifetime

  // Cache of all transactions currently in the mempool
  private val txCache = new TxCache(TxLifetime)

  // Transaction ids of all currently processable transactions, grouped by account and ordered by nonce
  private val executableTxs: TrieMap[SidechainTypes#SCP, TxIdByNonceMap] =
    TrieMap.empty[SidechainTypes#SCP, TxIdByNonceMap]
  // Transaction ids of all transactions that cannot be executed because they are "orphans", grouped by account and ordered by nonce
  private val nonExecutableTxs: TrieMap[SidechainTypes#SCP, TxIdByNonceMap] =
    TrieMap.empty[SidechainTypes#SCP, TxIdByNonceMap]
  // Next expected nonce for each account. It is the max nonce of the executable txs plus 1, if any, otherwise it has the
  // same value of the statedb nonce.
  private val nonces: TrieMap[SidechainTypes#SCP, BigInteger] = TrieMap.empty[SidechainTypes#SCP, BigInteger]

  def getMaxAcceptableNonce(stateNonce: BigInteger): BigInteger = stateNonce.add(maxAllowedNonceGap)

  private[mempool] def findTxWithSameNonce(account: SidechainTypes#SCP, nonce: BigInteger): Option[SidechainTypes#SCAT] = {
    val txOpt: Option[SidechainTypes#SCAT] = executableTxs.get(account).flatMap(txMap => txMap.get(nonce).map(txCache(_)))
    txOpt.orElse(nonExecutableTxs.get(account).flatMap(txMap => txMap.get(nonce).map(txCache(_))))
  }

  def add(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    require(ethTransaction.isInstanceOf[EthereumTransaction], "Transaction is not EthereumTransaction")

    if (!contains(ethTransaction.id)) {
      val account = ethTransaction.getFrom

      //Reject transactions that are too big
      val txSize = ethTransaction.size()
      if (txSize > MaxTxSize) {
        log.trace(s"Transaction $ethTransaction size exceeds maximum allowed size: current size $txSize, " +
          s"maximum size: $MaxTxSize")
        throw TxOversizedException(account.asInstanceOf[AddressProposition].address(), txSize)
      }

      //Reject transactions with a nonce gap too big
      val stateNonce = accountStateReaderProvider.getAccountStateReader().getNonce(account.asInstanceOf[AddressProposition].address())
      val maxAcceptableNonce = getMaxAcceptableNonce(stateNonce)
      if (ethTransaction.getNonce.compareTo(maxAcceptableNonce) > 0) {
        log.trace(s"Transaction $ethTransaction nonce gap respect state nonce exceeds maximum allowed size: tx nonce ${ethTransaction.getNonce}, " +
          s"state nonce: $stateNonce, maximum nonce gap $maxAllowedNonceGap")
        throw NonceGapTooWideException(ethTransaction.id, ethTransaction.getNonce, stateNonce)
      }

      // Reject transactions in case their account doesn't have enough space for them. If the new tx is replacing an
      // existing one, the space used by the old one must be taken into account.
      val txToReplaceOpt = findTxWithSameNonce(account, ethTransaction.getNonce)
      val numOfSlotsOfTxToReplace = txToReplaceOpt match {
        case Some(txToReplace) =>
          if (!canPayHigherFee(ethTransaction, txToReplace)) {
            log.trace(s"Transaction $ethTransaction cannot replace $txToReplace because it is underpriced")
            throw TransactionReplaceUnderpricedException(ethTransaction.id)
          }
          txSizeInSlot(txToReplace)
        case None => 0
       }

      val newTxSizeInSlot = sizeToSlot(txSize)
      val additionalSlots = math.max(newTxSizeInSlot - numOfSlotsOfTxToReplace, 0)

      val accountSlotsNumber = getAccountSlots(account)
      if (accountSlotsNumber + additionalSlots > MaxSlotsPerAccount) {
        log.trace(s"Adding transaction $ethTransaction exceeds maximum allowed size per account ($MaxSlotsPerAccount slots)")
        throw AccountMemPoolOutOfBoundException(ethTransaction.id)
      }

      val expectedNonce = nonces.getOrElseUpdate(
        account,
        stateNonce)

      expectedNonce.compareTo(ethTransaction.getNonce) match {
        case AddNewExecTransaction =>
          val executableTxsPerAccount =
            executableTxs.getOrElseUpdate(account, new mutable.TreeMap[BigInteger, ModifierId]())
          addNewTransaction(executableTxsPerAccount, ethTransaction, TxExecutableStatus.EXEC)
          var nextNonce = expectedNonce.add(BigInteger.ONE)
          nonExecutableTxs
            .get(account)
            .foreach(nonExecTxsPerAccount => {
              var candidateToPromotionTx = nonExecTxsPerAccount.remove(nextNonce)
              while (candidateToPromotionTx.isDefined) {
                val promotedTxId = candidateToPromotionTx.get
                executableTxsPerAccount.put(nextNonce, promotedTxId)
                txCache.promoteTransaction(promotedTxId)
                nextNonce = nextNonce.add(BigInteger.ONE)
                candidateToPromotionTx = nonExecTxsPerAccount.remove(nextNonce)
              }
              if (nonExecTxsPerAccount.isEmpty) {
                nonExecutableTxs.remove(account)
              }
            })
          nonces.put(account, nextNonce)
        case AddOrReplaceNonExecTransaction =>
          val nonExecTxsPerAccount =
            nonExecutableTxs.getOrElseUpdate(account, new mutable.TreeMap[BigInteger, ModifierId]())
          if (txToReplaceOpt.isDefined) {
            replaceTransaction(txToReplaceOpt.get.id, ethTransaction, nonExecTxsPerAccount, TxExecutableStatus.NON_EXEC)
          } else {
            addNewTransaction(nonExecTxsPerAccount, ethTransaction, TxExecutableStatus.NON_EXEC)
          }
        case ReplaceExecTransaction =>
          // This case means there is already an executable tx with the same nonce in the mem pool
          val executableTxsPerAccount = executableTxs(account)
          replaceTransaction(txToReplaceOpt.get.id, ethTransaction, executableTxsPerAccount, TxExecutableStatus.EXEC)
      }

      //After having added the new tx, check the resulting size of the non exec sub pool and of the mempool. If one or
      // both of them exceed the maximum limit, free some space.
      checkMempoolSize()
    }
    this
  }

  private[mempool] def addNewTransaction(txByNonceMap: TxIdByNonceMap, ethTransaction: SidechainTypes#SCAT, execStatus: TxExecutableStatus) = {
    txCache.add(ethTransaction, execStatus)
    txByNonceMap.put(ethTransaction.getNonce, ethTransaction.id)
  }

  private[mempool] def replaceTransaction(existingTxId: ModifierId, newTx: SidechainTypes#SCAT,
                                          mapOfTxsByNonce: TxIdByNonceMap, execStatus: TxExecutableStatus) = {
    txCache.remove(existingTxId)
    txCache.add(newTx, execStatus)
    mapOfTxsByNonce.put(newTx.getNonce, newTx.id)
  }

  def getAccountSlots(account: SidechainTypes#SCP): Int = {
    val execTxsSlots: Int = executableTxs.get(account).map(txs => txs.values.foldLeft(0) { (sum, txId) => sum + txSizeInSlot(txCache(txId)) }).getOrElse(0)
    val accountSlots: Int = nonExecutableTxs.get(account).map(txs => txs.values.foldLeft(execTxsSlots) { (sum, txId) => sum + txSizeInSlot(txCache(txId)) }).getOrElse(execTxsSlots)
    accountSlots
  }

  def getMempoolSizeInSlots: Int = txCache.getSizeInSlots

  def getNonExecSubpoolSizeInSlots: Int = txCache.getNonExecSizeInSlots

  private[mempool] def freeMempoolSlots(): Unit = {
    do {
      val oldestTx = txCache.getOldestTransaction()
      log.trace(s"Evicting transaction $oldestTx")
      remove(oldestTx.get)
    }
    while (getMempoolSizeInSlots > MaxMemPoolSlots)
  }

  private[mempool] def freeNonExecSubpoolSlots(): Unit = {
   val nonExecIter = txCache.getNonExecIterator()
    do {
      val oldestTx = nonExecIter.next
      log.trace(s"Evicting transaction $oldestTx")
      remove(oldestTx)
    }
    while (getNonExecSubpoolSizeInSlots > MaxNonExecSubPoolSlots)
  }

  private[mempool] def removeTimedoutTransactions(): Unit = {
    var oldestTxInfoOpt = txCache.getOldestTransactionInfo()
    while(oldestTxInfoOpt.isDefined && oldestTxInfoOpt.get.hasTimedOut){
      log.trace(s"Removing timed out transaction ${oldestTxInfoOpt.get.tx}")
      remove(oldestTxInfoOpt.get.tx)
      oldestTxInfoOpt = txCache.getOldestTransactionInfo()
    }
  }


  def removeFromMempool(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    remove(ethTransaction).get
    // Only Non Exec sub pool size can change when removing a tx
    if (getNonExecSubpoolSizeInSlots > MaxNonExecSubPoolSlots) {
      log.trace(s"Removing transaction $ethTransaction exceeded maximum allowed non exec subpool size ($MaxNonExecSubPoolSlots slots). " +
        s"Evicting oldest transactions")
      freeNonExecSubpoolSlots()
    }
    this
  }

  private[mempool] def remove(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] =   Try {
    if (txCache.remove(ethTransaction.id).isDefined) {
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
            //Demotes next transactions
            var nextNonce = ethTransaction.getNonce.add(BigInteger.ONE)
            var demotedTxId = execTxsPerAccount.remove(nextNonce)
            if (demotedTxId.isDefined) {
              val nonExecTxsPerAccount =
                nonExecutableTxs.getOrElseUpdate(ethTransaction.getFrom, new mutable.TreeMap[BigInteger, ModifierId]())
                do {
                  txCache.demoteTransaction(demotedTxId.get)
                  nonExecTxsPerAccount.put(nextNonce, demotedTxId.get)
                  nextNonce = nextNonce.add(BigInteger.ONE)
                  demotedTxId = execTxsPerAccount.remove(nextNonce)
                }
                while (demotedTxId.isDefined)

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

  def size: Int = txCache.size

  def getTransaction(txId: ModifierId): Option[SidechainTypes#SCAT] = txCache.getTransaction(txId)

  def contains(txId: ModifierId): Boolean = txCache.contains(txId)

  def values: Iterable[SidechainTypes#SCAT] = txCache.values

  def mempoolTransactions(executable: Boolean): Iterable[ModifierId] = {
    val txsList = new ListBuffer[ModifierId]
    val mempoolIdsMap = if (executable)
      executableTxs
    else
      nonExecutableTxs
    for ((_, v) <- mempoolIdsMap) {
      for ((_, innerV) <- v) {
        txsList += innerV
      }
    }
    txsList
  }

  def mempoolTransactionsMap(executable: Boolean): TrieMap[SidechainTypes#SCP, TxByNonceMap] = {
    val txsMap = TrieMap.empty[SidechainTypes#SCP, TxByNonceMap]
    val mempoolIdsMap = if (executable)
      executableTxs
    else
      nonExecutableTxs
    for ((from, nonceIdsMap) <- mempoolIdsMap) {
      val nonceTxsMap: mutable.TreeMap[BigInteger, SidechainTypes#SCAT] = new mutable.TreeMap[BigInteger, SidechainTypes#SCAT]()
      for ((txNonce, txId) <- nonceIdsMap) {
        nonceTxsMap.put(txNonce, getTransaction(txId).get)
      }
      txsMap.put(from, nonceTxsMap)
    }
    txsMap
  }

  def mempoolTransactionsMapFrom(executable: Boolean, requestedAddress: Address): TrieMap[SidechainTypes#SCP, TxByNonceMap] = {
    val txsMap = TrieMap.empty[SidechainTypes#SCP, TxByNonceMap]
    val mempoolIdsMap = if (executable)
      executableTxs
    else
      nonExecutableTxs
    for ((from, nonceIdsMap) <- mempoolIdsMap) {
      val fromAddress = from.asInstanceOf[AddressProposition].address()
      if(fromAddress.equals(requestedAddress)) {
        val nonceTxsMap: mutable.TreeMap[BigInteger, SidechainTypes#SCAT] = new mutable.TreeMap[BigInteger, SidechainTypes#SCAT]()
        for ((txNonce, txId) <- nonceIdsMap) {
          nonceTxsMap.put(txNonce, getTransaction(txId).get)
        }
        txsMap.put(from, nonceTxsMap)
      }
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
    (or for a nonce too low or for insufficient balance or else), should be removed. Txs
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
        updateAccountWithRevertedNonce(account, rejectedTxs)
    }
    appliedTxNoncesByAccount.foreach { case (account, nonce) =>
      updateAccount(account, nonce)
    }

    removeTimedoutTransactions()

    checkMempoolSize()
  }

  private[mempool] def updateAccountWithRevertedNonce(
                                   account: SidechainTypes#SCP,
                                   txsFromRejectedBlocks: Seq[SidechainTypes#SCAT]
                                 ): Unit = {
    // In this case, we had a chain switch and the resulting state nonce for the current account is lower than before
    // the switch.
    // There is no need to check for nonce too low, because the txs in the mempool were already
    // verified for it, while the txs from rejected blocks (txsFromRejectedBlocks) were already filtered for the new state nonce.
    // However we still need to check:
    // - both txs in mem pool and txs from reverted blocks for:
    //    * balance because it is possible that they were verified with a different balance respect the one restored.
    //    * max nonce gap because the stateNonce was reverted and it is lower than before, so some txs now need to be dropped
    //    * account size because we're re-adding the txs from rejected blocks
    // - only txs from reverted blocks for:
    //    * tx size because the size is not a consensus rule so other nodes may have allowed bigger txs in their blocks

    val fromAddress = account.asInstanceOf[AddressProposition].address()
    val balance = accountStateReaderProvider.getAccountStateReader().getBalance(fromAddress)

    val newExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()
    val newNonExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()

    val stateNonce = txsFromRejectedBlocks.head.getNonce
    val maxAcceptableNonce = getMaxAcceptableNonce(stateNonce)

    // Recreates from scratch the account's nonExecTxs and execTxs maps, starting from txs from rejected blocks.
    // They are by default directly added to the execTxs map, because the nonce is surely not too low. If a tx is found invalid
    // for balance or size, it is discarded. All the subsequent txs, if valid, will become not executable and they will be added
    // to the nonExec map.
    // In the (unlikely) case some rejected txs have a nonce gap too big, they and all the subsequent txs will be dropped.
    var destMap = newExecTxs
    var haveBecomeNonExecutable = false
    var maxNonceGapExceeded = false
    var currAccountSlots = 0L

    val txsToReinject = new scala.collection.mutable.ListBuffer[(SidechainTypes#SCAT, TxExecutableStatus)]
    txsFromRejectedBlocks.withFilter(_ => !maxNonceGapExceeded)
      .foreach { tx =>
        if (tx.getNonce.compareTo(maxAcceptableNonce) <= 0) {
          val txSizeInSlots = txSizeInSlot(tx)
          if (
            (balance.compareTo(tx.maxCost) >= 0) &&
            (txSizeInSlots <= MaxNumOfSlotsForTx) &&
            (currAccountSlots + txSizeInSlots <= MaxSlotsPerAccount)
          ) {
            val txStatus = if (haveBecomeNonExecutable) TxExecutableStatus.NON_EXEC else TxExecutableStatus.EXEC
            txsToReinject += ((tx, txStatus))
            destMap.put(tx.getNonce, tx.id)
            currAccountSlots += txSizeInSlots
          } else {
            if (!haveBecomeNonExecutable) {
              destMap = newNonExecTxs
              haveBecomeNonExecutable = true
            }
          }
        }
        else
          maxNonceGapExceeded = true
      }

    // reinjected txs are added in the txCache in reverse order, so in case of eviction the ones with greatest nonce gap will be
    // evicted first
    txsToReinject.reverseIterator.foreach{case (tx, execStatus) => txCache.add(tx, execStatus)}

    val execTxsOpt = executableTxs.remove(account)

    if (execTxsOpt.nonEmpty) {
      val execTxs = execTxsOpt.get
      if (maxNonceGapExceeded) {
        execTxs.foreach { case (_, id) =>
          txCache.remove(id)
        }
      }
      else {
        execTxs.foreach { case (nonce, id) =>
          if (!maxNonceGapExceeded && nonce.compareTo(maxAcceptableNonce) <= 0) {
            val tx = txCache(id)
            val txSizeInSlots = txSizeInSlot(tx)
            if (balance.compareTo(tx.maxCost) >= 0 && ((currAccountSlots + txSizeInSlots) <= MaxSlotsPerAccount)) {
              destMap.put(nonce, id)
              currAccountSlots += txSizeInSlots
              if (haveBecomeNonExecutable)
                txCache.demoteTransaction(id)
            } else {
              txCache.remove(id)
              if (!haveBecomeNonExecutable) {
                destMap = newNonExecTxs
                haveBecomeNonExecutable = true
              }
            }
          }
          else {
            if (!maxNonceGapExceeded)
              maxNonceGapExceeded = true
            txCache.remove(id)
          }
        }
      }
    }

    val nonExecTxsOpt = nonExecutableTxs.remove(account)
    if (nonExecTxsOpt.nonEmpty) {
      val nonExecTxs = nonExecTxsOpt.get
      if (maxNonceGapExceeded) {
        nonExecTxs.foreach { case (_, id) =>
          txCache.remove(id)
        }
      }
      else {
        nonExecTxs.foreach { case (nonce, id) =>
          if (!maxNonceGapExceeded && nonce.compareTo(maxAcceptableNonce) <= 0) {
            val tx = txCache(id)
            val txSizeInSlots = txSizeInSlot(tx)
            if (
              (balance.compareTo(tx.maxCost) >= 0) &&
              ((currAccountSlots + txSizeInSlots) <= MaxSlotsPerAccount)
            ) {
              newNonExecTxs.put(nonce, id)
              currAccountSlots += txSizeInSlots
            } else {
              txCache.remove(id)
            }
          }
          else {
            if (!maxNonceGapExceeded)
              maxNonceGapExceeded = true
            txCache.remove(id)
          }
        }
      }
    }


    if (newExecTxs.nonEmpty) {
      nonces.put(account, newExecTxs.lastKey.add(BigInteger.ONE))
      executableTxs.put(account, newExecTxs)
    } else
      nonces.put(account, stateNonce)

    if (newNonExecTxs.nonEmpty) {
      nonExecutableTxs.put(account, newNonExecTxs)
    }

  }

  private[mempool] def existRejectedTxsWithValidNonce(rejectedTxs: Seq[SidechainTypes#SCAT], expectedNonce: BigInteger): Boolean = {
    rejectedTxs.nonEmpty && rejectedTxs.last.getNonce.compareTo(expectedNonce) >= 0
  }

  private[mempool] def updateAccount(
                     account: SidechainTypes#SCP,
                     nonceOfTheLatestAppliedTx: BigInteger,
                     txsFromRejectedBlocks: Seq[SidechainTypes#SCAT] = Seq.empty[SidechainTypes#SCAT]
                   ): Unit = {
    var newExpectedNonce = nonceOfTheLatestAppliedTx.add(BigInteger.ONE)

    if (existRejectedTxsWithValidNonce(txsFromRejectedBlocks, newExpectedNonce)) {
      updateAccountWithRevertedNonce(account, txsFromRejectedBlocks.dropWhile(tx => tx.getNonce.compareTo(newExpectedNonce) < 0))
    }
    else {
      // The new expected nonce cannot be lower than the old one, so I don't need to check the remaining txs
      // for the nonce gap. No need to check for tx size too, because this check was already performed when they were added
      // the first time in the mempool. Because we're not adding new txs in the mempool, there is no need to check for
      // account or mempool size.
      val fromAddress = account.asInstanceOf[AddressProposition].address()
      val balance = accountStateReaderProvider.getAccountStateReader().getBalance(fromAddress)

      val newExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()
      val newNonExecTxs: mutable.TreeMap[BigInteger, ModifierId] = new mutable.TreeMap[BigInteger, ModifierId]()

      // Recreates from scratch the account's nonExecTxs and execTxs maps, starting from exec txs.
      // First all the txs with nonce too low are discarded. The remaining txs are checked for balance.
      // If a tx is found invalid, it is removed from the mem pool. All the subsequent txs, if valid, will become not
      // executable and they will be added to the nonExec map.
      var destMap = newExecTxs
      var haveBecomeNonExecutable = false

      val execTxsOpt = executableTxs.remove(account)
      if (execTxsOpt.nonEmpty) {
        execTxsOpt.get
          .withFilter {
            case (nonce, id) =>
              if (nonce.compareTo(newExpectedNonce) < 0) {
                txCache.remove(id)
                false
              } else
                true
          }.foreach { case (nonce, id) =>
          if (balance.compareTo(txCache(id).maxCost) >= 0) {
            destMap.put(nonce, id)
            if (haveBecomeNonExecutable)
              txCache.demoteTransaction(id)
          } else {
            txCache.remove(id)
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
            case (nonce, id) =>
              if (nonce.compareTo(newExpectedNonce) < 0) {
                txCache.remove(id)
                false
              } else
                true
          }
          .foreach { case (nonce, id) =>
            if (balance.compareTo(txCache(id).maxCost) >= 0) {
              if (nonce.compareTo(newExpectedNonce) == 0) {
                newExecTxs.put(nonce, id)
                txCache.promoteTransaction(id)
                newExpectedNonce = newExpectedNonce.add(BigInteger.ONE)
              } else {
                newNonExecTxs.put(nonce, id)
              }
            } else {
              txCache.remove(id)
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
  }

  private[mempool] def checkMempoolSize(): Unit = {
    if (getNonExecSubpoolSizeInSlots > MaxNonExecSubPoolSlots) {
      log.trace(s"Non exec sub pool size ($getNonExecSubpoolSizeInSlots slots) exceeds maximum allowed size " +
        s"($MaxNonExecSubPoolSlots slots). Evicting oldest transactions")
      freeNonExecSubpoolSlots()
    }

    if (getMempoolSizeInSlots > MaxMemPoolSlots) {
      log.warn(s"Memory pool size ($getMempoolSizeInSlots slots) exceeds maximum allowed size ($MaxMemPoolSlots slots). " +
        s"Start evicting oldest transactions")
      freeMempoolSlots()
      //Check a second time because evicting oldest txs can create orphans
      if (getNonExecSubpoolSizeInSlots > MaxNonExecSubPoolSlots) {
        freeNonExecSubpoolSlots()
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
      private val forcedTxQueue = new mutable.Queue[SidechainTypes#SCAT]()

      private val orderedQueue = new mutable.PriorityQueue[SidechainTypes#SCAT]()(Ordering.by(txOrder))

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



object MempoolMap {
  private val AddNewExecTransaction: Int = 0
  private val AddOrReplaceNonExecTransaction: Int = -1
  private val ReplaceExecTransaction: Int = 1


  val TxSlotSize: Int = 32 * 1024
  val MaxNumOfSlotsForTx: Int = 4
  val MaxTxSize: Int = MaxNumOfSlotsForTx * TxSlotSize

  def txSizeInSlot(tx: SidechainTypes#SCAT): Int = sizeToSlot(tx.size())

  def sizeToSlot(numOfBytes: Long): Int = {
    require(numOfBytes >= 0, "Illegal negative size value")
    ((numOfBytes + TxSlotSize - 1) / TxSlotSize).toInt
  }
}


