package com.horizen.account.chain

import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.chain.AbstractFeePaymentsInfo
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}


import java.math.BigInteger


case class AccountFeePaymentsInfo(address: AddressProposition, value: BigInteger) extends AbstractFeePaymentsInfo {
  override type M = AccountFeePaymentsInfo

  override def serializer: ScorexSerializer[M] = AccountFeePaymentsInfoSerializer
}

object AccountFeePaymentsInfoSerializer extends ScorexSerializer[AccountFeePaymentsInfo] {
  override def serialize(feePaymentsInfo: AccountFeePaymentsInfo, w: Writer): Unit = {
    AddressPropositionSerializer.getSerializer.serialize(feePaymentsInfo.address, w)
    w.putInt(feePaymentsInfo.value.toByteArray.length)
    w.putBytes(feePaymentsInfo.value.toByteArray)
  }

  override def parse(r: Reader): AccountFeePaymentsInfo = {
    val address = AddressPropositionSerializer.getSerializer.parse(r)
    val valueLength = r.getInt()
    val value = new BigInteger(r.getBytes(valueLength))
    new AccountFeePaymentsInfo(address, value)
  }
}