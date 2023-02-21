package com.horizen.account.state

import com.horizen.account.storage.AccountStateMetadataStorage
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment, FeeUtils}
import com.horizen.consensus.intToConsensusEpochNumber
import com.horizen.fixtures.{SecretFixture, SidechainTypesTestsExtension, StoreFixture, TransactionFixture}
import com.horizen.params.MainNetParams
import com.horizen.utils.BytesUtils
import io.horizen.evm.Database
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.VersionTag
import sparkz.core.utils.NetworkTimeProvider

import java.math.BigInteger
import scala.util.{Failure, Success}

class AccountStateTest
    extends JUnitSuite
      with SecretFixture
      with TransactionFixture
      with StoreFixture
      with MockitoSugar
      with SidechainTypesTestsExtension {

  val params: MainNetParams = MainNetParams()
  var state: AccountState = _
  val metadataStorage: AccountStateMetadataStorage = mock[AccountStateMetadataStorage]

  @Before
  def setUp(): Unit = {
    val messageProcessors: Seq[MessageProcessor] = Seq()

    val stateDbStorege: Database = mock[Database]
    val versionTag: VersionTag = VersionTag @@ BytesUtils.toHexString(getVersion.data())
    val mockedTimeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]

    state = new AccountState(
      params,
      mockedTimeProvider,
      MockedHistoryBlockHashProvider,
      versionTag,
      metadataStorage,
      stateDbStorege,
      messageProcessors
    )
  }

  private def sumFeeInfos(feeInfo: AccountBlockFeeInfo*): BigInteger = {
    feeInfo.foldLeft(BigInteger.ZERO)((sum, info) => sum.add(info.baseFee).add(info.forgerTips))
  }

  @Test
  def feePayments(): Unit = {

    // Test 1: No block fee info record in the storage
    Mockito.when(metadataStorage.getFeePayments(ArgumentMatchers.any[Int]())).thenReturn(Seq())
    var feePayments: Seq[AccountPayment] = state.getFeePaymentsInfo(0)
    assertEquals(s"Fee payments size expected to be different.", 0, feePayments.size)

    // Test 2: with single block fee info record in the storage
    Mockito.reset(metadataStorage)
    val blockFeeInfo1: AccountBlockFeeInfo =
      AccountBlockFeeInfo(BigInteger.valueOf(100), BigInteger.valueOf(50), getPrivateKeySecp256k1(1000).publicImage())

    Mockito.when(metadataStorage.getFeePayments(ArgumentMatchers.any[Int]())).thenReturn(Seq(blockFeeInfo1))

    feePayments = state.getFeePaymentsInfo(0)
    assertEquals(s"Fee payments size expected to be different.", 1, feePayments.size)
    assertEquals(
      s"Fee value for baseFee ${feePayments.head.value} is wrong",
      blockFeeInfo1.baseFee.add(blockFeeInfo1.forgerTips),
      feePayments.head.value
    )

    // Test 3: with multiple block fee info records for different forger keys in the storage
    Mockito.reset(metadataStorage)
    val blockFeeInfo2: AccountBlockFeeInfo =
      AccountBlockFeeInfo(BigInteger.valueOf(101), BigInteger.valueOf(51), getPrivateKeySecp256k1(1001).publicImage())

    // use value 103 in order to have a rounding in the pool quotas sum(baseFees) = 304 => 101 each quota and a remainder of 1 zat
    val blockFeeInfo3: AccountBlockFeeInfo =
      AccountBlockFeeInfo(BigInteger.valueOf(103), BigInteger.valueOf(52), getPrivateKeySecp256k1(1002).publicImage())

    var totalFee = sumFeeInfos(blockFeeInfo1, blockFeeInfo2, blockFeeInfo3)
    val poolFee = blockFeeInfo1.baseFee.add(blockFeeInfo2.baseFee.add(blockFeeInfo3.baseFee))
    val poolFeeQuota = poolFee.divide(BigInteger.valueOf(3))

    Mockito
      .when(metadataStorage.getFeePayments(ArgumentMatchers.any[Int]()))
      .thenReturn(Seq(blockFeeInfo1, blockFeeInfo2, blockFeeInfo3))

    feePayments = state.getFeePaymentsInfo(0)
    assertEquals(s"Fee payments size expected to be different.", 3, feePayments.size)

    var forgerTotalFee = feePayments.foldLeft(BigInteger.ZERO)((sum, payment) => sum.add(payment.value))
    assertEquals(s"Total fee value is wrong", totalFee, forgerTotalFee)

    val forger1Fee = blockFeeInfo1.forgerTips.add(poolFeeQuota).add(BigInteger.ONE) // plus 1 undistributed satoshi
    val forger2Fee = blockFeeInfo2.forgerTips.add(poolFeeQuota)
    val forger3Fee = blockFeeInfo3.forgerTips.add(poolFeeQuota)
    assertEquals(s"Fee value for forger1 is wrong", forger1Fee, feePayments(0).value)
    assertEquals(s"Fee value for forger2 is wrong", forger2Fee, feePayments(1).value)
    assertEquals(s"Fee value for forger3 is wrong", forger3Fee, feePayments(2).value)

    // Test 4: with multiple block fee info records for non-unique forger keys in the storage
    Mockito.reset(metadataStorage)
    // Block was created with the forger3 (second time in the epoch)
    val blockFeeInfo4: AccountBlockFeeInfo =
      AccountBlockFeeInfo(BigInteger.valueOf(50), BigInteger.valueOf(51), blockFeeInfo3.forgerAddress)

    totalFee = sumFeeInfos(blockFeeInfo1, blockFeeInfo2, blockFeeInfo3, blockFeeInfo4)

    Mockito
      .when(metadataStorage.getFeePayments(ArgumentMatchers.any[Int]()))
      .thenReturn(Seq(blockFeeInfo1, blockFeeInfo2, blockFeeInfo3, blockFeeInfo4))

    feePayments = state.getFeePaymentsInfo(0)
    assertEquals(s"Fee payments size expected to be different.", 3, feePayments.size)

    forgerTotalFee = feePayments.foldLeft(BigInteger.ZERO)((sum, payment) => sum.add(payment.value))
    assertEquals(s"Total fee value is wrong", totalFee, forgerTotalFee)

    // Test 5: with multiple block fee info records created by 2 unique forgers
    val f1Addr = getPrivateKeySecp256k1(1111).publicImage()
    val f2Addr = getPrivateKeySecp256k1(2222).publicImage()

    val bfi1 = AccountBlockFeeInfo(BigInteger.valueOf(100), BigInteger.valueOf(0), f1Addr)
    val bfi2 = AccountBlockFeeInfo(BigInteger.valueOf(200), BigInteger.valueOf(20), f1Addr)
    val bfi3 = AccountBlockFeeInfo(BigInteger.valueOf(300), BigInteger.valueOf(0), f1Addr)
    val bfi4 = AccountBlockFeeInfo(BigInteger.valueOf(100), BigInteger.valueOf(10), f2Addr)
    val bfi5 = AccountBlockFeeInfo(BigInteger.valueOf(200), BigInteger.valueOf(0), f2Addr)

    Mockito.reset(metadataStorage)
    Mockito
      .when(metadataStorage.getFeePayments(ArgumentMatchers.any[Int]()))
      .thenReturn(Seq(bfi1, bfi2, bfi3, bfi4, bfi5))

    totalFee = sumFeeInfos(bfi1, bfi2, bfi3, bfi4, bfi5)

    feePayments = state.getFeePaymentsInfo(0)
    assertEquals(s"Fee payments size expected to be different.", 2, feePayments.size)

    forgerTotalFee = feePayments.foldLeft(BigInteger.ZERO)((sum, payment) => sum.add(payment.value))
    assertEquals(s"Total fee value is wrong", totalFee, forgerTotalFee)
  }

  @Test
  def testSwitchingConsensusEpoch(): Unit = {
    Mockito.when(metadataStorage.getConsensusEpochNumber).thenReturn(Option(intToConsensusEpochNumber(86400)))
    val currentEpochNumber = state.getConsensusEpochNumber

    assertEquals(state.isSwitchingConsensusEpoch(intToConsensusEpochNumber(currentEpochNumber.get)), true)
  }

  @Test
  def testTransactionLimitExceedsBlockGasLimit(): Unit = {
    val tx = mock[EthereumTransaction]

    Mockito.when(tx.semanticValidity()).thenAnswer(_ => true)
    Mockito.when(tx.getGasLimit).thenReturn(FeeUtils.GAS_LIMIT.add(BigInteger.ONE))

    state.validate(tx) match {
      case Failure(_) =>
      case Success(_) => Assert.fail("Transaction with gas limit greater than block is expected to fail")
    }
  }
}
