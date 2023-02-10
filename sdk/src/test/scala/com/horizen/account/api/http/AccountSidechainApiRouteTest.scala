package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, GetDataFromCurrentSidechainNodeView, LocallyGeneratedSecret}
import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.transaction.{AccountTransaction, EthereumTransaction}
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.{ApiTokenHeader, SidechainApiErrorHandler, SidechainApiMockConfiguration, SidechainApiRejectionHandler, SidechainJSONBOChecker}
import com.horizen.cryptolibprovider.utils.CircuitTypes
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.node.NodeWalletBase
import com.horizen.params.MainNetParams
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.serialization.ApplicationJsonSerializer
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.settings.RESTApiSettings

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

@RunWith(classOf[JUnitRunner])
abstract class AccountSidechainApiRouteTest extends AnyWordSpec with Matchers with ScalatestRouteTest with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes {
  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
  val apiTokenHeader = new ApiTokenHeader("api_key", "Horizen")
  val badApiTokenHeader = new ApiTokenHeader("api_key", "Harizen")

  val jsonChecker = new SidechainJSONBOChecker

//  private val inetAddr1 = new InetSocketAddress("92.92.92.92", 27017)
//  private val inetAddr2 = new InetSocketAddress("93.93.93.93", 27017)
//  private val inetAddr3 = new InetSocketAddress("94.94.94.94", 27017)
//  val inetAddrBlackListed_1 = new InetSocketAddress("95.95.95.95", 27017)
//  val inetAddrBlackListed_2 = new InetSocketAddress("96.96.96.96", 27017)
//  val peersInfo: Array[PeerInfo] = Array(
//    PeerInfo(PeerSpec("app", Version.initial, "first", Some(inetAddr1), Seq()), System.currentTimeMillis() - 100, Some(Incoming)),
//    PeerInfo(PeerSpec("app", Version.initial, "second", Some(inetAddr2), Seq()), System.currentTimeMillis() + 100, Some(Outgoing)),
//    PeerInfo(PeerSpec("app", Version.initial, "third", Some(inetAddr3), Seq()), System.currentTimeMillis() + 200, Some(Outgoing))
//  )
//  val peers: Map[InetSocketAddress, PeerInfo] = Map(
//    inetAddr1 -> peersInfo(0),
//    inetAddr2 -> peersInfo(1),
//    inetAddr3 -> peersInfo(2)
//  )
//  val connectedPeers: Seq[PeerInfo] = Seq(peersInfo(0), peersInfo(2))
//
  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  // DO NOT REMOVE THIS LINE
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  val utilMocks = new AccountNodeViewUtilMocks()

  val memoryPool: java.util.List[EthereumTransaction] = utilMocks.transactionList
//  val genesisBlock = utilMocks.genesisBlock
//
//  val mainchainBlockReferenceInfoRef = utilMocks.mainchainBlockReferenceInfoRef
//
  val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 1 seconds)
  Mockito.when(mockedRESTSettings.apiKeyHash).thenAnswer(_ => Some("aa8ed2a907753a4a7c66f2aa1d48a0a74d4fde9a6ef34bae96a86dcd7800af98"))


  //  val mockedSidechainSettings: SidechainSettings = mock[SidechainSettings]
//  Mockito.when(mockedSidechainSettings.scorexSettings).thenAnswer(_ => {
//    val mockedScorexSettings: ScorexSettings = mock[ScorexSettings]
//    Mockito.when(mockedScorexSettings.restApi).thenAnswer(_ => mockedRESTSettings)
//    mockedScorexSettings
//  })
//
  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-api-routes")

  val mockedSidechainNodeViewHolder = TestProbe()
  mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case m: GetDataFromCurrentSidechainNodeView[
          AccountNodeView,
          _] @unchecked =>
          m match {
            case GetDataFromCurrentSidechainNodeView(f) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetDataFromCurrentNodeView_reply()) {
                sender ! f(utilMocks.getAccountNodeView(sidechainApiMockConfiguration))
              }
          }
        case m: ApplyFunctionOnNodeView[
          AccountNodeView,
          _] @unchecked =>
          m match {
            case ApplyFunctionOnNodeView(f) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_ApplyFunctionOnNodeView_reply())
                sender ! f(utilMocks.getAccountNodeView(sidechainApiMockConfiguration))
          }
        case m: ApplyBiFunctionOnNodeView[
          AccountNodeView,
          _,
          _] @unchecked =>
          m match {
            case ApplyBiFunctionOnNodeView(f, funParameter) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_ApplyBiFunctionOnNodeView_reply())
                sender ! f(utilMocks.getAccountNodeView(sidechainApiMockConfiguration), funParameter)
          }
        case LocallyGeneratedSecret(_) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_LocallyGeneratedSecret_reply())
            sender ! Success(Unit)
          else sender ! Failure(new Exception("Secret not added."))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

  val mockedSidechainTransactionActor = TestProbe()
  mockedSidechainTransactionActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case BroadcastTransaction(t) =>
          if (sidechainApiMockConfiguration.getShould_transactionActor_BroadcastTransaction_reply()) sender ! Future.successful(Unit)
          else sender ! Future.failed(new Exception("Broadcast failed."))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainTransactionActorRef: ActorRef = mockedSidechainTransactionActor.ref

//  val mockedPeerManagerActor = TestProbe()
//  mockedPeerManagerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
//    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
//      msg match {
//        case GetAllPeers =>
//          if (sidechainApiMockConfiguration.getSshould_peerManager_GetAllPeers_reply())
//            sender ! peers
//          else sender ! Failure(new Exception("No peers."))
//        case GetBlacklistedPeers =>
//          if (sidechainApiMockConfiguration.getShould_peerManager_GetBlacklistedPeers_reply())
//            sender ! Seq[InetAddress](inetAddrBlackListed_1.getAddress, inetAddrBlackListed_2.getAddress)
//          else new Exception("No black listed peers.")
//      }
//      TestActor.KeepRunning
//    }
//  })
//  val mockedPeerManagerRef: ActorRef = mockedPeerManagerActor.ref
//
//  val mockedNetworkControllerActor = TestProbe()
//  mockedNetworkControllerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
//    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
//      msg match {
//        case GetConnectedPeers =>
//          if (sidechainApiMockConfiguration.getShould_networkController_GetConnectedPeers_reply())
//            sender ! connectedPeers
//          else sender ! Failure(new Exception("No connected peers."))
//        case ConnectTo(_) =>
//      }
//      TestActor.KeepRunning
//    }
//  })
//  val mockedNetworkControllerRef: ActorRef = mockedNetworkControllerActor.ref
//
//  val mockedTimeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]
//
//  val mockedSidechainBlockForgerActor = TestProbe()
//  mockedSidechainBlockForgerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
//    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
//      msg match {
//        case AbstractForger.ReceivableMessages.StopForging => {
//          if (sidechainApiMockConfiguration.should_blockActor_StopForging_reply) {
//            sender ! Success(Unit)
//          }
//          else {
//            sender ! Failure(new IllegalStateException("Stop forging error"))
//          }
//        }
//        case AbstractForger.ReceivableMessages.StartForging => {
//          if (sidechainApiMockConfiguration.should_blockActor_StartForging_reply) {
//            sender ! Success(Unit)
//          }
//          else {
//            sender ! Failure(new IllegalStateException("Start forging error"))
//          }
//        }
//
//        case AbstractForger.ReceivableMessages.GetForgingInfo => {
//          sender ! sidechainApiMockConfiguration.should_blockActor_ForgingInfo_reply
//        }
//      }
//      TestActor.KeepRunning
//    }
//  })
//  val mockedSidechainBlockForgerActorRef: ActorRef = mockedSidechainBlockForgerActor.ref
//
//  val mockedSidechainBlockActor = TestProbe()
//  mockedSidechainBlockActor.setAutoPilot(new testkit.TestActor.AutoPilot {
//    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
//      msg match {
//        case AbstractForger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot(epoch, slot) => {
//          sidechainApiMockConfiguration.blockActor_ForgingEpochAndSlot_reply.get(ConsensusEpochAndSlot(epoch, slot)) match {
//            case Some(blockIdTry) => sender ! Future[Try[ModifierId]]{blockIdTry}
//            case None => sender ! Failure(new RuntimeException("Forge is failed"))
//          }
//        }
//
//        case SubmitSidechainBlock(b) =>
//          if (sidechainApiMockConfiguration.getShould_blockActor_SubmitSidechainBlock_reply()) sender ! Future[Try[ModifierId]](Try(genesisBlock.id))
//          else sender ! Future[Try[ModifierId]](Failure(new Exception("Block actor not configured for submit the block.")))
//        case GenerateSidechainBlocks(count) =>
//          if (sidechainApiMockConfiguration.getShould_blockActor_GenerateSidechainBlocks_reply())
//            sender ! Future[Try[Seq[ModifierId]]](Try(Seq(
//              bytesToId("block_id_1".getBytes(StandardCharsets.UTF_8)),
//              bytesToId("block_id_2".getBytes(StandardCharsets.UTF_8)),
//              bytesToId("block_id_3".getBytes(StandardCharsets.UTF_8)),
//              bytesToId("block_id_4".getBytes(StandardCharsets.UTF_8)))))
//          else sender ! Future[Try[ModifierId]](Failure(new Exception("Block actor not configured for generate blocks.")))
//      }
//      TestActor.KeepRunning
//    }
//  })
//  val mockedsidechainBlockActorRef: ActorRef = mockedSidechainBlockActor.ref
//
//  val mockedCswManagerActor = TestProbe()
//  mockedCswManagerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
//    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
//      msg match {
//        case GetCeasedStatus => {
//          sender ! true
//        }
//        case GetCswBoxIds => {
//          sender ! Seq(ByteUtils.fromHexString("1111"), ByteUtils.fromHexString("2222"), ByteUtils.fromHexString("3333"))
//        }
//        case GetCswInfo(boxId) => {
//          val expectedBoxId: Array[Byte] = getRandomBoxId(0)
//          if (boxId.deep != expectedBoxId.deep) {
//            sender ! Failure(new IllegalArgumentException("CSW info was not found for given box id."))
//          } else {
//            sender ! Success(CswInfo("UtxoCswData", // pure class name
//              42,
//              ByteUtils.fromHexString("ABCD"),
//              ByteUtils.fromHexString("FFFF"),
//              CswProofInfo(Absent, Some(ByteUtils.fromHexString("FBFB")), Some("SomeDestination")),
//              Some(ByteUtils.fromHexString("BBBB")),
//              ByteUtils.fromHexString("CCCC")))
//          }
//        }
//        case GetBoxNullifier(boxId) => {
//          val expectedBoxId: Array[Byte] = getRandomBoxId(0)
//          if (boxId.deep != expectedBoxId.deep) {
//            sender ! Failure(new IllegalArgumentException("Box was not found for given box id."))
//          } else {
//            sender ! Success(ByteUtils.fromHexString("FAFA"))
//          }
//        }
//        case GenerateCswProof(boxId, receiverAddress) => {
//          val expectedBoxId: Array[Byte] = getRandomBoxId(0)
//          if (boxId.deep != expectedBoxId.deep) {
//            sender ! NoProofData
//          } else {
//            sender ! ProofCreationFinished
//          }
//        }
//      }
//      TestActor.KeepRunning
//    }
//  })
//  val mockedCswManagerActorRef: ActorRef = mockedCswManagerActor.ref
//
  implicit def default() = RouteTestTimeout(3.second)

  val params = MainNetParams()

  val sidechainTransactionApiRoute: Route = AccountTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactionActorRef,sidechainTransactionsCompanion, params, CircuitTypes.NaiveThresholdSignatureCircuit).route
//  val sidechainWalletApiRoute: Route = SidechainWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
//  val sidechainNodeApiRoute: Route = SidechainNodeApiRoute(mockedPeerManagerRef, mockedNetworkControllerRef, mockedTimeProvider, mockedRESTSettings).route
//  val sidechainBlockApiRoute: Route = SidechainBlockApiRoute[BoxTransaction[Proposition, Box[Proposition]],
//    SidechainBlockHeader,SidechainBlock,NodeHistory,NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedsidechainBlockActorRef, mockedSidechainBlockForgerActorRef).route
//  val mainchainBlockApiRoute: Route = MainchainBlockApiRoute[BoxTransaction[Proposition, Box[Proposition]],
//    SidechainBlockHeader,SidechainBlock,NodeHistory,NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
//  val applicationApiRoute: Route = ApplicationApiRoute(mockedRESTSettings, new SimpleCustomApi(), mockedSidechainNodeViewHolderRef).route
//  val sidechainCswApiRoute: Route = SidechainCswApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedCswManagerActorRef).route
//  val walletCoinsBalanceApiRejected: Route = SidechainRejectionApiRoute("wallet", "coinsBalance", mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
//  val walletApiRejected: Route = SidechainRejectionApiRoute("wallet", "", mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
//

  val basePath: String

//  protected def assertsOnSidechainErrorResponseSchema(msg: String, errorCode: String): Unit = {
//    mapper.readTree(msg).get("error") match {
//      case error: JsonNode =>
//        assertEquals(1, error.findValues("code").size())
//        assertEquals(1, error.findValues("description").size())
//        assertEquals(1, error.findValues("detail").size())
//        assertTrue(error.get("code").isTextual)
//        assertEquals(errorCode, error.get("code").asText())
//      case _ => fail("Serialization failed for object SidechainApiErrorResponseScheme")
//    }
//  }
//
}

