package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.json.Views
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
    val baseFeeLength = r.getInt()
    val baseFee = new BigIntegerUInt256(r.getBytes(baseFeeLength)).getBigInt

    val forgerTipsLength = r.getInt()
    val forgerTips = new BigIntegerUInt256(r.getBytes(forgerTipsLength)).getBigInt

    val forgerRewardKey: AddressProposition = AddressPropositionSerializer.getSerializer.parse(r)

    AccountBlockFeeInfo(baseFee, forgerTips, forgerRewardKey)
  }
}
