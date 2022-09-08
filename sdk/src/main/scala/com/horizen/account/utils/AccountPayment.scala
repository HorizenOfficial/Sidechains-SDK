package com.horizen.account.utils

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.account.proposition.AddressProposition
import com.horizen.serialization.Views
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}

import java.math.BigInteger

@JsonView(Array(classOf[Views.Default]))
case class AccountPayment(address: AddressProposition, value: BigInteger) extends BytesSerializable {
  override type M = AccountPayment
  override def serializer: ScorexSerializer[AccountPayment] = AccountPaymentSerializer.getSerializer
}

