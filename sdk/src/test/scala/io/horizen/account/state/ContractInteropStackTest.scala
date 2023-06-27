package io.horizen.account.state

import io.horizen.account.utils.{FeeUtils, Secp256k1}
import io.horizen.evm.{Address, Hash}
import io.horizen.utils.BytesUtils
import org.junit.Assert.assertEquals
import org.junit.{Ignore, Test}
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.ByteBuffer
import scala.util.Try

class ContractInteropStackTest extends EvmMessageProcessorTestBase {

  /**
   * Native Contract to test correct call depth limiting: The input is parsed into three arguments
   *   - function signature: ignored here, but useful in combination with an EVM-based contract
   *   - target: address of the next contract to call
   *   - counter: to keep track of maximum reached call depth
   *
   * Execution causes one nested call to the given target address with the input set to:
   *   - function signature: passed through unchanged
   *   - target: the contracts own address
   *   - counter: incremented and passed along
   *
   * The return value is:
   *   - The result of the nested call in case of success.
   *   - The current counter if the nested call failed.
   *
   * Note this enables multiple test cases:
   *   - Passing the contract address itself as target causes an infinite recursive loop.
   *   - Passing a different target address allows that contract to call back to this one to cause a loop between two
   *     contracts.
   */
  private object NativeInteropStackContract extends NativeSmartContractMsgProcessor {
    override val contractAddress: Address = new Address("0x00000000000000000000000000000000deadbeef")
    override val contractCode: Array[Byte] = Keccak256.hash("NativeInteropStackContract")

    override def process(
        invocation: Invocation,
        view: BaseAccountStateView,
        context: ExecutionContext
    ): Array[Byte] = {
      // parse input
      val in = invocation.input
//      println(s"call at depth ${context.depth - 1} with ${invocation.gasPool.getGas} gas and input (${in.length}): ${BytesUtils.toHexString(in)}")
      if (in.length != 4 + 32 + 32) {
        throw new IllegalArgumentException("NativeInteropStackContract called with invalid arguments")
      }
//      val signature = in.take(4)
      val target = new Address(in.slice(16, 36))
      val counter = bytesToInt(in.slice(64, 68))
      assertEquals("unexpected call depth", context.depth - 1, counter)
      // execute nested call
      val nestedInput = abiEncode(contractAddress, counter + 1)
      val nestedGas = invocation.gasPool.getGas.divide(64).multiply(63)
//      println(s"nested call to $target with $nestedGas gas and input (${nestedInput.length}): ${BytesUtils.toHexString(nestedInput)}")
      val result = Try.apply(context.execute(invocation.staticCall(target, nestedInput, nestedGas)))
      // return result or the current counter in case the nested call failed
      result.getOrElse(intToBytes(counter))
    }
  }

  private class TestEvmMessageProcessor() extends EvmMessageProcessor {
    nativeContractAddresses = Array[Address](NativeInteropStackContract.contractAddress)
  }

  private def intToBytes(num: Int) = ByteBuffer.allocate(4).putInt(num).array()
  private def bytesToInt(arr: Array[Byte]) = BytesUtils.getInt(arr, arr.length - 4)

  private def abiEncode(target: Address, counter: Int = 0) = {
    funcSignature ++ new Array[Byte](12) ++ target.toBytes ++ new Array[Byte](28) ++ intToBytes(counter)
  }

  // compiled EVM byte-code of NativeInteropStack contract
  // source: libevm/contracts/NativeInteropStack.sol
  private val deployCode = BytesUtils.fromHexString(
    "608060405234801561001057600080fd5b506101e0806100206000396000f3fe608060405234801561001057600080fd5b506004361061002b5760003560e01c8063e08b626214610030575b600080fd5b61004361003e36600461010f565b61005c565b60405163ffffffff909116815260200160405180910390f35b60006001600160a01b03831663e08b626230610079856001610154565b6040516001600160e01b031960e085901b1681526001600160a01b03909216600483015263ffffffff1660248201526044016020604051808303816000875af19250505080156100e6575060408051601f3d908101601f191682019092526100e391810190610186565b60015b6100f15750806100f4565b90505b92915050565b63ffffffff8116811461010c57600080fd5b50565b6000806040838503121561012257600080fd5b82356001600160a01b038116811461013957600080fd5b91506020830135610149816100fa565b809150509250929050565b63ffffffff81811683821601908082111561017f57634e487b7160e01b600052601160045260246000fd5b5092915050565b60006020828403121561019857600080fd5b81516101a3816100fa565b939250505056fea264697066735822122098ffa2b10f6a71e67091f0ba55d6c687c344c6a9f32faa1e99e8a7b6e35fed4664736f6c63430008140033"
  )
  // function signature of loop(address,uint32)
  private val funcSignature = BytesUtils.fromHexString("e08b6262")

  private val blockContext =
    new BlockContext(Address.ZERO, 0, FeeUtils.INITIAL_BASE_FEE, 100000, 1, 1, 1, 1234, null, Hash.ZERO)

  private def transition(view: AccountStateView, processors: Seq[MessageProcessor], msg: Message) = {
    // note: the gas limit has to be ridiculously high to reach the maximum call depth of 1024 because of the 63/64 rule
    // when passing gas to a nested call:
    // - The remaining fraction of gas at depth 1024 is: (63/64)^1024
    // - The lower limit to have 10k gas available at depth 1024 is: 10k / (63/64)^1024 = ~100 billion
    // - Some gas is consumed along the way, so we give x10: 1 trillion
    val gasLimit = BigInteger.TEN.pow(12)
    val transition = new StateTransition(view, processors, new GasPool(gasLimit), blockContext, msg)
    transition.execute(Invocation.fromMessage(msg, new GasPool(gasLimit)))
  }

  @Test
  def testNativeCallDepth(): Unit = {
    val initialBalance = new BigInteger("2000000000000")
    val processors = Seq(NativeInteropStackContract)

    usingView(processors) { stateView =>
      stateView.addBalance(origin, initialBalance)

      // call a native contract and cause recursive loop by making the contract call itself in a loop
      val returnData = transition(
        stateView,
        processors,
        getMessage(
          NativeInteropStackContract.contractAddress,
          data = abiEncode(NativeInteropStackContract.contractAddress)
        )
      )
      // at call depth 1024 we expect the call to fail
      // as the function returns the maximum call depth reached we expect 1024
      val callDepthReached = bytesToInt(returnData)
      println("infinite loop returned: " + callDepthReached)
      assertEquals("unexpected call depth", 1024, callDepthReached)
    }
  }

  @Test
  def testEvmCallDepth(): Unit = {
    val initialBalance = new BigInteger("2000000000000")
    val evmMessageProcessor = new EvmMessageProcessor()
    val processors = Seq(NativeInteropStackContract, evmMessageProcessor)

    usingView(processors) { stateView =>
      stateView.addBalance(origin, initialBalance)

      // deploy the NativeInteropStack contract (EVM based)
      transition(stateView, processors, getMessage(null, data = deployCode))
      // get deployed contract address
      val evmContractAddress = Secp256k1.generateContractAddress(origin, 0)

      // call an EVM-based contract and cause recursive loop by making the contract call itself in a loop
      val returnData = transition(
        stateView,
        processors,
        getMessage(evmContractAddress, data = abiEncode(evmContractAddress))
      )
      // at call depth 1024 we expect the call to fail
      // as the function returns the maximum call depth reached we expect 1024
      val callDepthReached = bytesToInt(returnData)
      println("infinite loop returned: " + callDepthReached)
      assertEquals("unexpected call depth", 1024, callDepthReached)
    }
  }

  /**
   * TODO: This test is currently skipped because it causes a stack overflow in the libevm library after a few
   * iterations, long before the call depth limit can be reached.
   */
  @Test
  @Ignore
  def testInteropCallDepth(): Unit = {
    val initialBalance = new BigInteger("2000000000000")
    val evmMessageProcessor = new TestEvmMessageProcessor()
    val processors = Seq(NativeInteropStackContract, evmMessageProcessor)

    usingView(processors) { stateView =>
      stateView.addBalance(origin, initialBalance)

      // deploy the NativeInteropStack contract (EVM based)
      transition(stateView, processors, getMessage(null, data = deployCode))
      // get deployed contract address
      val evmContractAddress = Secp256k1.generateContractAddress(origin, 0)

      // cause a call loop: native contract => EVM-based contract => native contract => ...
//      val returnData = transition(
//        stateView,
//        processors,
//        getMessage(NativeInteropStackContract.contractAddress, data = abiEncode(evmContractAddress, 0))
//      )
      // cause a call loop: EVM-based contract => native contract => EVM-based contract => ...
      val returnData = transition(
        stateView,
        processors,
        getMessage(evmContractAddress, data = abiEncode(NativeInteropStackContract.contractAddress))
      )
      // at call depth 1024 we expect the call to fail
      // as the function returns the maximum call depth reached we expect 1024
      val callDepthReached = bytesToInt(returnData)
      println("infinite loop returned: " + callDepthReached)
      assertEquals("unexpected call depth", 1024, callDepthReached)
    }
  }
}
