package io.horizen.account.state

import io.horizen.evm.{Address, TraceOptions, Tracer}
import io.horizen.utils.BytesUtils
import org.junit.Assert.assertEquals
import org.junit.{Ignore, Test}
import sparkz.crypto.hash.Keccak256

import java.nio.ByteBuffer
import scala.util.Try

class ContractInteropStackTest extends ContractInteropTestBase {

  override val processorToTest: NativeSmartContractMsgProcessor = NativeTestContract

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
  private object NativeTestContract extends NativeSmartContractMsgProcessor {
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
      result.getOrElse(new Array[Byte](28) ++ intToBytes(counter))
    }
  }

  private def intToBytes(num: Int) = ByteBuffer.allocate(4).putInt(num).array()
  private def bytesToInt(arr: Array[Byte]) = BytesUtils.getInt(arr, arr.length - 4)

  private def abiEncode(target: Address, counter: Int = 0) = {
    funcSignature ++ new Array[Byte](12) ++ target.toBytes ++ new Array[Byte](28) ++ intToBytes(counter)
  }

  // function signature of loop(address,uint32)
  private val funcSignature = BytesUtils.fromHexString("e08b6262")

  @Test
  def testNativeCallDepth(): Unit = {
    // call a native contract and cause recursive loop by making the contract call itself in a loop
    val returnData =
      transition(getMessage(NativeTestContract.contractAddress, data = abiEncode(NativeTestContract.contractAddress)))
    // at call depth 1024 we expect the call to fail
    // as the function returns the maximum call depth reached we expect 1024
    val callDepthReached = bytesToInt(returnData)
    println("infinite loop returned: " + callDepthReached)
    assertEquals("unexpected call depth", 1024, callDepthReached)
  }

  @Test
  def testEvmCallDepth(): Unit = {
    val address = deploy(ContractInteropTestBase.nativeInteropContractCode)
    // call an EVM-based contract and cause recursive loop by making the contract call itself in a loop
    val returnData = transition(getMessage(address, data = abiEncode(address)))
    // at call depth 1024 we expect the call to fail
    // as the function returns the maximum call depth reached we expect 1024
    val callDepthReached = bytesToInt(returnData)
    println("infinite loop returned: " + callDepthReached)
    assertEquals("unexpected call depth", 1024, callDepthReached)
  }

  /**
   * TODO: This test is currently skipped because it causes a stack overflow in the libevm library after a few
   * iterations, long before the call depth limit can be reached.
   */
  @Test
//  @Ignore("current leads to a stack overflow in libevm")
  def testInteropCallDepth(): Unit = {
    val address = deploy(ContractInteropTestBase.nativeInteropContractCode)
    // cause a call loop: native contract => EVM-based contract => native contract => ...
    var tracer = new Tracer(new TraceOptions(false, false, false,
      false, "callTracer", null))
    blockContext.setTracer(tracer)

    val returnData =
      transition(getMessage(NativeTestContract.contractAddress, data = abiEncode(address)))
      println(tracer.getResult.result)

    // cause a call loop: EVM-based contract => native contract => EVM-based contract => ...
//    val returnData =
//      transition(getMessage(interopContractAddress, data = abiEncode(NativeInteropStackContract.contractAddress)))
    // at call depth 1024 we expect the call to fail
    // as the function returns the maximum call depth reached we expect 1024
    val callDepthReached = bytesToInt(returnData)
    println("infinite loop returned: " + callDepthReached)
    assertEquals("unexpected call depth", 1024, callDepthReached)
  }
}
