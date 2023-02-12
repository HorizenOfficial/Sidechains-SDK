package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.serialization.Views
import com.horizen.utils.Checker
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
    val baseFeeLength = Checker.readIntNotLessThanZero(r, "base fee length")
    val baseFee = new BigInteger(Checker.readBytes(r, baseFeeLength, "base fee"))
    val forgerTipsLength = Checker.readIntNotLessThanZero(r, "forger tips length")
    val forgerTips = new BigInteger(Checker.readBytes(r, forgerTipsLength, "forger tips"))
    val forgerRewardKey: AddressProposition = AddressPropositionSerializer.getSerializer.parse(r)

    AccountBlockFeeInfo(baseFee, forgerTips, forgerRewardKey)
  }
}
