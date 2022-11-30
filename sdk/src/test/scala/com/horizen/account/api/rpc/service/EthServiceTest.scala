package com.horizen.account.api.rpc.service

import akka.actor.{ActorRef, ActorSystem}
import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.SidechainSettings
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.request.RpcRequest
import com.horizen.account.api.rpc.types.Quantity
import com.horizen.account.node.AccountNodeView
import com.horizen.fixtures.FieldElementFixture
import com.horizen.params.{NetworkParams, RegTestParams}
import org.junit.Assert.assertEquals
import org.junit.{Before, Test}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.utils.Numeric
import java.math.BigInteger
import scala.concurrent.duration.{FiniteDuration, SECONDS}

// TODO: we need full coverage of eth rpc service. Every method with both success and fail cases
class EthServiceTest extends JUnitSuite
  with MockitoSugar
{
  var nodeView: AccountNodeView = _
  var ethService: EthService = _
  var params: NetworkParams = _
  var transactionActorRef: ActorRef = _
  var NodeViewHolderRef: ActorRef = _
  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")

  private[this] def buildRpcRequest(method: String, params: Array[String], paramValues: Array[Any]): RpcRequest = {
    var json = s"""{"id":"1","jsonrpc":"2.0","method":"$method""""
    val mapper = new ObjectMapper
    if (params != null) {
      json = json + """, "params":{"""
      if (params.length != paramValues.length) throw new IllegalArgumentException("Number of params given must be equal to number of values given")
      for ((arg, value) <- params.dropRight(1) zip paramValues.dropRight(1)) json = json + s""""$arg":"$value", """
      json = json + s""""${params(params.size - 1)}":"${paramValues(paramValues.size - 1)}"}}"""
    } else {
      if (paramValues != null) {
        json = json + """, "params":["""
        for (value <- paramValues.dropRight(1)) json = json + s""""$value", """
        json = json + s""""${paramValues(paramValues.size - 1)}"]"""
      }
      json = json + "}"
    }
    new RpcRequest(mapper.readTree(json))
  }

  @Before
  def setUp(): Unit = {
    val settings = mock[SidechainSettings]
    params = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())
    transactionActorRef = mock[ActorRef]
    NodeViewHolderRef = mock[ActorRef]

    ethService = new EthService(NodeViewHolderRef, new FiniteDuration(10, SECONDS), params, transactionActorRef)
  }

  @Test
  def eth_chainId(): Unit = {
    // Test 1: Request execution calls correct function and returns value correctly
    val rpcRequest = buildRpcRequest("eth_chainId", null, null)
    assertEquals(Numeric.toHexStringWithPrefix(BigInteger.valueOf(params.chainId)), ethService.execute(rpcRequest).asInstanceOf[Quantity].value)
  }

  @Test
  def invalidJsonRpcData(): Unit = {
    val mapper = new ObjectMapper

    // Test 1: Try to read json request with missing data
    val json = """{"id":"1", "jsonrpc":"2.0", "params":[]}}"""
    val request = mapper.readTree(json)
    assertThrows[RpcException] {
      new RpcRequest(request)
    }

    // Test 2: Parameters are of wrong type
    var rpcRequest = buildRpcRequest("eth_estimateGas", Array("tx", "tx2"), Array("test", "test2"))
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }

    // Test 3: Wrong number of parameters
    rpcRequest = buildRpcRequest("eth_estimateGas", null, Array(5, 10, 20))
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }

    // Test 44 Trigger IllegalArgumentException rpc call
    rpcRequest = buildRpcRequest("eth_estimateGas", null, Array(-1))
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }
  }
}
