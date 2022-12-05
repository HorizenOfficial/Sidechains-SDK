package com.horizen.account.api.rpc.service

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.SidechainTypes
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.request.RpcRequest
import com.horizen.account.api.rpc.types.Quantity
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.{EthereumReceipt, ReceiptFixture}
import com.horizen.account.state.AccountState
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.transaction.EthereumTransaction.EthereumTransactionType
import com.horizen.account.utils.{AccountMockDataHelper, EthereumTransactionEncoder, FeeUtils}
import com.horizen.account.wallet.AccountWallet
import com.horizen.fixtures.FieldElementFixture
import com.horizen.params.RegTestParams
import com.horizen.utils.BytesUtils
import org.junit.Assert.assertEquals
import org.junit.{Before, Test}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import scorex.util.bytesToId
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import java.math.BigInteger
import java.util.Optional
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.concurrent.duration.{FiniteDuration, SECONDS}

// TODO: we need full coverage of eth rpc service. Every method with both success and fail cases
class EthServiceTest extends JUnitSuite with MockitoSugar with ReceiptFixture with TableDrivenPropertyChecks {
  val invalidCasesTxHash =
    Table(
      "Test false length and missing 0x prefix",
      "123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba",
      "0x1234",
      "0x123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba1"
    )
  var ethService: EthService = _

  @Before
  def setUp(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
    val networkParams = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())
    val transactionActorRef = mock[ActorRef]
    val receipt: EthereumReceipt =
      createTestEthereumReceipt(
        EthereumTransactionType.DynamicFeeTxType.ordinal(),
        transactionIndex = 0,
        blockNumber = 1,
        logAddress = BytesUtils.fromHexString("d2a538a476aad6ecd245099df9297df6a129c2c5"),
        txHash = BytesUtils.fromHexString("386cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba")
      )
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val goodSignature = new SignatureSecp256k1(
      BytesUtils.fromHexString("1c"),
      BytesUtils.fromHexString("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023"),
      BytesUtils.fromHexString("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d")
    )
    val txs = new ListBuffer[SidechainTypes#SCAT]()
    val txEip1559 = new EthereumTransaction(
      networkParams.chainId,
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )
    val encodedMessage = EthereumTransactionEncoder.encodeEip1559AsRlpValues(txEip1559, txEip1559.isSigned)
    val txHash = BytesUtils.toHexString(Hash.sha3(encodedMessage, 0, encodedMessage.length))

    txs.append(txEip1559.asInstanceOf[SidechainTypes#SCAT])
    val mockedBlock: AccountBlock = mockHelper.getMockedBlock(
      FeeUtils.INITIAL_BASE_FEE,
      0,
      FeeUtils.GAS_LIMIT,
      bytesToId(Numeric.hexStringToByteArray("456")),
      bytesToId(Numeric.hexStringToByteArray("123")),
      txs
    )
    val mockedHistory: AccountHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)
    val mockedState: AccountState = mockHelper.getMockedState(receipt, Numeric.hexStringToByteArray(txHash))

    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
      override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
        msg match {
          case m: GetDataFromCurrentView[
                AccountHistory,
                AccountState,
                AccountWallet,
                AccountMemoryPool,
                _
              ] @unchecked =>
            m match {
              case GetDataFromCurrentView(f) =>
                sender ! f(CurrentView(mockedHistory, mockedState, mock[AccountWallet], mock[AccountMemoryPool]))
            }
        }
        TestActor.KeepRunning
      }
    })
    val nodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref
    ethService = new EthService(nodeViewHolderRef, new FiniteDuration(10, SECONDS), networkParams, transactionActorRef)
  }

  @Test
  def eth_chainId(): Unit = {
    assertEquals(
      Numeric.toHexStringWithPrefix(BigInteger.valueOf(1111111)),
      ethService.execute(getRpcRequest()).asInstanceOf[Quantity].value
    )
  }

  @Test
  def eth_blockNumber(): Unit = {
    assertEquals("0x1", ethService.execute(getRpcRequest()).asInstanceOf[Quantity].value)
  }

  @Test
  def net_version(): Unit = {
    assertEquals(String.valueOf(1111111), ethService.execute(getRpcRequest()))
  }

  /**
   * Helper for constructing a rpc request takes up to two Arrays containing the parameter names, values and the method
   * name
   * @param params
   *   default is null
   * @param paramValues
   *   default is null
   * @param method
   *   RPC method, default is calling method name
   * @return
   *   RpcRequest instance
   */
  private[this] def getRpcRequest(
      params: Array[String] = null,
      paramValues: Array[Any] = null,
      method: String = Thread.currentThread.getStackTrace()(2).getMethodName
  ): RpcRequest = {
    var json: String = s"""{"id":"1","jsonrpc":"2.0","method":"$method", "params":"""
    if (params == null && paramValues == null) {
      json = json + "[]}"
    } else if (params == null && paramValues != null) {
      json = json + paramValues
        .collect {
          case stringValue: String => s""""$stringValue""""
          case value => value
        }
        .mkString("[", ", ", "]}")
    } else {
      if (params.length != paramValues.length)
        throw new IllegalArgumentException("Number of parameters given must be equal to number of values given")
      json = json + (params zip paramValues)
        .map(param => s""""${param._1}":"${param._2}"""")
        .mkString("{", ", ", "}}")
    }
    new RpcRequest((new ObjectMapper).readTree(json))
  }

  @Test
  def eth_getTransactionByHash(): Unit = {
    val method = "eth_getTransactionByHash"
    val mapper = new ObjectMapper()

    val validCases = Table(
      ("Parameter value", "Expected output"),
      (
        "0x386cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba",
        s"""{"blockHash":"0xd60ee5d9b1a312631632d0ab8816ca64259093d8ab0b4d29f35db6a6151b0f8d","blockNumber":"0x1","from":"0x1a31b2dfda5b574428338c2316a087d572da1c97","hash":"0x386cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba","transactionIndex":"0x0","type":"0x2","nonce":"0x0","to":null,"gas":"0x1","value":"0x1","input":"0x","maxPriorityFeePerGas":"0x1","maxFeePerGas":"0x1","gasPrice":"0x1","accessList":null,"chainId":"0x10f447","v":"0x1c","r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023","s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d"}"""
      ),
      ("0x123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba", "null")
    )

    forAll(validCases) { (input, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(ethService.execute(getRpcRequest(paramValues = Array(input), method = method)))
      )
    }

    forAll(invalidCasesTxHash) { input =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(input), method = method))
      }
    }
  }

  @Test
  def eth_getTransactionReceipt(): Unit = {
    val method = "eth_getTransactionReceipt"
    val mapper = new ObjectMapper()

    val validCases = Table(
      ("Parameter value", "Expected output"),
      (
        "0x386cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba",
        s"""{"type":"0x02","transactionHash":"0x386cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba","transactionIndex":"0x0","blockHash":"0xd60ee5d9b1a312631632d0ab8816ca64259093d8ab0b4d29f35db6a6151b0f8d","blockNumber":"0x1","from":"0x1a31b2dfda5b574428338c2316a087d572da1c97","to":null,"cumulativeGasUsed":"0x3e8","gasUsed":"0x12d687","contractAddress":"0x1122334455667788990011223344556677889900","logs":[{"address":"0xd2a538a476aad6ecd245099df9297df6a129c2c5","topics":["0x0000000000000000000000000000000000000000000000000000000000000000","0x1111111111111111111111111111111111111111111111111111111111111111","0x2222222222222222222222222222222222222222222222222222222222222222","0x3333333333333333333333333333333333333333333333333333333333333333"],"data":"0xaabbccddeeff","blockHash":"0xd60ee5d9b1a312631632d0ab8816ca64259093d8ab0b4d29f35db6a6151b0f8d","blockNumber":"0x1","transactionHash":"0x386cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba","transactionIndex":"0x0","logIndex":"0x0","removed":false},{"address":"0xd2a538a476aad6ecd245099df9297df6a129c2c5","topics":["0x0000000000000000000000000000000000000000000000000000000000000000","0x1111111111111111111111111111111111111111111111111111111111111111","0x2222222222222222222222222222222222222222222222222222222222222222","0x3333333333333333333333333333333333333333333333333333333333333333"],"data":"0xaabbccddeeff","blockHash":"0xd60ee5d9b1a312631632d0ab8816ca64259093d8ab0b4d29f35db6a6151b0f8d","blockNumber":"0x1","transactionHash":"0x386cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba","transactionIndex":"0x0","logIndex":"0x1","removed":false}],"logsBloom":"0x00000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000020000000010000080000000000000000000020000002000000000000800000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000002000000000000002000000000800000000000000000000000000008000000000000020000000000000000000000000000000000000000000000000000000000000000000","status":"0x1","effectiveGasPrice":"0x1"}"""
      ),
      ("0x123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba", "null")
    )

    forAll(validCases) { (input, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(ethService.execute(getRpcRequest(paramValues = Array(input), method = method)))
      )
    }

    forAll(invalidCasesTxHash) { input =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(input), method = method))
      }
    }
  }

  @Test
  def invalidJsonRpcData(): Unit = {
    // Test 1: Try to read json request with missing data
    val json = """{"id":"1", "jsonrpc":"2.0", "params":[]}}"""
    val request = (new ObjectMapper).readTree(json)
    assertThrows[RpcException] {
      new RpcRequest(request)
    }

    // Test 2: Parameters are of wrong type
    var rpcRequest = getRpcRequest(Array("tx", "tx2"), Array("test", "test2"), "eth_estimateGas")
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }

    // Test 3: Wrong number of parameters
    rpcRequest = getRpcRequest(paramValues = Array(5, 10, 20), method = "eth_estimateGas")
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }

    // Test 44 Trigger IllegalArgumentException rpc call
    rpcRequest = getRpcRequest(paramValues = Array(-1), method = "eth_estimateGas")
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }
  }
}
