package io.horizen.account.mempool

import io.horizen.account.block.AccountBlock
import io.horizen.account.node.NodeAccountMemoryPool
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.{AccountStateReaderProvider, BaseStateReaderProvider}
import io.horizen.evm.Address
import io.horizen.{AccountMempoolSettings, SidechainTypes}
import sparkz.core.transaction.MempoolReader
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger
import java.util
import java.util.{Comparator, Optional}
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

class AccountMemoryPool(
    unconfirmed: MempoolMap,
    accountStateReaderProvider: AccountStateReaderProvider,
    baseStateReaderProvider: BaseStateReaderProvider,
    mempoolSettings: AccountMempoolSettings
) extends sparkz.core.transaction.MemoryPool[SidechainTypes#SCAT, AccountMemoryPool]
      with SidechainTypes
      with NodeAccountMemoryPool
      with SparkzLogging {
  override type NVCT = AccountMemoryPool

  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#SCAT] = {
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
    unconfirmed.takeExecutableTxs(Seq()).take(limit)
  }

  def takeExecutableTxs(forcedTx: Iterable[SidechainTypes#SCAT] = Seq()): MempoolMap#TransactionsByPriceAndNonce = {
    unconfirmed.takeExecutableTxs(forcedTx)
  }

  override def filter(txs: Seq[SidechainTypes#SCAT]): AccountMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(condition: SidechainTypes#SCAT => Boolean): AccountMemoryPool = {
    val filteredTxs = unconfirmed.values.filter(tx => condition(tx))
    // Reset everything
    val newMemPool =
      AccountMemoryPool.createEmptyMempool(accountStateReaderProvider, baseStateReaderProvider, mempoolSettings)
    filteredTxs.foreach(tx => newMemPool.put(tx))
    newMemPool
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#SCAT] = this

  override def put(tx: SidechainTypes#SCAT): Try[AccountMemoryPool] = {
    Try {
      new AccountMemoryPool(
        unconfirmed.add(tx).get,
        accountStateReaderProvider,
        baseStateReaderProvider,
        mempoolSettings
      )
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

  /**
   * This method is required by the Sparkz implementation of memory pool, but for the Account model the performance are
   * too low so the memory pool was changed. In the new implementation this method is no longer required.
   */
  override def putWithoutCheck(txs: Iterable[SidechainTypes#SCAT]): AccountMemoryPool = ???

  override def remove(tx: SidechainTypes#SCAT): AccountMemoryPool = {
    unconfirmed.removeFromMempool(tx) match {
      case Success(mempoolMap) =>
        new AccountMemoryPool(mempoolMap, accountStateReaderProvider, baseStateReaderProvider, mempoolSettings)
      case Failure(e) =>
        log.error(s"Exception while removing transaction $tx from MemPool", e)
        throw e
    }
  }

  override def getTransactions: util.List[SidechainTypes#SCAT] =
    unconfirmed.values.toList.asJava

  def getExecutableTransactions: util.List[ModifierId] =
    unconfirmed.mempoolTransactions(true).toList.asJava

  def getNonExecutableTransactions: util.List[ModifierId] =
    unconfirmed.mempoolTransactions(false).toList.asJava

  def getExecutableTransactionsMap: TrieMap[SidechainTypes#SCP, MempoolMap#TxByNonceMap] =
    unconfirmed.mempoolTransactionsMap(true)

  def getNonExecutableTransactionsMap: TrieMap[SidechainTypes#SCP, MempoolMap#TxByNonceMap] =
    unconfirmed.mempoolTransactionsMap(false)

  /**
   * Get the highest nonce from the pool or default to the current nonce in the state.
   */
  def getPoolNonce(account: Address): BigInteger = {
    unconfirmed.getAccountNonce(new AddressProposition(account)).getOrElse(
      accountStateReaderProvider.getAccountStateReader().getNonce(account.asInstanceOf[AddressProposition].address())
    )
  }

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
    new AccountMemoryPool(unconfirmed, accountStateReaderProvider, baseStateReaderProvider, mempoolSettings)
  }
}

object AccountMemoryPool {
  def createEmptyMempool(
      accountStateReaderProvider: AccountStateReaderProvider,
      baseStateReaderProvider: BaseStateReaderProvider,
      mempoolSettings: AccountMempoolSettings
  ): AccountMemoryPool = {
    new AccountMemoryPool(
      new MempoolMap(accountStateReaderProvider, baseStateReaderProvider, mempoolSettings),
      accountStateReaderProvider,
      baseStateReaderProvider,
      mempoolSettings
    )
  }
}
