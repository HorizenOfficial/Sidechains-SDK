package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.fixtures.SecretFixture
import org.junit.Assert.{assertArrayEquals, assertEquals}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar

import java.math.BigInteger

class EoaMessageProcessorIntegrationTest
  extends JUnitSuite
    with MockitoSugar
    with SecretFixture
    with MessageProcessorFixture {

  @Test
  def process(): Unit = {
    val address: AddressProposition = getAddressProposition(12345L)
    val value: java.math.BigInteger = java.math.BigInteger.TWO
    val emptyData: Array[Byte] = Array.emptyByteArray
    val msg: Message = getMessage(address, value, emptyData)

    val stateView: AccountStateView = getView

    val fromInitialValue: BigInteger = msg.getValue.multiply(BigInteger.TEN)
    stateView.addBalance(msg.getFrom.address(), fromInitialValue)


    EoaMessageProcessor.process(msg, stateView) match {
      case es: ExecutionSucceeded =>
        assertEquals("Different gas found", EoaMessageProcessor.gasUsed, es.gasUsed())
        assertArrayEquals("Different return data found", Array.emptyByteArray, es.returnData())

        assertEquals("Different from account value found", fromInitialValue.subtract(msg.getValue), stateView.getBalance(msg.getFrom.address()).get)
        assertEquals("Different to account value found", msg.getValue, stateView.getBalance(msg.getTo.address()).get)
      case _: ExecutionFailed | _: InvalidMessage => fail("Execution failure received")
    }

    stateView.stateDb.close()

  }
}
