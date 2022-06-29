package com.horizen.utils

import com.horizen.SidechainMemoryPoolEntry
import scala.collection.concurrent.TrieMap


/**
 * Map of SidechainMemoryPoolEntry, with additional data structures to keep the order by feeRate and the total bytes
 * of all the transactions contained.
 */
class MempoolMap(initialValues: Option[Iterable[SidechainMemoryPoolEntry]])  {

  private val map = new TrieMap[String, SidechainMemoryPoolEntry]()
  private var usedPoolSizeBytes = 0L
  private var idsSortedByFeeRate =  Seq[MempoolMapKey]()

  if (initialValues.isDefined){
    for (ele <- initialValues.get) {
      idsSortedByFeeRate :+ new MempoolMapKey(ele.getUnconfirmedTx().id(), ele.feeRate.getFeeRate())
      usedPoolSizeBytes += ele.feeRate.getSize()
      map.put(ele.getUnconfirmedTx().id(), ele)
    }
    idsSortedByFeeRate.sortBy(_.feeRate)
  }

  def add(entry: SidechainMemoryPoolEntry) : Option[SidechainMemoryPoolEntry] = {
    map.put(entry.getUnconfirmedTx().id(), entry) match {
      case Some(e) => Some(e)
      case None => {
        idsSortedByFeeRate = idsSortedByFeeRate :+ new MempoolMapKey(entry.getUnconfirmedTx().id(), entry.feeRate.getFeeRate())
        idsSortedByFeeRate = idsSortedByFeeRate.sortBy(_.feeRate)
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

class MempoolMapKey(val txid: String, val feeRate: Long)  {

}
