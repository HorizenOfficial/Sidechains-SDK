package com.horizen.account.state

import java.math.BigInteger

class OutOfGasException(gas: BigInteger, availableGas: BigInteger)
    extends ExecutionFailedException("out of gas") {
  def this() = this(BigInteger.ZERO, BigInteger.ZERO)
}
