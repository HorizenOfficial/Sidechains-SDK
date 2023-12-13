package io.horizen.account.state

import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.fork.Version1_3_0Fork
import io.horizen.evm.{Address, Hash}
import io.horizen.fork.{ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch, SimpleForkConfigurator}
import io.horizen.utils.{BytesUtils, Pair}
import org.junit.Assert.{assertFalse, assertNotNull, assertTrue, fail}
import org.junit.{Before, Test}
import org.scalatest.Assertions.{assertThrows, intercept}
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.util
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.{Failure, Success, Try}

class EvmMessageProcessorIntegrationTest extends EvmMessageProcessorTestBase {

  // compiled EVM byte-code of the Storage contract,
  // source: libevm/native/test/Storage.sol
  val deployCodeParis: Array[Byte] = BytesUtils.fromHexString(
    "608060405234801561001057600080fd5b5060405161023638038061023683398101604081905261002f916100f6565b6000819055604051339060008051602061021683398151915290610073906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03166000805160206102168339815191526040516100bf906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b9994690600090a15061010f565b60006020828403121561010857600080fd5b5051919050565b60f98061011d6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea264697066735822122080d9db531d29b1bd6b4e16762726b70e2a94f0b40ee4e2ab534d9b879cf1c25664736f6c634300080f00330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0"
  )

  val deployCodeShanghai: Array[Byte] = BytesUtils.fromHexString(
    "608060405234801561000f575f80fd5b5060405161022338038061022383398101604081905261002e916100f1565b5f81905560405133905f8051602061020383398151915290610070906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03165f805160206102038339815191526040516100bb906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b99946905f90a150610108565b5f60208284031215610101575f80fd5b5051919050565b60ef806101145f395ff3fe608060405260043610602f575f3560e01c80632e64cec1146033578063371303c01460525780636057361d146065575b5f80fd5b348015603d575f80fd5b505f5460405190815260200160405180910390f35b348015605c575f80fd5b5060636074565b005b60636070366004607f565b5f55565b5f5460709060016095565b5f60208284031215608e575f80fd5b5035919050565b8082018082111560b357634e487b7160e01b5f52601160045260245ffd5b9291505056fea2646970667358221220cff9a74160cdc242b2991e2bcb39a3b2f59afe7aa8d55cc05bc5a5653a1512d164736f6c634300081700330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0"
  )

  val V1_3_MOCK_FORK_POINT: Int = 100

  @Before
  def setup(): Unit = {
    val forkConfig = new SimpleForkConfigurator() {

      override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
        Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
          new Pair(SidechainForkConsensusEpoch(V1_3_MOCK_FORK_POINT, V1_3_MOCK_FORK_POINT, V1_3_MOCK_FORK_POINT), Version1_3_0Fork(true)),
        ).asJava
    }
    ForkManagerUtil.initializeForkManager(forkConfig, "regtest")
  }


  @Test
  def testCanProcess(): Unit = {
    usingView { stateView =>
      // prepare contract account
      stateView.addAccount(contractAddress, Keccak256.hash("testcode"))
      // prepare eoa account that is not empty
      stateView.addBalance(eoaAddress, BigInteger.TEN)

      val processor = new EvmMessageProcessor()
      assertTrue(
        "should process smart contract deployment",
        TestContext.canProcess(processor, getMessage(null), stateView, 0)
      )
      assertTrue(
        "should process calls to existing smart contracts",
        TestContext.canProcess(processor, getMessage(contractAddress), stateView, 0)
      )
      assertFalse(
        "should not process EOA to EOA transfer (empty account)",
        TestContext.canProcess(processor, getMessage(emptyAddress), stateView, 0)
      )
      assertFalse(
        "should not process EOA to EOA transfer (non-empty account)",
        TestContext.canProcess(processor, getMessage(eoaAddress), stateView, 0)
      )
    }
  }

  @Test
  def testProcessParis(): Unit = {

    val initialBalance = new BigInteger("2000000000000")
    val evmMessageProcessor = new EvmMessageProcessor()

    usingView(evmMessageProcessor) { stateView =>
      stateView.addBalance(origin, initialBalance)

      // smart contract constructor has one argument (256-bit uint)
      val initialValue = Array.fill(32) {
        0.toByte
      }
      initialValue(initialValue.length - 1) = 42
      // add constructor arguments to the end of the deployment code
      var msg = getMessage(null, data = deployCodeParis ++ initialValue)
      val result = assertGas(76990, msg, stateView, evmMessageProcessor, defaultBlockContext)
      assertNotNull("result should not be null", result)

      //Try with a smart contract compiled for Shanghai
      msg = getMessage(null, data = deployCodeShanghai ++ initialValue)
      val ex = intercept[ExecutionFailedException] {
        withGas(TestContext.process(evmMessageProcessor, msg, stateView, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("PUSH0"))

    }
  }

  @Test
  def testProcessShanghai(): Unit = {

    val blockContext =
      new BlockContext(Address.ZERO, 0, 0, DefaultGasFeeFork.blockGasLimit, 0, V1_3_MOCK_FORK_POINT, 0, 1, MockedHistoryBlockHashProvider, Hash.ZERO)


    val initialBalance = new BigInteger("2000000000000")
    val evmMessageProcessor = new EvmMessageProcessor()

    usingView(evmMessageProcessor) { stateView =>
      stateView.addBalance(origin, initialBalance)

      // smart contract constructor has one argument (256-bit uint)
      val initialValue = Array.fill(32) {
        0.toByte
      }
      initialValue(initialValue.length - 1) = 42
      // add constructor arguments to the end of the deployment code
      var msg = getMessage(null, data = deployCodeShanghai ++ initialValue)
      val result = assertGas(74983, msg, stateView, evmMessageProcessor, blockContext)
      assertNotNull("result should not be null", result)

      //Try with a smart contract compiled for Paris, it should still work
      msg = getMessage(null, data = deployCodeParis ++ initialValue)
      Try(assertGas(76990, msg, stateView, evmMessageProcessor, blockContext)) match {
        case Success(_) =>
        case Failure(e) => fail(e.getMessage)
      }

    }
  }

}
