package com.horizen.account.validation

import com.horizen.account.FeeUtils
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

class BaseFeeBlockValidatorTest extends JUnitSuite {
  val mockedGenesisBlock = AccountMockDataHelper(true).getMockedBlock(FeeUtils.INITIAL_BASE_FEE,
    0, bytesToId(Numeric.hexStringToByteArray("123")) ,bytesToId(new Array[Byte](32)))

  @Test
  def genesisBlockCheck(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(true)
    val mockedBlock: AccountBlock = mockHelper.getMockedBlock(BigInteger.ZERO, 0,
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
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    var mockedHistory: AccountHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedGenesisBlock).asJava)
    var mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(875000000), FeeUtils.GAS_LIMIT / 2,
      bytesToId(Numeric.hexStringToByteArray("456")), bytesToId(Numeric.hexStringToByteArray("123")))

    // Test 1: Successful validation, block is one after genesis block with 12.5% decrease, meaning genesis block was empty
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 2: Successful validation, last block is exactly 50% full, therefore base fee did not change
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(875000000), FeeUtils.GAS_LIMIT,
      bytesToId(Numeric.hexStringToByteArray("789")), bytesToId(Numeric.hexStringToByteArray("456")))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 3: Successful validation, last block was 100% full, therefore base fee did increase by 12.5%
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(984375000), FeeUtils.GAS_LIMIT/2,
      bytesToId(Numeric.hexStringToByteArray("111")), bytesToId(Numeric.hexStringToByteArray("789")))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

    // Test 4: Validation exception expected, last block was 50% full, but base fee did change
    mockedBlock = mockHelper.getMockedBlock(FeeUtils.INITIAL_BASE_FEE, 0,
      bytesToId(Numeric.hexStringToByteArray("222")), bytesToId(Numeric.hexStringToByteArray("111")))
    assertThrows[InvalidBaseFeeException] {
      (BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).get)
    }
  }
}

