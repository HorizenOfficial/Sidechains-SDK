package io.horizen.account.state

import io.horizen.evm.Address
import org.junit.Assert.{assertFalse, assertNotNull, assertTrue}
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar

import java.nio.charset.StandardCharsets

class EvmMessageProcessorTest extends EvmMessageProcessorTestBase with MockitoSugar {
  @Test
  def testInit(): Unit = {
    val mockStateView = MockitoSugar.mock[AccountStateView]
    val processor = new EvmMessageProcessor()
    // just make sure this does not throw
    processor.init(mockStateView, 0)
  }

  @Test
  def testCanProcess(): Unit = {
    val mockStateView = mock[AccountStateView]
    val processor = new EvmMessageProcessor()

    Mockito
      .when(mockStateView.isSmartContractAccount(ArgumentMatchers.any[Address]()))
      .thenAnswer(args => {
        val address: Address = args.getArgument(0)
        assertNotNull("should not check the null address", address)
        contractAddress.equals(address)
      })

    assertTrue(
      "should process smart contract deployment",
      TestContext.canProcess(processor, getMessage(null), mockStateView, 0)
    )
    assertTrue(
      "should process calls to existing smart contracts",
      TestContext.canProcess(processor, getMessage(contractAddress), mockStateView, 0)
    )
    assertFalse(
      "should not process EOA to EOA transfer (empty account)",
      TestContext.canProcess(processor, getMessage(emptyAddress), mockStateView, 0)
    )
    assertFalse(
      "should not process EOA to EOA transfer (non-empty account)",
      TestContext.canProcess(processor, getMessage(eoaAddress), mockStateView, 0)
    )
    assertFalse(
      "should ignore data on EOA to EOA transfer",
      TestContext.canProcess(
        processor,
        getMessage(eoaAddress, data = "the same thing we do every night, pinky".getBytes(StandardCharsets.UTF_8)),
        mockStateView,
        0
      )
    )
  }
}
