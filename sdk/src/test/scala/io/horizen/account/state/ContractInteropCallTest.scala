package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIUtil.{getArgumentsFromData, getFunctionSignature}
import io.horizen.account.state.ContractInteropTestBase._
import io.horizen.account.utils.BigIntegerUtil.toUint256Bytes
import io.horizen.account.utils.{FeeUtils, Secp256k1}
import io.horizen.evm._
import io.horizen.utils.BytesUtils
import org.junit.Assert.{assertArrayEquals, assertEquals, fail}
import org.junit.Test
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Try}

class ContractInteropCallTest extends ContractInteropTestBase {
  override val processorToTest: NativeSmartContractMsgProcessor = NativeTestContract


  val WRITE_PROTECTION_ERR_MSG_FROM_EVM = "write protection"
  val INVALID_OP_CODE__ERR_MSG_FROM_EVM = "invalid opcode"
  val WRITE_PROTECTION_ERR_MSG_FROM_NATIVE_CONTRACT = "invalid write access to storage"

  private object NativeTestContract extends NativeSmartContractMsgProcessor {
    override val contractAddress: Address = new Address("0x00000000000000000000000000000000deadbeef")
    override val contractCode: Array[Byte] = Keccak256.hash("NativeTestContract")

    val STATICCALL_READONLY_TEST_SIG = "aaaaaaaa"
    val STATICCALL_READWRITE_TEST_SIG = "bbbbbbbb"
    val STATICCALL_READWRITE_WITH_TRY_TEST_SIG = "cccccccc"
    val STATICCALL_NESTED_CALLS_TEST_SIG = "dddddddd"
    val CREATE_TEST_SIG = "eeeeeeee"

    val COUNTER_KEY = Keccak256.hash("key".getBytes(StandardCharsets.UTF_8))

    val SUB_CALLS_GAS: BigInteger =  BigInteger.valueOf(25000)
    val SUB_CALLS_GAS_HEX_STRING =  "0x" + SUB_CALLS_GAS.toString(16)

    val DEPLOY_GAS: BigInteger = BigInteger.valueOf(320000)
    val DEPLOY_GAS_HEX_STRING =  "0x" + DEPLOY_GAS.toString(16)


    override def process(
                          invocation: Invocation,
                          view: BaseAccountStateView,
                          context: ExecutionContext
                        ): Array[Byte] = {
      val gasView = view.getGasTrackedView(invocation.gasPool)
      //read method signature
      getFunctionSignature(invocation.input) match {
        case STATICCALL_READONLY_TEST_SIG => testStaticCallOnReadonlyMethod(invocation, context)
        case NATIVE_CONTRACT_RETRIEVE_ABI_ID => retrieve(gasView)
        case STATICCALL_READWRITE_TEST_SIG => testStaticCallOnReadwriteMethod(invocation, context)
        case NATIVE_CONTRACT_INC_ABI_ID => inc(gasView)
        case STATICCALL_READWRITE_WITH_TRY_TEST_SIG => testStaticCallOnReadwriteMethodWithTry(invocation, context)
        case STATICCALL_NESTED_CALLS_TEST_SIG => testStaticCallNestedCalls(invocation, context)
        case CREATE_TEST_SIG => testDeployContract(invocation, gasView, context)
        case _ => throw new IllegalArgumentException("Unknown method call")
      }
    }

    def testStaticCallOnReadonlyMethod(
                                  invocation: Invocation,
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

    def testDeployContract(
                                        invocation: Invocation,
                                        view: BaseAccountStateView,
                                        context: ExecutionContext
                                      ): Array[Byte] = {
      val evmContractCode = getArgumentsFromData(invocation.input)

      val createInvocation = Invocation(contractAddress, None, 0, evmContractCode, new GasPool(DEPLOY_GAS), readOnly = false)
      // execute nested call to EVM contract
      val res = context.execute(createInvocation)
      res
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
    // Test 1: Native contract executes a staticcall, that calls a readonly function on a Smart Contract
    ///////////////////////////////////////////////////////

    // call the native contract and pass along the Storage contract address
    val inputRetrieveRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READONLY_TEST_SIG),
      storageContractAddress.toBytes)
    var returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    // verify that the NativeTestContract was able to call the retrieve() function on the EVM based contract
    assertArrayEquals("unexpected result", initialValueBytes, returnData)

    // put a tracer into the context
    var tracer = new Tracer(new TraceOptions(false, false, false,
      false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    val returnDataTraced = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    // verify that the result is still correct. Calculate new expected value because before we called inc()
    var currentExpectedValue = initialValue.add(BigInteger.ONE)
    var currentExpectedValueBytes = toUint256Bytes(currentExpectedValue)
    assertArrayEquals("unexpected result", currentExpectedValueBytes, returnDataTraced)

    var traceResult = tracer.getResult.result

    // check tracer output
    val initialGasHexString = gasLimitHexString
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "$initialGasHexString",
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
    // Test 2: Native contract executes a staticcall on a Smart Contract, calling a function that modifies the state.
    // An exception is expected.
    ///////////////////////////////////////////////////////

    val expectedErrorMsg = WRITE_PROTECTION_ERR_MSG_FROM_EVM
    val inputIncRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READWRITE_TEST_SIG), storageContractAddress.toBytes)
    Try(transition(getMessage(NativeTestContract.contractAddress, data = inputIncRequest))) match {
      case Failure(ex: ExecutionFailedException) => assertEquals("Wrong failed exception", expectedErrorMsg, ex.getMessage)
      case _ => fail(s"Staticcall with readwrite method should have thrown a $expectedErrorMsg exception")
    }

    //Check that the statedb wasn't changed => call again retrieve()
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

    val failedSubCallGasHexString = NativeTestContract.SUB_CALLS_GAS_HEX_STRING

    // check tracer output
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "$initialGasHexString",
        "gasUsed": "$initialGasHexString",
        "input": "0x${BytesUtils.toHexString(inputIncRequest)}",
        "error": "$expectedErrorMsg",
        "value": "0x0",
        "calls": [{
          "type": "STATICCALL",
          "from": "${NativeTestContract.contractAddress}",
          "to": "$storageContractAddress",
          "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "gasUsed": "$failedSubCallGasHexString",
          "input": "0x$STORAGE_INC_ABI_ID",
          "error": "$expectedErrorMsg"
        }]
      }""",
      traceResult
    )


    ///////////////////////////////////////////////////////
    // Test 3: Native contract executes a staticcall on a Smart Contract, calling a function that modifies the state.
    // In this case the native smart contract catches the write protection exception and tries again to change the state
    // calling a call instead of a static call. The transaction should succeed.
    ///////////////////////////////////////////////////////

    currentExpectedValue = currentExpectedValue.add(BigInteger.ONE)
    currentExpectedValueBytes = toUint256Bytes(currentExpectedValue)
    val inputIncWithTryRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READWRITE_WITH_TRY_TEST_SIG), storageContractAddress.toBytes)
    returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputIncWithTryRequest))

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

    // check tracer output
    assertJsonEquals(
      s"""{
    "type": "CALL",
    "from": "$origin",
    "to": "${NativeTestContract.contractAddress}",
    "gas": "$initialGasHexString",
    "gasUsed": "0x6442",
    "input": "0x${BytesUtils.toHexString(inputIncWithTryRequest)}",
    "output": "0x${BytesUtils.toHexString(currentExpectedValueBytes)}",
    "value": "0x0",
    "calls": [{
      "type": "STATICCALL",
      "from": "${NativeTestContract.contractAddress}",
      "to": "$storageContractAddress",
      "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
      "gasUsed": "$failedSubCallGasHexString",
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
    // Test 1: Solidity contract executes a staticcall on a Native Smart Contract, before reaching the fork point.
    // It should fail because the EVM tries to execute the "fake" code associated with the native smart contract
    ///////////////////////////////////////////////////////
    val retrieveInput = BytesUtils.fromHexString(NATIVE_CALLER_STATIC_READONLY_ABI_ID)

    var tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    val blockContextForFork =
      new BlockContext(Address.ZERO, 0, FeeUtils.INITIAL_BASE_FEE, gasLimit, 1, 1, 1, 1234, null, Hash.ZERO)
    blockContextForFork.setTracer(tracer)

    Try(transition(getMessage(nativeCallerAddress, data = retrieveInput), blockContextForFork)) match {
      case Failure(ex: ExecutionRevertedException) => //OK
      case res => fail(s"Wrong result: $res")
    }

    var traceResult = tracer.getResult.result
    // check tracer output
    // Expected error from the EVM. The EVM doesn't know any native contract before the fork, it will treat them as EVM
    // contracts: it will try to execute the "fake" code saved inside the stateDb and this causes an invalid opcode.
    var expectedErrorMsg = "invalid opcode: opcode 0xce not defined"

    println(traceResult)
    assertJsonEquals(
      s"""{
                    "type": "CALL",
                    "from": "$origin",
                    "to": "${nativeCallerAddress}",
                    "gas": "$gasLimitHexString",
                    "gasUsed": "0x6eb6",
                    "input": "0x${BytesUtils.toHexString(retrieveInput)}",
                    "value": "0x0",
                    "error": "execution reverted",
                    "calls": [{
                      "type": "STATICCALL",
                      "from": "$nativeCallerAddress",
                      "to": "${NativeTestContract.contractAddress}",
                      "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
                      "gasUsed": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
                      "input": "0x$NATIVE_CONTRACT_RETRIEVE_ABI_ID",
                      "error": "$expectedErrorMsg"
                    }]
                  }""",
        traceResult
      )


    ///////////////////////////////////////////////////////
    // Test 2: Solidity contract executes a staticcall on a Native Smart Contract, calling a readonly function
    // In the same call, it executes a call for incrementing the counter, to check that the statedb doesn't remain readonly
    ///////////////////////////////////////////////////////

    var currentCounterValue = 0
    var returnData = transition(getMessage(nativeCallerAddress, data = retrieveInput))
    currentCounterValue = currentCounterValue + 1

    var numericResult = org.web3j.utils.Numeric.toBigInt(returnData).intValueExact()
    var expectedTxResult = 0
    assertEquals("Wrong result from first retrieve", expectedTxResult, numericResult)

    tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    expectedTxResult = currentCounterValue
    var returnDataTraced = transition(getMessage(nativeCallerAddress, data = retrieveInput))
    currentCounterValue = currentCounterValue + 1

    // verify that the result is still correct.
    var numericResultTraced = org.web3j.utils.Numeric.toBigInt(returnDataTraced).intValueExact()
    assertEquals("Wrong result from first retrieve", expectedTxResult, numericResultTraced)

    traceResult = tracer.getResult.result

    var expectedTxOutputHex = "0x" + BytesUtils.toHexString(org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(expectedTxResult), 32))
    val currentCounterValueHex = "0x" + BytesUtils.toHexString(org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(currentCounterValue), 32))
    // check tracer output

    assertJsonEquals(
      s"""{
    "type": "CALL",
    "from": "$origin",
    "to": "$nativeCallerAddress",
    "gas": "$gasLimitHexString",
    "gasUsed": "0x794",
    "input": "0x${BytesUtils.toHexString(retrieveInput)}",
    "value": "0x0",
    "output": "$expectedTxOutputHex",
    "calls": [{
      "type": "STATICCALL",
      "from": "$nativeCallerAddress",
      "to": "${NativeTestContract.contractAddress}",
      "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
      "gasUsed": "0x64",
      "input": "0x$NATIVE_CONTRACT_RETRIEVE_ABI_ID",
      "output": "$expectedTxOutputHex"
    },
    {
        "type": "CALL",
        "from": "$nativeCallerAddress",
        "to": "${NativeTestContract.contractAddress}",
        "value": "0x0",
        "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
        "gasUsed": "0xc8",
        "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
        "output": "$currentCounterValueHex"
          }]
  }""",
      traceResult
    )


    ///////////////////////////////////////////////////////
    // Test 3: Solidity contract calls inc() method on Native Smart Contract, first using staticcall and then using call.
    // Staticcall should fail but the transaction is not reverted because the Solidity contract doesn't check the staticcall result.
    // call should works so the counter will be increment by 1.
    ///////////////////////////////////////////////////////

    val readwriteInput = BytesUtils.fromHexString(NATIVE_CALLER_STATIC_READWRITE_ABI_ID)

    returnData = transition(getMessage(nativeCallerAddress, data = readwriteInput))
    numericResult = org.web3j.utils.Numeric.toBigInt(returnData).intValueExact()
    currentCounterValue = currentCounterValue + 1
    expectedTxResult = currentCounterValue
    assertEquals("Wrong result", expectedTxResult, numericResult)

    // Check that the counter inside the native smart contract didn't change
    var result = transition(getMessage(nativeCallerAddress, data = retrieveInput))
    // verify that the result is still correct.
    assertEquals("Wrong result from retrieve", currentCounterValue, org.web3j.utils.Numeric.toBigInt(result).intValueExact())
    currentCounterValue = currentCounterValue + 1

    tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    returnDataTraced = transition(getMessage(nativeCallerAddress, data = readwriteInput))
    currentCounterValue = currentCounterValue + 1
    expectedTxResult = currentCounterValue
    // verify that the result is still correct.
    numericResultTraced = org.web3j.utils.Numeric.toBigInt(returnDataTraced).intValueExact()
    assertEquals("Wrong result from tracer call", expectedTxResult, numericResultTraced)

    traceResult = tracer.getResult.result

    expectedErrorMsg = WRITE_PROTECTION_ERR_MSG_FROM_NATIVE_CONTRACT
    expectedTxOutputHex = "0x" + BytesUtils.toHexString(org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(expectedTxResult), 32))
    val failedSubCallGasHexString = NativeTestContract.SUB_CALLS_GAS_HEX_STRING


    assertJsonEquals(
      s"""{
          "type": "CALL",
          "from": "$origin",
          "to": "$nativeCallerAddress",
          "gas": "$gasLimitHexString",
          "gasUsed": "0x68ab",
          "input": "0x${BytesUtils.toHexString(readwriteInput)}",
          "value": "0x0",
          "output": "$expectedTxOutputHex",
          "calls": [{
            "type": "STATICCALL",
            "from": "$nativeCallerAddress",
            "to": "${NativeTestContract.contractAddress}",
            "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
            "gasUsed": "$failedSubCallGasHexString",
            "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
            "error": "$expectedErrorMsg"
          },
          {
            "type": "CALL",
            "from": "$nativeCallerAddress",
            "to": "${NativeTestContract.contractAddress}",
            "value": "0x0",
            "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
            "gasUsed": "0xc8",
            "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
            "output": "$expectedTxOutputHex"
          }]
          }""",
      traceResult
    )

    ///////////////////////////////////////////////////////
    // Test 4: Solidity contract calls a method on a Native Smart Contract using the contract interface. The method
    // is declared in the contract interface as view but it actually is a readwrite function.
    // The transaction should fail.
    ///////////////////////////////////////////////////////

    val readwriteContractCallInput = BytesUtils.fromHexString(NATIVE_CALLER_STATIC_RW_CONTRACT_ABI_ID)

    Try(transition(getMessage(nativeCallerAddress, data = readwriteContractCallInput))) match {
      case Failure(ex: ExecutionRevertedException) => //OK
      case res => fail(s"Wrong result: $res")
    }

    // Check that the counter inside the native smart contract didn't change
    result = transition(getMessage(nativeCallerAddress, data = retrieveInput))
    // verify that the result is still correct.
    assertEquals("Wrong result from first retrieve", currentCounterValue, org.web3j.utils.Numeric.toBigInt(result).intValueExact())
    currentCounterValue = currentCounterValue + 1

    tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    Try(transition(getMessage(nativeCallerAddress, data = readwriteContractCallInput)))

    traceResult = tracer.getResult.result


    assertJsonEquals(
      s"""{
                "type": "CALL",
                "from": "$origin",
                "to": "$nativeCallerAddress",
                "gas": "$gasLimitHexString",
                "gasUsed": "0x63d9",
                "input": "0x${BytesUtils.toHexString(readwriteContractCallInput)}",
                "value": "0x0",
                "error" : "execution reverted",
                "calls": [{
                  "type": "STATICCALL",
                  "from": "$nativeCallerAddress",
                  "to": "${NativeTestContract.contractAddress}",
                  "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
                  "gasUsed": "$failedSubCallGasHexString",
                  "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
                  "error": "$expectedErrorMsg"
                }]
                }""",
      traceResult
    )

  }


  @Test
  def testNativeContractNestedCalls(): Unit = {
    ///////////////////////////////////////////////////////
    // In this test the native smart contracts executes a staticcall on a EVM base smart contract. The method called on
    // the EVM smart contract in turn calls the inc() method on the original native smart contract. Because of the
    // original staticcall, this last call fails. The EVM contract checks the result and reverts the transaction.
    ///////////////////////////////////////////////////////

    // deploy the NativeCaller contract (EVM based) and set the initial value
    val nativeCallerContractAddress = deploy(ContractInteropTestBase.nativeCallerContractCode)

    val input = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_NESTED_CALLS_TEST_SIG), nativeCallerContractAddress.toBytes)

    Try(transition(getMessage(NativeTestContract.contractAddress, data = input))) match {
      case Failure(ex: ExecutionRevertedException) => //OK
      case e => fail(s"Should have failed with ExecutionRevertedException: $e")
    }

    // put a tracer into the context
    val tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    Try(transition(getMessage(NativeTestContract.contractAddress, data = input)))

    val traceResult = tracer.getResult.result
    val invalidWriteErrorMsg = WRITE_PROTECTION_ERR_MSG_FROM_NATIVE_CONTRACT

    val failedSubCallGas = BigInteger.valueOf(25000)
    val failedSubCallGasHexString = "0x" + failedSubCallGas.toString(16)

    // check tracer output
    assertJsonEquals(
      s"""{
      "type": "CALL",
      "from": "$origin",
      "to": "${NativeTestContract.contractAddress}",
      "gas": "$gasLimitHexString",
      "gasUsed": "0x6e5e",
      "input": "0x${BytesUtils.toHexString(input)}",
      "error": "execution reverted with return data \\"0x\\"",
      "value": "0x0",
      "calls": [{
        "type": "STATICCALL",
        "from": "${NativeTestContract.contractAddress}",
        "to": "$nativeCallerContractAddress",
        "gas": "0x9c40",
        "gasUsed": "0x6e5e",
        "input": "0x$NATIVE_CALLER_NESTED_ABI_ID",
        "error": "execution reverted",
        "calls" : [ {
                "type" : "STATICCALL",
                "from" : "$nativeCallerContractAddress",
                "to" : "${NativeTestContract.contractAddress}",
                "gas" : "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
                "gasUsed" : "$failedSubCallGasHexString",
                "input" : "0x$NATIVE_CONTRACT_INC_ABI_ID",
                "error" : "$invalidWriteErrorMsg"
              } ]
        }]
    }""",
      traceResult
    )
  }


  @Test
  def testNativeContractCallingNativeContract(): Unit = {

    val destNativeContractAddress = NativeTestContract.contractAddress

    ///////////////////////////////////////////////////////
    // Test 1: Native contract executes a staticcall on a Native Smart Contract, calling a readonly function
    ///////////////////////////////////////////////////////
    var currentCounterValue = 0
    var expectedTxResult = currentCounterValue

    // call a native contract and pass along the native contract address
    val inputRetrieveRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READONLY_TEST_SIG), destNativeContractAddress.toBytes)
    var returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    currentCounterValue = currentCounterValue + 1
    var numericResult = org.web3j.utils.Numeric.toBigInt(returnData).intValueExact()

    // verify that the NativeTestContract was able to call the retrieve() function on the native contract
    assertEquals("unexpected result", expectedTxResult, numericResult)

    // put a tracer into the context
    var tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    expectedTxResult = currentCounterValue
    // repeat the call again
    var returnDataTraced = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    // verify that the result is still correct. Calculate new expected value because before we called inc()
    currentCounterValue = currentCounterValue + 1
    val expectedTxOutputHex = "0x" + BytesUtils.toHexString(org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(expectedTxResult), 32))
    val currentCounterValueHex = "0x" + BytesUtils.toHexString(org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(currentCounterValue), 32))

    var traceResult = tracer.getResult.result

    // check tracer output
    val initialGasHexString = gasLimitHexString
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "$initialGasHexString",
        "gasUsed": "0x12c",
        "input": "0x${BytesUtils.toHexString(inputRetrieveRequest)}",
        "value": "0x0",
        "output": "$expectedTxOutputHex",
        "calls": [{
          "type": "STATICCALL",
          "from": "${NativeTestContract.contractAddress}",
          "to": "$destNativeContractAddress",
          "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "gasUsed": "0x64",
          "input": "0x$NATIVE_CONTRACT_RETRIEVE_ABI_ID",
          "output": "$expectedTxOutputHex"
        }, {
          "type" : "CALL",
          "from" : "${NativeTestContract.contractAddress}",
          "to" : "$destNativeContractAddress",
          "value" : "0x0",
          "gas" : "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "gasUsed" : "0xc8",
          "input" : "0x$NATIVE_CONTRACT_INC_ABI_ID",
          "output": "$currentCounterValueHex"
        } ]
      }""",
      traceResult
    )


    ///////////////////////////////////////////////////////
    // Test 2: Native contract executes a staticcall on a native Contract, calling a function that modifies the state
    // An exception is expected.
    ///////////////////////////////////////////////////////

    val expectedErrorMsg = WRITE_PROTECTION_ERR_MSG_FROM_NATIVE_CONTRACT
    val inputIncRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.STATICCALL_READWRITE_TEST_SIG), destNativeContractAddress.toBytes)
    Try(transition(getMessage(NativeTestContract.contractAddress, data = inputIncRequest))) match {
      case Failure(ex: ExecutionFailedException) => assertEquals("Wrong failed exception", expectedErrorMsg, ex.getMessage)
      case _ => fail("Staticcall with readwrite method should have thrown a " + expectedErrorMsg + " exception")
    }

    //Check that the statedb wasn't changed.

    returnData = transition(getMessage(NativeTestContract.contractAddress, data = inputRetrieveRequest))
    expectedTxResult = currentCounterValue
    currentCounterValue = currentCounterValue + 1
    numericResult = org.web3j.utils.Numeric.toBigInt(returnData).intValueExact()

    // verify that the NativeTestContract was able to call the retrieve() function on the native contract
    assertEquals("unexpected result", expectedTxResult, numericResult)

    // put a tracer into the context
    tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

    // repeat the call again
    currentCounterValue = currentCounterValue + 1
    Try(transition(getMessage(NativeTestContract.contractAddress, data = inputIncRequest)))

    traceResult = tracer.getResult.result

    val failedSubCallGasHexString = NativeTestContract.SUB_CALLS_GAS_HEX_STRING

    // check tracer output
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "$initialGasHexString",
        "gasUsed": "$initialGasHexString",
        "input": "0x${BytesUtils.toHexString(inputIncRequest)}",
        "error": "$expectedErrorMsg",
        "value": "0x0",
        "calls": [{
          "type": "STATICCALL",
          "from": "${NativeTestContract.contractAddress}",
          "to": "$destNativeContractAddress",
          "gas": "${NativeTestContract.SUB_CALLS_GAS_HEX_STRING}",
          "gasUsed": "$failedSubCallGasHexString",
          "input": "0x$NATIVE_CONTRACT_INC_ABI_ID",
          "error": "$expectedErrorMsg"
        }]
      }""",
      traceResult
    )

    ///////////////////////////////////////////////////////
    // Test 3: Native contract deploys a smart contract
    ///////////////////////////////////////////////////////

    // call a native contract and pass along the native contract address
    val initialNonce = stateView.getNonce(NativeTestContract.contractAddress)
    val inputCreateRequest = Bytes.concat(BytesUtils.fromHexString(NativeTestContract.CREATE_TEST_SIG), ContractInteropTestBase.nativeCallerContractCode)
    transition(getMessage(NativeTestContract.contractAddress, data = inputCreateRequest))

    //Check that the smart contract was actually deployed
    val contractAddress = Secp256k1.generateContractAddress(NativeTestContract.contractAddress, initialNonce)
    val retrieveInput = BytesUtils.fromHexString(NATIVE_CALLER_STATIC_READONLY_ABI_ID)

    transition(getMessage(contractAddress, data = retrieveInput))

    //Check that the nonce of he native smart contract was incremented
    val currentNonce = stateView.getNonce(NativeTestContract.contractAddress)
    assertEquals("Wrong nonce after deploying a contract", initialNonce.add(1), currentNonce)

    // put a tracer into the context
    tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
    blockContext.setTracer(tracer)

     // repeat the call again
    returnDataTraced = transition(getMessage(NativeTestContract.contractAddress, data = inputCreateRequest))
    traceResult = tracer.getResult.result
    println("traceResult" + traceResult.toPrettyString)

    val deployedContractAddress = Secp256k1.generateContractAddress(NativeTestContract.contractAddress, currentNonce)
    val inputContractCodeHexString = s"0x${BytesUtils.toHexString(ContractInteropTestBase.nativeCallerContractCode)}"
    val outputContractCodeHexString = s"0x${BytesUtils.toHexString(stateView.getCode(deployedContractAddress))}"

    // check tracer output
    assertJsonEquals(
      s"""{
        "type": "CALL",
        "from": "$origin",
        "to": "${NativeTestContract.contractAddress}",
        "gas": "$initialGasHexString",
        "gasUsed": "0x4c493",
        "input": "0x${BytesUtils.toHexString(inputCreateRequest)}",
        "value": "0x0",
        "output": "0x",
        "calls": [{
          "type" : "CREATE",
          "from" : "${NativeTestContract.contractAddress}",
          "to" : "$deployedContractAddress",
          "value" : "0x0",
          "gas" : "${NativeTestContract.DEPLOY_GAS_HEX_STRING}",
          "gasUsed" : "0x4c493",
          "input" : "$inputContractCodeHexString",
          "output": "$outputContractCodeHexString"
        } ]
      }""",
      traceResult
    )


  }

}
