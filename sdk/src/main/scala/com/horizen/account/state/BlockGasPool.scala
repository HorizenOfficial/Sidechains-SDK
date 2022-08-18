package com.horizen.account.state

import java.math.BigInteger

class BlockGasPool(initialGas: BigInteger) extends GasPool(initialGas) {
  override def subGas(gas: BigInteger): Unit = {
    try {
      super.subGas(gas)
    } catch {
      // we want to throw the block "gas limit reached" exception here instead
      case _: OutOfGasException => throw GasLimitReached()
    }
  }
}
