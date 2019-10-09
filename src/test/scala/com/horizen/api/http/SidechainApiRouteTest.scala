package com.horizen.api.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.SidechainSettings
import com.horizen.serialization.ApplicationJsonSerializer
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import scorex.core.settings.{RESTApiSettings, ScorexSettings}
import scorex.core.utils.NetworkTimeProvider

import scala.concurrent.duration._

abstract class SidechainApiRouteTest extends WordSpec with Matchers with ScalatestRouteTest with MockitoSugar {

  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler
  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  val sidechainApiMockConfiguration : SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper : ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

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

  val mockedSidechainNodeViewHolder = TestProbe()
  mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetDataFromCurrentSidechainNodeView(f) => sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration))
      }
      TestActor.KeepRunning
    }
  })
  val mockedSidechainNodeViewHolderRef : ActorRef = mockedSidechainNodeViewHolder.ref

  val mockedSidechainTransactioActor = TestProbe()
  mockedSidechainTransactioActor.setAutoPilot(TestActor.KeepRunning)
  val mockedSidechainTransactioActorRef : ActorRef = mockedSidechainTransactioActor.ref

  val mockedPeerManagerActor = TestProbe()
  mockedPeerManagerActor.setAutoPilot(TestActor.KeepRunning)
  val mockedPeerManagerRef : ActorRef = mockedPeerManagerActor.ref

  val mockedNetworkControllerActor = TestProbe()
  mockedNetworkControllerActor.setAutoPilot(TestActor.KeepRunning)
  val mockedNetworkControllerRef : ActorRef = mockedNetworkControllerActor.ref

  val mockedTimeProvider : NetworkTimeProvider = mock[NetworkTimeProvider]

  val mockedSidechainBlockForgerActor = TestProbe()
  mockedSidechainBlockForgerActor.setAutoPilot(TestActor.KeepRunning)
  val mockedSidechainBlockForgerActorRef : ActorRef = mockedSidechainBlockForgerActor.ref

  val mockedSidechainBlockActor = TestProbe()
  mockedSidechainBlockActor.setAutoPilot(TestActor.KeepRunning)
  val mockedsidechainBlockActorRef : ActorRef = mockedSidechainBlockActor.ref

  implicit def default() = RouteTestTimeout(3.second)

  val sidechainTransactionApiRoute : Route = SidechainTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactioActorRef).route
  val sidechainWalletApiRoute : Route = SidechainWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainUtilApiRoute : Route = SidechainUtilApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainNodeApiRoute : Route = SidechainNodeApiRoute(mockedPeerManagerRef, mockedNetworkControllerRef, mockedTimeProvider, mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainBlockApiRoute : Route = SidechainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedsidechainBlockActorRef, mockedSidechainBlockForgerActorRef).route
  val mainchainBlockApiRoute : Route = MainchainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef).route

  val basePath : String

}
