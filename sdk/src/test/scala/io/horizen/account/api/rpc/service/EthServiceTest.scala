package io.horizen.account.api.rpc.service

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.ObjectMapper
import io.horizen.account.api.rpc.handler.RpcException
import io.horizen.account.api.rpc.request.RpcRequest
import io.horizen.account.block.AccountBlock
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.PrivateKeySecp256k1Creator
import io.horizen.account.serialization.EthJsonMapper
import io.horizen.account.state.AccountState
import io.horizen.account.state.receipt.{EthereumReceipt, ReceiptFixture}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.transaction.EthereumTransaction.EthereumTransactionType
import io.horizen.account.utils.{AccountMockDataHelper, EthereumTransactionEncoder, FeeUtils}
import io.horizen.account.wallet.AccountWallet
import io.horizen.api.http.{SidechainApiMockConfiguration, SidechainTransactionActorRef}
import io.horizen.evm.Address
import io.horizen.fixtures.FieldElementFixture
import io.horizen.fixtures.SidechainBlockFixture.getDefaultAccountTransactionsCompanion
import io.horizen.network.SyncStatus
import io.horizen.network.SyncStatusActor.ReceivableMessages.GetSyncStatus
import io.horizen.params.RegTestParams
import io.horizen.utils.BytesUtils
import io.horizen.{EthServiceSettings, SidechainTypes}
import org.junit.{Before, Test}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.utils.Numeric
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.{GetDataFromCurrentView, LocallyGeneratedTransaction}
import sparkz.core.bytesToId
import sparkz.core.network.NetworkController.ReceivableMessages.GetConnectedPeers
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SuccessfulTransaction
import sparkz.crypto.hash.Keccak256
import sparkz.util.ByteArrayBuilder
import sparkz.util.serialization.VLQByteBufferWriter

import java.math.BigInteger
import java.util.Optional
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.Failure

class EthServiceTest extends JUnitSuite with MockitoSugar with ReceiptFixture with TableDrivenPropertyChecks {
  private val mapper = new ObjectMapper()

  private val invalidCasesHashes =
    Table(
      "Hash",
      // missing prefix
      "123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba",
      // too short
      "0x1234",
      // too long
      "0x123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba1"
    )

  private val expectedTxView =
    """{
      "blockHash": "0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc",
      "blockNumber": "0x2",
      "from": "0xd123b689dad8ed6b99f8bd55eed64ab357e6a8d1",
      "hash": "0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253",
      "transactionIndex": "0x0",
      "type": "0x2",
      "nonce": "0x0",
      "to": null,
      "gas": "0x1",
      "value": "0x1",
      "input": "0x",
      "maxPriorityFeePerGas": "0x1",
      "maxFeePerGas": "0x3b9aca64",
      "gasPrice": "0x342770c1",
      "chainId": "0x10f447",
      "accessList": [],
      "v": "0x1c",
      "r": "0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
      "s": "0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d"
    }"""

  private def blockViewOutput(transactions: String) =
    s"""{
        "baseFeePerGas": "0x342770c0",
        "difficulty": "0x0",
        "extraData": "0x",
        "gasLimit": "0x1c9c380",
        "gasUsed": "0x3b9aca01",
        "hash": "0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc",
        "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "miner": "0x1234567891011121314112345678910111213141",
        "mixHash": "0x2615aa670ccc0b19233528479b72ba75b133e2cf03c99c886624ded1f4b52123",
        "nonce": "0x0000000000000000",
        "number": "0x1",
        "parentHash": "0x0000000000000000000000000000000000000000000000000000000000000123",
        "receiptsRoot": "0x1234567891011121314112345678910111213141010203040506070809111444",
        "sha3Uncles": "0x",
        "size": "0x100",
        "stateRoot": "0x1234567891011121314112345678910111213141010203040506070809111333",
        "timestamp": "0x3b9aca00",
        "totalDifficulty": "0x0",
        "transactions": [
          $transactions
        ],
        "transactionsRoot": "0x1234567891011121314112345678910111213141010203040506070809111222",
        "uncles": []
      }"""

  private val expectedBlockViewTxHydrated = blockViewOutput(expectedTxView)

  private val expectedBlockViewTxHashes =
    blockViewOutput("\"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253\"")

  private val txPoolStatusOutput = """{"pending":3,"queued":1}"""

  private val txPoolContentOutput =
    """{
      "pending":{
         "0x15532e34426cd5c37371ff455a5ba07501c0f522":{
            "16":{
               "blockHash":null,
               "blockNumber":null,
               "transactionIndex":null,
               "hash":"0x68366d9034c74adb5d6e584116bc20838aedc15218a1d49eea43e04f31072044",
               "type":"0x2",
               "nonce":"0x10",
               "from":"0x5b19616a7277d58ea1040a5f44c54d41853ccde3",
               "to":"0x15532e34426cd5c37371ff455a5ba07501c0f522",
               "value":"0xe4e1c0",
               "input":"0xbd54d1f34e34a90f7dc5efe0b3d65fa4",
               "gas":"0xec0564",
               "gasPrice":"0x3b9aca64",
               "maxPriorityFeePerGas":"0x6ef91",
               "maxFeePerGas":"0x3b9aca64",
               "chainId":"0x7cd",
               "v":"0x1c",
               "r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
               "s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d",
               "accessList":[]
            },
            "24":{
               "blockHash":null,
               "blockNumber":null,
               "transactionIndex":null,
               "hash":"0xc8a7edb4bd87f30671879a1b12767591a4d73fc12153885ec96e556a97fc5b37",
               "type":"0x2",
               "nonce":"0x18",
               "from":"0x081d8a5b696ec5dfce641568e6665b6be2410ce2",
               "to":"0x15532e34426cd5c37371ff455a5ba07501c0f522",
               "value":"0x493e00",
               "input":"0x8c64fe48688ab096dfb6ac2eeefcf213",
               "gas":"0xec0564",
               "gasPrice":"0x3b9aca64",
               "maxPriorityFeePerGas":"0x6ef91",
               "maxFeePerGas":"0x3b9aca64",
               "chainId":"0x7cd",
               "v":"0x1c",
               "r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
               "s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d",
               "accessList":[]
            }
         },
         "0xb039865dbea73df08e23f185847bab8e6a44108d":{
            "32":{
               "blockHash":null,
               "blockNumber":null,
               "transactionIndex":null,
               "hash":"0xa401453d0258ceb1efbd58500fc60290a8579692ac129dc2317b4df8f16dadbd",
               "type":"0x2",
               "nonce":"0x20",
               "from":"0xb3151940f923813eca1d70ad405a852bcd2d7609",
               "to":"0x15532e34426cd5c37371ff455a5ba07501c0f522",
               "value":"0x112a880",
               "input":"0xbd54d1f34e34a90f7dc5efe0b3d65fa4",
               "gas":"0xec0564",
               "gasPrice":"0x3b9aca64",
               "maxPriorityFeePerGas":"0x6ef91",
               "maxFeePerGas":"0x3b9aca64",
               "chainId":"0x7cd",
               "v":"0x1c",
               "r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
               "s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d",
               "accessList":[]
            }
         }
      },
      "queued":{
         "0x15532e34426cd5c37371ff455a5ba07501c0f522":{
            "40":{
               "blockHash":null,
               "blockNumber":null,
               "transactionIndex":null,
               "hash":"0xa96d74a993d579d052ce37b28463a1e3ef4e0066cf2390ed7057a4013cb5b165",
               "type":"0x2",
               "nonce":"0x28",
               "from":"0xc803d7146a4df6937b609f7951bc7eda3def09fb",
               "to":"0x15532e34426cd5c37371ff455a5ba07501c0f522",
               "value":"0x3c14dc0",
               "input":"0x4aa64a075647e3621bbc14b03e4087903f2c9503",
               "gas":"0xec0564",
               "gasPrice":"0x3b9aca64",
               "maxPriorityFeePerGas":"0x6ef91",
               "maxFeePerGas":"0x3b9aca64",
               "chainId":"0x7cd",
               "v":"0x1c",
               "r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
               "s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d",
               "accessList":[]
            }
         }
      }
    }"""

  private val txPoolContentFromOutput =
    """{
       "pending":{
          "16":{
             "blockHash":null,
             "blockNumber":null,
             "transactionIndex":null,
             "hash":"0x68366d9034c74adb5d6e584116bc20838aedc15218a1d49eea43e04f31072044",
             "type":"0x2",
             "nonce":"0x10",
             "from":"0x5b19616a7277d58ea1040a5f44c54d41853ccde3",
             "to":"0x15532e34426cd5c37371ff455a5ba07501c0f522",
             "value":"0xe4e1c0",
             "input":"0xbd54d1f34e34a90f7dc5efe0b3d65fa4",
             "gas":"0xec0564",
             "gasPrice":"0x3b9aca64",
             "maxPriorityFeePerGas":"0x6ef91",
             "maxFeePerGas":"0x3b9aca64",
             "chainId":"0x7cd",
             "v":"0x1c",
             "r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
             "s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d",
             "accessList":[]
          },
          "24":{
             "blockHash":null,
             "blockNumber":null,
             "transactionIndex":null,
             "hash":"0xc8a7edb4bd87f30671879a1b12767591a4d73fc12153885ec96e556a97fc5b37",
             "type":"0x2",
             "nonce":"0x18",
             "from":"0x081d8a5b696ec5dfce641568e6665b6be2410ce2",
             "to":"0x15532e34426cd5c37371ff455a5ba07501c0f522",
             "value":"0x493e00",
             "input":"0x8c64fe48688ab096dfb6ac2eeefcf213",
             "gas":"0xec0564",
             "gasPrice":"0x3b9aca64",
             "maxPriorityFeePerGas":"0x6ef91",
             "maxFeePerGas":"0x3b9aca64",
             "chainId":"0x7cd",
             "v":"0x1c",
             "r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
             "s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d",
             "accessList":[]
          }
       },
       "queued":{
          "40":{
             "blockHash":null,
             "blockNumber":null,
             "transactionIndex":null,
             "hash":"0xa96d74a993d579d052ce37b28463a1e3ef4e0066cf2390ed7057a4013cb5b165",
             "type":"0x2",
             "nonce":"0x28",
             "from":"0xc803d7146a4df6937b609f7951bc7eda3def09fb",
             "to":"0x15532e34426cd5c37371ff455a5ba07501c0f522",
             "value":"0x3c14dc0",
             "input":"0x4aa64a075647e3621bbc14b03e4087903f2c9503",
             "gas":"0xec0564",
             "gasPrice":"0x3b9aca64",
             "maxPriorityFeePerGas":"0x6ef91",
             "maxFeePerGas":"0x3b9aca64",
             "chainId":"0x7cd",
             "v":"0x1c",
             "r":"0x805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023",
             "s":"0x568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d",
             "accessList":[]
          }
       }
    }"""

  private val txPoolInspectOutput =
    """{
    "pending": {
       "0x15532e34426cd5c37371ff455a5ba07501c0f522":{
          "16":"0x15532e34426cd5c37371ff455a5ba07501c0f522: 15000000 wei + 15467876 gas × 1000000100 wei",
          "24":"0x15532e34426cd5c37371ff455a5ba07501c0f522: 4800000 wei + 15467876 gas × 1000000100 wei"
       },
       "0xb039865dbea73df08e23f185847bab8e6a44108d":{
          "32":"0x15532e34426cd5c37371ff455a5ba07501c0f522: 18000000 wei + 15467876 gas × 1000000100 wei"
       }
    },
    "queued": {
       "0x15532e34426cd5c37371ff455a5ba07501c0f522":{
          "40":"0x15532e34426cd5c37371ff455a5ba07501c0f522: 63000000 wei + 15467876 gas × 1000000100 wei"
       }
    }
  }"""

  private var ethService: EthService = _
  private var senderWithSecret: String = _

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
        address = new Address("0xd2a538a476aad6ecd245099df9297df6a129c2c5"),
        txHash = Some(BytesUtils.fromHexString("6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253")),
        blockHash = "0456"
      )
    val mockHelper: AccountMockDataHelper = AccountMockDataHelper(true)
    val goodSignature = new SignatureSecp256k1(
      new BigInteger("1c", 16),
      new BigInteger("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023", 16),
      new BigInteger("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d", 16)
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
    val writer = new VLQByteBufferWriter(new ByteArrayBuilder)
    EthereumTransactionEncoder.encodeAsRlpValues(txEip1559, txEip1559.isSigned, writer)
    val encodedMessage = writer.toBytes
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
    senderWithSecret = secret.publicImage().address().toString

    val mockedWallet: AccountWallet = mockHelper.getMockedWallet(secret)
    val mockedMemoryPool: AccountMemoryPool = mockHelper.getMockedAccoutMemoryPool
    val mockedSidechainNodeViewHolder = TestProbe()

    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
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
              sender ! f(CurrentView(mockedHistory, mockedState, mockedWallet, mockedMemoryPool))
          }
        case LocallyGeneratedTransaction(tx) =>
          actorSystem.eventStream.publish(SuccessfulTransaction(tx))
      }
      TestActor.KeepRunning
    })
    val nodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()
    val mockedNetworkControllerActor = TestProbe()
    mockedNetworkControllerActor.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetConnectedPeers =>
          if (sidechainApiMockConfiguration.getShould_networkController_GetConnectedPeers_reply())
            sender ! Seq()
          else sender ! Failure(new Exception("No connected peers."))
      }
      TestActor.KeepRunning
    })
    val mockedNetworkControllerRef: ActorRef = mockedNetworkControllerActor.ref

    val transactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)

    val mockedSyncStatusActor = TestProbe()
    mockedSyncStatusActor.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetSyncStatus =>
          sender ! new SyncStatus(true, BigInt(250), BigInt(200), BigInt(300))
      }
      TestActor.KeepRunning
    })
    val mockedSyncStatusActorRef: ActorRef = mockedSyncStatusActor.ref

    val ethServiceSettings = EthServiceSettings()
    val transactionsCompanion = getDefaultAccountTransactionsCompanion

    ethService = new EthService(
      nodeViewHolderRef,
      mockedNetworkControllerRef,
      new FiniteDuration(10, SECONDS),
      networkParams,
      ethServiceSettings,
      10,
      "testVersion",
      transactionActorRef,
      mockedSyncStatusActorRef,
      transactionsCompanion
    )
  }

  /**
   * Helper for executing an RPC request.
   * @param method
   *   name of RPC method to execute
   * @param params
   *   list of parameters
   * @return
   *   value returned by the RPC method, before serialization
   */
  private def rpc(method: String, params: Any*): Object = {
    val jsonParams = EthJsonMapper.serialize(params)
    val json = s"""{"jsonrpc":"2.0","id":"1","method":"$method", "params":$jsonParams}"""
    val request = new RpcRequest(mapper.readTree(json))
    ethService.execute(request)
  }

  @Test
  def net_version(): Unit = {
    assertJsonEquals("\"1111111\"", rpc("net_version"))
  }

  @Test
  def eth_chainId(): Unit = {
    assertJsonEquals("\"0x10f447\"", rpc("eth_chainId"))
  }

  @Test
  def eth_blockNumber(): Unit = {
    assertJsonEquals("\"0x2\"", rpc("eth_blockNumber"))
  }

  @Test
  def net_listening(): Unit = {
    assertJsonEquals("true", rpc("net_listening"))
  }

  @Test
  def net_peerCount(): Unit = {
    assertJsonEquals("\"0x0\"", rpc("net_peerCount"))
  }

  @Test
  def web3_clientVersion(): Unit = {
    assertJsonEquals("\"testVersion\"", rpc("web3_clientVersion"))
  }

  @Test
  def eth_gasPrice(): Unit = {
    assertJsonEquals("\"0x342770c0\"", rpc("eth_gasPrice"))
  }

  @Test
  def eth_syncing(): Unit = {
    val expectedSyncStatus =
      """{
        "currentBlock": "0xfa",
        "startingBlock": "0xc8",
        "highestBlock": "0x12c"
      }"""
    assertJsonEquals(expectedSyncStatus, rpc("eth_syncing"))
  }

  @Test
  def eth_getTransactionByHash(): Unit = {
    val validCases = Table(
      ("Transaction hash", "Expected output"),
      ("0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253", expectedTxView),
      ("0x123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba", "null")
    )

    forAll(validCases) { (input, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getTransactionByHash", input))
    }

    forAll(invalidCasesHashes) { input =>
      assertThrows[RpcException] {
        rpc("eth_getTransactionByHash", input)
      }
    }
  }

  @Test
  def eth_getTransactionReceipt(): Unit = {
    // TODO: add more txs and look at log index (should increase with more than one transaction)
    val validCases = Table(
      ("Transaction hash", "Expected output"),
      (
        "0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253",
        s"""{"type":"0x2","transactionHash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","from":"0xd123b689dad8ed6b99f8bd55eed64ab357e6a8d1","to":null,"cumulativeGasUsed":"0x3e8","gasUsed":"0x12d687","contractAddress":"0x1122334455667788990011223344556677889900","logs":[{"address":"0xd2a538a476aad6ecd245099df9297df6a129c2c5","topics":["0x0000000000000000000000000000000000000000000000000000000000000000","0x1111111111111111111111111111111111111111111111111111111111111111","0x2222222222222222222222222222222222222222222222222222222222222222","0x3333333333333333333333333333333333333333333333333333333333333333"],"data":"0xaabbccddeeff","blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","transactionHash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","logIndex":"0x0","removed":false},{"address":"0xd2a538a476aad6ecd245099df9297df6a129c2c5","topics":["0x0000000000000000000000000000000000000000000000000000000000000000","0x1111111111111111111111111111111111111111111111111111111111111111","0x2222222222222222222222222222222222222222222222222222222222222222","0x3333333333333333333333333333333333333333333333333333333333333333"],"data":"0xaabbccddeeff","blockHash":"0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc","blockNumber":"0x2","transactionHash":"0x6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253","transactionIndex":"0x0","logIndex":"0x1","removed":false}],"logsBloom":"0x00000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000020000000010000080000000000000000000020000002000000000000800000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000002000000000000002000000000800000000000000000000000000008000000000000020000000000000000000000000000000000000000000000000000000000000000000","status":"0x1","effectiveGasPrice":"0x342770c1"}"""
      ),
      ("0x123cfae639e9fcab216904adf931d55cc2cc54668dab04365437927b9cb2c7ba", "null")
    )

    forAll(validCases) { (input, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getTransactionReceipt", input))
    }

    forAll(invalidCasesHashes) { input =>
      assertThrows[RpcException] {
        rpc("eth_getTransactionReceipt", input)
      }
    }
  }

  @Test
  def eth_getTransactionByBlockNumberAndIndex(): Unit = {
    val validCases = Table(
      ("Block tag", "Transaction index", "Expected output"),
      ("latest", "0x0", expectedTxView),
      (null, "0x0", expectedTxView),
      ("0x2", "0x0", expectedTxView),
      ("2", "0x0", expectedTxView),
      ("1", "0x0", "null"),
      ("earliest", "0x0", "null"),
      ("earliest", "0x1", "null"),
      ("0x1337", "0x0", "null")
    )

    val invalidCases = Table(
      ("Block tag", "Transaction index"),
      // the "safe" block is not available here (mininum block height > 100)
      ("safe", "0"),
      // the "finalized" block is not available here (mininum block height > 100)
      ("finalized", "0"),
      // invalid block tag
      ("aaaa", "0"),
      // transaction index has to be hex formatted
      ("0x1337", "0")
    )

    forAll(validCases) { (tag, index, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getTransactionByBlockNumberAndIndex", tag, index))
    }

    forAll(invalidCases) { (tag, index) =>
      assertThrows[RpcException] {
        rpc("eth_getTransactionByBlockNumberAndIndex", tag, index)
      }
    }
  }

  @Test
  def eth_getTransactionByBlockHashAndIndex(): Unit = {
    val validCases = Table(
      ("Block hash", "Transaction index", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "0x0", expectedTxView),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "0x1", "null"),
      ("0x0000000000000000000000000000000000000000000000000000000000000123", "0x0", "null"),
      ("0x0000000000000000000000000000000000000000000000000000000000000456", "0x0", "null")
    )

    val invalidCases = Table(
      ("Block hash", "Transaction index"),
      // null is not allowed
      ("null", "0"),
      // missing prefix
      ("dc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "0"),
      // too short
      ("0x1337", "0")
    )

    forAll(validCases) { (tag, index, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getTransactionByBlockHashAndIndex", tag, index))
    }

    forAll(invalidCases) { (tag, index) =>
      assertThrows[RpcException] {
        rpc("eth_getTransactionByBlockHashAndIndex", tag, index)
      }
    }
  }

  @Test
  def eth_sendRawTransaction(): Unit = {
    val validRawTx =
      "0xf86f82674685031b1de8ce83019a289452cceccf519c4575a3cbf3bff5effa5e9181cec4880b9f5bd224727a808025a0cdf8d5eb0f83dff14c87aee3ff7cb373780520117fe735de78bc5eb25e700beba00b7120958d87d26425fd70d1e4c2bfb4022392417bc567887eafd5d7da09ccdf"
    val validCases = Table(
      ("Transaction", "Expected output"),
      (validRawTx, "\"0xe0499a7e779f0b82a292accd57ad4015635b2d43897c5ea7989c55049ed5b824\"")
    )

    val invalidCases = Table(
      "Raw transaction",
      // missing prefix
      validRawTx.drop(2),
      // invalid tx, missing last byte
      validRawTx.dropRight(2),
      // spurious data at the end
      validRawTx ++ "00",
      // garbage data
      "0x123cfae639e9fcab216"
    )

    forAll(validCases) { (input, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_sendRawTransaction", input))
    }

    forAll(invalidCases) { input =>
      assertThrows[RpcException] {
        rpc("eth_sendRawTransaction", input)
      }
    }
  }

  @Test
  def eth_getBlockByNumber(): Unit = {
    val validCases = Table(
      ("Block tag", "Full transaction objects", "Expected output"),
      ("latest", true, expectedBlockViewTxHydrated),
      ("latest", false, expectedBlockViewTxHashes),
      ("0x2", true, expectedBlockViewTxHydrated),
      // blocks that are not available should result in null
      ("safe", true, "null"),
      ("finalized", true, "null"),
      ("0x1337", true, "null"),
    )

    val invalidCases =
      Table(
        ("Block tag / number", "Full transaction objects"),
        ("aaaa", true),
      )

    forAll(validCases) { (tag, fullTx, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getBlockByNumber", tag, fullTx))
    }

    forAll(invalidCases) { (tag, fullTx) =>
      assertThrows[RpcException] {
        rpc("eth_getBlockByNumber", tag, fullTx)
      }
    }
  }

  @Test
  def eth_getBlockByHash(): Unit = {
    val validCases = Table(
      ("Block hash", "Full transaction objects", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", true, expectedBlockViewTxHydrated),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", false, expectedBlockViewTxHashes)
    )

    val invalidCases = Table(
      ("Block hash", "Full transaction objects"),
      ("0x1337", true),
      ("1337abcd", true)
    )

    forAll(validCases) { (hash, fullTx, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getBlockByHash", hash, fullTx))
    }

    forAll(invalidCases) { (hash, fullTx) =>
      assertThrows[RpcException] {
        rpc("eth_getBlockByHash", hash, fullTx)
      }
    }
  }

  @Test
  def eth_getBlockTransactionCountByNumber(): Unit = {
    val validCases = Table(
      ("Block tag / index", "Expected output"),
      ("latest", "\"0x1\""),
      ("0x2", "\"0x1\""),
      ("0x1", "\"0x0\""),
      ("0x1337", "null")
    )

    val invalidCases = Table("Block tag / index", "1337abcd")

    forAll(validCases) { (tag, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getBlockTransactionCountByNumber", tag))
    }

    forAll(invalidCases) { tag =>
      assertThrows[RpcException] {
        rpc("eth_getBlockTransactionCountByNumber", tag)
      }
    }
  }

  @Test
  def eth_getBlockTransactionCountByHash(): Unit = {
    val validCases = Table(
      ("Block hash", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "\"0x1\""),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "\"0x1\""),
      ("0x0000000000000000000000000000000000000000000000000000000000000123", "\"0x0\"")
    )

    val invalidCases = Table("Block hash", "0x1337", "1337abcd")

    forAll(validCases) { (hash, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getBlockTransactionCountByHash", hash))
    }

    forAll(invalidCases) { hash =>
      assertThrows[RpcException] {
        rpc("eth_getBlockTransactionCountByHash", hash)
      }
    }
  }

  @Test
  def eth_sendTransaction(): Unit = {
    val validCases = Table(
      ("Transaction parameters", "Expected output"),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
          "gas" -> "0x76c0",
          "gasPrice" -> "0x9184e72a000",
          "value" -> "0x9184e72a",
          "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
          "nonce" -> "0x1",
        ),
        "\"0x2fe27cbdd1034b4077f3b37b531de7ee751f2d36068d3793ac5a9b23713c61e1\""
      ),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
          "gas" -> "0x76c0",
          "gasPrice" -> "0x9184e72a000",
          "value" -> "0x0",
          "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
          "nonce" -> "0x1",
        ),
        "\"0x629a4e92ea4968420d29e0986f20fffed84f01296c24e51363a9369be4daca6c\""
      ),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
          "gas" -> "0x76c0",
          "gasPrice" -> "0x9184e72a000",
          "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
          "nonce" -> "0x1",
        ),
        "\"0x629a4e92ea4968420d29e0986f20fffed84f01296c24e51363a9369be4daca6c\""
      ),
    )

    val invalidCases = Table(
      "Transaction",
      // invalid params
      "aaaa",
      // invalid sender, private key for that account is not in the wallet
      Map(
        "from" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
        "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
        "gas" -> "0x76c0",
        "gasPrice" -> "0x9184e72a000",
        "value" -> "0x9184e72a",
        "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
      )
    )

    forAll(validCases) { (params, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_sendTransaction", params))
    }

    forAll(invalidCases) { tx =>
      assertThrows[RpcException] {
        rpc("eth_sendTransaction", tx)
      }
    }
  }

  @Test
  def eth_signTransaction(): Unit = {
    val validCases = Table(
      ("Transaction parameters", "Expected output"),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
          "gas" -> "0x76c0",
          "gasPrice" -> "0x9184e72a000",
          "value" -> "0x9184e72a",
          "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
          "nonce" -> "0x1",
        ),
        "\"0xf892018609184e72a0008276c09452cceccf519c4575a3cbf3bff5effa5e9181cec4849184e72aa9d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f0724456751ca016a663f0c1372024737b6498df67128b64ad77fa4e29cce26195efc3d47b36eda02bb2a98dd10ae161d98adb4d127ede506ab155dc4730e5978167a27b965916c8\""
      )
    )

    val invalidCases = Table(
      "Transaction",
      // invalid params
      "aaaa",
      // invalid sender, private key for that account is not in the wallet
      Map(
        "from" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
        "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
        "gas" -> "0x76c0",
        "gasPrice" -> "0x9184e72a000",
        "value" -> "0x9184e72a",
        "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
        "nonce" -> "0x1",
      ),
    )

    forAll(validCases) { (params, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_signTransaction", params))
    }

    forAll(invalidCases) { tx =>
      assertThrows[RpcException] {
        rpc("eth_signTransaction", tx)
      }
    }
  }

  @Test
  def eth_sign(): Unit = {
    val validCases = Table(
      ("Sender", "message", "Expected output"),
      (
        senderWithSecret,
        "0xdeadbeef",
        "\"0x7bc625667af8bd9665fdded8dde91683d78c1186765679dc610e1eda1b4ea3ad5632a25794a6daad146c9e829bce987f56314f4824938e38b7fdbcf79f8133031c\""
      )
    )

    val invalidCases = Table(("sender", "message"), ("asd", "message"), ("aaaa", "message"))

    forAll(validCases) { (sender, message, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_sign", sender, message))
    }

    forAll(invalidCases) { (sender, message) =>
      assertThrows[RpcException] {
        rpc("eth_sign", sender, message)
      }
    }
  }

  /**
   * invalid cases for functions that should return errors if address or block number / tag parameter are invalid, e.g.
   *   - eth_getBalance
   *   - eth_getTransactionCount
   *   - eth_getCode
   */
  private val invalidCasesAddressAndTag = Table(
    ("Address", "Tag"),
    // address: empty is not valid
    ("", "latest"),
    // address: invalid
    ("0x", "latest"),
    // address: missing prefix
    ("1234567890123456789012345678901234567890", "latest"),
    // address: too short
    ("0x123456789012345678901234567890123456789", "latest"),
    // address: too long
    ("0x12345678901234567890123456789012345678900", "latest"),
    // tag: empty string is not valid
    ("0x1234567890123456789012345678901234567890", ""),
    // tag: block with number 0x1337 does not exist
    ("0x1234567891011121314151617181920212223242", "0x1337")
  )

  @Test
  def eth_getBalance(): Unit = {
    val validCases = Table(
      ("Address", "tag", "Expected output"),
      ("0x1234567891011121314151617181920212223242", "latest", "\"0x7b\""),
      ("0x1234567891011121314151617181920212223241", "latest", "\"0x16345785d89ffff\"")
    )

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getBalance", address, tag))
    }

    forAll(invalidCasesAddressAndTag) { (address, tag) =>
      assertThrows[RpcException] {
        rpc("eth_getBalance", address, tag)
      }
    }
  }

  @Test
  def eth_getCode(): Unit = {
    val validCases = Table(
      ("Address", "Tag", "Expected output"),
      ("0x1234567891011121314151617181920212223242", "latest", "\"0x1234\""),
      ("0x1234567890123456789012345678901234567890", "latest", "\"0x\"")
    )

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getCode", address, tag))
    }

    forAll(invalidCasesAddressAndTag) { (address, tag) =>
      assertThrows[RpcException] {
        rpc("eth_getCode", address, tag)
      }
    }
  }

  @Test
  def eth_getTransactionCount(): Unit = {
    val validCases = Table(
      ("Address", "Tag", "Expected output"),
      ("0x1234567891011121314151617181920212223242", "latest", "\"0x1\""),
      ("0x1234567890123456789012345678901234567890", "latest", "\"0x0\"")
    )

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getTransactionCount", address, tag))
    }

    forAll(invalidCasesAddressAndTag) { (address, tag) =>
      assertThrows[RpcException] {
        rpc("eth_getTransactionCount", address, tag)
      }
    }
  }

  @Test
  def eth_getStorageAt(): Unit = {
    val validCases = Table(
      ("Address", "Key", "Tag", "Expected output"),
      (
        "0x1234567891011121314151617181920212223242",
        "0x0",
        "latest",
        "\"0x1511111111111111111111111111111111111111111111111111111111111111\""
      ),
      (
        "0x1234567890123456789012345678901234567890",
        "0x0",
        "latest",
        "\"0x1411111111111111111111111111111111111111111111111111111111111111\""
      )
    )

    val invalidCases = Table(
      ("Address", "Key", "Tag"),
      ("0x12", "0x12", "latest"),
      ("0x1234567890123456789012345678901234567890", "0x12", "")
    )

    forAll(validCases) { (address, key, tag, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getStorageAt", address, key, tag))
    }

    forAll(invalidCases) { (address, key, tag) =>
      assertThrows[RpcException] {
        rpc("eth_getStorageAt", address, key, tag)
      }
    }
  }

  @Test
  def eth_getProof(): Unit = {
    def proofMockData(address: String) =
      s"""{"address":"$address","accountProof":["123"],"balance":"0x7b","codeHash":null,"nonce":"0x1","storageHash":null,"storageProof":[]}"""
    val validCases = Table(
      ("Address", "Tag", "Expected output"),
      (
        "0x1234567891011121314151617181920212223242",
        "latest",
        proofMockData("0x1234567891011121314151617181920212223242"),
      ),
      (
        "0x1234567890123456789012345678901234567890",
        "latest",
        proofMockData("0x1234567890123456789012345678901234567890"),
      )
    )

    val invalidCases = Table(
      ("Address", "Tag"),
      // invalid address
      ("0x12", "latest"),
      // invalid tag parameter: empty string is not allowed
      ("0x1234567890123456789012345678901234567890", "")
    )

    forAll(validCases) { (address, tag, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_getProof", address, Array("0x1", "0x2"), tag))
    }

    forAll(invalidCases) { (address, tag) =>
      assertThrows[RpcException] {
        rpc("eth_getProof", address, Array("0x1", "0x2"), tag)
      }
    }
  }

  @Test
  def eth_accounts(): Unit = {
    assertJsonEquals(s"""["$senderWithSecret"]""", rpc("eth_accounts"))
  }

  @Test
  def zen_getFeePayments(): Unit = {
    val validCases = Table(
      ("Block id", "Expected output"),
      ("0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "null"),
      ("0x2", "null"),
    )

    val invalidCases =
      Table("Block id", "null", "dc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc", "latest")

    forAll(validCases) { (id, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("zen_getFeePayments", id))
    }

    forAll(invalidCases) { id =>
      assertThrows[RpcException] {
        rpc("zen_getFeePayments", id)
      }
    }
  }

  @Test
  def zen_getForwardTransfers(): Unit = {
    val validCases = Table(
      ("Block id", "Expected output"),
      (
        "0xdc7ac3d7de9d7fc524bbb95025a98c3e9290b041189ee73c638cf981e7f99bfc",
        """{"forwardTransfers":[
          {"to":"0x5d3eff12e7c2f48e1bd660694101049f8fb678c9","value":"0x51b7d5554400"},
          {"to":"0xaea09d1e14cbf1604dc36c76cc9d5cb1e7e493a7","value":"0x378d4bb3f000"}
        ]}"""
      ),
      (
        "0x2",
        """{"forwardTransfers":[
          {"to":"0x5d3eff12e7c2f48e1bd660694101049f8fb678c9","value":"0x51b7d5554400"},
          {"to":"0xaea09d1e14cbf1604dc36c76cc9d5cb1e7e493a7","value":"0x378d4bb3f000"}
        ]}"""
      )
    )

    val invalidCases = Table(
      "Block id",
      "0x1337",
      "1337abcd",
      "latest",
      "pending"
    )

    forAll(validCases) { (id, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("zen_getForwardTransfers", id))
    }

    forAll(invalidCases) { id =>
      assertThrows[RpcException] {
        rpc("zen_getForwardTransfers", id)
      }
    }
  }

  @Test
  def eth_estimateGas(): Unit = {
    val validCases = Table(
      ("Transaction args", "Expected output"),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x0000000000000000000022222222222222222222",
          "value" -> "0xE8D4A51000",
          "data" -> "0x",
          "gasPrice" -> "0x4B9ACA00",
          "nonce" -> "0x1",
        ),
        "\"0x5208\""
      ),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x0000000000000000000011111111111111111111",
          "value" -> "0xE8D4A51000",
          "data" -> "0x4267ec5edbcbaf2b14a48cfc24941ef5acfdac0a8c590255000000000000000000000000",
          "gasPrice" -> "0x4B9ACA00",
          "nonce" -> "0x1",
        ),
        "\"0x53b8\""
      )
    )

    forAll(validCases) { (transactionArgs, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_estimateGas", transactionArgs))
    }

    val invalidCases = Table(
      "Transaction args",
      Map(
        "from" -> senderWithSecret,
        "to" -> "0x0000000000000000000022222222222222222222",
        "value" -> "0x999999999900000000000000000",
        "data" -> "5ca748ff1122334455669988112233445566778811223344556677881122334455667788aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff00123400000000000000000000000000000000000000000000000000000000000000000000000000000000000000bbdf1daf64ed9d6e30f80b93f647b8bc6ea13191",
        "gasPrice" -> "0x4B9ACA00",
        "nonce" -> "0x1",
      )
    )

    forAll(invalidCases) { transactionArgs =>
      assertThrows[RpcException] {
        rpc("eth_estimateGas", transactionArgs)
      }
    }
  }

  @Test
  def eth_call(): Unit = {
    val validCases = Table(
      ("Transaction args", "Expected output"),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x0000000000000000000022222222222222222222",
          "value" -> "0xE8D4A51000",
          "data" -> "0x",
          "gasPrice" -> "0x4B9ACA00",
          "nonce" -> "0x1",
        ),
        "\"0x\""
      ),
      (
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x0000000000000000000011111111111111111111",
          "value" -> "0xE8D4A51000",
          "data" -> "0x4267ec5edbcbaf2b14a48cfc24941ef5acfdac0a8c590255000000000000000000000000",
          "gasPrice" -> "0x4B9ACA00",
          "nonce" -> "0x1",
        ),
        "\"0x\""
      )
    )

    forAll(validCases) { (transactionArgs, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("eth_call", transactionArgs))
    }

    val invalidCases = Table(
      "Transaction args",
      Map(
        "from" -> senderWithSecret,
        "to" -> "0x0000000000000000000022222222222222222222",
        "value" -> "0x999999999900000000000000000",
        "data" -> "0x5ca748ff1122334455669988112233445566778811223344556677881122334455667788aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff00123400000000000000000000000000000000000000000000000000000000000000000000000000000000000000bbdf1daf64ed9d6e30f80b93f647b8bc6ea13191",
        "gasPrice" -> "0x4B9ACA00",
        "nonce" -> "0x1",
      ),
      Map(
        "from" -> senderWithSecret,
        "to" -> "0x0000000000000000000022222222222222222222",
        "value" -> "0x0",
        "data" -> "5ca748ff1122334455669988112233445566778811223344556677881122334455667788aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff00123400000000000000000000000000000000000000000000000000000000000000000000000000000000000000bbdf1daf64ed9d6e30f80b93f647b8bc6ea13191",
        "gasPrice" -> "0x4B9ACA00",
        "nonce" -> "0x1",
      )
    )

    forAll(invalidCases) { transactionArgs =>
      assertThrows[RpcException] {
        rpc("eth_call", transactionArgs)
      }
    }
  }

  @Test
  def eth_feeHistory(): Unit = {
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
      assertJsonEquals(expectedOutput, rpc("eth_feeHistory", nrOfBlocks, tag, rewardPercentiles))
    }

    val invalidCases =
      Table(("Block count", "Newest block (tag)", "Reward percentiles"), ("0x1", "latest", Array(20, 50, 130)))

    forAll(invalidCases) { (nrOfBlocks, tag, rewardPercentiles) =>
      assertThrows[RpcException] {
        rpc("eth_feeHistory", nrOfBlocks, tag, rewardPercentiles)
      }
    }
  }

  @Test
  def invalidJsonRpcData(): Unit = {
    // Test 1: Try to read json request with missing data
    val json = """{"jsonrpc":"2.0","id":"1","params":[]}}"""
    val request = (new ObjectMapper).readTree(json)
    assertThrows[RpcException] {
      new RpcRequest(request)
    }

    // Test 2: Wrong number of parameters
    assertThrows[RpcException] {
      rpc("eth_estimateGas", 5, 10, 20)
    }

    // Test 3: Trigger IllegalArgumentException rpc call
    assertThrows[RpcException] {
      rpc("eth_estimateGas", -1)
    }
  }

  @Test
  def txpool_status(): Unit = {
    assertJsonEquals(txPoolStatusOutput, rpc("txpool_status"))
  }

  @Test
  def txpool_content(): Unit = {
    assertJsonEquals(txPoolContentOutput, rpc("txpool_content"))
  }

  @Test
  def txpool_contentFrom(): Unit = {
    assertJsonEquals(txPoolContentFromOutput, rpc("txpool_contentFrom", "0x15532e34426cd5c37371ff455a5ba07501c0f522"))
  }

  @Test
  def txpool_inspect(): Unit = {
    assertJsonEquals(txPoolInspectOutput, rpc("txpool_inspect"))
  }

  @Test
  def eth_getSHA3(): Unit = {
    val validCases = Table(
      ("Input", "Expected output"),
      ("0x68656c6c6f20776f726c64", "\"0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad\""),
      ("0x68656C6C6F20776F726C64", "\"0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad\""),
      ("0x", "\"0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470\""),
    )

    val invalidCases = Table("Input",
        "0X68656C6C6F20776F726C64",
        "0x68656c6c6f20776f726c642",
        "0x68656c6c6f20776f726c6w",
        "68656c6c6f20776f726c6w",
        ""
      )

    forAll(validCases) { (input, expectedOutput) =>
      assertJsonEquals(expectedOutput, rpc("web3_sha3", input))
    }

    forAll(invalidCases) { input =>
      assertThrows[RpcException] {
        rpc("web3_sha3", input)
      }
    }
  }

}
