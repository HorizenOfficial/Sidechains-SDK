package com.horizen.api.http

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.{TestActorRef, TestKit}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.{SidechainHistory, SidechainNodeViewHolder, SidechainSettings, SidechainSyncInfo}
import com.horizen.forge.Forger
import com.horizen.node.SidechainNodeView
import com.horizen.params.MainNetParams
import com.horizen.transaction.Transaction
import org.mockito.internal.creation.MockSettingsImpl
import org.mockito.{ArgumentMatchers, MockSettings, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import scorex.core.PersistentNodeViewModifier
import scorex.core.network.NetworkController
import scorex.core.network.peer.PeerManager
import scorex.core.settings.{RESTApiSettings, ScorexSettings}
import scorex.core.utils.NetworkTimeProvider

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class SidechainApiRouteTest extends WordSpec with Matchers with ScalatestRouteTest with MockitoSugar {

  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler
  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val utilMocks = new SidechainNodeViewUtilMocks()

  val mockedRESTSettings : RESTApiSettings = mock[RESTApiSettings]
  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 3 seconds)

  val mockedSidechainSettings : SidechainSettings = mock[SidechainSettings]
  Mockito.when(mockedSidechainSettings.scorexSettings).thenAnswer(_ => {
    val mockedScorexSettings : ScorexSettings = mock[ScorexSettings]
    Mockito.when(mockedScorexSettings.restApi).thenAnswer(_ => mockedRESTSettings)
    mockedScorexSettings
  })

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-api-routes")

  val mockedSidechainNodeViewHolder : SidechainNodeViewHolder = mock[SidechainNodeViewHolder]
  //(
   // new MockSettingsImpl[SidechainNodeViewHolder]().useConstructor(mockedSidechainSettings.leftSide)
  //)
  def f: SidechainNodeView => Route = ???
  Mockito.when(mockedSidechainNodeViewHolder.receive(
    ArgumentMatchers.any[GetDataFromCurrentSidechainNodeView(ArgumentMatchers.)]
  )).thenAnswer(asw => {
    case SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView(f) => f(utilMocks.getSidechainNodeView())
  } )

  val mockedSidechainNodeViewHolderRef : ActorRef = actorSystem.actorOf(Props(mockedSidechainNodeViewHolder))

  val mockedSidechainTransactioActor : SidechainTransactionActor[_ <: Transaction] = mock[SidechainTransactionActor[_ <: Transaction]]
  val mockedSidechainTransactioActorRef : ActorRef = actorSystem.actorOf(Props(mockedSidechainTransactioActor))

  val mockedPeerManagerActor : PeerManager = mock[PeerManager]
  val mockedPeerManagerRef : ActorRef = actorSystem.actorOf(Props(mockedPeerManagerActor))

  val mockedNetworkControllerActor: NetworkController = mock[NetworkController]
  val mockedNetworkControllerRef : ActorRef = actorSystem.actorOf(Props(mockedNetworkControllerActor))

  val mockedTimeProvider : NetworkTimeProvider = mock[NetworkTimeProvider]

  val mockedSidechainBlockForgerActor : Forger = mock[Forger]
  val mockedSidechainBlockForgerActorRef : ActorRef = actorSystem.actorOf(Props(mockedSidechainBlockForgerActor))

  val mockedSidechainBlockActor : SidechainBlockActor[_ <: PersistentNodeViewModifier, _ <: SidechainSyncInfo, _ <: SidechainHistory] = mock[SidechainBlockActor[_ <: PersistentNodeViewModifier, _ <: SidechainSyncInfo, _ <: SidechainHistory]]
  val mockedsidechainBlockActorRef : ActorRef = actorSystem.actorOf(Props(mockedSidechainBlockActor))

  implicit def default() = RouteTestTimeout(3.second)

  val sidechainTransactionApiRoute : Route = SidechainTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactioActorRef).route
  val sidechainWalletApiRoute : Route = SidechainWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainUtilApiRoute : Route = SidechainUtilApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainNodeApiRoute : Route = SidechainNodeApiRoute(mockedPeerManagerRef, mockedNetworkControllerRef, mockedTimeProvider, mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainBlockApiRoute : Route = SidechainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedsidechainBlockActorRef, mockedSidechainBlockForgerActorRef).route
  val mainchainBlockApiRoute : Route = MainchainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route

  val basePath : String

}
