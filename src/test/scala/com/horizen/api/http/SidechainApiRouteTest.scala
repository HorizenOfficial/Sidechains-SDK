package com.horizen.api.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestActorRef
import com.horizen.{SidechainHistory, SidechainNodeViewHolder, SidechainSyncInfo}
import com.horizen.forge.Forger
import com.horizen.transaction.Transaction
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import scorex.core.PersistentNodeViewModifier
import scorex.core.network.NetworkController
import scorex.core.network.peer.PeerManager
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.NetworkTimeProvider

import scala.concurrent.duration._

abstract class SidechainApiRouteTest extends WordSpec with Matchers with ScalatestRouteTest with MockitoSugar {

  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler
  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val mockedSidechainNodeViewHolder : SidechainNodeViewHolder = mock[SidechainNodeViewHolder]
  val mockedSidechainNodeViewHolderRef : ActorRef = TestActorRef(mockedSidechainNodeViewHolder)

  val mockedSidechainTransactioActor : SidechainTransactionActor[_ <: Transaction] = mock[SidechainTransactionActor[_ <: Transaction]]
  val mockedSidechainTransactioActorRef : ActorRef = TestActorRef(mockedSidechainTransactioActor)

  val mockedPeerManagerActor : PeerManager = mock[PeerManager]
  val mockedPeerManagerRef : ActorRef = TestActorRef(mockedPeerManagerActor)

  val mockedNetworkControllerActor: NetworkController = mock[NetworkController]
  val mockedNetworkControllerRef : ActorRef = TestActorRef(mockedNetworkControllerActor)

  val mockedTimeProvider : NetworkTimeProvider = mock[NetworkTimeProvider]

  val mockedSidechainBlockActor : SidechainBlockActor[_ <: PersistentNodeViewModifier, _ <: SidechainSyncInfo, _ <: SidechainHistory] = mock[SidechainBlockActor[_ <: PersistentNodeViewModifier, _ <: SidechainSyncInfo, _ <: SidechainHistory]]
  val mockedSidechainBlockForgerActor : Forger = mock[Forger]

  val mockedsidechainBlockActorRef : ActorRef = TestActorRef(mockedSidechainBlockActor)
  val mockedSidechainBlockForgerActorRef : ActorRef = TestActorRef(mockedSidechainBlockForgerActor)

  val mockedRESTSettings : RESTApiSettings = mock[RESTApiSettings]

  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 3 seconds)
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(3.second)

  val sidechainTransactionApiRoute : Route = SidechainTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactioActorRef).route
  val sidechainWalletApiRoute : Route = SidechainWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainUtilApiRoute : Route = SidechainUtilApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainNodeApiRoute : Route = SidechainNodeApiRoute(mockedPeerManagerRef, mockedNetworkControllerRef, mockedTimeProvider, mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainBlockApiRoute : Route = SidechainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedsidechainBlockActorRef, mockedSidechainBlockForgerActorRef).route
  val mainchainBlockApiRoute : Route = MainchainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route

  val basePath : String

}
