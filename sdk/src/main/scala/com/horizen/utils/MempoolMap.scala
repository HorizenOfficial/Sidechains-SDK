package com.horizen.utils

import com.horizen.utxo.SidechainMemoryPoolEntry
import scala.collection.concurrent.TrieMap


/**
 * Map of SidechainMemoryPoolEntry, with additional data structures to keep the order by feeRate and the total bytes
 * of all the transactions contained.
 */
class MempoolMap(initialValues: Iterable[SidechainMemoryPoolEntry])  {

  private val map = new TrieMap[String, SidechainMemoryPoolEntry]()
  private var usedPoolSizeBytes = 0L
  private var idsSortedByFeeRate =  scala.collection.SortedSet[MempoolMapKey]()

  for (ele <- initialValues) {
    idsSortedByFeeRate = idsSortedByFeeRate + MempoolMapKey(ele.getUnconfirmedTx().id(), ele.feeRate.getFeeRate())
    usedPoolSizeBytes += ele.feeRate.getSize()
    map.put(ele.getUnconfirmedTx().id(), ele)
  }


  def add(entry: SidechainMemoryPoolEntry) : Option[SidechainMemoryPoolEntry] = {
    map.put(entry.getUnconfirmedTx().id(), entry) match {
      case Some(e) => Some(e)
      case None => {
        idsSortedByFeeRate = idsSortedByFeeRate + MempoolMapKey(entry.getUnconfirmedTx().id(), entry.feeRate.getFeeRate())
        usedPoolSizeBytes += entry.feeRate.getSize()
        None
      }
    }
  }

  def remove(id: String) : Option[SidechainMemoryPoolEntry]  = {
    map.remove(id) match  {
      case Some(entry) => {
        idsSortedByFeeRate = idsSortedByFeeRate.filter(_.txid != id)
        usedPoolSizeBytes -= entry.feeRate.getSize()
        Some(entry)
      }
      case None => None
    }
  }

  def values : Iterable[SidechainMemoryPoolEntry] = {
    map.values
  }

  def size : Int = {
    map.size
  }

  def usedSizeBytes : Long = {
    usedPoolSizeBytes
  }

  def get(id : String) : Option[SidechainMemoryPoolEntry] = {
    map.get(id)
  }


  def contains(id: String): Boolean = {
    map.contains(id)
  }

  /**
   * Take the tx with lowest feeRate
   */
  def headOption() : Option[SidechainMemoryPoolEntry]  = {
    idsSortedByFeeRate.headOption match {
      case Some(value) => map.get(value.txid)
      case None => None
    }
  }

  /**
   * Take n lowest entries sorted by feeRate (ascending)
   */
  def takeLowest(n: Int) : Seq[SidechainMemoryPoolEntry]   = {
    idsSortedByFeeRate.toList.take(n).map(item => map.get(item.txid).get)
  }

  /**
   * Take n highest entries sorted by feeRate (descending)
   */
  def takeHighest(n: Int) : Seq[SidechainMemoryPoolEntry]   = {
    idsSortedByFeeRate.toList.takeRight(n).map(item => map.get(item.txid).get).reverse
  }

}

case class MempoolMapKey(txid: String, feeRate: Long) extends Ordered[MempoolMapKey]{

  override def compare(that: MempoolMapKey): Int = {
    if (this.feeRate > that.feeRate){
      1
    }else if (this.feeRate < that.feeRate){
      -1
    }else{
      this.txid.compareTo(that.txid)
    }
  }

}
