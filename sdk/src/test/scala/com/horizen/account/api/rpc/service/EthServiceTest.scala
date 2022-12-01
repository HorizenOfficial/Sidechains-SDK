package com.horizen.account.api.rpc.service

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.request.RpcRequest
import com.horizen.account.api.rpc.types.Quantity
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.node.AccountNodeView
import com.horizen.account.state.AccountState
import com.horizen.account.utils.{AccountMockDataHelper, FeeUtils}
import com.horizen.account.wallet.AccountWallet
import com.horizen.fixtures.FieldElementFixture
import com.horizen.params.{NetworkParams, RegTestParams}
import org.junit.Assert.assertEquals
import org.junit.{Before, Test}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.utils.Numeric
import scorex.util.bytesToId
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.concurrent.duration.{FiniteDuration, SECONDS}

// TODO: we need full coverage of eth rpc service. Every method with both success and fail cases
class EthServiceTest extends JUnitSuite with MockitoSugar {
  var nodeView: AccountNodeView = _
  var ethService: EthService = _
  var params: NetworkParams = _
  var transactionActorRef: ActorRef = _
  var NodeViewHolderRef: ActorRef = _
  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")

  @Before
  def setUp(): Unit = {
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(false)
    val mockedBlock: AccountBlock = mockHelper.getMockedBlock(
      FeeUtils.INITIAL_BASE_FEE,
      0,
      FeeUtils.GAS_LIMIT,
      bytesToId(Numeric.hexStringToByteArray("456")),
      bytesToId(Numeric.hexStringToByteArray("123"))
    )
    val mockedHistory: AccountHistory = mockHelper.getMockedAccountHistory(Option.apply(mockedBlock).asJava)

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
                sender ! f(CurrentView(mockedHistory, mock[AccountState], mock[AccountWallet], mock[AccountMemoryPool]))
            }
        }
        TestActor.KeepRunning
      }
    })

    val NodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref
    params = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())
    transactionActorRef = mock[ActorRef]

    ethService = new EthService(NodeViewHolderRef, new FiniteDuration(10, SECONDS), params, transactionActorRef)
  }

  @Test
  def eth_chainId(): Unit = {
    val rpcRequest = getRpcRequest()
    assertEquals(
      Numeric.toHexStringWithPrefix(BigInteger.valueOf(params.chainId)),
      ethService.execute(rpcRequest).asInstanceOf[Quantity].value
    )
  }

  @Test
  def eth_blockNumber(): Unit = {
    val rpcRequest = getRpcRequest()
    assertEquals("0x1", ethService.execute(rpcRequest).asInstanceOf[Quantity].value)
  }

  @Test
  def net_version(): Unit = {
    val rpcRequest = getRpcRequest()
    assertEquals(String.valueOf(params.chainId), ethService.execute(rpcRequest))
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

  /**
   * Helper for constructing a rpc request takes up to two Arrays containing the parameter names, values and the method
   * name
   * @param params
   *   default is null
   * @param paramValues
   *   default is null
   * @param method
   *   RPC method, default is calling function name
   * @return
   *   RpcRequest instance
   */
  private[this] def getRpcRequest(
      params: Array[String] = null,
      paramValues: Array[Any] = null,
      method: String = Thread.currentThread.getStackTrace()(2).getMethodName
  ): RpcRequest = {
    var json: String = s"""{"id":"1","jsonrpc":"2.0","method":"$method""""
    if (params == null && paramValues == null) {
      json = json + """, "params":[]}"""
    } else if (params == null && paramValues != null) {
      json = json + paramValues.mkString(""", "params":[""", ", ", "]}")
    } else {
      if (params.length != paramValues.length)
        throw new IllegalArgumentException("Number of parameters given must be equal to number of values given")
      json = json + (params zip paramValues)
        .map(param => s""""${param._1}":"${param._2}"""")
        .mkString(""", "params":{""", ", ", "}}")
    }
    new RpcRequest((new ObjectMapper).readTree(json))
  }
}
