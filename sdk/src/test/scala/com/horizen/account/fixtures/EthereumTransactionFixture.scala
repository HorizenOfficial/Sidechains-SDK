package com.horizen.account.fixtures

import com.horizen.account.transaction.EthereumTransaction
import org.web3j.crypto.Sign.SignatureData
import org.web3j.crypto.{ECKeyPair, Keys, RawTransaction, Sign, SignedRawTransaction}

import java.math.BigInteger

trait EthereumTransactionFixture {


  def createLegacyTransaction(value: java.math.BigInteger, nonce: java.math.BigInteger = BigInteger.ZERO): EthereumTransaction = {
    val rawTransaction = RawTransaction.createTransaction(nonce, value, value, "0x", value, "")
    createSignedTransaction(rawTransaction, None)
  }

  def createEIP1559Transaction(value: java.math.BigInteger,
                               nonce: java.math.BigInteger = BigInteger.ZERO,
                               pairOpt: Option[ECKeyPair] = None,
                               gasFee: java.math.BigInteger = BigInteger.ONE,
                               priorityGasFee: java.math.BigInteger = BigInteger.ONE): EthereumTransaction = {

    val rawTransaction = RawTransaction.createTransaction(1997, nonce, value, "", value
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
