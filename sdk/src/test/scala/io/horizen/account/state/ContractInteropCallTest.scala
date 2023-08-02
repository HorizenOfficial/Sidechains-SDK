package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIUtil.{getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.state.ContractInteropTestBase._
import io.horizen.account.utils.BigIntegerUtil.toUint256Bytes
import io.horizen.evm._
import io.horizen.utils.BytesUtils
import org.junit.Assert.{assertArrayEquals, assertEquals, assertTrue, fail}
import org.junit.Test
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Try}

class ContractInteropCallTest extends ContractInteropTestBase {
  override val processorToTest: NativeSmartContractMsgProcessor = NativeTestContract

  private object NativeTestContract extends NativeSmartContractMsgProcessor {
    override val contractAddress: Address = new Address("0x00000000000000000000000000000000deadbeef")
    override val contractCode: Array[Byte] = Keccak256.hash("NativeTestContract")

    val STATICCALL_READONLY_TEST_SIG = "aaaaaaaa"
    val STATICCALL_READWRITE_TEST_SIG = "bbbbbbbb"
    val STATICCALL_READWRITE_WITH_TRY_TEST_SIG = "cccccccc"
    val STATICCALL_NESTED_CALLS_TEST_SIG = "dddddddd"

    val INC_SIG = io.horizen.account.abi.ABIUtil.getABIMethodId("inc()")
    val RETRIEVE_SIG =  io.horizen.account.abi.ABIUtil.getABIMethodId("retrieve()")
    val COUNTER_KEY = Keccak256.hash("key".getBytes(StandardCharsets.UTF_8))

    val SUB_CALLS_GAS: BigInteger =  BigInteger.valueOf(10000)
    val SUB_CALLS_GAS_HEX_STRING =  "0x" + SUB_CALLS_GAS.toString(16)

    override def process(
        invocation: Invocation,
        view: BaseAccountStateView,
        context: ExecutionContext
    ): Array[Byte] = {

      val gasView = view.getGasTrackedView(invocation.gasPool)
      //read method signature
     getFunctionSignature(invocation.input) match {
        case STATICCALL_READONLY_TEST_SIG => testStaticCallOnReadonlyMethod(invocation, gasView, context)
        case RETRIEVE_SIG => retrieve(gasView)
        case STATICCALL_READWRITE_TEST_SIG => testStaticCallOnReadwriteMethod(invocation, gasView, context)
        case INC_SIG => inc(gasView)
        case STATICCALL_READWRITE_WITH_TRY_TEST_SIG => testStaticCallOnReadwriteMethodWithTry(invocation, gasView, context)
        case STATICCALL_NESTED_CALLS_TEST_SIG => testStaticCallNestedCalls(invocation, gasView, context)
        case _ => throw new IllegalArgumentException("Unknown method call")
      }
    }

    def testStaticCallOnReadonlyMethod(
                                  invocation: Invocation,
                                  view: BaseAccountStateView,
                                  context: ExecutionContext
                                ): Array[Byte] = {
      val evmContractAddress = new Address(getArgumentsFromData(invocation.input))

      //read method signature
      val externalMethod = BytesUtils.fromHexString(STORAGE_RETRIEVE_ABI_ID)

      // execute nested call to EVM contract
      val res = context.execute(invocation.staticCall(evmContractAddress, externalMethod, SUB_CALLS_GAS))
      //Check that the statedb is readwrite again
      context.execute(invocation.call(evmContractAddress, 0, BytesUtils.fromHexString(STORAGE_INC_ABI_ID), SUB_CALLS_GAS))
      res
    }

    def testStaticCallOnReadwriteMethod(
                                        invocation: Invocation,
                                        view: BaseAccountStateView,
                                        context: ExecutionContext
                                      ): Array[Byte] = {
      val evmContractAddress = new Address(getArgumentsFromData(invocation.input))

      //read method signature
      val externalMethod = BytesUtils.fromHexString(STORAGE_INC_ABI_ID)

      // execute nested call to EVM contract
      context.execute(invocation.staticCall(evmContractAddress, externalMethod, SUB_CALLS_GAS))
    }

    def testStaticCallNestedCalls(
                                         invocation: Invocation,
                                         view: BaseAccountStateView,
                                         context: ExecutionContext
                                       ): Array[Byte] = {
      val evmContractAddress = new Address(getArgumentsFromData(invocation.input))

      //read method signature
      val externalMethod = BytesUtils.fromHexString(NATIVE_CALLER_NESTED_ABI_ID)

      // execute nested call to EVM contract
      context.execute(invocation.staticCall(evmContractAddress, externalMethod, BigInteger.valueOf(40000)))
    }

    def testStaticCallOnReadwriteMethodWithTry(
                                         invocation: Invocation,
                                         view: BaseAccountStateView,
                                         context: ExecutionContext
                                       ): Array[Byte] = {
      val evmContractAddress = new Address(getArgumentsFromData(invocation.input))

      //read method signature
      val externalMethod = BytesUtils.fromHexString(STORAGE_INC_ABI_ID)

      // execute nested call to EVM contract. It should throw an exception but we continue with the transaction
      Try(context.execute(invocation.staticCall(evmContractAddress, externalMethod, SUB_CALLS_GAS)))

      context.execute(invocation.call(evmContractAddress, 0, BytesUtils.fromHexString(STORAGE_INC_ABI_ID), SUB_CALLS_GAS))
      context.execute(invocation.staticCall(evmContractAddress, BytesUtils.fromHexString(STORAGE_RETRIEVE_ABI_ID), SUB_CALLS_GAS))
    }

    def retrieve(view: BaseAccountStateView): Array[Byte] = {
      val counterInBytesPadded = view.getAccountStorage(contractAddress, COUNTER_KEY)
      counterInBytesPadded
    }

    def inc(view: BaseAccountStateView): Array[Byte] = {
      val counterInBytesPadded = view.getAccountStorage(contractAddress, COUNTER_KEY)
      var counter = org.web3j.utils.Numeric.toBigInt(counterInBytesPadded).intValueExact()
      counter = counter + 1
      val newValue = org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(counter), 32)
      view.updateAccountStorage(contractAddress, COUNTER_KEY, newValue)
      newValue
    }

  }

  @Test
  def testNativeContractCallingEvmContract(): Unit = {
    val initialValue = new BigInteger("400000000000000000000000000000000000000000000000000000000000002a", 16)
    val initialValueBytes = toUint256Bytes(initialValue)

    // deploy the Storage contract (EVM based) and set the initial value
    val storageContractAddress = deploy(ContractInteropTestBase.storageContractCode(initialValue))

    ///////////////////////////////////////////////////////
    // Test 1: Native contract executes a staticcall on a Smart Contract, calling a readonly function
    ///////////////////////////////////////////////////////

    // call a native contract and pass along the Storage contract address
    val inputRetrieveRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READONLY_TEST_SIG), storageContractAddress.toBytes)
    var returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    // verify that the NativeTestContract was able to call the retrieve() function on the EVM based contract
    assertArrayEquals("unexpected result", initialValueBytes, returnData)

    // put a tracer into the context
    var tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    val returnDataTraced = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    // verify that the result is still correct. Calculate new expected value because before we called inc()
    var currentExpectedValue = initialValue.add(BigInteger.ONE)
    var currentExpectedValueBytes = toUint256Bytes(currentExpectedValue)
    assertArrayEquals("unexpected result", currentExpectedValueBytes, returnDataTraced)

    var traceResult = tracer.getResult.result
    //println("traceResult" + traceResult.toPrettyString)

    // check tracer output
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "0x${gasLimit.toString(16)}",
        "gasUsed": "0x29a",
        "input": "0x${BytesUtils.toHexString(inputRetrieveRequest)}",
        "value": "0x0",
        "output": "0x${BytesUtils.toHexString(currentExpectedValueBytes)}",
        "calls": [{
          "type": "STATICCALL",
          "from": "${NativeTestContract.contractAddress}",
          "to": "$storageContractAddress",
          "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "gasUsed": "0xf6",
          "input": "0x$STORAGE_RETRIEVE_ABI_ID",
          "output": "0x${BytesUtils.toHexString(currentExpectedValueBytes)}"
        }, {
          "type" : "CALL",
          "from" : "${NativeTestContract.contractAddress}",
          "to" : "$storageContractAddress",
          "value" : "0x0",
          "gas" : "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "gasUsed" : "0x1a4",
          "input" : "0x$STORAGE_INC_ABI_ID",
          "output" : "0x"
        } ]
      }""",
      traceResult
    )


    ///////////////////////////////////////////////////////
    // Test 2: Native contract executes a staticcall on a Smart Contract, calling a function that modifies the state
    // An exception is expected.
    ///////////////////////////////////////////////////////

    val expectedErrorMsg = "write protection"
    val inputIncRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READWRITE_TEST_SIG), storageContractAddress.toBytes)
    Try(transition(getMessage(NativeTestContract.contractAddress, data = inputIncRequest))) match {
      case Failure(ex: ExecutionFailedException) => assertTrue("Wrong failed exception", ex.getMessage.equals(expectedErrorMsg))
      case _ => fail("Staticcall with readwrite method should have thrown a write protection exception")
    }

    //Check that the statedb wasn't changed
    currentExpectedValue = currentExpectedValue.add(BigInteger.ONE)
    currentExpectedValueBytes = toUint256Bytes(currentExpectedValue)

    returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    // verify that the NativeTestContract was able to call the retrieve() function on the EVM based contract
    assertArrayEquals("unexpected result", currentExpectedValueBytes, returnData)

    // put a tracer into the context
    tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    currentExpectedValue = currentExpectedValue.add(BigInteger.ONE)
    currentExpectedValueBytes = toUint256Bytes(currentExpectedValue)
    Try(transition(getMessage(NativeTestContract.contractAddress, data = inputIncRequest)))

    traceResult = tracer.getResult.result
    //println("traceResult" + traceResult.toPrettyString)

    // check tracer output
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "0x${gasLimit.toString(16)}",
        "gasUsed": "0x${gasLimit.toString(16)}",
        "input": "0x${BytesUtils.toHexString(inputIncRequest)}",
        "error": "$expectedErrorMsg",
        "value": "0x0",
        "calls": [{
          "type": "STATICCALL",
          "from": "${NativeTestContract.contractAddress}",
          "to": "$storageContractAddress",
          "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "gasUsed": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "input": "0x$STORAGE_INC_ABI_ID",
          "error": "$expectedErrorMsg"
        }]
      }""",
      traceResult
    )


    ///////////////////////////////////////////////////////
    // Test 3: Native contract executes a staticcall on a Smart Contract, calling a function that modifies the state.
    // In this case the native smart contract catches the write protection exception and tries again to change the state
    // calling a call instead of a static call.
    ///////////////////////////////////////////////////////

    currentExpectedValue = currentExpectedValue.add(BigInteger.ONE)
    currentExpectedValueBytes = toUint256Bytes(currentExpectedValue)
    val inputIncWithTryRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READWRITE_WITH_TRY_TEST_SIG), storageContractAddress.toBytes)
    returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputIncWithTryRequest))
    // verify that the NativeTestContract was able to call the retrieve() function on the EVM based contract
    assertArrayEquals("unexpected result", currentExpectedValueBytes, returnData)

    // put a tracer into the context
    tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    currentExpectedValue = currentExpectedValue.add(BigInteger.ONE)
    currentExpectedValueBytes = toUint256Bytes(currentExpectedValue)
    returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputIncWithTryRequest))
    assertArrayEquals("unexpected result", currentExpectedValueBytes, returnData)

    traceResult = tracer.getResult.result
    //println("traceResult" + traceResult.toPrettyString)

    // check tracer output
    assertJsonEquals(
      s"""{
    "type": "CALL",
    "from": "$origin",
    "to": "${NativeTestContract.contractAddress}",
    "gas": "0x${gasLimit.toString(16)}",
    "gasUsed": "0x29aa",
    "input": "0x${BytesUtils.toHexString(inputIncWithTryRequest)}",
    "output": "0x${BytesUtils.toHexString(currentExpectedValueBytes)}",
    "value": "0x0",
    "calls": [{
      "type": "STATICCALL",
      "from": "${NativeTestContract.contractAddress}",
      "to": "$storageContractAddress",
      "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
      "gasUsed": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
      "input": "0x$STORAGE_INC_ABI_ID",
      "error": "$expectedErrorMsg"
    },
    {
      "type" : "CALL",
      "from": "${NativeTestContract.contractAddress}",
      "to": "$storageContractAddress",
      "value" : "0x0",
      "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
      "gasUsed" : "0x1a4",
      "input" : "0x$STORAGE_INC_ABI_ID",
      "output" : "0x"
    },
    {
      "type" : "STATICCALL",
      "from": "${NativeTestContract.contractAddress}",
      "to": "$storageContractAddress",
      "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
      "gasUsed" : "0xf6",
      "input" : "0x$STORAGE_RETRIEVE_ABI_ID",
      "output": "0x${BytesUtils.toHexString(currentExpectedValueBytes)}"
    } ]
  }""",
      traceResult
    )

  }

  @Test
  def testEvmContractCallingNativeContract(): Unit = {
    val nativeCallerAddress = deploy(ContractInteropTestBase.nativeCallerContractCode)

    ///////////////////////////////////////////////////////
    // Test 1: Solidity contract executes a staticcall on a Native Smart Contract, calling a readonly function
    // In the same call, it executes a call for incrementing the counter, to check that the statedb doesn't remain readonly
    ///////////////////////////////////////////////////////
    val retrieveInput = BytesUtils.fromHexString(getABIMethodId("testStaticCallOnReadonlyMethod()"))

    var returnData = transition(getMessage(nativeCallerAddress, data = retrieveInput))
    var numericResult = org.web3j.utils.Numeric.toBigInt(returnData).intValueExact()
    var expectedValue = 0
    assertEquals("Wrong result from first retrieve", expectedValue, numericResult)
    expectedValue = expectedValue + 1
    //TODO: the tracer part should be completed
//    // put a tracer into the context
//    val tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
//    blockContext.setTracer(tracer)
//
//    // repeat the call again
//    val returnDataTraced = transition(getMessage(nativeCallerAddress, data = retrieveInput))
//    // verify that the result is still correct.
//    val numericResultTraced = org.web3j.utils.Numeric.toBigInt(returnDataTraced).intValueExact()
//    assertEquals("Wrong result from first retrieve", expectedValue, numericResultTraced)
//
//    var traceResult = tracer.getResult.result
//    println("traceResult" + traceResult.toPrettyString)
//
//    val expectedOutputHex = "0x" + BytesUtils.toHexString(org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(expectedValue), 32))
//    // check tracer output
//
//    assertJsonEquals(
//      s"""{
//    "type": "CALL",
//    "from": "$origin",
//    "to": "${nativeCallerAddress}",
//    "gas": "0x${gasLimit.toString(16)}",
//    "gasUsed": "0x77e",
//    "input": "0x${BytesUtils.toHexString(retrieveInput)}",
//    "value": "0x0",
//    "output": "$expectedOutputHex",
//    "calls": [{
//      "type": "STATICCALL",
//      "from": "$nativeCallerAddress",
//      "to": "${NativeTestContract.contractAddress}",
//      "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
//      "gasUsed": "0x64",
//      "input": "0x$NATIVE_CONTRACT_RETRIEVE_ABI_ID",
//      "output": "$expectedOutputHex"
//    },
//    {
//        "type": "CALL",
//        "from": "$nativeCallerAddress",
//        "to": "${NativeTestContract.contractAddress}",
//        "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
//        "gasUsed": "0x64",
//        "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
//        "output": "$expectedOutputHex"
//          }]
//  }""",
//      traceResult
//    )


    ///////////////////////////////////////////////////////
    // Test 2: Solidity contract call inc() method on Native Smart Contract, first using staticcall and then using call.
    // Staticcall should fail but the transaction is not reverted because the Solidity contract doesn't check the staticcall result.
    // call should works so the counter will be increment by 1.
    ///////////////////////////////////////////////////////

      val readwriteInput = BytesUtils.fromHexString(getABIMethodId("testStaticCallOnReadwriteMethod()"))

      returnData = transition(getMessage(nativeCallerAddress, data = readwriteInput))
      numericResult = org.web3j.utils.Numeric.toBigInt(returnData).intValueExact()
      expectedValue = expectedValue + 1
      assertEquals("Wrong result", expectedValue, numericResult)

      // Check that the counter inside the native smart contract didn't change
      var result = transition(getMessage(nativeCallerAddress, data = retrieveInput))
      // verify that the result is still correct.
      assertEquals("Wrong result from retrieve", expectedValue, org.web3j.utils.Numeric.toBigInt(result).intValueExact())
      expectedValue = expectedValue + 1

    //TODO: the tracer part should be completed
//      // put a tracer into the context
//
//      val tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
//      blockContext.setTracer(tracer)
//
//      // repeat the call again
//      val returnDataTraced = transition(getMessage(nativeCallerAddress, data = readwriteInput))
//      expectedValue = expectedValue + 1
//      // verify that the result is still correct.
//      val numericResultTraced = org.web3j.utils.Numeric.toBigInt(returnDataTraced).intValueExact()
//      assertEquals("Wrong result from tracer call", expectedValue, numericResultTraced)
//
//      var traceResult = tracer.getResult.result
//      println("traceResult" + traceResult.toPrettyString)
//
//      val expectedErrorMsg = "external contract invocation failed: io.horizen.account.state.WriteProtectionException: invalid write access to storage"
//      val expectedOutputHex = "0x" + BytesUtils.toHexString(org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(expectedValue), 32))
//      assertJsonEquals(
//        s"""{
//          "type": "CALL",
//          "from": "$origin",
//          "to": "${nativeCallerAddress}",
//          "gas": "0x${gasLimit.toString(16)}",
//          "gasUsed": "0x6895",
//          "input": "0x${BytesUtils.toHexString(readwriteInput)}",
//          "value": "0x0",
//          "output": "$expectedOutputHex",
//          "calls": [{
//            "type": "STATICCALL",
//            "from": "$nativeCallerAddress",
//            "to": "${NativeTestContract.contractAddress}",
//            "gas": "0x61a8",
//            "gasUsed": "0x61a8",
//            "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
//            "error": "$expectedErrorMsg"
//          },
//          {
//            "type": "CALL",
//            "from": "$nativeCallerAddress",
//            "to": "${NativeTestContract.contractAddress}",
//            "gas": "0x61a8",
//            "gasUsed": "0x61a8",
//            "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
//            "output": "$expectedOutputHex"
//          }]
//          }""",
//        traceResult
//      )

      ///////////////////////////////////////////////////////
      // Test 3: Solidity contract calls a method on a Native Smart Contract using the contract interface. The method
      // is declared in the contract interface as view but it actually is a readwrite function.
      // The transaction should fail.
      ///////////////////////////////////////////////////////

      val readwriteContractCallInput = BytesUtils.fromHexString(getABIMethodId("testStaticCallOnReadwriteMethodContractCall()"))

      Try(transition(getMessage(nativeCallerAddress, data = readwriteContractCallInput))) match {
        case Failure(ex: ExecutionRevertedException) =>//OK
        case res => fail(s"Wrong result: $res")
      }

      // Check that the counter inside the native smart contract didn't change
      result = transition(getMessage(nativeCallerAddress, data = retrieveInput))
      // verify that the result is still correct.
      assertEquals("Wrong result from first retrieve", expectedValue, org.web3j.utils.Numeric.toBigInt(result).intValueExact())
      expectedValue = expectedValue + 1

    //TODO: the tracer part should be completed
//      // put a tracer into the context
//
//      val tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
//      blockContext.setTracer(tracer)
//
//      // repeat the call again
//      Try(transition(getMessage(nativeCallerAddress, data = readwriteContractCallInput)))
//
//      var traceResult = tracer.getResult.result
//      println("traceResult" + traceResult.toPrettyString)
//
//      val expectedErrorMsg = "external contract invocation failed: io.horizen.account.state.WriteProtectionException: invalid write access to storage"
//
//      assertJsonEquals(
//        s"""{
//                "type": "CALL",
//                "from": "$origin",
//                "to": "${nativeCallerAddress}",
//                "gas": "0x${gasLimit.toString(16)}",
//                "gasUsed": "0x63c3",
//                "input": "0x${BytesUtils.toHexString(readwriteContractCallInput)}",
//                "value": "0x0",
//                "error" : "execution reverted",
//                "calls": [{
//                  "type": "STATICCALL",
//                  "from": "$nativeCallerAddress",
//                  "to": "${NativeTestContract.contractAddress}",
//                  "gas": "0x61a8",
//                  "gasUsed": "0x61a8",
//                  "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
//                  "error": "$expectedErrorMsg"
//                }]
//                }""",
//        traceResult
//      )

  }


  @Test
  def testNativeContractNestedCalls(): Unit = {

    // deploy the NativeCaller contract (EVM based) and set the initial value
    val nativeCallerContractAddress = deploy(ContractInteropTestBase.nativeCallerContractCode)

    val input = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_NESTED_CALLS_TEST_SIG), nativeCallerContractAddress.toBytes)

    Try(transition(getMessage(NativeTestContract.contractAddress, data = input))) match {
      case Failure(ex: ExecutionRevertedException) => //OK
      case e => fail(s"Should have failed with  ExecutionRevertedException: $e")
    }

    //TODO tracer
//    // put a tracer into the context
//    val tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
//    blockContext.setTracer(tracer)
//
//    // repeat the call again
//    Try(transition(getMessage(NativeTestContract.contractAddress, data = input)))
//
//    val traceResult = tracer.getResult.result
//    println("traceResult" + traceResult.toPrettyString)
//    val expectedErrorMsg = "execution reverted"
//
//    // check tracer output
//    assertJsonEquals(
//      s"""{
//      "type": "CALL",
//      "from": "$origin",
//      "to": "${NativeTestContract.contractAddress}",
//      "gas": "0x${gasLimit.toString(16)}",
//      "gasUsed": "0x0x6124",
//      "input": "0x${BytesUtils.toHexString(input)}",
//      "error": "$expectedErrorMsg",
//      "value": "0x0",
//      "calls": [{
//        "type": "STATICCALL",
//        "from": "${NativeTestContract.contractAddress}",
//        "to": "$nativeCallerContractAddress",
//        "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
//        "gasUsed": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
//        "input": "0x$NATIVE_CALLER_NESTED_ABI_ID",
//        "error": "$expectedErrorMsg"
//      }]
//    }""",
//      traceResult
//    )
  }
}
