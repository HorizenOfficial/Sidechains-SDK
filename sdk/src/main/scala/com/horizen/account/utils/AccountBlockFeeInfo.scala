package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.serialization.Views
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger

@JsonView(Array(classOf[Views.Default]))
case class AccountBlockFeeInfo(baseFee: BigInteger, forgerTips: BigInteger, forgerAddress: AddressProposition) extends BytesSerializable {
  override type M = AccountBlockFeeInfo
  override def serializer: SparkzSerializer[AccountBlockFeeInfo] = AccountBlockFeeInfoSerializer
}


object AccountBlockFeeInfoSerializer extends SparkzSerializer[AccountBlockFeeInfo] {
  override def serialize(obj: AccountBlockFeeInfo, w: Writer): Unit = {
    val baseFeeByteArray = obj.baseFee.toByteArray
    w.putInt(baseFeeByteArray.length)
    w.putBytes(baseFeeByteArray)
    val forgerTipsByteArray = obj.forgerTips.toByteArray
    w.putInt(forgerTipsByteArray.length)
    w.putBytes(forgerTipsByteArray)
    AddressPropositionSerializer.getSerializer.serialize(obj.forgerAddress, w)
  }

  override def parse(r: Reader): AccountBlockFeeInfo = {
    var bigIntBitLength: Integer = 0
    val baseFeeLength = r.getInt()
    val baseFee = new BigInteger(r.getBytes(baseFeeLength))
    bigIntBitLength = baseFee.bitLength()
    if (bigIntBitLength > Account.BIG_INT_MAX_BIT_SIZE)
      throw new IllegalArgumentException(s"Base Fee bit size $bigIntBitLength exceeds the limit ${Account.BIG_INT_MAX_BIT_SIZE}")

    val forgerTipsLength = r.getInt()
    val forgerTips = new BigInteger(r.getBytes(forgerTipsLength))
    bigIntBitLength = baseFee.bitLength()
    if (bigIntBitLength > Account.BIG_INT_MAX_BIT_SIZE)
      throw new IllegalArgumentException(s"Base Fee bit size $bigIntBitLength exceeds the limit ${Account.BIG_INT_MAX_BIT_SIZE}")
    val forgerRewardKey: AddressProposition = AddressPropositionSerializer.getSerializer.parse(r)

    AccountBlockFeeInfo(baseFee, forgerTips, forgerRewardKey)
  }
}
