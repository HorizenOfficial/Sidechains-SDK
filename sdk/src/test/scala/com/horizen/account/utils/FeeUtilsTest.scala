package com.horizen.account.utils

import com.horizen.account.FeeUtils.{GAS_LIMIT, INITIAL_BASE_FEE, calculateNextBaseFee}
import com.horizen.account.block.AccountBlock
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger

class FeeUtilsTest extends JUnitSuite {
  // separate test for calculate next base fee as it is not used in base fee validator test
  @Test
  def calculateNextBaseFeeTest(): Unit = {
    val mockedBlock: AccountBlock = AccountMockDataHelper(false).getMockedBlock(INITIAL_BASE_FEE, GAS_LIMIT, GAS_LIMIT,
      null, null)

    // Test 1: No block passed returns initial base fee
    assertTrue(calculateNextBaseFee(null) == INITIAL_BASE_FEE)

    // Test 2: 100% full block passed returns 12.5% increased value
    assertTrue(calculateNextBaseFee(mockedBlock) == BigInteger.valueOf(1125000000))
  }
}
