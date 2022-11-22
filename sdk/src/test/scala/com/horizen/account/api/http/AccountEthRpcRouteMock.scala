package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, GetDataFromCurrentSidechainNodeView, LocallyGeneratedSecret}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state.MessageProcessor
import com.horizen.account.storage.AccountStateMetadataStorage
import com.horizen.account.transaction.{AccountTransaction, EthereumTransaction}
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http._
import com.horizen.evm.LevelDBDatabase
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.node.NodeWalletBase
import com.horizen.params.MainNetParams
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.{SidechainSettings, SidechainTypes}
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
abstract class AccountEthRpcRouteMock extends AnyWordSpec with Matchers with ScalatestRouteTest with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes {
  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
  val apiTokenHeader = new ApiTokenHeader("api_key", "Horizen")
  val badApiTokenHeader = new ApiTokenHeader("api_key", "Harizen")

  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  val utilMocks = new AccountNodeViewUtilMocks()

  val memoryPool: java.util.List[EthereumTransaction] = utilMocks.transactionList
  val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 1 seconds)
  Mockito.when(mockedRESTSettings.apiKeyHash).thenAnswer(_ => Some("aa8ed2a907753a4a7c66f2aa1d48a0a74d4fde9a6ef34bae96a86dcd7800af98"))

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-api-routes")

  val mockedSidechainNodeViewHolder = TestProbe()
  mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case m: GetDataFromCurrentSidechainNodeView[
          AccountTransaction[Proposition, Proof[Proposition]],
          AccountBlockHeader,
          AccountBlock,
          AccountFeePaymentsInfo,
          NodeAccountHistory,
          NodeAccountState,
          NodeWalletBase,
          NodeAccountMemoryPool,
          AccountNodeView,
          _] @unchecked =>
          m match {
            case GetDataFromCurrentSidechainNodeView(f) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetDataFromCurrentNodeView_reply()) {
                sender ! f(utilMocks.getAccountNodeView(sidechainApiMockConfiguration))
              }
          }
        case m: ApplyFunctionOnNodeView[
          AccountTransaction[Proposition, Proof[Proposition]],
          AccountBlockHeader,
          AccountBlock,
          AccountFeePaymentsInfo,
          NodeAccountHistory,
          NodeAccountState,
          NodeWalletBase,
          NodeAccountMemoryPool,
          AccountNodeView,
          _] @unchecked =>
          m match {
            case ApplyFunctionOnNodeView(f) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_ApplyFunctionOnNodeView_reply())
                sender ! f(utilMocks.getAccountNodeView(sidechainApiMockConfiguration))
          }
        case m: ApplyBiFunctionOnNodeView[
          AccountTransaction[Proposition, Proof[Proposition]],
          AccountBlockHeader,
          AccountBlock,
          AccountFeePaymentsInfo,
          NodeAccountHistory,
          NodeAccountState,
          NodeWalletBase,
          NodeAccountMemoryPool,
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

  implicit def default() = RouteTestTimeout(3.second)

  val params = MainNetParams()

  val sidechainSettings = mock[SidechainSettings]
  val metadataStorage = mock[AccountStateMetadataStorage]
  val stateDb = mock[LevelDBDatabase]
  val messageProcessors = mock[Seq[MessageProcessor]]

  val ethRpcRoute: Route = AccountEthRpcRoute(
    mockedRESTSettings,
    mockedSidechainNodeViewHolderRef,
    sidechainSettings,
    params,
    mockedSidechainTransactionActorRef,
    metadataStorage,
    stateDb,
    messageProcessors
  ).route

  val basePath = "/ethv1"

}

