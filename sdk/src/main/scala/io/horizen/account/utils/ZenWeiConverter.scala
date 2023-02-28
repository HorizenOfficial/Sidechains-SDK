package io.horizen.account.utils

import com.horizen.utils.ZenCoinsUtils

import java.math.BigInteger

object ZenWeiConverter {
  val ZENNY_TO_WEI_MULTIPLIER: BigInteger = BigInteger.TEN.pow(10)
  val MAX_MONEY_IN_WEI: BigInteger = convertZenniesToWei(ZenCoinsUtils.MAX_MONEY)

  def convertZenniesToWei(valueInZennies: Long): BigInteger = {
    ZENNY_TO_WEI_MULTIPLIER.multiply(BigInteger.valueOf(valueInZennies))
  }

  def convertWeiToZennies(valueInWei: BigInteger): Long = {
    require(isValidZenAmount(valueInWei), s"Amount $valueInWei wei is not a valid Zen sum")
    valueInWei.divide(ZENNY_TO_WEI_MULTIPLIER).longValueExact()
  }

  def isValidZenAmount(valueInWei: BigInteger): Boolean = {
    require(valueInWei != null, s"Wei amount is null")
    valueInWei.signum() >= 0 &&
    valueInWei.compareTo(MAX_MONEY_IN_WEI) <= 0 &&
    valueInWei.mod(ZENNY_TO_WEI_MULTIPLIER).signum() == 0
  }
}
