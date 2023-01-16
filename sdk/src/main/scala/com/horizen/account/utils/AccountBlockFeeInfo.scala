package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.serialization.Views
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import scorex.util.serialization.{Reader, Writer}

import java.math.BigInteger

@JsonView(Array(classOf[Views.Default]))
case class AccountBlockFeeInfo(baseFee: BigInteger, forgerTips: BigInteger, forgerAddress: AddressProposition) extends BytesSerializable {
  override type M = AccountBlockFeeInfo
  override def serializer: SparkzSerializer[AccountBlockFeeInfo] = AccountBlockFeeInfoSerializer
}


object AccountBlockFeeInfoSerializer extends SparkzSerializer[AccountBlockFeeInfo] {
  override def serialize(obj: AccountBlockFeeInfo, w: Writer): Unit = {
    w.putInt(obj.baseFee.toByteArray.length)
    w.putBytes(obj.baseFee.toByteArray)
    w.putInt(obj.forgerTips.toByteArray.length)
    w.putBytes(obj.forgerTips.toByteArray)
    AddressPropositionSerializer.getSerializer.serialize(obj.forgerAddress, w)
  }

  override def parse(r: Reader): AccountBlockFeeInfo = {
    val baseFeeLength = r.getInt()
    val baseFee = new BigInteger(r.getBytes(baseFeeLength))
    val forgerTipsLength = r.getInt()
    val forgerTips = new BigInteger(r.getBytes(forgerTipsLength))
    val forgerRewardKey: AddressProposition = AddressPropositionSerializer.getSerializer.parse(r)

    AccountBlockFeeInfo(baseFee, forgerTips, forgerRewardKey)
  }
}
