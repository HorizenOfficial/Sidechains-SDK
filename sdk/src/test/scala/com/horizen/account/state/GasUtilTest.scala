package com.horizen.account.state

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger

class GasUtilTest extends JUnitSuite {

  @Test
  def testIntrinsicGas(): Unit = {
    assertEquals(
      "eoa to eao",
      BigInteger.valueOf(21000),
      GasUtil.intrinsicGas(Array[Byte](), isContractCreation = false))

    assertEquals(
      "eoa to eao with data",
      BigInteger.valueOf(24040),
      GasUtil.intrinsicGas(Array.range(10, 200).map(_.toByte), isContractCreation = false))

    assertEquals(
      "eoa to eao with data including zero bytes",
      BigInteger.valueOf(24240),
      GasUtil.intrinsicGas(zeroes(25) ++ Array.range(10, 200).map(_.toByte) ++ zeroes(25), isContractCreation = false))

    assertEquals(
      "contract creation small",
      BigInteger.valueOf(56040),
      GasUtil.intrinsicGas(Array.range(10, 200).map(_.toByte), isContractCreation = true))

    assertEquals(
      "contract creation big including zero bytes",
      BigInteger.valueOf(293200),
      GasUtil.intrinsicGas(zeroes(8) ++ Array.fill(15000) { 0x83.toByte } ++ zeroes(42), isContractCreation = true))
  }

  private def zeroes(n: Int) = Array.fill(n) { 0.toByte }
}
