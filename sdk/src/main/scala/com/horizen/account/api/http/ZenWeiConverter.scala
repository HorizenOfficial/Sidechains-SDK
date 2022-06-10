package com.horizen.account.api.http

import com.horizen.utils.ZenCoinsUtils

object ZenWeiConverter {
  val ZENNY_TO_WEI_MULTIPLIER: java.math.BigInteger = java.math.BigInteger.valueOf(10000000000L)
  val MAX_MONEY_IN_WEI: java.math.BigInteger = convertZenniesToWei(ZenCoinsUtils.MAX_MONEY)


  def convertZenniesToWei(valueInZennies: Long): java.math.BigInteger = {
    ZENNY_TO_WEI_MULTIPLIER.multiply(java.math.BigInteger.valueOf(valueInZennies))
  }

  def convertWeiToZennies(valueInWei: java.math.BigInteger): Long = {
    require(isValidZenAmount(valueInWei), s"Amount $valueInWei wei is not a valid Zen sum")
    valueInWei.divide(ZENNY_TO_WEI_MULTIPLIER).longValue()
  }


  def isValidZenAmount(valueInWei: java.math.BigInteger): Boolean = {
    require(valueInWei != null, s"Wei amount is null")
    if (valueInWei.compareTo(java.math.BigInteger.ZERO) >= 0){
      (valueInWei.compareTo(MAX_MONEY_IN_WEI) <= 0 &&
        valueInWei.remainder(ZENNY_TO_WEI_MULTIPLIER).longValue() == 0)
    }
    else
      false
  }
}
