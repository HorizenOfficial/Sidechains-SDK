package com.horizen.account.fixtures

import com.horizen.account.transaction.EthereumTransaction
import java.math.BigInteger
import org.web3j.crypto.Sign.SignatureData
import org.web3j.crypto.{ECKeyPair, Keys, RawTransaction, Sign, SignedRawTransaction}

trait EthereumTransactionFixture {


  def createLegacyTransaction(value: BigInteger,
                              nonce: BigInteger = BigInteger.ZERO,
                              pairOpt: Option[ECKeyPair] = None,
                              gasPrice: BigInteger = BigInteger.valueOf(10000),
                              gasLimit: BigInteger = BigInteger.valueOf(21000)): EthereumTransaction = {
    val rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, "0x", value, "")
    createSignedTransaction(rawTransaction, pairOpt)
  }

  def createEIP1559Transaction(value: BigInteger,
                               nonce: BigInteger = BigInteger.ZERO,
                               pairOpt: Option[ECKeyPair] = None,
                               gasFee: BigInteger = BigInteger.ONE,
                               priorityGasFee: BigInteger = BigInteger.ONE,
                               gasLimit: BigInteger = BigInteger.ONE): EthereumTransaction = {

    val rawTransaction = RawTransaction.createTransaction(1997, nonce, gasLimit, "", value
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
}
