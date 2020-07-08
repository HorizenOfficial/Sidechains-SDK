package com.horizen.api.http

import java.net.{InetAddress, InetSocketAddress}
import java.util

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper, SerializationFeature}
import com.google.inject.{Guice, Injector}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.{ApplyFunctionOnNodeView, GetDataFromCurrentSidechainNodeView, LocallyGeneratedSecret}
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus.ConsensusEpochAndSlot
import com.horizen.fixtures.{CompanionsFixture, DefaultInjectorStub, SidechainBlockFixture}
import com.horizen.forge.Forger
import com.horizen.forge.Forger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.transaction._
import com.horizen.{SidechainSettings, SidechainTypes}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import scorex.core.app.Version
import scorex.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import scorex.core.network.peer.PeerInfo
import scorex.core.network.peer.PeerManager.ReceivableMessages.{GetAllPeers, GetBlacklistedPeers}
import scorex.core.network.{Incoming, Outgoing, PeerSpec}
import scorex.core.settings.{RESTApiSettings, ScorexSettings}
import scorex.core.utils.NetworkTimeProvider
import scorex.util.{ModifierId, bytesToId}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

@RunWith(classOf[JUnitRunner])
abstract class SidechainApiRouteTest extends WordSpec with Matchers with ScalatestRouteTest with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes {

  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val jsonChecker = new SidechainJSONBOChecker

  private val inetAddr1 = new InetSocketAddress("92.92.92.92", 27017)
  private val inetAddr2 = new InetSocketAddress("93.93.93.93", 27017)
  private val inetAddr3 = new InetSocketAddress("94.94.94.94", 27017)
  val inetAddrBlackListed_1 = new InetSocketAddress("95.95.95.95", 27017)
  val inetAddrBlackListed_2 = new InetSocketAddress("96.96.96.96", 27017)
  val peersInfo: Array[PeerInfo] = Array(
    PeerInfo(PeerSpec("app", Version.initial, "first", Some(inetAddr1), Seq()), System.currentTimeMillis() - 100, Some(Incoming)),
    PeerInfo(PeerSpec("app", Version.initial, "second", Some(inetAddr2), Seq()), System.currentTimeMillis() + 100, Some(Outgoing)),
    PeerInfo(PeerSpec("app", Version.initial, "second", Some(inetAddr3), Seq()), System.currentTimeMillis() + 200, Some(Outgoing))
  )
  val peers: Map[InetSocketAddress, PeerInfo] = Map(
    inetAddr1 -> peersInfo(0),
    inetAddr2 -> peersInfo(1),
    inetAddr3 -> peersInfo(2)
  )
  val connectedPeers: Seq[PeerInfo] = Seq(peersInfo(0), peersInfo(2))

  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  // DO NOT REMOVE THIS LINE
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  val utilMocks = new SidechainNodeViewUtilMocks()


  val memoryPool: util.List[RegularTransaction] = utilMocks.transactionList
  val allBoxes = utilMocks.allBoxes
  val genesisBlock = utilMocks.genesisBlock

  val mainchainBlockReferenceInfoRef = utilMocks.mainchainBlockReferenceInfoRef

  val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 1 seconds)

  val mockedSidechainSettings: SidechainSettings = mock[SidechainSettings]
  Mockito.when(mockedSidechainSettings.scorexSettings).thenAnswer(_ => {
    val mockedScorexSettings: ScorexSettings = mock[ScorexSettings]
    Mockito.when(mockedScorexSettings.restApi).thenAnswer(_ => mockedRESTSettings)
    mockedScorexSettings
  })

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-api-routes")

  val mockedSidechainNodeViewHolder = TestProbe()
  mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetDataFromCurrentSidechainNodeView(f) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply())
            sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration))
        case ApplyFunctionOnNodeView(f) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetDataFromCurrentSidechainNodeView_reply())
            sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration))
        case LocallyGeneratedSecret(_) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_LocallyGeneratedSecret_reply())
            sender ! Success()
          else sender ! Failure(new Exception("Secret not added."))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

  val mockedSidechainTransactioActor = TestProbe()
  mockedSidechainTransactioActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case BroadcastTransaction(t) =>
          if (sidechainApiMockConfiguration.getShould_transactionActor_BroadcastTransaction_reply()) sender ! Future.successful()
          else sender ! Future.failed(new Exception("Broadcast failed."))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainTransactioActorRef: ActorRef = mockedSidechainTransactioActor.ref

  val mockedPeerManagerActor = TestProbe()
  mockedPeerManagerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetAllPeers =>
          if (sidechainApiMockConfiguration.getSshould_peerManager_GetAllPeers_reply())
            sender ! peers
          else sender ! Failure(new Exception("No peers."))
        case GetBlacklistedPeers =>
          if (sidechainApiMockConfiguration.getShould_peerManager_GetBlacklistedPeers_reply())
            sender ! Seq[InetAddress](inetAddrBlackListed_1.getAddress, inetAddrBlackListed_2.getAddress)
          else new Exception("No black listed peers.")
      }
      TestActor.KeepRunning
    }
  })
  val mockedPeerManagerRef: ActorRef = mockedPeerManagerActor.ref

  val mockedNetworkControllerActor = TestProbe()
  mockedNetworkControllerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetConnectedPeers =>
          if (sidechainApiMockConfiguration.getShould_networkController_GetConnectedPeers_reply())
            sender ! connectedPeers
          else sender ! Failure(new Exception("No connected peers."))
        case ConnectTo(_) =>
      }
      TestActor.KeepRunning
    }
  })
  val mockedNetworkControllerRef: ActorRef = mockedNetworkControllerActor.ref

  val mockedTimeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]

  val mockedSidechainBlockForgerActor = TestProbe()
  mockedSidechainBlockForgerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case Forger.ReceivableMessages.StopForging => {
          if (sidechainApiMockConfiguration.should_blockActor_StopForging_reply) {
            sender ! Success()
          }
          else {
            sender ! Failure(new IllegalStateException("Stop forging error"))
          }
        }
        case Forger.ReceivableMessages.StartForging => {
          if (sidechainApiMockConfiguration.should_blockActor_StartForging_reply) {
            sender ! Success()
          }
          else {
            sender ! Failure(new IllegalStateException("Start forging error"))
          }
        }

        case Forger.ReceivableMessages.GetForgingInfo => {
          sender ! sidechainApiMockConfiguration.should_blockActor_ForgingInfo_reply
        }
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainBlockForgerActorRef: ActorRef = mockedSidechainBlockForgerActor.ref

  val mockedSidechainBlockActor = TestProbe()
  mockedSidechainBlockActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case TryForgeNextBlockForEpochAndSlot(epoch, slot) => {
          sidechainApiMockConfiguration.blockActor_ForgingEpochAndSlot_reply.get(ConsensusEpochAndSlot(epoch, slot)) match {
            case Some(blockIdTry) => sender ! Future[Try[ModifierId]]{blockIdTry}
            case None => sender ! Failure(new RuntimeException("Forge is failed"))
          }
        }

        case SubmitSidechainBlock(b) =>
          if (sidechainApiMockConfiguration.getShould_blockActor_SubmitSidechainBlock_reply()) sender ! Future[Try[ModifierId]](Try(genesisBlock.id))
          else sender ! Future[Try[ModifierId]](Failure(new Exception("Block actor not configured for submit the block.")))
        case GenerateSidechainBlocks(count) =>
          if (sidechainApiMockConfiguration.getShould_blockActor_GenerateSidechainBlocks_reply())
            sender ! Future[Try[Seq[ModifierId]]](Try(Seq(
              bytesToId("block_id_1".getBytes),
              bytesToId("block_id_2".getBytes),
              bytesToId("block_id_3".getBytes),
              bytesToId("block_id_4".getBytes))))
          else sender ! Future[Try[ModifierId]](Failure(new Exception("Block actor not configured for generate blocks.")))
      }
      TestActor.KeepRunning
    }
  })
  val mockedsidechainBlockActorRef: ActorRef = mockedSidechainBlockActor.ref

  implicit def default() = RouteTestTimeout(3.second)

  val injector: Injector = Guice.createInjector(new DefaultInjectorStub())
  val sidechainCoreTransactionFactory = injector.getInstance(classOf[SidechainCoreTransactionFactory])

  val sidechainTransactionApiRoute: Route = SidechainTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactioActorRef,
    sidechainTransactionsCompanion, sidechainCoreTransactionFactory).route
  val sidechainWalletApiRoute: Route = SidechainWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainNodeApiRoute: Route = SidechainNodeApiRoute(mockedPeerManagerRef, mockedNetworkControllerRef, mockedTimeProvider, mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainBlockApiRoute: Route = SidechainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedsidechainBlockActorRef, mockedSidechainBlockForgerActorRef).route
  val mainchainBlockApiRoute: Route = MainchainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val applicationApiRoute: Route = ApplicationApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, new SimpleCustomApi()).route
  val walletBalanceApiRejected: Route = SidechainRejectionApiRoute("wallet", "balance", mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val walletApiRejected: Route = SidechainRejectionApiRoute("wallet", "", mockedRESTSettings, mockedSidechainNodeViewHolderRef).route


  val basePath: String

  protected def assertsOnSidechainErrorResponseSchema(msg: String, errorCode: String): Unit = {
    mapper.readTree(msg).get("error") match {
      case error: JsonNode =>
        assertEquals(1, error.findValues("code").size())
        assertEquals(1, error.findValues("description").size())
        assertEquals(1, error.findValues("detail").size())
        assertTrue(error.get("code").isTextual)
        assertEquals(errorCode, error.get("code").asText())
      case _ => fail("Serialization failed for object SidechainApiErrorResponseScheme")
    }
  }

}