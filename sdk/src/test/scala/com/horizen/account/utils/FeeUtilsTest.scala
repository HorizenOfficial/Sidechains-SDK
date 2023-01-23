package com.horizen.account.utils

import com.horizen.account.utils.FeeUtils.{INITIAL_BASE_FEE, calculateNextBaseFee}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger

class FeeUtilsTest extends JUnitSuite {

  private def assertBaseFeeChange(
      message: String,
      gasUsedPercent: Long,
      currentBaseFee: Long,
      expectedBaseFee: Long
  ): Unit = {
    val block =
      AccountMockDataHelper(false).getMockedBlock(BigInteger.valueOf(currentBaseFee), gasUsedPercent, 100)
    assertEquals(message, calculateNextBaseFee(block), BigInteger.valueOf(expectedBaseFee))
  }

  /**
   * Separate test for calculating the next base fee as it is not used in base fee validator test.
   */
  @Test
  def calculateNextBaseFeeTest(): Unit = {
    assertTrue("no block passed returns initial base fee", calculateNextBaseFee(null) == INITIAL_BASE_FEE)

    assertBaseFeeChange("full block should increase base fee by 12.5%", 100, 1000000000, 1125000000)
    assertBaseFeeChange("full block should increase base fee by 12.5%", 100, 1672530, 1881596)
    assertBaseFeeChange("empty block should decrease base fee by 12.5%", 0, 1000000000, 875000000)
    assertBaseFeeChange("empty block should decrease base fee by 12.5%", 0, 523122, 457732)
    assertBaseFeeChange("50% filled block should not change base fee", 50, 1000000000, 1000000000)
    assertBaseFeeChange("50% filled block should not change base fee", 50, 671823, 671823)

    assertBaseFeeChange("70% filled block should increase base fee by 5%", 70, 1000000000, 1050000000)
    assertBaseFeeChange("30% filled block should decrease base fee by 5%", 30, 1000000000, 950000000)

    assertBaseFeeChange("base fee should not fall to zero", 0, 1, 1)
    // because of integer division the base fee cannot decrease anymore below 8
    assertBaseFeeChange("base fee should decrease by one", 0, 8, 7)
    assertBaseFeeChange("base fee should not decrease below 8", 0, 7, 7)
    // sanity check: base fee should never reach zero, but even if it does it should increase by at least one here
    assertBaseFeeChange("base fee should increase at least by one", 51, 0, 1)
    assertBaseFeeChange("base fee should increase at least by one", 51, 1, 2)
    assertBaseFeeChange("base fee should increase at least by one", 51, 2, 3)
  }
}
