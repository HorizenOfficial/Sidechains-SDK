package io.horizen.account.state

import io.horizen.account.utils.{FeeUtils, Secp256k1}
import io.horizen.evm.{Address, Hash, TraceOptions, Tracer}
import io.horizen.utils.BytesUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger

class ContractInteropTest extends EvmMessageProcessorTestBase {

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

  // compiled EVM byte-code of the Storage contract,
  // source: libevm/contracts/Storage.sol
  private val deployCode: Array[Byte] = BytesUtils.fromHexString(
    "608060405234801561001057600080fd5b5060405161023638038061023683398101604081905261002f916100f6565b6000819055604051339060008051602061021683398151915290610073906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03166000805160206102168339815191526040516100bf906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b9994690600090a15061010f565b60006020828403121561010857600080fd5b5051919050565b60f98061011d6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea264697066735822122080d9db531d29b1bd6b4e16762726b70e2a94f0b40ee4e2ab534d9b879cf1c25664736f6c634300080f00330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0"
  )

  private val blockContext =
    new BlockContext(Address.ZERO, 0, FeeUtils.INITIAL_BASE_FEE, 100000, 1, 1, 1, 1234, null, Hash.ZERO)

  private def transition(view: AccountStateView, processors: Seq[MessageProcessor], msg: Message) = {
    val transition = new StateTransition(view, processors, new GasPool(10000000), blockContext, msg)
    transition.execute(Invocation.fromMessage(msg, new GasPool(100000)))
  }

  @Test
  def testProcess(): Unit = {
    val initialBalance = new BigInteger("2000000000000")
    val evmMessageProcessor = new EvmMessageProcessor()
    val processors = Seq(NativeTestContract, evmMessageProcessor)

    usingView(processors) { stateView =>
      stateView.addBalance(origin, initialBalance)

      // smart contract constructor has one argument (256-bit uint)
      val initialValue = Array.fill(32) { 0.toByte }
      initialValue(0) = 64
      initialValue(initialValue.length - 1) = 42

      // deploy the Storage contract (EVM based)
      transition(stateView, processors, getMessage(null, data = deployCode ++ initialValue))
      // get deployed contract address
      val contractAddress = Secp256k1.generateContractAddress(origin, 0)
      // call a native contract and pass along the Storage contract address
      val returnData = transition(
        stateView,
        processors,
        getMessage(NativeTestContract.contractAddress, data = contractAddress.toBytes)
      )
      // verify that the NativeTestContract was able to call the retrieve() function on the EVM based contract
      assertArrayEquals("unexpected result", initialValue, returnData)

      // put a tracer into the context
      val tracer = new Tracer(new TraceOptions(false, false, false, false, "callTracer", null))
      blockContext.setTracer(tracer)

      // repeat the last call again
      val returnDataTraced = transition(
        stateView,
        processors,
        getMessage(NativeTestContract.contractAddress, data = contractAddress.toBytes)
      )
      // verify that the result is still correct
      assertArrayEquals("unexpected result", initialValue, returnDataTraced)

      val traceResult = tracer.getResult.result
//      println("traceResult" + traceResult.toPrettyString)

      assertJsonEquals(
        s"""{
          "type": "CALL",
          "from": "$origin",
          "to": "${NativeTestContract.contractAddress}",
          "gas": "0x186a0",
          "gasUsed": "0xf6",
          "input": "$contractAddress",
          "value": "0x0",
          "output": "0x400000000000000000000000000000000000000000000000000000000000002a",
          "calls": [{
            "type": "STATICCALL",
            "from": "${NativeTestContract.contractAddress}",
            "to": "$contractAddress",
            "gas": "0x2710",
            "gasUsed": "0xf6",
            "input": "0x2e64cec1",
            "output": "0x400000000000000000000000000000000000000000000000000000000000002a"
          }]
        }""",
        traceResult
      )
    }
  }
}
