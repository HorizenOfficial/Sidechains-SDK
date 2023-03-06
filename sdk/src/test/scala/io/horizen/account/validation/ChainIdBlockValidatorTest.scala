package io.horizen.account.validation

import io.horizen.SidechainTypes
import io.horizen.account.block.AccountBlock
import io.horizen.account.history.validation.{ChainIdBlockSemanticValidator, InvalidTransactionChainIdException, MissingTransactionSignatureException}
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.AccountMockDataHelper
import io.horizen.params.{MainNetParams, NetworkParams}
import io.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertNotEquals, fail => jFail}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger
import java.util.Optional
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

class ChainIdBlockValidatorTest extends JUnitSuite
{
  val params: NetworkParams = MainNetParams()

  val goodSignature = new SignatureSecp256k1(
    BytesUtils.fromHexString("1c"),
    BytesUtils.fromHexString("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023"),
    BytesUtils.fromHexString("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d")
  )

  @Test
  def blockCheckFailsUnsignedTx(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txUnsigned = new EthereumTransaction(
      params.chainId,
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      null
    )

    // verify chainIds are the same
    assertEquals(params.chainId, txUnsigned.getChainId)

    txs.append(txUnsigned.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithUnsignedTx: AccountBlock = mockHelper.getMockedBlock(txs = txs)

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
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )

    // verify chainIds are the same
    assertEquals(params.chainId, txEip1559.getChainId)

    txs.append(txEip1559.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip1559Tx: AccountBlock = mockHelper.getMockedBlock(txs = txs)

    // Test: Validation succeeds if the block contains an eip1559 tx with the good chainId
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithEip1559Tx) match {
      case Success(_) => // expected
      case Failure(e) =>
        jFail("Block with a tx having good chainId expected to be valid." + e)
    }
  }

  @Test
  def blockCheckSucceedsTxLegacy(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txLegacy = new EthereumTransaction(
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )

    // verify tx chainIds is not defined
    assertEquals(null, txLegacy.getChainId)

    txs.append(txLegacy.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithLegacyTx: AccountBlock = mockHelper.getMockedBlock(txs = txs)

    // Test: Validation succeeds if the block contains a legacy tx with no chainId indicated
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithLegacyTx) match {
      case Success(_) => // expected
      case Failure(e) =>
        jFail("Block with a legacy tx expected to be valid." + e)
    }
  }

  @Test
  def blockCheckSucceedsTxEip155(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()

    val txEip155 = new EthereumTransaction(
      params.chainId,
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )

    // verify chainIds are the same
    assertEquals(params.chainId, txEip155.getChainId)

    txs.append(txEip155.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip155Tx: AccountBlock = mockHelper.getMockedBlock(txs = txs)

    // Test: Validation succeeds if the block contains an eip155 tx with the good chainId
    ChainIdBlockSemanticValidator(params).validate(mockedBlockWithEip155Tx) match {
      case Success(_) => // expected
      case Failure(e) =>
        jFail("Block with a legacy tx expected to be valid." + e)
    }
  }

  @Test
  def blockCheckFailsTxEip1559(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txEip1559 = new EthereumTransaction(
      1997L,
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )
    // verify chainIds are different
    assertNotEquals(params.chainId, txEip1559.getChainId)

    txs.append(txEip1559.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip1559Tx: AccountBlock = mockHelper.getMockedBlock(txs = txs)

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
      1997L,
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )

    // verify chainIds are different
    assertNotEquals(params.chainId, txEip155.getChainId)

    txs.append(txEip155.asInstanceOf[SidechainTypes#SCAT])

    val mockedBlockWithEip155Tx: AccountBlock = mockHelper.getMockedBlock(txs = txs)

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

