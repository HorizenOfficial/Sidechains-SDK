package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper, SerializationFeature}
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, GetDataFromCurrentSidechainNodeView, LocallyGeneratedSecret}
import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.AccountNodeView
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http._
import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.consensus.ConsensusEpochAndSlot
import com.horizen.cryptolibprovider.utils.CircuitTypes
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.forge.AbstractForger
import com.horizen.params.MainNetParams
import com.horizen.secret.SecretSerializer
import com.horizen.serialization.ApplicationJsonSerializer
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.runner.RunWith
import org.mindrot.jbcrypt.BCrypt
import org.mockito.Mockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import sparkz.util.ModifierId
import sparkz.core.bytesToId
import sparkz.core.settings.RESTApiSettings
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@RunWith(classOf[JUnitRunner])
abstract class AccountSidechainApiRouteTest extends AnyWordSpec with Matchers with ScalatestRouteTest with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes {
  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
  val genesisBlock: AccountBlock = mock[AccountBlock]

  val jsonChecker = new SidechainJSONBOChecker

  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  // DO NOT REMOVE THIS LINE
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  val utilMocks = new AccountNodeViewUtilMocks()

  val memoryPool: java.util.List[EthereumTransaction] = utilMocks.transactionList

  val credentials = HttpCredentials.createBasicHttpCredentials("username","password")
  val badCredentials = HttpCredentials.createBasicHttpCredentials("username","wrong_password")
  val apiKeyHash = BCrypt.hashpw(credentials.password(), BCrypt.gensalt())

  val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 1 seconds)
  Mockito.when(mockedRESTSettings.apiKeyHash).thenAnswer(_ => Some(apiKeyHash))

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

  val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
  val sidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)
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

  val mockedSidechainBlockForgerActor = TestProbe()
  mockedSidechainBlockForgerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case AbstractForger.ReceivableMessages.StopForging => {
          if (sidechainApiMockConfiguration.should_blockActor_StopForging_reply) {
            sender ! Success(Unit)
          }
          else {
            sender ! Failure(new IllegalStateException("Stop forging error"))
          }
        }
        case AbstractForger.ReceivableMessages.StartForging => {
          if (sidechainApiMockConfiguration.should_blockActor_StartForging_reply) {
            sender ! Success(Unit)
          }
          else {
            sender ! Failure(new IllegalStateException("Start forging error"))
          }
        }

        case AbstractForger.ReceivableMessages.GetForgingInfo => {
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

        case AbstractForger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot(epoch, slot, _) => {

          sidechainApiMockConfiguration.blockActor_ForgingEpochAndSlot_reply.get(ConsensusEpochAndSlot(epoch, slot)) match {
            case Some(blockIdTry) => sender ! Future[Try[ModifierId]] {
              blockIdTry
            }
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
  val mockedSidechainBlockActorRef: ActorRef = mockedSidechainBlockActor.ref

  implicit def default() = RouteTestTimeout(3.second)

  val params = mock[MainNetParams]
  Mockito.when(params.chainId).thenReturn(1997L)

  val sidechainTransactionApiRoute: Route = AccountTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactionActorRef,sidechainTransactionsCompanion, params, CircuitTypes.NaiveThresholdSignatureCircuit).route
  val sidechainWalletApiRoute: Route = AccountWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, sidechainSecretsCompanion).route
  val sidechainBlockApiRoute: Route = AccountBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainBlockActorRef, sidechainTransactionsCompanion, mockedSidechainBlockForgerActorRef, params).route

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

