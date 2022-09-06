package com.horizen

import com.horizen.box.Box
import com.horizen.node.NodeMemoryPool
import com.horizen.transaction.BoxTransaction
import com.horizen.utils.MempoolMap
import com.horizen.utils.tps.TpsUtils
import scorex.util.{ModifierId, ScorexLogging}

import java.time.LocalDateTime
import java.util.{Comparator, Optional, List => JList}
import java.util.{ArrayList => JArrayList}
import com.horizen.box.{WithdrawalRequestBox}
import sparkz.core.transaction.MempoolReader

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class SidechainMemoryPool private(unconfirmed: MempoolMap, mempoolSettings: MempoolSettings)
  extends sparkz.core.transaction.MemoryPool[SidechainTypes#SCBT, SidechainMemoryPool]
  with SidechainTypes
  with NodeMemoryPool
  with ScorexLogging
{
  var maxPoolSizeBytes : Long =  mempoolSettings.maxSize * 1024 * 1024
  val minFeeRate : Long = mempoolSettings.minFeeRate

  override type NVCT = SidechainMemoryPool

  // Getters:
  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#SCBT] = {
    unconfirmed.get(modifierId) match {
      case Some(tx) => Some(tx.getUnconfirmedTx())
      case None => None
    }
  }

  override def contains(id: ModifierId): Boolean = {
    unconfirmed.contains(id)
  }

  override def getAll(ids: Seq[ModifierId]): Seq[SidechainTypes#SCBT] = {
    ids.flatMap(modifierById)
  }

  override def size: Int = {
    unconfirmed.size
  }

  override def getTransactionsSortedByFee(limit: Int): JList[SidechainTypes#SCBT] = {
    unconfirmed.values.toList.sortBy(-_.getUnconfirmedTx().fee).take(limit).map(tx => tx.getUnconfirmedTx()).asJava
  }

  override def getTransactionsSortedByFeeRate(limit: Int): JList[SidechainTypes#SCBT] = {
    take(limit).toList.asJava
  }

  override def take(limit: Int): Iterable[SidechainTypes#SCBT] = {
    unconfirmed.takeHighest(limit).map(ele => ele.getUnconfirmedTx())
  }

  def take(sortFunc: (SidechainMemoryPoolEntry, SidechainMemoryPoolEntry) => Boolean,
           limit: Int): Iterable[SidechainTypes#SCBT] = {
    unconfirmed.values.toSeq.sortWith(sortFunc).take(limit).map(tx => tx.getUnconfirmedTx())
  }

  def takeWithWithdrawalBoxesLimit(allowedWithdrawalBoxes: Int): Iterable[SidechainTypes#SCBT] = {
    val filteredTxs: JArrayList[SidechainTypes#SCBT] = new JArrayList[SidechainTypes#SCBT]()
    var newWithdrawalBoxes = 0
    take(size).foreach( tx => {
      val txWithdrawalBoxes = tx.newBoxes().asScala.count(box => box.isInstanceOf[WithdrawalRequestBox])
      if( txWithdrawalBoxes + newWithdrawalBoxes <= allowedWithdrawalBoxes) {
        newWithdrawalBoxes += txWithdrawalBoxes
        filteredTxs.add(tx)
      }
    })
    filteredTxs.asScala.toList
  }

  override def filter(txs: Seq[SidechainTypes#SCBT]): SidechainMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  /**
   * Return a new SidechainMemoryPool instance with the subset of the original txs satisfying the condition check
   * @param condition to use to filter the transactions
   */
  override def filter(condition: SidechainTypes#SCBT => Boolean): SidechainMemoryPool = {
    val filteredMap = unconfirmed.values.filter(ele => condition(ele.getUnconfirmedTx()))
    new SidechainMemoryPool(new MempoolMap(filteredMap), mempoolSettings)
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#SCBT] = {
    this
  }

  // Setters:
  override def put(tx: SidechainTypes#SCBT): Try[SidechainMemoryPool] = {
    val startTime = LocalDateTime.now()
    TpsUtils.log("put single tx in mempool, checking tx compatibility", log)
    // check if tx is not colliding with unconfirmed using
    // tx.incompatibilityChecker().hasIncompatibleTransactions(tx, unconfirmed)
    val entry = SidechainMemoryPoolEntry(tx)
    if (entry.feeRate.getFeeRate() < minFeeRate) {
      val compatibilityCheckEndTime = LocalDateTime.now()
      val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
      TpsUtils.log(s"put single tx in mempool stopped, tx fee is less than mempool.minFeeRate in $compatibilityCheckDuration", log)

      Failure(new IllegalArgumentException("Transaction fee is less than mempool.minFeeRate - " + tx))
    } else if (tx.incompatibilityChecker().isMemoryPoolCompatible &&
        tx.incompatibilityChecker().isTransactionCompatible(tx, unconfirmed.values.toList.map(t => t.getUnconfirmedTx()).asJava)) {

      val compatibilityCheckEndTime = LocalDateTime.now()
      val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
      TpsUtils.log(s"done checking tx compatibility in $compatibilityCheckDuration", log)

      if (addWithSizeCheck(entry))
        Success[SidechainMemoryPool](this)
      else
        Failure(new IllegalArgumentException("Mempool full and tx feeRate too low, unable to add transaction - " + tx))
    } else {
      val compatibilityCheckEndTime = LocalDateTime.now()
      val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
      TpsUtils.log(s"put single tx in mempool stopped, tx fee is incompatible in $compatibilityCheckDuration", log)

      Failure(new IllegalArgumentException("Transaction is incompatible - " + tx))
    }
  }


  override def put(txs: Iterable[SidechainTypes#SCBT]): Try[SidechainMemoryPool] = {
    val startTime = LocalDateTime.now()
    TpsUtils.log("put multiple tx in mempool, checking tx compatibility", log)
    // for each tx in txs call "put"
    // rollback to initial state if "put(tx)" failed
    for (t <- txs.tails) {
      if (t != Nil &&
          (!t.head.incompatibilityChecker().isMemoryPoolCompatible ||
           !t.head.incompatibilityChecker().isTransactionCompatible(t.head, t.tail.toList.asJava))) {

        val compatibilityCheckEndTime = LocalDateTime.now()
        val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
        TpsUtils.log(s"put multiple tx in mempool stopped, incompatible transaction found in $compatibilityCheckDuration", log)

        return Failure(new IllegalArgumentException("There is incompatible transaction - " + t.head))
      }
    }

    val currentUnconfirmed = unconfirmed.values.toList.map(t => t.getUnconfirmedTx()).asJava
    for (t <- txs) {
      if (!t.incompatibilityChecker().isTransactionCompatible(t, currentUnconfirmed)) {

        val compatibilityCheckEndTime = LocalDateTime.now()
        val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
        TpsUtils.log(s"put multiple tx in mempool stopped, incompatible transaction found in $compatibilityCheckDuration", log)

        return Failure(new IllegalArgumentException("There is incompatible transaction - " + t))
      }
    }

    val compatibilityCheckEndTime = LocalDateTime.now()
    val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
    TpsUtils.log(s"done checking tx compatibility in $compatibilityCheckDuration", log)

    for (t <- txs) {
      val entry = SidechainMemoryPoolEntry(t)
      if (entry.feeRate.getFeeRate() >= minFeeRate) {
        addWithSizeCheck(entry)
      }
    }
    new Success[SidechainMemoryPool](this)
  }

  /**
   * Add tx to the mempool.
   * Lowest fee-rate txs are removed in case max mempool size is reached.
   * @return true if the transaction has been inserted succesfully
   */
  def addWithSizeCheck(entry: SidechainMemoryPoolEntry) : Boolean = {
    while (unconfirmed.usedSizeBytes + entry.feeRate.getSize() > maxPoolSizeBytes){
      val lastEntry = unconfirmed.headOption
      if (lastEntry.isEmpty || (lastEntry.get.feeRate.getFeeRate() > entry.feeRate.getFeeRate())){
        //the pool is empty but txsize exceeds its  limit,
        //or the pool is full, and the entry we are trying to add has feerate lower than the miminum in pool
        //In both cases the insert will fail
        return false;
      }
      unconfirmed.remove(lastEntry.get.getUnconfirmedTx().id())
    }
    unconfirmed.add(entry)
    true
  }

  // TO DO: check usage in Scorex core
  // Probably, we need to do a Global check inside for both new and existing transactions.
  override def putWithoutCheck(txs: Iterable[SidechainTypes#SCBT]): SidechainMemoryPool = {
    val startTime = LocalDateTime.now()
    TpsUtils.log("putWithoutCheck multiple tx in mempool, checking tx compatibility", log)
    for (t <- txs.tails) {
      if (t != Nil && !t.head.incompatibilityChecker().isTransactionCompatible(t.head, t.tail.toList.asJava)) {
        val compatibilityCheckEndTime = LocalDateTime.now()
        val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
        TpsUtils.log(s"putWithoutCheck multiple tx in mempool stopped, incompatible transaction found in $compatibilityCheckDuration", log)

        return this
      }
    }

    for (t <- txs) {
      if (!t.incompatibilityChecker().isTransactionCompatible(t, unconfirmed.values.toList.map(t => t.getUnconfirmedTx()).asJava)) {
        val compatibilityCheckEndTime = LocalDateTime.now()
        val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
        TpsUtils.log(s"putWithoutCheck multiple tx in mempool stopped, incompatible transaction found in $compatibilityCheckDuration", log)

        return this
      }
    }

    val compatibilityCheckEndTime = LocalDateTime.now()
    val compatibilityCheckDuration = TpsUtils.getMinAndSecFromTwoDates(startTime, compatibilityCheckEndTime)
    TpsUtils.log(s"done checking tx compatibility in $compatibilityCheckDuration", log)

    for (t <- txs) {
      val entry = SidechainMemoryPoolEntry(t)
      if (entry.feeRate.getFeeRate() >= minFeeRate) {
        addWithSizeCheck(entry)
      }
    }

    this
  }

  override def remove(tx: SidechainTypes#SCBT): SidechainMemoryPool = {
    unconfirmed.remove(tx.id)
    this
  }
  
  override def getTransactions: JList[SidechainTypes#SCBT] = {
    unconfirmed.values.map(el => el.getUnconfirmedTx()).toList.asJava
  }

  override def getTransactions(c: Comparator[SidechainTypes#SCBT], limit: Int): JList[SidechainTypes#SCBT] = {
    val txs = unconfirmed.values.toList.map(tx => tx.getUnconfirmedTx()).asJava
    txs.sort(c)
    txs.subList(0, limit)
  }

  override def getSize: Int = unconfirmed.size

  def usedSizeBytes: Long = {
    unconfirmed.usedSizeBytes
  }

  def usedSizeKBytes: Int = {
    Math.round(unconfirmed.usedSizeBytes/1024)
  }

  def usedPercentage: Int = {
    Math.round((unconfirmed.usedSizeBytes*100)/maxPoolSizeBytes)
  }

  override def getTransactionById(transactionId: String): Optional[BoxTransaction[SCP, Box[SCP]]] = {
    Optional.ofNullable(unconfirmed.get(transactionId) match {
      case Some(mempoolEntry) => mempoolEntry.getUnconfirmedTx()
      case None => null
    })
  }


}

object SidechainMemoryPool
{
  def createEmptyMempool(mempoolSettings: MempoolSettings) : SidechainMemoryPool = {
    new SidechainMemoryPool(new MempoolMap(List()), mempoolSettings)
  }

}

