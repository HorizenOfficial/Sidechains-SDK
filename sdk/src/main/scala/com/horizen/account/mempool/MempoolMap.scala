package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.transaction.EthereumTransaction
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

    if (!contains(ethTransaction)) {

      val account = ethTransaction.getFrom
      val expectedNonce = nonces.get(account).get
      if (expectedNonce.equals(ethTransaction.getNonce)) {
        all.put(ethTransaction.id, ethTransaction)
        val executableTxsPerAccount = executableTxs.get(account).getOrElse({
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
        val listOfTxs = nonExecutableTxs.get(account).getOrElse({
          val txsPerAccountMap = new mutable.TreeMap[BigInteger, ModifierId]()
          nonExecutableTxs.put(account, txsPerAccountMap)
          txsPerAccountMap
        })
        if (listOfTxs.contains(ethTransaction.getNonce)) {
          val oldTxId = listOfTxs.get(ethTransaction.getNonce).get
          val oldTx = all.get(oldTxId).get
          if (canPayHigherFee(ethTransaction, oldTx)) {
            log.debug(s"Replacing transaction ${oldTx} with ${ethTransaction}")
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
        val listOfTxs = executableTxs.get(account).get
        val oldTxId = listOfTxs.get(ethTransaction.getNonce).get
        val oldTx = all.get(oldTxId).get
        if (canPayHigherFee(ethTransaction, oldTx)) {
          log.debug(s"Replacing transaction ${oldTx} with ${ethTransaction}")
          all.remove(oldTxId)
          all.put(ethTransaction.id, ethTransaction)
          listOfTxs.put(ethTransaction.getNonce, ethTransaction.id)
        }

      }
    }
    this
  }

  def canPayHigherFee(newTx: SidechainTypes#SCAT, oldTx: SidechainTypes#SCAT): Boolean = {
    require(newTx.isInstanceOf[EthereumTransaction], "Transaction is not of type EthereumTransaction")
    require(oldTx.isInstanceOf[EthereumTransaction], "Transaction is not of type EthereumTransaction")
    val newEthTx = newTx.asInstanceOf[EthereumTransaction]
    val oldEthTx = oldTx.asInstanceOf[EthereumTransaction]

    newEthTx.getMaxFeePerGas.compareTo(oldEthTx.getMaxFeePerGas) > 0 && newEthTx.getMaxPriorityFeePerGas.compareTo(oldEthTx.getMaxPriorityFeePerGas) > 0
  }

  def remove(ethTransaction: SidechainTypes#SCAT): Try[MempoolMap] = Try {
    if (all.remove(ethTransaction.id).isDefined) {
      if (nonces.get(ethTransaction.getFrom).get.compareTo(ethTransaction.getNonce) < 0){
        nonExecutableTxs.get(ethTransaction.getFrom).foreach(nonExecTxsPerAccount => {
            nonExecTxsPerAccount.remove(ethTransaction.getNonce)
            if (nonExecTxsPerAccount.isEmpty) {
              nonExecutableTxs.remove(ethTransaction.getFrom)
              if (executableTxs.get(ethTransaction.getFrom).isEmpty) {
                nonces.remove(ethTransaction.getFrom)
              }
            }
        })
       }
      else {
        val execTxsPerAccount = executableTxs.get(ethTransaction.getFrom).foreach( execTxsPerAccount => {
          execTxsPerAccount.remove(ethTransaction.getNonce)
          nonces.put(ethTransaction.getFrom, ethTransaction.getNonce)
          var demotedTxId = execTxsPerAccount.remove(ethTransaction.getNonce.add(BigInteger.ONE))
          while (demotedTxId.isDefined) {
            val demotedTx = all.get(demotedTxId.get).get
            val nonExecTxsPerAccount = nonExecutableTxs.get(ethTransaction.getFrom).getOrElse({
              val txsPerAccountMap = new mutable.TreeMap[BigInteger, ModifierId]()
              nonExecutableTxs.put(ethTransaction.getFrom, txsPerAccountMap)
              txsPerAccountMap
            })
            nonExecTxsPerAccount.put(demotedTx.getNonce, demotedTxId.get)
            demotedTxId = execTxsPerAccount.remove(demotedTx.getNonce.add(BigInteger.ONE))
          }
          if (execTxsPerAccount.isEmpty) {
            executableTxs.remove(ethTransaction.getFrom)
            if (nonExecutableTxs.get(ethTransaction.getFrom).isEmpty) {
              nonces.remove(ethTransaction.getFrom)
            }
          }
        })
       }
    }
    this
  }


  }
