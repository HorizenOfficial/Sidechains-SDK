package com.horizen.account.state

import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertFalse, assertNotNull, assertTrue}
import org.junit.Test
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger

class EvmMessageProcessorIntegrationTest extends EvmMessageProcessorTestBase {

  // compiled EVM byte-code of the Storage contract,
  // source: libevm/contracts/Storage.sol
  val deployCode: Array[Byte] = BytesUtils.fromHexString(
    "608060405234801561001057600080fd5b5060405161023638038061023683398101604081905261002f916100f6565b6000819055604051339060008051602061021683398151915290610073906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03166000805160206102168339815191526040516100bf906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b9994690600090a15061010f565b60006020828403121561010857600080fd5b5051919050565b60f98061011d6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea264697066735822122080d9db531d29b1bd6b4e16762726b70e2a94f0b40ee4e2ab534d9b879cf1c25664736f6c634300080f00330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0")

  @Test
  def testCanProcess(): Unit = {
    usingView { stateView =>
      // prepare contract account
      stateView.addAccount(contractAddress, Keccak256.hash("testcode"))
      // prepare eoa account that is not empty
      stateView.addBalance(eoaAddress, BigInteger.TEN)

      val processor = new EvmMessageProcessor()
      assertTrue("should process smart contract deployment", processor.canProcess(getMessage(null), stateView))
      assertTrue(
        "should process calls to existing smart contracts",
        processor.canProcess(getMessage(contractAddress), stateView))
      assertFalse(
        "should not process EOA to EOA transfer (empty account)",
        processor.canProcess(getMessage(emptyAddress), stateView))
      assertFalse(
        "should not process EOA to EOA transfer (non-empty account)",
        processor.canProcess(getMessage(eoaAddress), stateView))
    }
  }

  @Test
  def testProcess(): Unit = {

    val initialBalance = new BigInteger("2000000000000")
    val evmMessageProcessor = new EvmMessageProcessor()

    usingView(evmMessageProcessor) { stateView =>
      stateView.addBalance(origin, initialBalance)

      // smart contract constructor has one argument (256-bit uint)
      val initialValue = Array.fill(32) { 0.toByte }
      initialValue(initialValue.length - 1) = 42
      // add constructor arguments to the end of the deployment code
      val msg = getMessage(null, data = deployCode ++ initialValue)
      val result = assertGas(76990, msg, stateView, evmMessageProcessor, defaultBlockContext)
      assertNotNull("result should not be null", result)
    }
  }
}
