package com.horizen.account.state

import java.math.BigInteger

class OutOfGasException(gas: BigInteger, availableGas: BigInteger) extends Exception {}
