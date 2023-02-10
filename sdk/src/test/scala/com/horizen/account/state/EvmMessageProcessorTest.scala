package com.horizen.account.state

import org.junit.Assert.{assertFalse, assertNotNull, assertTrue}
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar

import java.nio.charset.StandardCharsets
import java.util

class EvmMessageProcessorTest extends MessageProcessorFixture with EvmMessageProcessorTestBase with MockitoSugar {
  @Test
  def testInit(): Unit = {
    val mockStateView = MockitoSugar.mock[AccountStateView]
    val processor = new EvmMessageProcessor()
    // just make sure this does not throw
    processor.init(mockStateView)
  }

  @Test
  def testCanProcess(): Unit = {
    val mockStateView = mock[AccountStateView]
    val processor = new EvmMessageProcessor()

    Mockito
      .when(mockStateView.isSmartContractAccount(ArgumentMatchers.any[Array[Byte]]()))
      .thenAnswer(args => {
        val addressBytes: Array[Byte] = args.getArgument(0)
        assertNotNull("should not check the null address", addressBytes)
        util.Arrays.equals(addressBytes, contractAddress.address())
      })

    assertTrue("should process smart contract deployment", processor.canProcess(getMessage(null), mockStateView))
    assertTrue(
      "should process calls to existing smart contracts",
      processor.canProcess(getMessage(contractAddress), mockStateView))
    assertFalse(
      "should not process EOA to EOA transfer (empty account)",
      processor.canProcess(getMessage(emptyAddress), mockStateView))
    assertFalse(
      "should not process EOA to EOA transfer (non-empty account)",
      processor.canProcess(getMessage(eoaAddress), mockStateView))
    assertFalse(
      "should ignore data on EOA to EOA transfer",
      processor.canProcess(getMessage(eoaAddress, "the same thing we do every night, pinky".getBytes(StandardCharsets.UTF_8)), mockStateView))
  }
}
