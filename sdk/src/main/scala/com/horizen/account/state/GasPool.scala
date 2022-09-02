package com.horizen.account.state

import scorex.util.ScorexLogging

import java.math.BigInteger

class GasPool(initialGas: BigInteger) extends ScorexLogging {

  private var currentGas = initialGas

  def getGas: BigInteger = currentGas

  def getUsedGas: BigInteger = initialGas.subtract(currentGas)

  @throws(classOf[OutOfGasException])
  def subGas(gas: BigInteger): Unit = {
    if (gas.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot consume a negative amount of gas")
    if (currentGas.compareTo(gas) < 0) {
      throw new OutOfGasException()
    }
    log.debug(s"subtracting $gas from currentGas=$currentGas")
    currentGas = currentGas.subtract(gas)
    log.debug(s"---> currentGas=$currentGas")
  }

  def addGas(gas: BigInteger): Unit = {
    if (gas.compareTo(BigInteger.ZERO) < 0)
      throw new IllegalArgumentException("cannot return a negative amount of gas")
    log.debug(s"adding $gas to currentGas=$currentGas")
    val sum = currentGas.add(gas)
    if (sum.compareTo(initialGas) > 0)
      throw new IllegalArgumentException("cannot return more gas than was used")
    currentGas = sum
    log.debug(s"---> currentGas=$currentGas")
  }
}
