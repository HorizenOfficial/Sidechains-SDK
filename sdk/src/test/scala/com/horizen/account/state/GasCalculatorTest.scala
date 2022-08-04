package com.horizen.account.state

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger

class GasCalculatorTest extends JUnitSuite {

  private def zeroes(n: Int) = Array.fill(n) { 0.toByte }

  @Test
  def testIntrinsicGas(): Unit = {
    assertEquals(
      "eoa to eao",
      BigInteger.valueOf(21000),
      GasCalculator.intrinsicGas(Array[Byte](), isContractCreation = false))

    assertEquals(
      "eoa to eao with data",
      BigInteger.valueOf(24040),
      GasCalculator.intrinsicGas(Array.range(10, 200).map(_.toByte), isContractCreation = false))

    assertEquals(
      "eoa to eao with data including zero bytes",
      BigInteger.valueOf(24240),
      GasCalculator.intrinsicGas(
        zeroes(25) ++ Array.range(10, 200).map(_.toByte) ++ zeroes(25),
        isContractCreation = false))

    assertEquals(
      "contract creation small",
      BigInteger.valueOf(56040),
      GasCalculator.intrinsicGas(Array.range(10, 200).map(_.toByte), isContractCreation = true))

    assertEquals(
      "contract creation big including zero bytes",
      BigInteger.valueOf(293200),
      GasCalculator.intrinsicGas(
        zeroes(8) ++ Array.fill(15000) { 0x83.toByte } ++ zeroes(42),
        isContractCreation = true))
  }

}
