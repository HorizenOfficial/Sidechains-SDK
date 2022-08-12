package com.horizen.account.mempool

import com.horizen.SidechainTypes
import scorex.util.ModifierId

import java.math.BigInteger
import scala.collection.concurrent.TrieMap

case class MempoolMap() {
  val all = TrieMap[ModifierId, SidechainTypes#SCAT]()
  val executableTxs = TrieMap[SidechainTypes#SCP, scala.collection.mutable.SortedMap[BigInteger, ModifierId]]()
  val nonExecutableTxs = TrieMap[SidechainTypes#SCP, scala.collection.mutable.SortedMap[BigInteger, ModifierId]]()
  val nonces = TrieMap[SidechainTypes#SCP, BigInteger]()

}
