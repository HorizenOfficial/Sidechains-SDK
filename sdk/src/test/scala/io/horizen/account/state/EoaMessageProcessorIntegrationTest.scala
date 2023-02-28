package com.horizen.account.state

import com.horizen.fixtures.SecretFixture
import com.horizen.utils.ClosableResourceHandler
import org.junit.Assert.{assertArrayEquals, assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets

class EoaMessageProcessorIntegrationTest
  extends JUnitSuite
    with MockitoSugar
    with SecretFixture
    with MessageProcessorFixture {

  @Test
  def canProcess(): Unit = {
    val toAddress = getAddressProposition(12345L).address()
    val value = BigInteger.TWO
    val msg = getMessage(toAddress, value, Array.emptyByteArray)

    usingView(EoaMessageProcessor) { view =>
      // Test 1: to account doesn't exist, so considered as EOA
      assertTrue("Processor expected to BE ABLE to process message", EoaMessageProcessor.canProcess(msg, view))

      // Test 2: to account exists and has NO code hash defined, so considered as EOA
      // declare account with some coins
      view.addBalance(toAddress, BigInteger.ONE)
      assertTrue("Processor expected to BE ABLE to process message", EoaMessageProcessor.canProcess(msg, view))

      // Test 3: to account exists and has code hash defined, so considered as Smart contract account
      val codeHash: Array[Byte] = Keccak256.hash("abcd".getBytes(StandardCharsets.UTF_8))
      view.addAccount(toAddress, codeHash)
      assertFalse("Processor expected to UNABLE to process message", EoaMessageProcessor.canProcess(msg, view))

      // Test 4: "to" is null -> smart contract declaration case
      val data: Array[Byte] = new Array[Byte](100)
      val contractDeclarationMessage = getMessage(toAddress, value, data)
      assertFalse("Processor expected to UNABLE to process message", EoaMessageProcessor.canProcess(contractDeclarationMessage, view))
    }
  }

  @Test
  def process(): Unit = {
    val value = BigInteger.valueOf(1337)
    val initialBalance = new BigInteger("2000000000000")
    val to = randomAddress
    val msg = getMessage(to, value, Array.emptyByteArray)
    val sender = msg.getFrom

    usingView(EoaMessageProcessor) { view =>
      view.addBalance(sender, initialBalance)
      // EOA transactions only consume intrinsic gas, the processor itself therefore must not use any gas
      val returnData = assertGas(0, msg, view, EoaMessageProcessor, defaultBlockContext)
      assertArrayEquals("Different return data found", Array.emptyByteArray, returnData)
      assertEquals("Different from account value found", initialBalance.subtract(value), view.getBalance(sender))
      assertEquals("Different to account value found", value, view.getBalance(to))
    }
  }
}
