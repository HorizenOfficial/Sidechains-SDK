package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.evm.utils.Address
import com.horizen.serialization.Views
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

import java.math.BigInteger

@JsonView(Array(classOf[Views.Default]))
case class AccountBlockFeeInfo(baseFee: BigInteger, forgerTips: BigInteger, forgerAddress: Address)
    extends BytesSerializable {
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
    w.putBytes(obj.forgerAddress.toBytes)
  }

  override def parse(r: Reader): AccountBlockFeeInfo = {
    val baseFeeLength = r.getInt()
    val baseFee = new BigInteger(r.getBytes(baseFeeLength))
    val forgerTipsLength = r.getInt()
    val forgerTips = new BigInteger(r.getBytes(forgerTipsLength))
    val forgerAddress = Address.fromBytes(r.getBytes(Address.LENGTH))

    AccountBlockFeeInfo(baseFee, forgerTips, forgerAddress)
  }
}
