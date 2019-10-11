package com.horizen.api.http

import java.time.Instant
import java.{lang, util}

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.{SidechainSettings, SidechainTypes}
import com.horizen.api.http.schema.SECRET_NOT_ADDED
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.forge.Forger.ReceivableMessages.TryGetBlockTemplate
import com.horizen.params.MainNetParams
import com.horizen.secret.PrivateKey25519Creator
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.transaction.TransactionSerializer
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import scorex.core.settings.{RESTApiSettings, ScorexSettings}
import scorex.core.utils.NetworkTimeProvider
import scorex.util.{ModifierId, bytesToId}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

@RunWith(classOf[JUnitRunner])
abstract class SidechainApiRouteTest extends WordSpec with Matchers with ScalatestRouteTest with MockitoSugar {

  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  private val genesisBlock = SidechainBlock.create(bytesToId(new Array[Byte](32)), Instant.now.getEpochSecond - 10000, Seq(), Seq(),
    PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(6543211L).getBytes),
    SidechainTransactionsCompanion(new util.HashMap[lang.Byte, TransactionSerializer[SidechainTypes#SCBT]]()), null).get

  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  val utilMocks = new SidechainNodeViewUtilMocks()

  val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 3 seconds)

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
        case GetDataFromCurrentSidechainNodeView(f) => sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

  val mockedSidechainTransactioActor = TestProbe()
  mockedSidechainTransactioActor.setAutoPilot(TestActor.KeepRunning)
  val mockedSidechainTransactioActorRef: ActorRef = mockedSidechainTransactioActor.ref

  val mockedPeerManagerActor = TestProbe()
  mockedPeerManagerActor.setAutoPilot(TestActor.KeepRunning)
  val mockedPeerManagerRef: ActorRef = mockedPeerManagerActor.ref

  val mockedNetworkControllerActor = TestProbe()
  mockedNetworkControllerActor.setAutoPilot(TestActor.KeepRunning)
  val mockedNetworkControllerRef: ActorRef = mockedNetworkControllerActor.ref

  val mockedTimeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]

  val mockedSidechainBlockForgerActor = TestProbe()
  mockedSidechainBlockForgerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case TryGetBlockTemplate =>
          if (sidechainApiMockConfiguration.getShould_forger_TryGetBlockTemplate_reply())
            sender ! Try(genesisBlock)
          else sender ! Failure(new Exception(s"Unable to collect information for block template creation."))

      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainBlockForgerActorRef: ActorRef = mockedSidechainBlockForgerActor.ref

  val mockedSidechainBlockActor = TestProbe()
  mockedSidechainBlockActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case SubmitSidechainBlock(b) =>
          if (sidechainApiMockConfiguration.getShould_blockActor_SubmitSidechainBlock_reply()) sender ! Future[Try[ModifierId]](Try(genesisBlock.id))
          else sender ! Future[Try[ModifierId]](Failure(new Exception("Block actor not configured for submit the block.")))
        case GenerateSidechainBlocks(count) =>
          if(sidechainApiMockConfiguration.getShould_blockActor_GenerateSidechainBlocks_reply())
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

  val sidechainTransactionApiRoute: Route = SidechainTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactioActorRef).route
  val sidechainWalletApiRoute: Route = SidechainWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainUtilApiRoute: Route = SidechainUtilsApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainNodeApiRoute: Route = SidechainNodeApiRoute(mockedPeerManagerRef, mockedNetworkControllerRef, mockedTimeProvider, mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainBlockApiRoute: Route = SidechainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedsidechainBlockActorRef, mockedSidechainBlockForgerActorRef).route
  val mainchainBlockApiRoute: Route = MainchainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, new MainNetParams()).route

  val basePath: String

  protected def assertsOnSidechainErrorResponseSchema(msg: String, errorCode: String): Unit = {
    mapper.readTree(msg).get("error") match {
      case error =>
        assertEquals(1, error.findValues("code").size())
        assertEquals(1, error.findValues("description").size())
        assertEquals(1, error.findValues("detail").size())
        assertTrue(error.get("code").isTextual)
        assertEquals(errorCode, error.get("code").asText())
      case _ => fail("Serialization failed for object SidechainApiErrorResponseScheme")
    }
  }

}
