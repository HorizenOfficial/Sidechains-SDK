package com.horizen.account.utils

import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import org.junit.Assert.{assertArrayEquals, assertEquals}
import org.junit.Test
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar
import scorex.util.serialization.{Reader, Writer}

class AccountPaymentSerializerTest extends MockitoSugar  {
  @Test
  def serializeAccountPayment(): Unit = {
    val mockAddressPropositionSerializer = mock[AddressPropositionSerializer]
    val mockAddressProposition = mock[AddressProposition]
    val mockWriter = mock[Writer]
    val mockReader = mock[Reader]

    val value = BigInt("1234567890")
    val accountPayment = AccountPayment(mockAddressProposition, value.bigInteger)
    val testAddressBytes = Array.fill[Byte](Account.ADDRESS_SIZE)(0)

    when(mockAddressProposition.address).thenReturn(testAddressBytes)
    when(mockAddressPropositionSerializer.parse(mockReader)).thenReturn(mockAddressProposition)
    when(mockReader.getInt).thenReturn(value.toByteArray.length)
    when(mockReader.getBytes(value.toByteArray.length)).thenReturn(value.toByteArray)
    when(mockReader.getBytes(Account.ADDRESS_SIZE)).thenReturn(testAddressBytes)

    AccountPaymentSerializer.serialize(accountPayment, mockWriter)
    verify(mockWriter).putInt(value.toByteArray.length)
    verify(mockWriter).putBytes(value.toByteArray)

    val deserialized = AccountPaymentSerializer.parse(mockReader)
    assertArrayEquals(deserialized.addressBytes, testAddressBytes)
    assertEquals(deserialized.value.toString(), value.bigInteger.toString())
  }
}
