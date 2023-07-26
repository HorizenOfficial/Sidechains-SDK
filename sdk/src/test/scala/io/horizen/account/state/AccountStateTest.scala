package io.horizen.account.state

import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.fork.GasFeeFork
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.storage.AccountStateMetadataStorage
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment}
import io.horizen.consensus.{ConsensusParamsUtil, intToConsensusEpochNumber}
import io.horizen.evm._
import io.horizen.fixtures.{SecretFixture, SidechainTypesTestsExtension, StoreFixture}
import io.horizen.fork.{ConsensusParamsFork, ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch, SimpleForkConfigurator}
import io.horizen.params.NetworkParams
import io.horizen.utils
import io.horizen.utils.{BytesUtils, ClosableResourceHandler, TimeToEpochUtils}
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.VersionTag
import sparkz.core.utils.NetworkTimeProvider

import java.math.BigInteger
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.{Failure, Success}

class AccountStateTest
    extends JUnitSuite
      with SecretFixture
      with EthereumTransactionFixture
      with StoreFixture
      with MockitoSugar
      with SidechainTypesTestsExtension
      with ClosableResourceHandler {

  val stateDbStorage: Database = new MemoryDatabase
  var params: NetworkParams = mock[NetworkParams]
  val metadataStorage: AccountStateMetadataStorage = mock[AccountStateMetadataStorage]
  var state: AccountState = _
  ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
    (0, ConsensusParamsFork.DefaultConsensusParamsFork),
  ))
  ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp)))

  private def addMockBalance(account: Address, value: BigInteger) = {
    val stateDB = new StateDB(stateDbStorage, new Hash(metadataStorage.getAccountStateRoot))
    stateDB.addBalance(account, value)
    val root = stateDB.commit()
    stateDB.close()
    Mockito.when(metadataStorage.getAccountStateRoot).thenReturn(root.toBytes)
  }

  @Before
  def setUp(): Unit = {
    ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")

    val versionTag: VersionTag = VersionTag @@ BytesUtils.toHexString(getVersion.data())
    val mockedTimeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]
    val messageProcessors: Seq[MessageProcessor] = Seq()

    Mockito.when(params.chainId).thenReturn(1997)
    Mockito.when(metadataStorage.getConsensusEpochNumber).thenReturn(None)
    Mockito.when(metadataStorage.getAccountStateRoot).thenReturn(Hash.ZERO.toBytes)
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, ConsensusParamsFork.DefaultConsensusParamsFork),
    ))
    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp)))

    state = new AccountState(
      params,
      mockedTimeProvider,
      MockedHistoryBlockHashProvider,
      versionTag,
      metadataStorage,
      stateDbStorage,
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
    ConsensusParamsUtil.setCurrentConsensusEpoch(86400)

    val currentEpochNumber = state.getConsensusEpochNumber

    assertEquals(state.isSwitchingConsensusEpoch(intToConsensusEpochNumber(currentEpochNumber.get)), true)
  }

  @Test
  def testTransactionLimitExceedsBlockGasLimit(): Unit = {
    val tx = mock[EthereumTransaction]

    Mockito.when(tx.semanticValidity()).thenAnswer(_ => true)
    Mockito.when(tx.getGasLimit).thenReturn(DefaultGasFeeFork.blockGasLimit.add(BigInteger.ONE))

    state.validate(tx) match {
      case Failure(_) =>
      case Success(_) => Assert.fail("Transaction with gas limit greater than block is expected to fail")
    }
  }

  def testTransactionMaxFeesBelowMinimum(
      minimumBaseFee: BigInteger,
      txGasLow: EthereumTransaction,
      txGasHigh: EthereumTransaction
  ): Unit = {
    val activationEpoch = 5

    class MinimumFeeTestFork extends SimpleForkConfigurator {
      override def getOptionalSidechainForks
          : java.util.List[utils.Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = {
        List(
          new utils.Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](
            SidechainForkConsensusEpoch(activationEpoch, activationEpoch, activationEpoch),
            new GasFeeFork(
              BigInteger.valueOf(30000000),
              BigInteger.valueOf(2),
              BigInteger.valueOf(8),
              minimumBaseFee
            )
          ),
        ).asJava
      }
    }
    ForkManagerUtil.initializeForkManager(new MinimumFeeTestFork(), "regtest")

    // make sure sender has enough balance to not fail other validation checks
    addMockBalance(txGasLow.getFromAddress, BigInteger.valueOf(1000000000L))
    addMockBalance(txGasHigh.getFromAddress, BigInteger.valueOf(1000000000L))

    // before the fork both TX should be valid
    Mockito.when(metadataStorage.getConsensusEpochNumber).thenReturn(None)
    state.validate(txGasLow).get
    state.validate(txGasHigh).get

    // after the fork the TX with max fee lower than the minimum should be invalid
    Mockito.when(metadataStorage.getConsensusEpochNumber).thenReturn(Some(intToConsensusEpochNumber(activationEpoch)))
    assertThrows[IllegalArgumentException](state.validate(txGasLow).get)
    state.validate(txGasHigh).get
  }

  @Test
  def testTransactionMaxFeesBelowMinimumLegacy(): Unit = {
    // minimum base fee required after fork (before the fork the default is zero)
    val mininumBaseFee = BigInteger.valueOf(1000)

    val txGasLow = createLegacyTransaction(
      value = BigInteger.ONE,
      gasPrice = mininumBaseFee.subtract(BigInteger.ONE),
    )
    val txGasHigh = createLegacyTransaction(
      value = BigInteger.ONE,
      gasPrice = mininumBaseFee,
    )
    testTransactionMaxFeesBelowMinimum(mininumBaseFee, txGasLow, txGasHigh)
  }

  @Test
  def testTransactionMaxFeesBelowMinimumEIP1559(): Unit = {
    // minimum base fee required after fork (before the fork the default is zero)
    val mininumBaseFee = BigInteger.valueOf(1000)

    val txGasLow = createEIP1559Transaction(
      value = BigInteger.ONE,
      gasFee = mininumBaseFee.subtract(BigInteger.ONE),
      priorityGasFee = mininumBaseFee.divide(BigInteger.TWO)
    )
    val txGasHigh = createEIP1559Transaction(
      value = BigInteger.ONE,
      gasFee = mininumBaseFee,
      priorityGasFee = mininumBaseFee.divide(BigInteger.TWO)
    )
    testTransactionMaxFeesBelowMinimum(mininumBaseFee, txGasLow, txGasHigh)
  }
}
