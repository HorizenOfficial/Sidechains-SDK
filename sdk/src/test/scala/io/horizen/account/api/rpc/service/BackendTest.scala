package io.horizen.account.api.rpc.service

import io.horizen.SidechainTypes
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.history.AccountHistory
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import io.horizen.account.transaction.EthereumTransaction
import org.junit.Assert.assertEquals
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.util.ModifierId

import java.math.BigInteger
import java.nio.charset.StandardCharsets

class BackendTest
    extends JUnitSuite
      with EthereumTransactionFixture {
  var historyMock: AccountHistory = _
  var headerMock: AccountBlockHeader = _

  val DefaultGasPrice: BigInteger = BigInteger.ZERO


  @Before
  def setUp(): Unit = {
    Backend.tipCache  = Option.empty
    historyMock = mock[AccountHistory]
    headerMock = mock[AccountBlockHeader]
    Mockito.when(headerMock.baseFee).thenReturn(BigInteger.ZERO)
    Mockito.when(historyMock.blockIdByHeight(ArgumentMatchers.anyInt())).thenAnswer { answer =>
      Some(answer.getArgument(0).toString)
    }

  }

  @Test
  def suggestTipCapWithAllFullBlocks(): Unit = {
    var baseGasPrice = BigInteger.ZERO
    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { _ =>
      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(AddressProposition.ZERO)
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = (0 until 3).map(_ => {
        baseGasPrice = baseGasPrice.add(BigInteger.ONE)
        val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(1000000000))
        createEIP1559Transaction(
          value = BigInteger.ZERO,
          gasFee = gasPrice,
          priorityGasFee = gasPrice)
      }
      )

      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))

    val tip = Backend.suggestTipCap(historyMock)
    /* In this case, 20 blocks will be used, with 3 txs each => total number of samples will be 60.
    The samples have values starting from 1 Gwei up to 60 Gwei, the 60th percentile of 60 samples is 35 => expected
    value is 36 Gwei
     */
    assertEquals(BigInteger.valueOf(36000000000L), tip)
  }

  @Test
  def suggestTipCapWith1TxForEachBlock(): Unit = {

    var baseGasPrice = BigInteger.ZERO
    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { _ =>
      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(AddressProposition.ZERO)
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = (0 until 1).map(_ => {
        baseGasPrice = baseGasPrice.add(BigInteger.ONE)
        val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(1000000000))
        createEIP1559Transaction(
          value = BigInteger.ZERO,
          gasFee = gasPrice,
          priorityGasFee = gasPrice)
      }
      )

      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))

    val tip = Backend.suggestTipCap(historyMock)
    /* In this case, 40 blocks will be used, with 1 txs each => total number of samples will be 40.
     The samples have values starting from 1 Gwei up to 40 Gwei, the 60th percentile of 40 samples is 23 => expected
     value is 24 Gwei
      */
    assertEquals(BigInteger.valueOf(24000000000L), tip)
  }


  @Test
  def suggestTipCapWith1FullBlockOutOf5(): Unit = {
    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))

    var baseGasPrice = BigInteger.ZERO
    var blockIdx = 0

    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { _ =>
      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(AddressProposition.ZERO)
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = if (blockIdx % 5 == 0) {
        (0 until 3).map(_ => {
          baseGasPrice = baseGasPrice.add(BigInteger.ONE)
          val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(1000000000))
          createEIP1559Transaction(
            value = BigInteger.ZERO,
            gasFee = gasPrice,
            priorityGasFee = gasPrice)
          }
        )
      }
      else
        Seq.empty[EthereumTransaction]
      blockIdx += 1
      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    val tip = Backend.suggestTipCap(historyMock)

    /* In this case, 32 blocks will be used. 1 block out of 5 has 3 txs, the other 4 are empty so they contribute with
      just 1 sample with value = lastPrice, that in this case is the default (0 Gwei) => total number of samples is
      7 "full" blocks * 3 txs + 25 empty blocks * 1 sample = 46. The 60th percentile of 46 samples is 27. The samples
      from the full blocks have values from 1 Gwei to 21 Gwei while the 25 samples from empty blocks are all 0 Gwei. So
      the first 25 samples are 0 Gwei then the other are from 1 Gwei to 21 Gwei. At the 27th position we have 3 Gwei.
      */
    assertEquals(BigInteger.valueOf(3000000000L), tip)

  }

  @Test
  def suggestTipCapWithJust1ValidSample(): Unit = {
    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))
    var blockIdx = 0

    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { _ =>
      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(AddressProposition.ZERO)
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = if (blockIdx == 0) {
        (0 until 1).map(_ => {
          val gasPrice = BigInteger.valueOf(78000000000L)
          createEIP1559Transaction(
            value = BigInteger.ZERO,
            gasFee = gasPrice,
            priorityGasFee = gasPrice)
        }
        )
      }
      else
        Seq.empty[EthereumTransaction]
      blockIdx += 1
      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    val tip = Backend.suggestTipCap(historyMock)

    /* In this case, 40 blocks will be used, with 1 sample each => total number of samples will be 40.
     39 samples have the default value (0 Gwei) and 1 sample will be 78 Gwei, the 60th percentile of 40 samples is 23
      => expected value is 0 Gwei
      */
    assertEquals(DefaultGasPrice, tip)

  }


  @Test
  def suggestTipCapWithoutValidSamples(): Unit = {
    /*
      This tests the corner case when there is no block with at least 1 valid gas price from a transaction. In this case
      we are sure that the value would be equal to the lastPrice in cache or, if the cache is empty, to the default value.
     */

    var baseGasPrice = BigInteger.ZERO
    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { answer =>
      val blockNum = answer.getArgument(0).toString.toInt
      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(AddressProposition.ZERO)
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = if (blockNum <= 100 && blockNum > 50) {
        (0 until 3).map(_ => {
          baseGasPrice = baseGasPrice.add(BigInteger.ONE)
          val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(1000000000))
          createEIP1559Transaction(
            value = BigInteger.ZERO,
            gasFee = gasPrice,
            priorityGasFee = gasPrice)
        }
        )
      }
      else
        Seq.empty[EthereumTransaction]

      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    //First test the case without a value cached. It should return the default lastPrice (0 Gwei)
    Mockito.when(historyMock.getCurrentHeight).thenReturn(50)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("50"))

    var tip = Backend.suggestTipCap(historyMock)

    assertEquals(DefaultGasPrice, tip)

    //Second test. In this case, it should return the lastPrice calculated. So, execute first suggestTipCap to put a
    // value in the cache, then try again with all empty blocks
    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))

    val defaultTip = Backend.suggestTipCap(historyMock)

    //Change the chain tip
    Mockito.when(historyMock.getCurrentHeight).thenReturn(200)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("200"))

    tip = Backend.suggestTipCap(historyMock)

    assertEquals(defaultTip, tip)
  }

  @Test
  def suggestTipCapCached(): Unit = {
    /*
    This tests that in case suggestTipCap is called more than once with the same tip of the chain,
    the gas price is not calculated again but it is used the one in the cache. In this test, the way blocks are populated
    with transactions would change the gas price, if it was calculated twice.
     */
    var baseGasPrice = BigInteger.ZERO
    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { _ =>
      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(AddressProposition.ZERO)
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = (0 until 3).map(_ => {
        baseGasPrice = baseGasPrice.add(BigInteger.ONE)
        val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(1000000000))
        createEIP1559Transaction(
          value = BigInteger.ZERO,
          gasFee = gasPrice,
          priorityGasFee = gasPrice)
      }
      )

      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))


    val defaultTip = Backend.suggestTipCap(historyMock)

    val tip = Backend.suggestTipCap(historyMock)

    assertEquals(defaultTip, tip)
  }

  @Test
  def suggestTipCapWithOnlyForgerTxs(): Unit = {
    /* This tests that txs made by the forger are not taken as samples. In this case, all the txs were created by the
    block forger, so the blocks will be considered empty => the gas price should be equal to DefaultGasPrice.
     */

    var baseGasPrice = BigInteger.ZERO
    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { _ =>
      val forger: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance()
        .generateSecret("forger1".getBytes(StandardCharsets.UTF_8))

      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(forger.publicImage())
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = (0 until 3).map(_ => {
        baseGasPrice = baseGasPrice.add(BigInteger.ONE)
        val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(1000000000))
        createEIP1559Transaction(
          value = BigInteger.ZERO,
          keyOpt = Some(forger),
          gasFee = gasPrice,
          priorityGasFee = gasPrice)
      }
      )

      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))

    val tip = Backend.suggestTipCap(historyMock)
    assertEquals(DefaultGasPrice, tip)
  }

  @Test
  def suggestTipCapWithTxsUnderPriced(): Unit = {
    /* This tests that txs with gas price lesser than the ignorePrice are not taken as samples.
    In this case, all the txs have prices under ignorePrice, so the blocks will be considered empty => the gas price
    should be equal to DefaultGasPrice.
     */
    var baseGasPrice = BigInteger.ZERO
    Mockito.when(historyMock.getStorageBlockById(ArgumentMatchers.any[ModifierId]())).thenAnswer { _ =>
      val blockMock = mock[AccountBlock]
      Mockito.when(blockMock.forgerPublicKey).thenReturn(AddressProposition.ZERO)
      Mockito.when(blockMock.header).thenReturn(headerMock)

      val listOfTxs = (0 until 3).map(_ => {
        baseGasPrice = baseGasPrice.add(BigInteger.ONE)
        val gasPrice = baseGasPrice.multiply(BigInteger.valueOf(10))
        createEIP1559Transaction(
          value = BigInteger.ZERO,
          gasFee = gasPrice,
          priorityGasFee = gasPrice)
      }
      )

      Mockito.when(blockMock.transactions).thenReturn(listOfTxs.asInstanceOf[Seq[SidechainTypes#SCAT]])
      Some(blockMock)
    }

    Mockito.when(historyMock.getCurrentHeight).thenReturn(100)
    Mockito.when(historyMock.bestBlockId).thenReturn(ModifierId("100"))

    val tip = Backend.suggestTipCap(historyMock, ignorePrice = BigInteger.valueOf(1000000000L))
    assertEquals(DefaultGasPrice, tip)
  }

}
