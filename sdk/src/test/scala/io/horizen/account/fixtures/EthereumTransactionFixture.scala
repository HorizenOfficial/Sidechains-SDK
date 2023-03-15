package io.horizen.account.fixtures

import com.google.common.primitives.Ints
import io.horizen.account.mempool.MempoolMap
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import io.horizen.account.state.GasUtil
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.{EthereumTransactionUtils, ZenWeiConverter}
import io.horizen.utils.BytesUtils
import org.mockito.Mockito

import java.{lang, util}
import java.math.BigInteger
import java.util.Optional
import scala.collection.mutable.ListBuffer
import scala.util.Random

trait EthereumTransactionFixture {


  def createLegacyTransaction(value: BigInteger,
                              nonce: BigInteger = BigInteger.ZERO,
                              keyOpt: Option[PrivateKeySecp256k1] = None,
                              gasPrice: BigInteger = BigInteger.valueOf(10000),
                              gasLimit: BigInteger = GasUtil.TxGas): EthereumTransaction = {
    val unsignedTx = new EthereumTransaction(
      EthereumTransactionUtils.getToAddressFromString("0x1234567890123456789012345678901234567890"),
      nonce, gasPrice, gasLimit, value, new Array[Byte](0), null)
    createSignedTransaction(unsignedTx, keyOpt)
  }

  def createLegacyEip155Transaction(value: BigInteger,
                                    nonce: BigInteger = BigInteger.ZERO,
                                    keyOpt: Option[PrivateKeySecp256k1] = None,
                                    gasPrice: BigInteger = BigInteger.valueOf(10000),
                                    gasLimit: BigInteger = GasUtil.TxGas): EthereumTransaction = {

    val unsignedTx = new EthereumTransaction(
      1997L,
      EthereumTransactionUtils.getToAddressFromString("0x1234567890123456789012345678901234567890"),
      nonce, gasPrice, gasLimit, value, new Array[Byte](0), null)
    createSignedTransaction(unsignedTx, keyOpt)
  }

  def createEIP1559Transaction(value: BigInteger,
                               nonce: BigInteger = BigInteger.ZERO,
                               keyOpt: Option[PrivateKeySecp256k1] = None,
                               gasFee: BigInteger = BigInteger.valueOf(10000),
                               priorityGasFee: BigInteger = BigInteger.valueOf(10000),
                               gasLimit: BigInteger = GasUtil.TxGas,
                               data: Array[Byte] = new Array[Byte](0)): EthereumTransaction = {

    val unsignedTx = new EthereumTransaction(
      1997L,
      EthereumTransactionUtils.getToAddressFromString("0x1234567890123456789012345678901234567890"),
      nonce, gasLimit, priorityGasFee, gasFee, value, data, null)
    createSignedTransaction(unsignedTx, keyOpt)
  }

  private def createSignedTransaction(unsignedTx: EthereumTransaction, keyOpt: Option[PrivateKeySecp256k1]): EthereumTransaction = {
    // Create a key if not present, create tx signature and create ethereum Transaction
    val key = keyOpt.getOrElse( {
      val seed = new Array[Byte](10)
      Random.nextBytes(seed)
      PrivateKeySecp256k1Creator.getInstance().generateSecret(seed)
    })

    val message = unsignedTx.messageToSign()
    val signature: SignatureSecp256k1 = key.sign(message)

    new EthereumTransaction(unsignedTx, signature)
  }

  def getTransactionList(listSize: Int): util.List[EthereumTransaction] = {
    val list: util.List[EthereumTransaction] = new util.ArrayList[EthereumTransaction]()
    for (a <- 1 to listSize ) {
      list.add(createLegacyTransaction(
        value=ZenWeiConverter.convertZenniesToWei(a),
        nonce=BigInteger.valueOf(a))
      )
    }
    list
  }

  def getEoa2EoaLegacyTransaction: EthereumTransaction = {
    new EthereumTransaction(
      EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
      BigInteger.valueOf(70L), // nonce
      BigInteger.valueOf(3).pow(9), // gasPrice
      GasUtil.TxGas,  // gasLimit
      BigInteger.TEN.pow(19), // value
      new Array[Byte](0),
      new SignatureSecp256k1(
        BytesUtils.fromHexString("1c"),
        BytesUtils.fromHexString("2a4afbdd7e8d99c3df9dfd9e4ecd0afe018d8dec0b8b5fe1a44d5f30e7d0a5c5"),
        BytesUtils.fromHexString("7ca554a8317ff86eb6b23d06fa210d23e551bed58f58f803a87e5950aa47a9e9"))
    )
  }

  def getUnsignedEoa2EoaLegacyTransaction: EthereumTransaction = {
    new EthereumTransaction(
      EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
      BigInteger.valueOf(70L), // nonce
      BigInteger.valueOf(3).pow(9), // gasPrice
      GasUtil.TxGas,  // gasLimit
      BigInteger.TEN.pow(19), // value
      new Array[Byte](0),
      null
    )
  }

  def getContractCallEip155LegacyTransaction: EthereumTransaction = {
    new EthereumTransaction(
      1L,
      EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
      BigInteger.valueOf(0x15), // nonce
      new BigInteger(BytesUtils.fromHexString("0a02ffee00")), // gasPrice
      new BigInteger(BytesUtils.fromHexString("0000008877")),  // gasLimit
      BigInteger.ZERO, // value
      EthereumTransactionUtils.getDataFromString("0xa9059cbb0000000000000000000000000ee513f5366075aa80565f2b0cc2dc0adc76a475000000000000000000000000000000000000000000000000b917d212e0933000"),
      new SignatureSecp256k1(
        BytesUtils.fromHexString("1b"),
        BytesUtils.fromHexString("b83e51baa9bb20d7b281032f44c1ed75f25f69ac47ac26d97e66859ea80c1295"),
        BytesUtils.fromHexString("22d794fa14567f77d7a6e6e9f780094010d70991c3028b4ea50faf62f1631e52"))
    )
  }

  def getContractDeploymentEip1559Transaction: EthereumTransaction = {
    new EthereumTransaction(
      31337,
      EthereumTransactionUtils.getToAddressFromString(""),
      BigInteger.valueOf(33L), // nonce
      new BigInteger(BytesUtils.fromHexString("00e469c3")),  // gasLimit
      new BigInteger(BytesUtils.fromHexString("3b9aca00")),  // maxPriorityFeePerGas
      new BigInteger(BytesUtils.fromHexString("0c6e64e7f7")),  // maxFeePerGas
      BigInteger.ZERO, // value
      EthereumTransactionUtils.getDataFromString("c7265637420783d223136302220793d22323335222077696474683d2231383022206865696768743d22353022207374796c653d226"),
      new SignatureSecp256k1(
        BytesUtils.fromHexString("1c"),
        BytesUtils.fromHexString("1f9202e5b7e4d65e381c63b9a6235838e7bc5d6a0ee0f070be40fd3913855d3c"),
        BytesUtils.fromHexString("1f9202e5b7e4d65e381c63b9a6235838e7bc5d6a0ee0f070be40fd3913855d3c"))
    )
  }

  def getEoa2EoaEip1559Transaction: EthereumTransaction = {
    new EthereumTransaction(
      31337,
      EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
      BigInteger.valueOf(33L), // nonce
      GasUtil.TxGas,  // gasLimit
      new BigInteger(BytesUtils.fromHexString("09172ea8")),  // maxPriorityFeePerGas
      new BigInteger(BytesUtils.fromHexString("0390a21a17")),  // maxFeePerGas
      new BigInteger(BytesUtils.fromHexString("320ec7c4e2a000")), // value
      new Array[Byte](0),
      new SignatureSecp256k1(
        BytesUtils.fromHexString("1c"),
        BytesUtils.fromHexString("e430ed7a69eb23bd96d543d6acb605af31d3eb6b1967ca4a69f31245527d942e"),
        BytesUtils.fromHexString("06d2c6034ad1905b4121aa036a382795d8f9fb2f801eafb256febb09f88f6d46"))
    )
  }

  def getUnsignedEoa2EoaEip1559Transaction: EthereumTransaction = {
    new EthereumTransaction(
      31337,
      EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
      BigInteger.valueOf(33L), // nonce
      GasUtil.TxGas,  // gasLimit
      new BigInteger(BytesUtils.fromHexString("09172ea8")),  // maxPriorityFeePerGas
      new BigInteger(BytesUtils.fromHexString("0390a21a17")),  // maxFeePerGas
      new BigInteger(BytesUtils.fromHexString("320ec7c4e2a000")), // value
      new Array[Byte](0),
      null
    )
  }

  def getEoa2EoaEip155LegacyTransaction: EthereumTransaction = {

    new EthereumTransaction(
      77L,
      EthereumTransactionUtils.getToAddressFromString("0x3535353535353535353535353535353535353535"),
      BigInteger.valueOf(9L), // nonce
      new BigInteger(BytesUtils.fromHexString("0a02ffee00")), // gasPrice
      GasUtil.TxGas,  // gasLimit
      BigInteger.TEN.pow(18), // value
      new Array[Byte](0),
      new SignatureSecp256k1(
        BytesUtils.fromHexString("1b"),
        BytesUtils.fromHexString("28EF61340BD939BC2195FE537567866003E1A15D3C71FF63E1590620AA636276"),
        BytesUtils.fromHexString("67CBE9D8997F761AECB703304B3800CCF555C9F3DC64214B297FB1966A3B6D83")
      )
    )
  }

  def getUnsignedEip155LegacyTransaction: EthereumTransaction = {
    new EthereumTransaction(
      1L,
      EthereumTransactionUtils.getToAddressFromString("0x3535353535353535353535353535353535353535"),
      BigInteger.valueOf(9L), // nonce
      new BigInteger(BytesUtils.fromHexString("0a02ffee00")), // gasPrice
      GasUtil.TxGas,  // gasLimit
      BigInteger.TEN.pow(18), // value
      new Array[Byte](0),
      null
    )
  }

  def getPartiallySignedEip155LegacyTransaction: EthereumTransaction = {
    // partially signed means this is not a real signature nor a null signature object
    // but has v=chainId and empty arrays for r,s
    // Such signature is produced for EIP155 unsigned transactions in the encoding phase
    new EthereumTransaction(
      88L,
      EthereumTransactionUtils.getToAddressFromString("0x3535353535353535353535353535353535353535"),
      BigInteger.valueOf(9L), // nonce
      new BigInteger(BytesUtils.fromHexString("0a02ffee00")), // gasPrice
      GasUtil.TxGas,  // gasLimit
      BigInteger.TEN.pow(18), // value
      new Array[Byte](0),
      new SignatureSecp256k1(Array[Byte](88), Array.fill[Byte](32)(0), Array.fill[Byte](32)(0))
    )
  }

  def getBigDataTransaction(dataSize: Int, gasLimit: BigInteger): EthereumTransaction = {

    val randomData = Array.fill(dataSize)((scala.util.Random.nextInt(256) - 128).toByte)
    new EthereumTransaction(
      1L,
      EthereumTransactionUtils.getToAddressFromString("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
      BigInteger.valueOf(0x15), // nonce
      new BigInteger(BytesUtils.fromHexString("0a02ffee00")), // gasPrice
      gasLimit,
      BigInteger.ONE, // value
      randomData,
      new SignatureSecp256k1(
        BytesUtils.fromHexString("1b"),
        BytesUtils.fromHexString("b83e51baa9bb20d7b281032f44c1ed75f25f69ac47ac26d97e66859ea80c1295"),
        BytesUtils.fromHexString("22d794fa14567f77d7a6e6e9f780094010d70991c3028b4ea50faf62f1631e52"))
    )
  }

  def addMockSizeToTx(txToMock: EthereumTransaction, size: Long): EthereumTransaction = {
    val tx = Mockito.spy[EthereumTransaction](txToMock)
    Mockito.when(tx.size()).thenReturn(size)
    tx
  }

  def setupMockSizeInSlotsToTx(txToMock: EthereumTransaction, numOfSlots: Int): EthereumTransaction = {
    val tx = Mockito.spy[EthereumTransaction](txToMock)
    Mockito.when(tx.size()).thenReturn((numOfSlots - 1) * MempoolMap.TxSlotSize + 1)
    tx
  }

  def copyEip1599EthereumTransaction(
                                      inTx: EthereumTransaction,
                                      inChainId: lang.Long = null,
                                      inTo: Optional[String] = null,
                                      inNonce: BigInteger = null,
                                      inGasLimit: BigInteger = null,
                                      inMaxPriorityFeePerGas: BigInteger = null,
                                      inMaxFeePerGas: BigInteger = null,
                                      inValue: BigInteger = null,
                                      inData: String = null,
                                      inSignature: Optional[SignatureSecp256k1] = null): EthereumTransaction =
  {
    val chainId : Long = if (inChainId != null) inChainId   else inTx.getChainId

    val to : String = if (inTo != null) {
      inTo.get()
    } else {
        if (inTx.getTo.isPresent) {
          inTx.getTo.get().address().toString
        }
        else
          ""
    }

    val nonce    = if (inNonce != null) inNonce       else inTx.getNonce
    val gasLimit = if (inGasLimit != null) inGasLimit else inTx.getGasLimit
    val maxPriorityFeePerGas = if (inMaxPriorityFeePerGas != null) inMaxPriorityFeePerGas else inTx.getMaxPriorityFeePerGas
    val maxFeePerGas = if (inMaxFeePerGas != null) inMaxFeePerGas else inTx.getMaxFeePerGas
    val value = if (inValue != null) inValue else inTx.getValue
    val data = if (inData != null) inData else BytesUtils.toHexString(inTx.getData)

    val signature = if (inSignature != null) inSignature.get() else inTx.getSignature

    new EthereumTransaction(
      chainId,
      EthereumTransactionUtils.getToAddressFromString(to),
      nonce, gasLimit, maxPriorityFeePerGas, maxFeePerGas, value,
      EthereumTransactionUtils.getDataFromString(data),
      signature)

  }

  def copyLegacyEthereumTransaction(
                                     inTx: EthereumTransaction,
                                     inTo: Optional[String] = null,
                                     inNonce: BigInteger = null,
                                     inGasLimit: BigInteger = null,
                                     inGasPrice: BigInteger = null,
                                     inValue: BigInteger = null,
                                     inData: String = null,
                                     inSignature: Optional[SignatureSecp256k1] = null): EthereumTransaction =
  {
    val to : String = if (inTo != null) {
      inTo.get()
    } else {
      if (inTx.getTo.isPresent) {
        inTx.getTo.get().address().toString
      }
      else
        ""
    }

    val nonce    = if (inNonce != null) inNonce       else inTx.getNonce
    val gasLimit = if (inGasLimit != null) inGasLimit else inTx.getGasLimit
    val gasPrice = if (inGasPrice != null) inGasPrice else inTx.getGasPrice
    val value = if (inValue != null) inValue else inTx.getValue
    val data = if (inData != null) inData else BytesUtils.toHexString(inTx.getData)

    val signature = if (inSignature != null) inSignature.get() else inTx.getSignature

    new EthereumTransaction(
      EthereumTransactionUtils.getToAddressFromString(to),
      nonce, gasPrice, gasLimit, value,
      EthereumTransactionUtils.getDataFromString(data),
      signature)

  }

  def copyEip155LegacyEthereumTransaction(
                                     inTx: EthereumTransaction,
                                     inChainId: Optional[java.lang.Long],
                                     inTo: Optional[String] = null,
                                     inNonce: BigInteger = null,
                                     inGasLimit: BigInteger = null,
                                     inGasPrice: BigInteger = null,
                                     inValue: BigInteger = null,
                                     inData: String = null,
                                     inSignature: Optional[SignatureSecp256k1] = null): EthereumTransaction =
  {
    val chainId : lang.Long = if (inChainId != null) {
      if (!inChainId.isEmpty) {
        inChainId.get()
      } else {
        null
      }
    } else {
      inTx.getChainId
    }

    val to : String = if (inTo != null) {
      inTo.get()
    } else {
      if (inTx.getTo.isPresent) {
        inTx.getTo.get().address().toString
      }
      else
        ""
    }

    val nonce    = if (inNonce != null) inNonce       else inTx.getNonce
    val gasLimit = if (inGasLimit != null) inGasLimit else inTx.getGasLimit
    val gasPrice = if (inGasPrice != null) inGasPrice else inTx.getGasPrice
    val value = if (inValue != null) inValue else inTx.getValue
    val data = if (inData != null) inData else BytesUtils.toHexString(inTx.getData)

    val signature = if (inSignature != null) inSignature.get() else inTx.getSignature

    if (chainId != null)
      new EthereumTransaction(
        chainId,
        EthereumTransactionUtils.getToAddressFromString(to),
        nonce, gasPrice, gasLimit, value,
        EthereumTransactionUtils.getDataFromString(data),
        signature)
    else
      new EthereumTransaction(
        EthereumTransactionUtils.getToAddressFromString(to),
        nonce, gasPrice, gasLimit, value,
        EthereumTransactionUtils.getDataFromString(data),
        signature)

  }



  /*
  This method creates a list of txs for different accounts. In case orphanIdx is set, 1 account out of 10 will have a gap
  in the nonce at index orphanIdx, in order to create orphan txs.
  Every tx will have the same maxGasFee but different priorityGasFee
   */
  def createTransactions(
                          numOfAccount: Int,
                          numOfTxsPerAccount: Int,
                          seed: Int = 0,
                          orphanIdx: Int = -1
                        ): scala.collection.mutable.ListBuffer[EthereumTransaction] = {
    val value = BigInteger.valueOf(12)

    val baseGas = 10000
    val maxGasFee = BigInteger.valueOf(baseGas + numOfAccount * numOfTxsPerAccount)
    val listOfAccounts: ListBuffer[Option[PrivateKeySecp256k1]] = ListBuffer[Option[PrivateKeySecp256k1]]()
    val listOfTxs = new scala.collection.mutable.ListBuffer[EthereumTransaction]

    // CircularPriorityGasBuilder is used to guarantee the txs with the highest fees
    // don't come all from the same accounts
    val gasBuilder = new CircularPriorityGasBuilder(baseGas, 17)

    (1 to numOfAccount).foreach(idx => {
      listOfAccounts += Some(PrivateKeySecp256k1Creator.getInstance().generateSecret(Ints.toByteArray(seed + idx)))
    })

    (0 until numOfTxsPerAccount).foreach(nonceTx => {
      val currentNonce = BigInteger.valueOf(nonceTx)

      listOfAccounts.zipWithIndex.foreach {
        case (keyOpt, idx) =>
          if (idx % 10 == 0 && orphanIdx >= 0 && nonceTx >= orphanIdx) { // Create orphans
            listOfTxs += createEIP1559Transaction(
              value,
              nonce = BigInteger.valueOf(nonceTx + 1),
              keyOpt = keyOpt,
              gasFee = maxGasFee,
              priorityGasFee = gasBuilder.nextPriorityGas()
            )
          } else
            listOfTxs += createEIP1559Transaction(
              value,
              nonce = currentNonce,
              keyOpt = keyOpt,
              gasFee = maxGasFee,
              priorityGasFee = gasBuilder.nextPriorityGas()
            )

      }
    })
    listOfTxs
  }

  /*
  This class returns an increasing priority gas every time nextPriorityGas is called.
  When the number of times nextPriorityGas is called is equal to period, the priority gas value returns to its original value.

   */
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

