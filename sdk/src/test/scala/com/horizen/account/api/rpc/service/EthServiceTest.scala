package com.horizen.account.api.rpc.service

import akka.actor.{ActorRef, ActorSystem}
import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.SidechainSettings
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.request.RpcRequest
import com.horizen.account.api.rpc.types.Quantity
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.node.AccountNodeView
import com.horizen.account.state.{AccountState, AccountStateView}
import com.horizen.account.wallet.AccountWallet
import com.horizen.fixtures.{FieldElementFixture, MockedSidechainNodeViewHolderFixture}
import com.horizen.params.{NetworkParams, RegTestParams}
import org.junit.Assert.assertEquals
import org.junit.{Before, Test}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.utils.Numeric
import scorex.core.NodeViewHolder.CurrentView

import java.math.BigInteger
// TODO: we need full coverage of eth rpc service. Every method with both success and fail cases
class EthServiceTest extends JUnitSuite
  with MockitoSugar
  with MockedSidechainNodeViewHolderFixture {
  var nodeView: AccountNodeView = _
  var ethService: EthService = _
  var params: NetworkParams = _
  var transactionActorRef: ActorRef = _

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")

  @Before
  def setUp(): Unit = {
    val settings = mock[SidechainSettings]
    params = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())
    transactionActorRef = mock[ActorRef]
    val nodeView = mock[CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]]
    val stateView = mock[AccountStateView]
    ethService = new EthService(stateView, nodeView, params, settings, transactionActorRef)
  }

  @Test
  def testEthService(): Unit = {
    val mapper = new ObjectMapper

    // Test 1: Parameters are of wrong type
    var json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_estimateGas\", \"params\":{\"tx\":\"test\", \"tx2\":\"test2\"}}"
    var request = mapper.readTree(json)
    var rpcRequest = new RpcRequest(request)
    assertThrows[RpcException] { ethService.execute(rpcRequest) }

    // Test 1: Parameters are of wrong type
    json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_estimateGas\", \"params\":{\"tx\":\"test\", \"tx2\":\"test2\"}}"
    request = mapper.readTree(json)
    rpcRequest = new RpcRequest(request)
    assertThrows[RpcException] { ethService.execute(rpcRequest) }

    // Test 2: Wrong number of parameters
    json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_estimateGas\", \"params\":[5, 10, 20]}}"
    request = mapper.readTree(json)
    rpcRequest = new RpcRequest(request)
    assertThrows[RpcException] { ethService.execute(rpcRequest) }

    // Test 3: Request execution calls correct function and returns value correctly
    json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\", \"params\":[]}}"
    request = mapper.readTree(json)
    rpcRequest = new RpcRequest(request)
    assertEquals(Numeric.toHexStringWithPrefix(BigInteger.valueOf(params.chainId)), ethService.execute(rpcRequest).asInstanceOf[Quantity].getValue)

    // Test 4: Access list is not supported by us (EIP-1559)
    // https://etherscan.io/getRawTx?tx=0xde78fe4a45109823845dc47c9030aac4c3efd3e5c540e229984d6f7b5eb4ec83
    json = "{\"id\":\"1\", \"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\", \"params\":[\"0x02f9040c0183012ec786023199fa3df88602e59652e99b8303851d9400000000003b3cc22af3ae1eac0440bcee416b4080b8530100d5a0afa68dd8cb83097765263adad881af6eed479c4a33ab293dce330b92aa52bc2a7cd3816edaa75f890b00000000000000000000000000000000000000000000007eb2e82c51126a5dde0a2e2a52f701f90344f9024994a68dd8cb83097765263adad881af6eed479c4a33f90231a00000000000000000000000000000000000000000000000000000000000000004a0745448ebd86f892e3973b919a6686b32d8505f8eb2e02df5a36797f187adb881a00000000000000000000000000000000000000000000000000000000000000003a00000000000000000000000000000000000000000000000000000000000000011a0a580422a537c1b63e41b8febf02c6c28bef8713a2a44af985cc8d4c2b24b1c86a091e3d6ffd1390da3bfbc0e0875515e89982841b064fcda9b67cffc63d8082ab6a091e3d6ffd1390da3bfbc0e0875515e89982841b064fcda9b67cffc63d8082ab8a0bf9ee777cf4683df01da9dfd7aeab60490278463b1d516455d67d23c750f96dca00000000000000000000000000000000000000000000000000000000000000012a0000000000000000000000000000000000000000000000000000000000000000fa00000000000000000000000000000000000000000000000000000000000000010a0a580422a537c1b63e41b8febf02c6c28bef8713a2a44af985cc8d4c2b24b1c88a0bd9bbcf6ef1c613b05ca02fcfe3d4505eb1c5d375083cb127bda8b8afcd050fba06306683371f43cb3203ee553ce8ac90eb82e4721cc5335d281e1e556d3edcdbca00000000000000000000000000000000000000000000000000000000000000013a0bd9bbcf6ef1c613b05ca02fcfe3d4505eb1c5d375083cb127bda8b8afcd050f9a00000000000000000000000000000000000000000000000000000000000000014f89b94ab293dce330b92aa52bc2a7cd3816edaa75f890bf884a0000000000000000000000000000000000000000000000000000000000000000ca00000000000000000000000000000000000000000000000000000000000000008a00000000000000000000000000000000000000000000000000000000000000006a00000000000000000000000000000000000000000000000000000000000000007f85994c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2f842a051c9df7cdd01b5cb5fb293792b1e67ec1ac1048ae7e4c7cf6cf46883589dfbd4a03c679e5fc421e825187f885e3dcd7f4493f886ceeb4930450588e35818a32b9c80a020d7f34682e1c2834fcb0838e08be184ea6eba5189eda34c9a7561a209f7ed04a07c63c158f32d26630a9732d7553cfc5b16cff01f0a72c41842da693821ccdfcb\"]}}"
    request = mapper.readTree(json)
    rpcRequest = new RpcRequest(request)
    assertThrows[RpcException] { ethService.execute(rpcRequest) }
  }
}
