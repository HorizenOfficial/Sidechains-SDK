package com.horizen.account.state

import java.math.BigInteger

trait GasSpender {

  private var initialGas = BigInteger.ZERO
  private var currentGas = BigInteger.ZERO

  def getGas: BigInteger = currentGas

  def setGas(gasLimit: BigInteger): Unit = {
    if (gasLimit.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot set a negative amount of gas")
    initialGas = gasLimit
    currentGas = gasLimit
  }

  def getUsedGas: BigInteger = initialGas.subtract(currentGas)

  def subGas(gas: BigInteger): Unit = {
    if (gas.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot consume a negative amount of gas")
    if (currentGas.compareTo(gas) < 0) {
      throw OutOfGasException()
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


