package com.horizen.account.mempool

import com.horizen.SidechainTypes
import scorex.util.{ModifierId, ScorexLogging}

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.Try

case class MempoolMap() extends ScorexLogging {

  val all = TrieMap[ModifierId, SidechainTypes#SCAT]()
  val executableTxs = TrieMap[SidechainTypes#SCP, scala.collection.mutable.SortedMap[BigInteger, ModifierId]]()
  val nonExecutableTxs = TrieMap[SidechainTypes#SCP, scala.collection.mutable.SortedMap[BigInteger, ModifierId]]()
  val nonces = TrieMap[SidechainTypes#SCP, BigInteger]()

  def contains(ethTransaction: SidechainTypes#SCAT): Boolean = all.contains(ethTransaction.id)

  def containsAccountInfo(account: SidechainTypes#SCP): Boolean = nonces.contains(account)

  def initializeAccount(stateNonce: BigInteger, account: SidechainTypes#SCP): Try[MempoolMap] = Try {
    nonces.put(account, stateNonce)
    this
  }

  def add(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    if (!containsAccountInfo(ethTransaction.getFrom)) {
      log.error(s"Adding transaction failed because mempoolMap is not initialized for account ${ethTransaction.getFrom}")
      throw new IllegalStateException(s"MempoolMap is not initialized for account ${ethTransaction.getFrom}")
    }

    if (contains(ethTransaction)) {
      log.error(s"Adding transaction failed because transaction ${ethTransaction} is already present")
      throw new IllegalStateException(s"Transaction is already present")
    }

    all.put(ethTransaction.id, ethTransaction)
    val listOfTxs = executableTxs.get(ethTransaction.getFrom).getOrElse({
      val txsPerAccountMap = new mutable.TreeMap[BigInteger, ModifierId]()
      executableTxs.put(ethTransaction.getFrom, txsPerAccountMap)
      txsPerAccountMap
    })
    listOfTxs.put(ethTransaction.getNonce, ethTransaction.id)
    this
  }


}
