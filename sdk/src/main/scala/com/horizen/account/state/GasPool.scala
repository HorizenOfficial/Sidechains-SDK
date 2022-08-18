package com.horizen.account.state

import java.math.BigInteger

class GasPool(initialGas: BigInteger) {

  private var currentGas = initialGas

  def getGas: BigInteger = currentGas

  def getUsedGas: BigInteger = initialGas.subtract(currentGas)

  def subGas(gas: BigInteger): Unit = {
    if (gas.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot consume a negative amount of gas")
    if (currentGas.compareTo(gas) < 0) {
      throw new OutOfGasException()
    }
    currentGas = currentGas.subtract(gas)
  }

  def addGas(gas: BigInteger): Unit = {
    if (gas.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot return a negative amount of gas")
    val sum = currentGas.add(gas)
    if (sum.compareTo(initialGas) > 0)
      throw new IllegalArgumentException("cannot return more gas than was used")
    currentGas = sum
  }
}
