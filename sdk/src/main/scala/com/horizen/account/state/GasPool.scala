package com.horizen.account.state

import java.math.BigInteger

class GasPool(initialGas: BigInteger) {

  if (initialGas.compareTo(BigInteger.ZERO) < 0)
    throw new IllegalArgumentException("gas pool cannot have a negative amount of gas")

  private var availableGas = initialGas

  def getInitialGas: BigInteger = initialGas

  def getAvailableGas: BigInteger = availableGas

  def getUsedGas: BigInteger = initialGas.subtract(availableGas)

  def consumeGas(gas: BigInteger): Unit = {
    if (gas.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot consume a negative amount of gas")
    if (availableGas.compareTo(gas) < 0) {
      throw GasLimitReached()
    }
    availableGas = availableGas.subtract(gas)
  }

  def returnGas(gas: BigInteger): Unit = {
    if (gas.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot return a negative amount of gas")
    val sum = availableGas.add(gas)
    if (sum.compareTo(initialGas) > 0)
      throw new IllegalArgumentException("cannot return more gas than was used")
    availableGas = sum
  }
}
