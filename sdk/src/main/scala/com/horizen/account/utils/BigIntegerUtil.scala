package com.horizen.account.utils

import java.math.BigInteger

object BigIntegerUtil {
  def isUint64(number: BigInteger): Boolean = number.signum() >= 0 && number.bitLength() <= 64
}
