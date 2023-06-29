package io.horizen.account.state

import io.horizen.account.utils.BigIntegerUtil.toUint256Bytes
import io.horizen.evm._
import io.horizen.utils.BytesUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger

class ContractInteropCallTest extends ContractInteropTestBase {
  override val processorToTest: NativeSmartContractMsgProcessor = NativeTestContract

  private object NativeTestContract extends NativeSmartContractMsgProcessor {
    override val contractAddress: Address = new Address("0x00000000000000000000000000000000deadbeef")
    override val contractCode: Array[Byte] = Keccak256.hash("NativeTestContract")

    override def process(
        invocation: Invocation,
        view: BaseAccountStateView,
        context: ExecutionContext
    ): Array[Byte] = {
      // read target contract address from input
      val evmContractAddress = new Address(invocation.input)
      // function signature of retrieve()
      val retrieveSignature = BytesUtils.fromHexString("2e64cec1")
      // execute nested call to EVM contract
      context.execute(invocation.staticCall(evmContractAddress, retrieveSignature, 10000))
    }
  }

  @Test
  def testNativeContractCallingEvmContract(): Unit = {
    val initialValue = new BigInteger("400000000000000000000000000000000000000000000000000000000000002a", 16)
    val initialValueHex = "0x" + initialValue.toString(16)
    val initialValueBytes = toUint256Bytes(initialValue)

    // deploy the Storage contract (EVM based) and set the initial value
    val storageContractAddress = deploy(ContractInteropTestBase.storageContractCode(initialValue))
    // call a native contract and pass along the Storage contract address
    val returnData = transition(getMessage(NativeTestContract.contractAddress, data = storageContractAddress.toBytes))
    // verify that the NativeTestContract was able to call the retrieve() function on the EVM based contract
    assertArrayEquals("unexpected result", initialValueBytes, returnData)

    // put a tracer into the context
    val tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    val returnDataTraced = transition(getMessage(NativeTestContract.contractAddress, data = storageContractAddress.toBytes))
    // verify that the result is still correct
    assertArrayEquals("unexpected result", initialValueBytes, returnDataTraced)

    val traceResult = tracer.getResult.result
//      println("traceResult" + traceResult.toPrettyString)

    // check tracer output
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "0x${gasLimit.toString(16)}",
        "gasUsed": "0xf6",
        "input": "$storageContractAddress",
        "value": "0x0",
        "output": "$initialValueHex",
        "calls": [{
          "type": "STATICCALL",
          "from": "${NativeTestContract.contractAddress}",
          "to": "$storageContractAddress",
          "gas": "0x2710",
          "gasUsed": "0xf6",
          "input": "0x2e64cec1",
          "output": "$initialValueHex"
        }]
      }""",
      traceResult
    )
  }

  @Test
  def testEvmContractCallingNativeContract(): Unit = {
    // TODO
  }
}
