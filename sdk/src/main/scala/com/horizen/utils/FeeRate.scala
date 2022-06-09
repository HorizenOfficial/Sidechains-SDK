package com.horizen.utils

class FeeRate(val fee: Long, val size: Long) {
  private val satoshiPerK : Long = if (size > 0) {
    fee*1000/size
  } else {
    0
  }

  def getFeeRate(): Long = {
    satoshiPerK
  }

  def getFee(): Long = {
    fee
  }

  def getSize(): Long = {
    size
  }

  def >(that: FeeRate) : Boolean =
    this.satoshiPerK > that.satoshiPerK

  override def toString() = {
    s"FeeRate(${satoshiPerK / ZenCoinsUtils.COIN}.${satoshiPerK % ZenCoinsUtils.COIN} Zen/Kb)"
  }

}
