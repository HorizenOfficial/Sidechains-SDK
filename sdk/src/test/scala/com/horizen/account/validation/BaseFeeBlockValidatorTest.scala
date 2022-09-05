package com.horizen.account.validation

import com.horizen.account.FeeUtils
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.utils.AccountMockDataHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionForJava8

class BaseFeeBlockValidatorTest extends JUnitSuite {

  @Test
  def nonGenesisBlockCheck(): Unit = {
    var mockHelper: AccountMockDataHelper = AccountMockDataHelper(true)
    val mockedGenesisBlock = mockHelper.getMockedBlock(FeeUtils.INITIAL_BASE_FEE)
    var mockedBlock: AccountBlock = mockHelper.getMockedBlock(BigInteger.ZERO)
    var mockedHistory: AccountHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedGenesisBlock).asJava)

    // Test 1: Successful validation, block is genesis block with initial base fee
    assertTrue(BaseFeeBlockValidator().validate(mockedGenesisBlock, mockedHistory).isSuccess)

    // Test 2: Validation exception expected, block is genesis block and does not have initial base fee
    assertThrows[InvalidBaseFeeException] {
      BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).get
    }

    mockHelper = AccountMockDataHelper(false)
    mockedHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedGenesisBlock).asJava)
    mockedBlock = mockHelper.getMockedBlock(FeeUtils.INITIAL_BASE_FEE)

    // Test 3: Successful validation, block is empty, therefore base fee did not change
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    // Test 4: Successful validation, block is genesis block
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(875000000))
    assertTrue(BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).isSuccess)

    // Test 5: Validation exception expected, block base fee is out of adjustment range
    mockedBlock = mockHelper.getMockedBlock(BigInteger.valueOf(531))
    assertThrows[InvalidBaseFeeException] {
      BaseFeeBlockValidator().validate(mockedBlock, mockedHistory).get
    }
  }
}

