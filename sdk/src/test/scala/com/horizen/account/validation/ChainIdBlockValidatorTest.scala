package com.horizen.account.validation

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.AccountMockDataHelper
import com.horizen.account.utils.EthereumTransactionUtils.convertToBytes
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertNotEquals, fail => jFail}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.web3j.crypto.Sign.SignatureData

import java.math.BigInteger
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

class ChainIdBlockValidatorTest extends JUnitSuite
{
  val params: NetworkParams = MainNetParams()

  val goodSignatureData = new SignatureData(
    27.toByte,
    BytesUtils.fromHexString("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023"),
    BytesUtils.fromHexString("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d")
  )

  val goodEip155SignatureData = new SignatureData(
    convertToBytes(params.chainId*2+35),
    BytesUtils.fromHexString("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023"),
    BytesUtils.fromHexString("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d")
  )

  val badEip155SignatureData = new SignatureData(
    convertToBytes(44*2+35),
    BytesUtils.fromHexString("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023"),
    BytesUtils.fromHexString("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d")
  )


  @Test
  def blockCheckFailsUnsignedTx(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txUnsigned = new EthereumTransaction(
      params.chainId,
      null,
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      "",
      null
    )

    // verify chainIds are the same
    assertEquals(params.chainId, txUnsigned.getChainId)

    txs.append(txUnsigned.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithUnsignedTx: AccountBlock = mockHelper.getMockedBlock2(txs)

    // Test: Validation fails if the block contains an unsigned tx
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithUnsignedTx) match {
      case Success(_) =>
        jFail("Block with an unsigned tx expected to be invalid.")
      case Failure(e) =>
        println(e.getMessage)
        assertEquals("Different exception type expected during validation.",
          classOf[MissingTransactionSignatureException], e.getClass)
    }
  }


  @Test
  def blockCheckSucceedsTxEip1559(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txEip1559 = new EthereumTransaction(
      params.chainId,
      null,
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      "",
      goodSignatureData
    )

    // verify chainIds are the same
    assertEquals(params.chainId, txEip1559.getChainId)

    txs.append(txEip1559.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip1559Tx: AccountBlock = mockHelper.getMockedBlock2(txs)

    // Test: Validation succeeds if the block contains an eip1559 tx with the good chainId
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithEip1559Tx) match {
      case Success(_) => // expected
      case Failure(e) =>
        jFail("Block with a tx having good chainId expected to be valid.")
    }
  }

  @Test
  def blockCheckSucceedsTxLegacy(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txLegacy = new EthereumTransaction(
      null,
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      "",
      goodSignatureData
    )

    // verify tx chainIds is not defined
    assertEquals(null, txLegacy.getChainId)

    txs.append(txLegacy.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithLegacyTx: AccountBlock = mockHelper.getMockedBlock2(txs)

    // Test: Validation succeeds if the block contains a legacy tx with no chainId indicated
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithLegacyTx) match {
      case Success(_) => // expected
      case Failure(e) =>
        jFail("Block with a jegacy tx expected to be valid.")
    }
  }

  @Test
  def blockCheckSucceedsTxEip155(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()

    val txEip155 = new EthereumTransaction(
      null,
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      "",
      goodEip155SignatureData
    )

    // verify chainIds are the same
    assertEquals(params.chainId, txEip155.getChainId)

    txs.append(txEip155.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip155Tx: AccountBlock = mockHelper.getMockedBlock2(txs)

    // Test: Validation succeeds if the block contains an eip155 tx with the good chainId
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithEip155Tx) match {
      case Success(_) => // expected
      case Failure(e) =>
        jFail("Block with a jegacy tx expected to be valid.")
    }
  }

  @Test
  def blockCheckFailsTxEip1559(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txEip1559 = new EthereumTransaction(
      1997L,
      null,
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      "",
      goodSignatureData
    )
    // verify chainIds are different
    assertNotEquals(params.chainId, txEip1559.getChainId)

    txs.append(txEip1559.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip1559Tx: AccountBlock = mockHelper.getMockedBlock2(txs)

    // Test: Validation fails if the block contains an eip1559 tx with a wrong chainId
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithEip1559Tx) match {
      case Success(_) =>
        jFail("Block with a tx having wrong chainId expected to be invalid.")
      case Failure(e) =>
        println(e.getMessage)
        assertEquals("Different exception type expected during validation.",
          classOf[InvalidTransactionChainIdException], e.getClass)
    }
  }

  @Test
  def blockCheckFailsTxEip155(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()

    val txEip155 = new EthereumTransaction(
      null,
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      "", badEip155SignatureData
    )

    // verify chainIds are different
    assertNotEquals(params.chainId, txEip155.getChainId)

    txs.append(txEip155.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip155Tx: AccountBlock = mockHelper.getMockedBlock2(txs)

    // Test: Validation fails if the block contains an eip155 tx with a wrong chainId
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithEip155Tx) match {
      case Success(_) =>
        jFail("Block with a tx having wrong chainId expected to be invalid.")
      case Failure(e) =>
        println(e.getMessage)
        assertEquals("Different exception type expected during validation.",
          classOf[InvalidTransactionChainIdException], e.getClass)
    }
  }
}

