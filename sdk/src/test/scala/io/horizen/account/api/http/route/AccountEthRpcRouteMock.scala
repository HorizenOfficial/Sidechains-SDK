package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorSystem}
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import at.favre.lib.crypto.bcrypt.BCrypt
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, GetDataFromCurrentSidechainNodeView, LocallyGeneratedSecret}
import io.horizen.account.api.http.AccountNodeViewUtilMocks
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.node.AccountNodeView
import io.horizen.account.state.MessageProcessor
import io.horizen.account.storage.AccountStateMetadataStorage
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import io.horizen.api.http._
import io.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import io.horizen.json.serializer.ApplicationJsonSerializer
import io.horizen.params.MainNetParams
import io.horizen.{SidechainSettings, SidechainTypes}
import io.horizen.evm.LevelDBDatabase
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.network.NetworkController.ReceivableMessages.GetConnectedPeers
import sparkz.core.settings.{NetworkSettings, RESTApiSettings, SparkzSettings}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

@RunWith(classOf[JUnitRunner])
abstract class AccountEthRpcRouteMock extends AnyWordSpec with Matchers with ScalatestRouteTest with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes {
  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion

  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  val utilMocks = new AccountNodeViewUtilMocks()

  val credentials = HttpCredentials.createBasicHttpCredentials("username","password")
  val badCredentials = HttpCredentials.createBasicHttpCredentials("username","wrong_password")
  val apiKeyHash = BCrypt.`with`(BCrypt.Version.VERSION_2Y).hashToString(12, credentials.password().toCharArray)

  val memoryPool: java.util.List[EthereumTransaction] = utilMocks.transactionList
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

  implicit def default() = RouteTestTimeout(3.second)

  val params = MainNetParams()

  val mockedSidechainSettings = mock[SidechainSettings]
  Mockito.when(mockedSidechainSettings.sparkzSettings).thenReturn(mock[SparkzSettings])
  Mockito.when(mockedSidechainSettings.sparkzSettings.network).thenReturn(mock[NetworkSettings])
  Mockito.when(mockedSidechainSettings.sparkzSettings.network.maxIncomingConnections).thenReturn(10)

  val metadataStorage = mock[AccountStateMetadataStorage]
  val stateDb = mock[LevelDBDatabase]
  val messageProcessors = mock[Seq[MessageProcessor]]

  val ethRpcRoute: Route = AccountEthRpcRoute(
    mockedRESTSettings,
    mockedSidechainNodeViewHolderRef,
    mockedNetworkControllerRef,
    mockedSidechainSettings,
    params,
    mockedSidechainTransactionActorRef,
    metadataStorage,
    stateDb,
    messageProcessors
  ).route

  val basePath = "/ethv1"

}

