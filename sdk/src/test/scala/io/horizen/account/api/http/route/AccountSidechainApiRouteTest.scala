package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper, SerializationFeature}
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages._
import io.horizen.SidechainTypes
import io.horizen.account.api.http.AccountNodeViewUtilMocks
import io.horizen.account.block.AccountBlock
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.fixtures.BasicAuthenticationFixture
import io.horizen.account.node.AccountNodeView
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import io.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import io.horizen.api.http._
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.consensus.ConsensusEpochAndSlot
import io.horizen.cryptolibprovider.CircuitTypes
import io.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import io.horizen.forge.AbstractForger
import io.horizen.json.serializer.ApplicationJsonSerializer
import io.horizen.params.MainNetParams
import io.horizen.secret.SecretSerializer
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.bytesToId
import sparkz.core.settings.RESTApiSettings
import sparkz.util.ModifierId

import java.lang.{Byte => JByte}
import java.nio.charset.StandardCharsets
import java.util.{HashMap => JHashMap}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@RunWith(classOf[JUnitRunner])
abstract class AccountSidechainApiRouteTest extends AnyWordSpec with Matchers with ScalatestRouteTest with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes with BasicAuthenticationFixture{
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

  val credentials = getBasicAuthCredentials()
  val badCredentials = getBasicAuthCredentials(password = "wrong_password")
  val apiKeyHash = getBasicAuthApiKeyHash(credentials.password())

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
        case GenerateSecret(_) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GenerateSecret_reply())
            sender ! Success(Unit)
          else sender ! Failure(new Exception("Secret not added."))
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
              bytesToId("block_id_1".getBytes(StandardCharsets.UTF_8)),
              bytesToId("block_id_2".getBytes(StandardCharsets.UTF_8)),
              bytesToId("block_id_3".getBytes(StandardCharsets.UTF_8)),
              bytesToId("block_id_4".getBytes(StandardCharsets.UTF_8)))))
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

