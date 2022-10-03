package com.horizen.account.validation

import com.horizen.account.utils.FeeUtils
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.utils.AccountMockDataHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import scorex.util.bytesToId
import org.web3j.utils.Numeric
import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.Random

class BaseFeeBlockValidatorTest extends JUnitSuite {
  val mockedGenesisBlock = AccountMockDataHelper(true).getMockedBlock(FeeUtils.INITIAL_BASE_FEE, 0,
    FeeUtils.GAS_LIMIT, bytesToId(Numeric.hexStringToByteArray("123")) ,bytesToId(new Array[Byte](32)))

  @Test
  def genesisBlockCheck(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(true)
    val mockedBlock: AccountBlock = mockHelper.getMockedBlock(BigInteger.ZERO, 0, FeeUtils.GAS_LIMIT,
      bytesToId(Numeric.hexStringToByteArray("123")) ,bytesToId(new Array[Byte](32)))
    val mockedHistory: AccountHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedGenesisBlock).asJava)

    // Test 1: Successful validation, block is genesis block with initial base fee
    assertTrue(BaseFeeBlockValidator().validate(mockedGenesisBlock, mockedHistory).isSuccess)

    // Test 2: Validation exception expected, block is genesis block and does not have initial base fee
    assertThrows[InvalidBaseFeeException] {
      BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).get
    }
  }

  @Test
  def nonGenesisBlockCheck(): Unit = {
    var gasLimit: Long = Math.max(1, Math.abs(new Random().nextLong()))
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    var mockedHistory: AccountHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedGenesisBlock).asJava)
    var mockedBlock: AccountBlock = mockHelper.getMockedBlock(BigInteger.valueOf(875000000), gasLimit / 2, gasLimit,
      bytesToId(Numeric.hexStringToByteArray("456")), bytesToId(Numeric.hexStringToByteArray("123")))

    // Test 1: Successful validation, block is one after genesis block with 12.5% decrease, meaning genesis block was empty
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 2: Successful validation, last block is exactly 50% full, therefore base fee did not change
    gasLimit = Math.abs(new Random().nextLong())
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(875000000), gasLimit, gasLimit,
      bytesToId(Numeric.hexStringToByteArray("789")), bytesToId(Numeric.hexStringToByteArray("456")))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 3: Successful validation, last block was 100% full, therefore base fee did increase by 12.5%
    gasLimit = Math.abs(new Random().nextLong())
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(984375000), gasLimit / 2, gasLimit,
      bytesToId(Numeric.hexStringToByteArray("111")), bytesToId(Numeric.hexStringToByteArray("789")))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 4: Validation exception expected, last block was 50% full, but base fee did change
    mockedBlock = mockHelper.getMockedBlock(FeeUtils.INITIAL_BASE_FEE, 10000000, 20000000,
      bytesToId(Numeric.hexStringToByteArray("222")), bytesToId(Numeric.hexStringToByteArray("111")))
    assertThrows[InvalidBaseFeeException] {
      (BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).get)
    }

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 5 - 7
    // https://github.com/ethereum/go-ethereum/blob/53b1420edefcf6be154ea6df887b1e4d68fcc36f/consensus/misc/eip1559_test.go
    // Test 5: Successful validation, last block was 50% full, base fee did not change
    mockedBlock = mockHelper.getMockedBlock(FeeUtils.INITIAL_BASE_FEE, 9000000, 20000000,
      bytesToId(Numeric.hexStringToByteArray("333")), bytesToId(Numeric.hexStringToByteArray("222")))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 6: Successful validation, base fee did decrease by 12.5%
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(987500000), 11000000, 20000000,
      bytesToId(Numeric.hexStringToByteArray("444")), bytesToId(Numeric.hexStringToByteArray("333")))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedBlock = mockHelper.getMockedBlock(FeeUtils.INITIAL_BASE_FEE, 11000000, 20000000,
      bytesToId(Numeric.hexStringToByteArray("444")), bytesToId(Numeric.hexStringToByteArray("333")))
    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 7: Successful validation, base fee did increase by 12.5%
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(1012500000), 9000000, 20000000,
      bytesToId(Numeric.hexStringToByteArray("555")), bytesToId(Numeric.hexStringToByteArray("444")))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

  }
}

