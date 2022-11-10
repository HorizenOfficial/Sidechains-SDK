package com.horizen.account.fixtures

import com.horizen.account.state.GasUtil
import com.horizen.account.transaction.EthereumTransaction

import java.math.BigInteger
import org.web3j.crypto.Sign.SignatureData
import org.web3j.crypto.{ECKeyPair, Keys, RawTransaction, Sign, SignedRawTransaction}

trait EthereumTransactionFixture {


  def createLegacyTransaction(value: BigInteger,
                              nonce: BigInteger = BigInteger.ZERO,
                              pairOpt: Option[ECKeyPair] = None,
                              gasPrice: BigInteger = BigInteger.valueOf(10000),
                              gasLimit: BigInteger = GasUtil.TxGas): EthereumTransaction = {
    val rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, "0x", value, "")
    createSignedTransaction(rawTransaction, pairOpt)
  }

  def createEIP1559Transaction(value: BigInteger,
                               nonce: BigInteger = BigInteger.ZERO,
                               pairOpt: Option[ECKeyPair] = None,
                               gasFee: BigInteger = BigInteger.valueOf(10000),
                               priorityGasFee: BigInteger = BigInteger.valueOf(10000),
                               gasLimit: BigInteger = GasUtil.TxGas,
                               to: String = ""): EthereumTransaction = {

    val rawTransaction = RawTransaction.createTransaction(1997, nonce, gasLimit, to, value
    , "", priorityGasFee, gasFee)
    createSignedTransaction(rawTransaction, pairOpt)
  }

  private def createSignedTransaction(rawTransaction: RawTransaction, pairOpt: Option[ECKeyPair]): EthereumTransaction = {
    val tmp = new EthereumTransaction(rawTransaction)
    val message = tmp.messageToSign()

    // Create a key pair, create tx signature and create ethereum Transaction
    val pair = pairOpt.getOrElse(Keys.createEcKeyPair)
    val msgSignature = Sign.signMessage(message, pair, true)
    val signedRawTransaction = new SignedRawTransaction(tmp.getTransaction.getTransaction,
      new SignatureData(msgSignature.getV, msgSignature.getR, msgSignature.getS)
    )
    new EthereumTransaction(signedRawTransaction)

  }


  def createTransactions(
                          numOfAccount: Int,
                          numOfTxsPerAccount: Int,
                          orphanIdx: Int = -1
                        ): scala.collection.mutable.ListBuffer[EthereumTransaction] = {
    val toAddr = "0x00112233445566778899AABBCCDDEEFF01020304"
    val value = BigInteger.valueOf(12)

    val baseGas = 10000
    val maxGasFee = BigInteger.valueOf(baseGas + numOfAccount * numOfTxsPerAccount)
    val listOfAccounts: scala.collection.mutable.ListBuffer[Option[ECKeyPair]] =
      new scala.collection.mutable.ListBuffer[Option[ECKeyPair]]
    val listOfTxs = new scala.collection.mutable.ListBuffer[EthereumTransaction]

    val gasBuilder = new CircularPriorityGasBuilder(baseGas, 17)

    (1 to numOfAccount).foreach(_ => {
      listOfAccounts += Some(Keys.createEcKeyPair())
    })

    (0 until numOfTxsPerAccount).foreach(nonceTx => {
      val currentNonce = BigInteger.valueOf(nonceTx)

      listOfAccounts.zipWithIndex.foreach {
        case (pair, idx) => {
          if (idx % 10 == 0 && orphanIdx >= 0 && nonceTx >= orphanIdx) { // Create orphans
            listOfTxs += createEIP1559Transaction(
              value,
              nonce = BigInteger.valueOf(nonceTx + 1),
              pairOpt = pair,
              gasFee = maxGasFee,
              priorityGasFee = gasBuilder.nextPriorityGas(),
              to = toAddr
            )
          } else
            listOfTxs += createEIP1559Transaction(
              value,
              nonce = currentNonce,
              pairOpt = pair,
              gasFee = maxGasFee,
              priorityGasFee = gasBuilder.nextPriorityGas(),
              to = toAddr
            )
        }
      }
    })
    listOfTxs
  }

  class CircularPriorityGasBuilder(baseGas: Int, period: Int) {
    var counter: Int = 0

    def nextPriorityGas(): BigInteger = {
      if (counter == period) {
        counter = 0
      }
      val gas = baseGas + counter
      counter = counter + 1
      BigInteger.valueOf(gas)
    }
  }


}
