package com.horizen.account.api.http

object ZenConverter {
  val ZENNY_TO_WEI_MULTIPLIER: java.math.BigInteger =  java.math.BigInteger.valueOf(10000000000L)

  def convertZenniesToWei(valueInZennies: Long): java.math.BigInteger = {
    ZENNY_TO_WEI_MULTIPLIER.multiply(java.math.BigInteger.valueOf(valueInZennies))
  }
}
