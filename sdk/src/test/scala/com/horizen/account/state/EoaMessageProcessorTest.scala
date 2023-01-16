package com.horizen.account.state

import com.horizen.fixtures.SecretFixture
import org.junit.Assert.{assertArrayEquals, assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar

import java.math.BigInteger

class EoaMessageProcessorTest extends JUnitSuite with MockitoSugar with SecretFixture with MessageProcessorFixture {

  @Test
  def init(): Unit = {
    val mockStateView: AccountStateView = mock[AccountStateView]

    try {
      EoaMessageProcessor.init(mockStateView)
    } catch {
      case ex: Exception =>
        fail("Initialization failed", ex)
    }
  }

  @Test
  def canProcess(): Unit = {
    val address = getAddressProposition(12345L).address()
    val value = BigInteger.ONE
    val msg = getMessage(address, value, Array.emptyByteArray)

    val mockStateView = mock[AccountStateView]

    // Test 1: send to EOA account, tx with empty "data"
    Mockito
      .when(mockStateView.isEoaAccount(ArgumentMatchers.any[Array[Byte]]))
      .thenAnswer(args => {
        assertArrayEquals("Different address found", msg.getTo.address(), args.getArgument(0))
        true
      })
    assertTrue(
      "Message for EoaMessageProcessor cannot be processed",
      EoaMessageProcessor.canProcess(msg, mockStateView))

    // Test 2: send to EOA account, tx with no-empty "data"
    val data = new Array[Byte](1000)
    val msgWithData = getMessage(address, value, data)
    assertTrue(
      "Message for EoaMessageProcessor cannot be processed",
      EoaMessageProcessor.canProcess(msgWithData, mockStateView))

    // Test 3: Failure: send to smart contract account
    Mockito.reset(mockStateView)
    Mockito
      .when(mockStateView.isEoaAccount(ArgumentMatchers.any[Array[Byte]]))
      .thenAnswer(args => {
        assertArrayEquals("Different address found", msg.getTo.address(), args.getArgument(0))
        false
      })
    assertFalse(
      "Message for EoaMessageProcessor wrongly can be processed",
      EoaMessageProcessor.canProcess(msg, mockStateView))

    // Test 4: Failure: to is null
    Mockito.reset(mockStateView)
    val contractDeclarationMessage = getMessage(null, value, data)
    assertFalse(
      "Message for EoaMessageProcessor wrongly can be processed",
      EoaMessageProcessor.canProcess(contractDeclarationMessage, mockStateView))
  }

  @Test
  def process(): Unit = {
    val msg = getMessage(randomAddress, randomU256)
    val mockStateView = mock[AccountStateView]

    // Test 1: Success: no failures during balance changes
    Mockito
      .when(mockStateView.subBalance(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[BigInteger]))
      .thenAnswer(args => {
        assertArrayEquals("Different address found", msg.getFrom.address(), args.getArgument(0))
        assertEquals("Different amount found", msg.getValue, args.getArgument(1))
      })

    Mockito
      .when(mockStateView.addBalance(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[BigInteger]))
      .thenAnswer(args => {
        assertArrayEquals("Different address found", msg.getTo.address(), args.getArgument(0))
        assertEquals("Different amount found", msg.getValue, args.getArgument(1))
      })

    val returnData = assertGas(BigInteger.ZERO)(EoaMessageProcessor.process(msg, mockStateView, _, defaultBlockContext))
    assertArrayEquals("Different return data found", Array.emptyByteArray, returnData)

    // Test 2: Failure during subBalance
    Mockito.reset(mockStateView)
    Mockito
      .when(mockStateView.subBalance(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[BigInteger]))
      .thenAnswer(_ => {
        throw new Exception("something went error")
      })
    assertThrows[Exception](withGas(EoaMessageProcessor.process(msg, mockStateView, _, defaultBlockContext)))

    // Test 3: Failure during addBalance
    Mockito.reset(mockStateView)
    Mockito
      .when(mockStateView.addBalance(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[BigInteger]))
      .thenAnswer(_ => {
        throw new Exception("something else went error")
      })
    assertThrows[Exception](withGas(EoaMessageProcessor.process(msg, mockStateView, _, defaultBlockContext)))
  }
}
