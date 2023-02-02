package com.horizen.account.state

import sparkz.util.SparkzLogging

import java.math.BigInteger

class GasPool(initialGas: BigInteger) extends SparkzLogging {

  private var currentGas = initialGas

  def getGas: BigInteger = currentGas

  def getUsedGas: BigInteger = initialGas.subtract(currentGas)

  @throws(classOf[OutOfGasException])
  def subGas(gas: BigInteger): Unit = {
    if (gas.signum() == -1)
      throw new IllegalArgumentException("cannot consume a negative amount of gas")
    if (currentGas.compareTo(gas) < 0) {
      throw new OutOfGasException(s"trying to remove gas=$gas from current-gas=$currentGas")
    }
    log.trace(s"subtracting $gas from currentGas=$currentGas")
    currentGas = currentGas.subtract(gas)
    log.trace(s"---> currentGas=$currentGas")
  }

  def addGas(gas: BigInteger): Unit = {
    if (gas.signum() == -1)
      throw new IllegalArgumentException("cannot return a negative amount of gas")
    log.trace(s"adding $gas to currentGas=$currentGas")
    val sum = currentGas.add(gas)
    if (sum.compareTo(initialGas) > 0)
      throw new IllegalArgumentException("cannot return more gas than was used")
    currentGas = sum
    log.trace(s"---> currentGas=$currentGas")
  }
}
