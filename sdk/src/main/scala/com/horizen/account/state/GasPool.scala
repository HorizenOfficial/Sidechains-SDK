package com.horizen.account.state

import java.math.BigInteger

class GasPool(initialGas: BigInteger) {

  private var availableGas = initialGas

  def getInitialGas: BigInteger = initialGas

  def getAvailableGas: BigInteger = availableGas

  def getUsedGas: BigInteger = initialGas.subtract(availableGas)

  def consumeGas(gas: BigInteger): Unit = {
    if (availableGas.compareTo(gas) < 0) {
      throw new OutOfGasException(gas, availableGas)
    }
    availableGas = availableGas.subtract(gas)
  }
}
