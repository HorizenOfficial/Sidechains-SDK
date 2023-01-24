package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.evm.utils.Address
import com.horizen.serialization.Views
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

import java.math.BigInteger

@JsonView(Array(classOf[Views.Default]))
case class AccountPayment(address: Address, value: BigInteger) extends BytesSerializable {
  override type M = AccountPayment
  override def serializer: SparkzSerializer[AccountPayment] = AccountPaymentSerializer
}

object AccountPaymentSerializer extends SparkzSerializer[AccountPayment] {
  override def serialize(obj: AccountPayment, w: Writer): Unit = {
    w.putBytes(obj.address.toBytes)
    w.putInt(obj.value.toByteArray.length)
    w.putBytes(obj.value.toByteArray)
  }

  override def parse(r: Reader): AccountPayment = {
    val address = Address.fromBytes(r.getBytes(Address.LENGTH))
    val valueLength = r.getInt
    val value = new BigInteger(r.getBytes(valueLength))

    AccountPayment(address, value)
  }
}

