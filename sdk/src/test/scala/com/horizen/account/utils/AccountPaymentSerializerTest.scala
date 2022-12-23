package com.horizen.account.utils

import com.horizen.account.proposition.AddressProposition
import com.horizen.fixtures.SecretFixture
import org.junit.Assert.assertEquals
import org.junit.Test

import java.math.BigInteger

class AccountPaymentSerializerTest extends SecretFixture  {
  @Test
  def serializeAccountPayment(): Unit = {
    val address: AddressProposition = getAddressProposition(123)
    val value: BigInteger = BigInteger.valueOf(1234567890L)
    val accountPayment: AccountPayment = AccountPayment(address, value)

    val serializedBytes: Array[Byte] = AccountPaymentSerializer.toBytes(accountPayment)

    val deserializedAccountPayment: AccountPayment = AccountPaymentSerializer.parseBytes(serializedBytes)

    assertEquals(accountPayment, deserializedAccountPayment)
  }
}
