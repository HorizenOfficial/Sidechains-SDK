package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.serialization.Views
import sparkz.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

import java.math.BigInteger

@JsonView(Array(classOf[Views.Default]))
case class AccountPayment(address: AddressProposition, value: BigInteger) extends BytesSerializable {
  override type M = AccountPayment
  override def serializer: SparkzSerializer[AccountPayment] = AccountPaymentSerializer
}

object AccountPaymentSerializer extends SparkzSerializer[AccountPayment] {
  override def serialize(obj: AccountPayment, w: Writer): Unit = {
    AddressPropositionSerializer.getSerializer.serialize(obj.address, w)
    w.putInt(obj.value.toByteArray.length)
    w.putBytes(obj.value.toByteArray)
  }

  override def parse(r: Reader): AccountPayment = {
    val address = AddressPropositionSerializer.getSerializer.parse(r)
    val valueLength = r.getInt
    val value = new BigInteger(r.getBytes(valueLength))

    AccountPayment(address, value)
  }
}

