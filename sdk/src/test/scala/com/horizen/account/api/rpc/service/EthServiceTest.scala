package com.horizen.account.api.rpc.service

import akka.actor.{ActorRef, ActorSystem}
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
import com.horizen.account.secret.PrivateKeySecp256k1Creator
import com.horizen.account.state.AccountState
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.transaction.EthereumTransaction.EthereumTransactionType
import com.horizen.account.utils.{AccountMockDataHelper, EthereumTransactionEncoder, FeeUtils}
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.SidechainTransactionActorRef
import com.horizen.evm.utils.Hash
import com.horizen.fixtures.FieldElementFixture
import com.horizen.params.RegTestParams
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.utils.Numeric
import scorex.crypto.hash.Keccak256
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.{GetDataFromCurrentView, LocallyGeneratedTransaction}
import sparkz.core.bytesToId
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SuccessfulTransaction

import java.math.BigInteger
import java.util.Optional
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class EthServiceTest extends JUnitSuite with MockitoSugar with ReceiptFixture with TableDrivenPropertyChecks {
  private val mapper = new ObjectMapper()
  private val invalidCasesTxHash =
    Table(
      "Test false length and missing 0x prefix",
      "123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba",
      "0x1234",
      "0x123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba1"
    )
  private val blockViewOutput =
    """{"number":"0x1","hash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","parentHash":"0xnull","nonce":"0x0000000000000000","sha3Uncles":"0x","logsBloom":"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000","transactionsRoot":"0x1234567891011121314112345678910111213141","stateRoot":"0x1234567891011121314112345678910111213141","receiptsRoot":"0x1234567891011121314112345678910111213141","miner":"0x1234567891011121314112345678910111213141","mixHash":"0x","extraData":"0x","size":"0x100","gasLimit":"0x1c9c380","gasUsed":"0x3b9aca01","timestamp":"0x3b9aca00","transactions":[{"blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","from":"0xd123b689dad8ed6b99f8bd55eed64ab357e6a8d1","hash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","type":"0x2","nonce":"0x0","to":null,"gas":"0x1","value":"0x1","input":"0x","maxPriorityFeePerGas":"0x1","maxFeePerGas":"0x3b9aca64","gasPrice":"0x342770c1","accessList":null,"chainId":"0x10f447","v":"0x1c","r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023","s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d"}],"author":"0x1234567891011121314112345678910111213141","difficulty":"0x0","totalDifficulty":"0x0","uncles":[],"sealFields":[],"baseFeePerGas":"0x342770c0"}"""
  private val blockViewOutputTxHashes =
    """{"number":"0x1","hash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","parentHash":"0xnull","nonce":"0x0000000000000000","sha3Uncles":"0x","logsBloom":"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000","transactionsRoot":"0x1234567891011121314112345678910111213141","stateRoot":"0x1234567891011121314112345678910111213141","receiptsRoot":"0x1234567891011121314112345678910111213141","miner":"0x1234567891011121314112345678910111213141","mixHash":"0x","extraData":"0x","size":"0x100","gasLimit":"0x1c9c380","gasUsed":"0x3b9aca01","timestamp":"0x3b9aca00","transactions":["0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253"],"author":"0x1234567891011121314112345678910111213141","difficulty":"0x0","totalDifficulty":"0x0","uncles":[],"sealFields":[],"baseFeePerGas":"0x342770c0"}"""
  private val txViewOutput =
    """{"blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","from":"0xd123b689dad8ed6b99f8bd55eed64ab357e6a8d1","hash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","type":"0x2","nonce":"0x0","to":null,"gas":"0x1","value":"0x1","input":"0x","maxPriorityFeePerGas":"0x1","maxFeePerGas":"0x3b9aca64","gasPrice":"0x342770c1","accessList":null,"chainId":"0x10f447","v":"0x1c","r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023","s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d"}"""
  private val txJsonNoSecret =
    s"""{"from": "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4", "to": "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4", "gas": "0x76c0", "gasPrice": "0x9184e72a000", "value": "0x9184e72a", "data": "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675"}"""

  val txPoolStatusOutput = """{"pending":3,"queued":1}"""
  val txPoolContentOutput = """{"pending":{"0x15532e34426cd5c37371ff455a5ba07501c0f522":{"16":{"blockHash":"0x0000000000000000000000000000000000000000000000000000000000000000","blockNumber":null,"from":"0x5b19616a7277d58ea1040a5f44c54d41853ccde3","gas":"0xec0564","gasPrice":"0x3b9aca64","hash":"0x68366d9034c74adb5d6e584116bc20838aedc15218a1d49eea43e04f31072044","input":"0xbd54d1f34e34a90f7dc5efe0b3d65fa4","nonce":"0x10","to":"0x15532e34426cd5c37371ff455a5ba07501c0f522","transactionIndex":null,"value":"0xe4e1c0"},"24":{"blockHash":"0x0000000000000000000000000000000000000000000000000000000000000000","blockNumber":null,"from":"0x081d8a5b696ec5dfce641568e6665b6be2410ce2","gas":"0xec0564","gasPrice":"0x3b9aca64","hash":"0xc8a7edb4bd87f30671879a1b12767591a4d73fc12153885ec96e556a97fc5b37","input":"0x8c64fe48688ab096dfb6ac2eeefcf213","nonce":"0x18","to":"0x15532e34426cd5c37371ff455a5ba07501c0f522","transactionIndex":null,"value":"0x493e00"}},"0xb039865dbea73df08e23f185847bab8e6a44108d":{"32":{"blockHash":"0x0000000000000000000000000000000000000000000000000000000000000000","blockNumber":null,"from":"0xb3151940f923813eca1d70ad405a852bcd2d7609","gas":"0xec0564","gasPrice":"0x3b9aca64","hash":"0xa401453d0258ceb1efbd58500fc60290a8579692ac129dc2317b4df8f16dadbd","input":"0xbd54d1f34e34a90f7dc5efe0b3d65fa4","nonce":"0x20","to":"0x15532e34426cd5c37371ff455a5ba07501c0f522","transactionIndex":null,"value":"0x112a880"}}},"queued":{"0x15532e34426cd5c37371ff455a5ba07501c0f522":{"40":{"blockHash":"0x0000000000000000000000000000000000000000000000000000000000000000","blockNumber":null,"from":"0xc803d7146a4df6937b609f7951bc7eda3def09fb","gas":"0xec0564","gasPrice":"0x3b9aca64","hash":"0xa96d74a993d579d052ce37b28463a1e3ef4e0066cf2390ed7057a4013cb5b165","input":"0x4aa64a075647e3621bbc14b03e4087903f2c9503","nonce":"0x28","to":"0x15532e34426cd5c37371ff455a5ba07501c0f522","transactionIndex":null,"value":"0x3c14dc0"}}}}"""
  var ethService: EthService = _
  var txJson: String = null
  var senderWithSecret: String = null

  private def assertJsonLength(expected: String, actual: Object): Unit = {
    // TODO: mapper must be configured exactly like the one used in the actual RPC response
    // TODO: don't just compare the string length, do a deep compare of the results,
    //  e.g. the order of properties does not matter, but all properties and values need to match
    // TODO: currently, some of the results can be random which prevents exact matches, remove this restriction
    val actualJson = mapper.writeValueAsString(actual)
    assertEquals(
      s"json should match:\nexpected (${expected.length}): $expected\nactual (${actualJson.length}): $actualJson",
      expected.length,
      actualJson.length
    )
  }

  @Before
  def setUp(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
    val genesisBlockId = bytesToId(
      Numeric.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000123")
    )
    val networkParams = RegTestParams(
      sidechainGenesisBlockId = genesisBlockId,
      initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement()
    )
    val receipt: EthereumReceipt =
      createTestEthereumReceipt(
        EthereumTransactionType.DynamicFeeTxType.ordinal(),
        transactionIndex = 0,
        blockNumber = 2,
        address = BytesUtils.fromHexString("d2a538a476aad6ecd245099df9297df6a129c2c5"),
        txHash = Some(BytesUtils.fromHexString("6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253")),
        blockHash = "0456"
      )
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(true)
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
      FeeUtils.INITIAL_BASE_FEE.add(BigInteger.valueOf(100)),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )
    val encodedMessage = EthereumTransactionEncoder.encodeAsRlpValues(txEip1559, txEip1559.isSigned)
    val txHash = BytesUtils.toHexString(Keccak256.hash(encodedMessage))

    txs.append(txEip1559.asInstanceOf[SidechainTypes#SCAT])

    val genesisBlock: AccountBlock = mockHelper.getMockedBlock(
      FeeUtils.INITIAL_BASE_FEE,
      0,
      FeeUtils.GAS_LIMIT,
      genesisBlockId,
      bytesToId(new Array[Byte](32))
    )
    val mockedBlock: AccountBlock = mockHelper.getMockedBlock(
      BigInteger.valueOf(875000000),
      txEip1559.getEffectiveGasPrice(FeeUtils.INITIAL_BASE_FEE).longValueExact(),
      FeeUtils.GAS_LIMIT,
      bytesToId(Numeric.hexStringToByteArray("dc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc")),
      genesisBlockId,
      txs
    )
    val mockedHistory: AccountHistory = mockHelper.getMockedAccountHistory(
      Some(mockedBlock),
      Some(genesisBlock),
      Some(genesisBlockId)
    )
    val mockedState: AccountState = mockHelper.getMockedState(receipt, Numeric.hexStringToByteArray(txHash))

    val secret = PrivateKeySecp256k1Creator
      .getInstance()
      .generateSecret(BytesUtils.fromHexString("1231231231231231231231231231231231231231231123123123123123123123"))
    senderWithSecret = Numeric.toHexString(secret.publicImage().address())
    txJson =
      s"""{"from": "$senderWithSecret", "to": "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4", "gas": "0x76c0", "gasPrice": "0x9184e72a000", "value": "0x9184e72a", "data": "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675", "nonce": "0x1"}"""
    val mockedWallet: AccountWallet = mockHelper.getMockedWallet(secret)

    val mockedMemoryPool: AccountMemoryPool = mockHelper.getMockedAccoutMemoryPool()

    val mockedSidechainNodeViewHolder = TestProbe()

    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case m: GetDataFromCurrentView[
          AccountHistory,
          AccountState,
          AccountWallet,
          AccountMemoryPool,
          _
        ]@unchecked =>
          m match {
            case GetDataFromCurrentView(f) =>
              sender ! f(CurrentView(mockedHistory, mockedState, mockedWallet, mockedMemoryPool))
          }
        case LocallyGeneratedTransaction(tx) =>
          actorSystem.eventStream.publish(SuccessfulTransaction(tx))
      }
      TestActor.KeepRunning
    })

    val nodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref
    val transactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
    ethService = new EthService(nodeViewHolderRef, new FiniteDuration(10, SECONDS), networkParams, transactionActorRef)
  }

  @Test
  def eth_chainId(): Unit = {
    assertEquals(
      Numeric.toHexStringWithPrefix(BigInteger.valueOf(1111111)),
      ethService.execute(getRpcRequest()).asInstanceOf[Quantity].getValue
    )
  }

  @Test
  def eth_blockNumber(): Unit = {
    assertEquals("0x2", ethService.execute(getRpcRequest()).asInstanceOf[Quantity].getValue)
  }

  @Test
  def net_version(): Unit = {
    assertEquals(String.valueOf(1111111), ethService.execute(getRpcRequest()))
  }

  @Test
  def eth_gasPrice(): Unit = {
    assertEquals(
      Numeric.toHexStringWithPrefix(BigInteger.valueOf(875000000)),
      ethService.execute(getRpcRequest()).asInstanceOf[Quantity].getValue
    )
  }

  @Test
  def eth_syncing(): Unit = {
    assertEquals(false, ethService.execute(getRpcRequest()).asInstanceOf[Boolean])
  }

  @Test
  def eth_getTransactionByHash(): Unit = {
    val method = "eth_getTransactionByHash"

    val validCases = Table(
      ("Parameter value", "Expected output"),
      ("0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253", txViewOutput),
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

    // TODO: add more txs and look at log index (should increase with more than one transaction)
    val validCases = Table(
      ("Parameter value", "Expected output"),
      (
        "0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253",
        s"""{"type":"0x02","transactionHash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","from":"0xd123b689dad8ed6b99f8bd55eed64ab357e6a8d1","to":null,"cumulativeGasUsed":"0x3e8","gasUsed":"0x12d687","contractAddress":"0x1122334455667788990011223344556677889900","logs":[{"address":"0xd2a538a476aad6ecd245099df9297df6a129c2c5","topics":["0x0000000000000000000000000000000000000000000000000000000000000000","0x1111111111111111111111111111111111111111111111111111111111111111","0x2222222222222222222222222222222222222222222222222222222222222222","0x3333333333333333333333333333333333333333333333333333333333333333"],"data":"0xaabbccddeeff","blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","transactionHash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","logIndex":"0x0","removed":false},{"address":"0xd2a538a476aad6ecd245099df9297df6a129c2c5","topics":["0x0000000000000000000000000000000000000000000000000000000000000000","0x1111111111111111111111111111111111111111111111111111111111111111","0x2222222222222222222222222222222222222222222222222222222222222222","0x3333333333333333333333333333333333333333333333333333333333333333"],"data":"0xaabbccddeeff","blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","transactionHash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","logIndex":"0x1","removed":false}],"logsBloom":"0x00000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000020000000010000080000000000000000000020000002000000000000800000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000002000000000000002000000000800000000000000000000000000008000000000000020000000000000000000000000000000000000000000000000000000000000000000","status":"0x1","effectiveGasPrice":"0x342770c1"}"""
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
  def eth_getTransactionByBlockNumberAndIndex(): Unit = {
    val method = "eth_getTransactionByBlockNumberAndIndex"
    val validCases = Table(
      ("Block tag", "Transaction index", "Expected output"),
      ("latest", "0", txViewOutput),
      ("pending", "0", txViewOutput),
      (null, "0", txViewOutput),
      ("0x2", "0", txViewOutput),
      ("2", "0", txViewOutput),
      ("1", "0", "null"),
      ("earliest", "0", "null"),
      ("earliest", "1", "null")
    )

    val invalidCases =
      Table(("Block tag", "Transaction index"), ("safe", "0"), ("finalized", "0"), ("aaaa", "0"), ("0x1337", "0"))

    forAll(validCases) { (tag, index, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(ethService.execute(getRpcRequest(paramValues = Array(tag, index), method = method)))
      )
    }

    forAll(invalidCases) { (tag, index) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(tag, index), method = method))
      }
    }
  }

  @Test
  def eth_getTransactionByBlockHashAndIndex(): Unit = {
    val method = "eth_getTransactionByBlockHashAndIndex"

    val validCases = Table(
      ("Block hash", "Transaction index", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "0", txViewOutput),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "1", "null"),
      ("0x0000000000000000000000000000000000000000000000000000000000000123", "0", "null"),
      ("0x0000000000000000000000000000000000000000000000000000000000000456", "0", "null")
    )

    val invalidCases =
      Table(("Block hash", "Transaction index"), ("null", "0"), ("aaaa", "0"), ("0x1337", "0"))

    forAll(validCases) { (tag, index, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(ethService.execute(getRpcRequest(paramValues = Array(tag, index), method = method)))
      )
    }

    forAll(invalidCases) { (tag, index) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(tag, index), method = method))
      }
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
   *   RPC method, default is calling method name
   * @return
   *   RpcRequest instance
   */
  private[this] def getRpcRequest(
      params: Array[String] = null,
      paramValues: Array[Any] = null,
      method: String = Thread.currentThread.getStackTrace()(2).getMethodName
  ): RpcRequest = {
    val jsonParams = if (params != null) {
      if (params.length != paramValues.length)
        throw new IllegalArgumentException("Number of parameters given must be equal to number of values given")
      (params zip paramValues)
        .map(param => s""""${param._1}":"${param._2}"""")
        .mkString("[{", ", ", "}]")
    } else if (paramValues != null) {
      paramValues
        .collect {
          case stringValue: String => if (stringValue.length > 255) stringValue else s""""$stringValue""""
          case arrayValue: Array[_] => arrayValue.mkString("[", ", ", "]")
          case value => value
        }
        .mkString("[", ", ", "]")
    } else {
      "[]"
    }
    val json = s"""{"id":"1","jsonrpc":"2.0","method":"$method", "params":$jsonParams}"""
    new RpcRequest((new ObjectMapper).readTree(json))
  }

  @Test
  def eth_sendRawTransaction(): Unit = {
    val method = "eth_sendRawTransaction"
    val validCases = Table(
      ("Transaction", "Expected output"),
      (
        "0xf86f82674685031b1de8ce83019a289452cceccf519c4575a3cbf3bff5effa5e9181cec4880b9f5bd224727a808025a0cdf8d5eb0f83dff14c87aee3ff7cb373780520117fe735de78bc5eb25e700beba00b7120958d87d26425fd70d1e4c2bfb4022392417bc567887eafd5d7da09ccdf",
        "0xe0499a7e779f0b82a292accd57ad4015635b2d43897c5ea7989c55049ed5b824"
      )
    )

    val invalidCases = Table(
      "Raw transaction",
      "123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba",
      "0x123cfae639e9fcab216"
    )

    forAll(validCases) { (input, expectedOutput) =>
      assertEquals(expectedOutput, ethService.execute(getRpcRequest(paramValues = Array(input), method = method)))
    }

    forAll(invalidCases) { input =>
      assertThrows[RuntimeException] {
        ethService.execute(getRpcRequest(paramValues = Array(input), method = method))
      }
    }
  }

  @Test
  def eth_getBlockByNumber(): Unit = {
    val method = "eth_getBlockByNumber"
    val validCases = Table(
      ("Block tag", "Full transaction objects", "Expected output"),
      ("latest", true, blockViewOutput),
      ("pending", false, blockViewOutputTxHashes),
      ("0x2", true, blockViewOutput)
    )

    val invalidCases =
      Table(
        ("Block tag / number", "Full transaction objects"),
        ("safe", true),
        ("finalized", true),
        ("aaaa", true),
        ("0x1337", true)
      )

    forAll(validCases) { (tag, fullTx, expectedOutput) =>
      assertJsonLength(
        expectedOutput,
        ethService.execute(getRpcRequest(paramValues = Array(tag, fullTx), method = method))
      )
    }

    forAll(invalidCases) { (tag, fullTx) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(tag, fullTx), method = method))
      }
    }
  }

  @Test
  def eth_getBlockByHash(): Unit = {
    val method = "eth_getBlockByHash"
    val validCases = Table(
      ("Block hash", "Full transaction objects", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", true, blockViewOutput),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", false, blockViewOutputTxHashes),
      ("0x12345677de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", true, "null")
    )

    val invalidCases =
      Table(("Block hash", "Full transaction objects"), ("0x1337", true), ("1337abcd", true))

    forAll(validCases) { (hash, fullTx, expectedOutput) =>
      assertJsonLength(
        expectedOutput,
        ethService.execute(getRpcRequest(paramValues = Array(hash, fullTx), method = method))
      )
    }

    forAll(invalidCases) { (hash, fullTx) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(hash, fullTx), method = method))
      }
    }
  }

  @Test
  def eth_getBlockTransactionCountByNumber(): Unit = {
    val method = "eth_getBlockTransactionCountByNumber"
    val validCases = Table(
      ("Block tag / index", "Expected output"),
      ("latest", "0x1"),
      ("pending", "0x1"),
      ("0x2", "0x1"),
      ("0x1", "0x0")
    )

    val invalidCases =
      Table("Block tag / index", "0x1337", "1337abcd")

    forAll(validCases) { (tag, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService.execute(getRpcRequest(paramValues = Array(tag), method = method)).asInstanceOf[Quantity].getValue
      )
    }

    forAll(invalidCases) { tag =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(tag), method = method))
      }
    }
  }

  @Test
  def eth_getBlockTransactionCountByHash(): Unit = {
    val method = "eth_getBlockTransactionCountByHash"
    val validCases = Table(
      ("Block hash", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "0x1"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "0x1"),
      ("0x0000000000000000000000000000000000000000000000000000000000000123", "0x0")
    )

    val invalidCases =
      Table("Block hash", "0x1337", "1337abcd")

    forAll(validCases) { (hash, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService.execute(getRpcRequest(paramValues = Array(hash), method = method)).asInstanceOf[Quantity].getValue
      )
    }

    forAll(invalidCases) { hash =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(hash), method = method))
      }
    }
  }

  @Test
  def eth_sendTransaction(): Unit = {
    val method = "eth_sendTransaction"

    val validCases = Table(
      ("Transaction parameters", "Expected output"),
      // tx hash length = 68
      (Array[Any](txJson), 68)
    )

    val invalidCases = Table("Transaction", Array[Any](txJsonNoSecret), "aaaa")

    forAll(validCases) { (params, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(ethService.execute(getRpcRequest(paramValues = params, method = method))).length
      )
    }

    forAll(invalidCases) { tx =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(tx), method = method))
      }
    }
  }

  @Test
  def eth_signTransaction(): Unit = {
    val method = "eth_signTransaction"

    val validCases = Table(
      ("Transaction parameters", "Expected output"),
      // signed tx length = 300
      (Array[Any](txJson), 300)
    )

    val invalidCases = Table("Transaction", Array[Any](txJsonNoSecret), "aaaa")

    forAll(validCases) { (params, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(ethService.execute(getRpcRequest(paramValues = params, method = method))).length
      )
    }

    forAll(invalidCases) { tx =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(tx), method = method))
      }
    }
  }

  @Test
  def eth_sign(): Unit = {
    val method = "eth_sign"

    val validCases = Table(
      ("Sender", "message", "Expected output"),
      // signed msg length = 134
      (senderWithSecret, "message", 134)
    )

    val invalidCases = Table(("sender", "message"), ("asd", "message"), ("aaaa", "message"))

    forAll(validCases) { (sender, message, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper
          .writeValueAsString(ethService.execute(getRpcRequest(paramValues = Array(sender, message), method = method)))
          .length
      )
    }

    forAll(invalidCases) { (sender, message) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(sender, message), method = method))
      }
    }
  }

  @Test
  def eth_getBalance(): Unit = {
    val method = "eth_getBalance"

    val validCases = Table(
      ("Address", "tag", "Expected output"),
      ("0x1234567891011121314151617181920212223242", "latest", "0x7b"),
      ("0x1234567891011121314151617181920212223241", "latest", "0x16345785d89ffff")
    )

    val invalidCases = Table(("Address", "Tag"), ("0x", "latest"), ("0x1234567890123456789012345678901234567890", ""))

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService
          .execute(getRpcRequest(paramValues = Array(address, tag), method = method))
          .asInstanceOf[Quantity]
          .getValue
      )
    }

    forAll(invalidCases) { (address, tag) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(address, tag), method = method))
      }
    }
  }

  @Test
  def eth_getCode(): Unit = {
    val method = "eth_getCode"
    val validCases = Table(
      ("Address", "Tag", "Expected output"),
      ("0x1234567891011121314151617181920212223242", "latest", "0x1234"),
      ("0x1234567890123456789012345678901234567890", "latest", "0x")
    )

    val invalidCases = Table(("Address", "Tag"), ("0x", "latest"), ("0x1234567890123456789012345678901234567890", ""))

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService.execute(getRpcRequest(paramValues = Array(address, tag), method = method))
      )
    }

    forAll(invalidCases) { (address, tag) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(address, tag), method = method))
      }
    }
  }

  @Test
  def eth_getTransactionCount(): Unit = {
    val method = "eth_getTransactionCount"
    val validCases = Table(
      ("Address", "Tag", "Expected output"),
      ("0x1234567891011121314151617181920212223242", "latest", "0x1"),
      ("0x1234567890123456789012345678901234567890", "latest", "0x0")
    )

    val invalidCases = Table(("Address", "Tag"), ("0x", "latest"), ("0x1234567890123456789012345678901234567890", ""))

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService
          .execute(getRpcRequest(paramValues = Array(address, tag), method = method))
          .asInstanceOf[Quantity]
          .getValue
      )
    }

    forAll(invalidCases) { (address, tag) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(address, tag), method = method))
      }
    }
  }

  @Test
  def eth_getStorageAt(): Unit = {
    val method = "eth_getStorageAt"
    val validCases = Table(
      ("Address", "Key", "Tag", "Expected output"),
      (
        "0x1234567891011121314151617181920212223242",
        "0x0",
        "latest",
        "0x1511111111111111111111111111111111111111111111111111111111111111"
      ),
      (
        "0x1234567890123456789012345678901234567890",
        "0x0",
        "latest",
        "0x1411111111111111111111111111111111111111111111111111111111111111"
      )
    )

    val invalidCases = Table(
      ("Address", "Key", "Tag"),
      ("0x12", "0x12", "latest"),
      ("0x1234567890123456789012345678901234567890", "0x12", "")
    )

    forAll(validCases) { (address, key, tag, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService
          .execute(getRpcRequest(paramValues = Array(address, key, tag), method = method))
          .asInstanceOf[Hash]
          .toString
      )
    }

    forAll(invalidCases) { (address, key, tag) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(address, key, tag), method = method))
      }
    }
  }

  @Test
  def eth_getProof(): Unit = {
    val method = "eth_getProof"
    val validCases = Table(
      ("Address", "Tag", "Expected output"),
      (
        "0x1234567891011121314151617181920212223242",
        "latest",
        """{"address":"0x1234567891011121314151617181920212223242","accountProof":["123"],"balance":"0x7b","codeHash":null,"nonce":"0x1","storageHash":null,"storageProof":null}"""
      ),
      (
        "0x1234567890123456789012345678901234567890",
        "latest",
        """{"address":"0x1234567891011121314151617181920212223242","accountProof":["123"],"balance":"0x7b","codeHash":null,"nonce":"0x1","storageHash":null,"storageProof":null}"""
      )
    )

    val invalidCases = Table(("Address", "Tag"), ("0x12", "latest"), ("0x1234567890123456789012345678901234567890", ""))

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(
          ethService.execute(
            new RpcRequest(
              mapper.readTree(
                s"""{"jsonrpc":"2.0","method":"$method", "params":["$address", ["0x1", "0x2"], "$tag"],"id":1}"""
              )
            )
          )
        )
      )
    }

    forAll(invalidCases) { (address, tag) =>
      assertThrows[RpcException] {
        ethService.execute(
          new RpcRequest(
            mapper.readTree(
              s"""{"jsonrpc":"2.0","method":"$method", "params":["$address", ["0x1", "0x2"], "$tag"],"id":1}"""
            )
          )
        )
      }
    }
  }

  @Test
  def eth_accounts(): Unit = {
    assertEquals(
      s"""["$senderWithSecret"]""",
      mapper.writeValueAsString(ethService.execute(getRpcRequest())).toLowerCase()
    )
  }

  @Test
  def zen_getFeePayments(): Unit = {
    val method = "zen_getFeePayments"
    val validCases = Table(
      ("Block id", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "null"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "null"),
      ("null", "null")
    )

    forAll(validCases) { (id, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(ethService.execute(getRpcRequest(paramValues = Array(id), method = method)))
      )
    }
  }

  @Test
  def zen_getForwardTransfers(): Unit = {
    val method = "zen_getForwardTransfers"
    val validCases = Table(("Block id", "Size"), ("latest", 150), ("earliest", 150))

    val invalidCases =
      Table("Block id", "0x1337", "1337abcd")

    forAll(validCases) { (id, size) =>
      assertTrue(
        mapper
          .writeValueAsString(ethService.execute(getRpcRequest(paramValues = Array(id), method = method)))
          .length > size
      )
    }

    forAll(invalidCases) { id =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(id), method = method))
      }
    }
  }

  @Test
  def eth_estimateGas(): Unit = {
    val method = "eth_estimateGas"

    val validCases = Table(
      ("from", "to", "data", "value", "gasPrice", "nonce", "Expected output"),
      (
        senderWithSecret,
        "0x0000000000000000000022222222222222222222",
        "0x",
        "0xE8D4A51000",
        "0x4B9ACA00",
        "0x1",
        "0x5208"
      ),
      (
        senderWithSecret,
        "0x0000000000000000000011111111111111111111",
        "0x9950a60fdbcbaf2b14a48cfc24941ef5acfdac0a8c590255000000000000000000000000",
        "0xE8D4A51000",
        "0x4B9ACA00",
        "0x1",
        "0x53b8"
      )
    )

    forAll(validCases) { (from, to, data, value, gasPrice, nonce, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService
          .execute(
            getRpcRequest(
              params = Array("from", "to", "data", "value", "gasPrice", "nonce"),
              paramValues = Array(from, to, data, value, gasPrice, nonce),
              method = method
            )
          )
          .asInstanceOf[Quantity]
          .getValue
      )
    }

    val invalidCases = Table(
      ("from", "to", "data", "value", "gasPrice", "nonce"),
      (
        senderWithSecret,
        "0x0000000000000000000022222222222222222222",
        "5ca748ff1122334455669988112233445566778811223344556677881122334455667788aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff00123400000000000000000000000000000000000000000000000000000000000000000000000000000000000000bbdf1daf64ed9d6e30f80b93f647b8bc6ea13191",
        "0x999999999900000000000000000",
        "0x4B9ACA00",
        "0x1"
      )
    )

    forAll(invalidCases) { (from, to, data, value, gasPrice, nonce) =>
      assertThrows[RpcException] {
        ethService.execute(
          getRpcRequest(
            params = Array("from", "to", "data", "value", "gasPrice", "nonce"),
            paramValues = Array(from, to, data, value, gasPrice, nonce),
            method = method
          )
        )
      }
    }
  }

  @Test
  def eth_call(): Unit = {
    val method = "eth_call"

    val validCases = Table(
      ("from", "to", "data", "value", "gasPrice", "nonce", "Expected output"),
      (senderWithSecret, "0x0000000000000000000022222222222222222222", "0x", "0xE8D4A51000", "0x4B9ACA00", "0x1", "0x"),
      (
        senderWithSecret,
        "0x0000000000000000000011111111111111111111",
        "0x9950a60fdbcbaf2b14a48cfc24941ef5acfdac0a8c590255000000000000000000000000",
        "0xE8D4A51000",
        "0x4B9ACA00",
        "0x1",
        "0x"
      )
    )

    forAll(validCases) { (from, to, data, value, gasPrice, nonce, expectedOutput) =>
      assertEquals(
        expectedOutput,
        ethService
          .execute(
            getRpcRequest(
              params = Array("from", "to", "data", "value", "gasPrice", "nonce"),
              paramValues = Array(from, to, data, value, gasPrice, nonce),
              method = method
            )
          )
      )
    }

    val invalidCases = Table(
      ("from", "to", "data", "value", "gasPrice", "nonce"),
      (
        senderWithSecret,
        "0x0000000000000000000022222222222222222222",
        "5ca748ff1122334455669988112233445566778811223344556677881122334455667788aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff00123400000000000000000000000000000000000000000000000000000000000000000000000000000000000000bbdf1daf64ed9d6e30f80b93f647b8bc6ea13191",
        "0x999999999900000000000000000",
        "0x4B9ACA00",
        "0x1"
      ),
      (
        senderWithSecret,
        "0x0000000000000000000022222222222222222222",
        "5ca748ff1122334455669988112233445566778811223344556677881122334455667788aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff00123400000000000000000000000000000000000000000000000000000000000000000000000000000000000000bbdf1daf64ed9d6e30f80b93f647b8bc6ea13191",
        "0x0",
        "0x4B9ACA00",
        ""
      )
    )

    forAll(invalidCases) { (from, to, data, value, gasPrice, nonce) =>
      assertThrows[RpcException] {
        ethService.execute(
          getRpcRequest(
            params = Array("from", "to", "data", "value", "gasPrice", "nonce"),
            paramValues = Array(from, to, data, value, gasPrice, nonce),
            method = method
          )
        )
      }
    }
  }

  @Test
  def eth_feeHistory(): Unit = {
    val method = "eth_feeHistory"
    val validCases = Table(
      ("Block count", "Newest block (tag)", "Reward percentiles", "Expected output"),
      (
        "0x1",
        "latest",
        Array(20, 50, 70),
        """{"oldestBlock":"0x2","baseFeePerGas":["0x342770c0","0x1e0408399"],"gasUsedRatio":[33.333333366666665],"reward":[["0x1","0x1","0x1"]]}"""
      ),
      (
        "0x1",
        "latest",
        null,
        """{"oldestBlock":"0x2","baseFeePerGas":["0x342770c0","0x1e0408399"],"gasUsedRatio":[33.333333366666665],"reward":null}"""
      ),
      (
        "0x20",
        "latest",
        null,
        """{"oldestBlock":"0x1","baseFeePerGas":["0x3b9aca00","0x342770c0","0x1e0408399"],"gasUsedRatio":[0.0,33.333333366666665],"reward":null}"""
      )
    )

    forAll(validCases) { (nrOfBlocks, tag, rewardPercentiles, expectedOutput) =>
      assertEquals(
        expectedOutput,
        mapper.writeValueAsString(
          ethService.execute(getRpcRequest(paramValues = Array(nrOfBlocks, tag, rewardPercentiles), method = method))
        )
      )
    }

    val invalidCases =
      Table(("Block count", "Newest block (tag)", "Reward percentiles"), ("0x1", "latest", Array(20, 50, 130)))

    forAll(invalidCases) { (nrOfBlocks, tag, rewardPercentiles) =>
      assertThrows[RpcException] {
        ethService.execute(getRpcRequest(paramValues = Array(nrOfBlocks, tag, rewardPercentiles), method = method))
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

    // Test 2: Wrong number of parameters
    var rpcRequest = getRpcRequest(paramValues = Array(5, 10, 20), method = "eth_estimateGas")
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }

    // Test 3: Trigger IllegalArgumentException rpc call
    rpcRequest = getRpcRequest(paramValues = Array(-1), method = "eth_estimateGas")
    assertThrows[RpcException] {
      ethService.execute(rpcRequest)
    }
  }

  @Test
  def txpool_status(): Unit = {
    assertEquals(
      txPoolStatusOutput,
      mapper.writeValueAsString(ethService.execute(getRpcRequest()))
    )
  }

  @Test
  def txpool_content(): Unit = {
    assertEquals(
      txPoolContentOutput,
      mapper.writeValueAsString(ethService.execute(getRpcRequest()))
    )
  }

}
