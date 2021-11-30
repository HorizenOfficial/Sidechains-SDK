package com.horizen.csw

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestActor, TestActorRef, TestProbe}
import akka.util.Timeout
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.fixtures.CswDataFixture
import com.horizen.{SidechainAppEvents, SidechainHistory, SidechainMemoryPool, SidechainSettings, SidechainState, SidechainWallet}
import com.horizen.params.NetworkParams
import com.horizen.utils.{CswData, ForwardTransferCswData, UtxoCswData, WithdrawalEpochInfo}
import io.iohk.iodb.ByteArrayWrapper
import org.junit.Assert._
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.settings.{RESTApiSettings, ScorexSettings}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class CswManagerTest extends JUnitSuite with MockitoSugar with CswDataFixture {
  implicit lazy val actorSystem: ActorSystem = ActorSystem("submitter-actor-test")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")
  implicit val timeout: Timeout = 100 milliseconds

  private def getMockedSettings(timeoutDuration: FiniteDuration): SidechainSettings = {
    val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
    Mockito.when(mockedRESTSettings.timeout).thenReturn(timeoutDuration)

    val mockedSidechainSettings: SidechainSettings = mock[SidechainSettings]
    Mockito.when(mockedSidechainSettings.scorexSettings).thenAnswer(_ => {
      val mockedScorexSettings: ScorexSettings = mock[ScorexSettings]
      Mockito.when(mockedScorexSettings.restApi).thenAnswer(_ => mockedRESTSettings)
      mockedScorexSettings
    })

    mockedSidechainSettings
  }

  @Test
  def initializationFailure_MissingNodeViewHolder(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration)
    val params: NetworkParams = mock[NetworkParams]

    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val cswManagerRef: TestActorRef[CswManager] = TestActorRef(
      Props(new CswManager(mockedSettings, params, mockedSidechainNodeViewHolderRef)))

    val deathWatch = TestProbe()
    deathWatch.watch(cswManagerRef)

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)
    deathWatch.expectTerminated(cswManagerRef, timeout.duration * 2)
  }

  @Test
  def initialization_NotCeased(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration)
    val params: NetworkParams = mock[NetworkParams]

    val history: SidechainHistory = mock[SidechainHistory]
    val state: SidechainState = mock[SidechainState]
    val wallet: SidechainWallet = mock[SidechainWallet]

    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          sender ! f(CurrentView(history, state, wallet, mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    // Set SC alive
    Mockito.when(state.hasCeased).thenReturn(false)

    val cswManagerRef: TestActorRef[CswManager] = TestActorRef(
      Props(new CswManager(mockedSettings, params, mockedSidechainNodeViewHolderRef)))

    val cswManager: CswManager = cswManagerRef.underlyingActor

    val watch = TestProbe()
    watch.watch(cswManagerRef)

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)
    watch.expectNoMessage(timeout.duration)

    assertFalse("Sidechain expected to be alive.", cswManager.hasSidechainCeased)
    assertTrue("No csw witness data expected to be defined.", cswManager.cswWitnessHolderOpt.isEmpty)
  }

  @Test
  def initialization_Ceased(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration)
    val params: NetworkParams = mock[NetworkParams]

    val history: SidechainHistory = mock[SidechainHistory]
    val state: SidechainState = mock[SidechainState]
    val wallet: SidechainWallet = mock[SidechainWallet]

    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          sender ! f(CurrentView(history, state, wallet, mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    // Set SC has ceased
    Mockito.when(state.hasCeased).thenReturn(true)

    val withdrawalEpochInfo = WithdrawalEpochInfo(3, 10)
    val cswDataMap: Map[Int, Seq[CswData]] = Map(
      withdrawalEpochInfo.epoch -> Seq(getUtxoCswData(1L), getForwardTransferCswData(2L)),
      withdrawalEpochInfo.epoch - 1 -> Seq(getUtxoCswData(3L), getForwardTransferCswData(4L)),
      withdrawalEpochInfo.epoch - 2 -> Seq(getUtxoCswData(5L), getForwardTransferCswData(6L)),
      withdrawalEpochInfo.epoch - 3 -> Seq(getUtxoCswData(7L), getUtxoCswData(8L), getForwardTransferCswData(9L))
    )
    val certificate: WithdrawalEpochCertificate = mock[WithdrawalEpochCertificate]
    // Mock loadCswWitness() related methods
    Mockito.when(state.getWithdrawalEpochInfo).thenReturn(withdrawalEpochInfo)
    Mockito.when(state.certificate(ArgumentMatchers.any[Int]())).thenAnswer(args => {
      val epochNumber: Int = args.getArgument(0)
      assertEquals("Different referenced epoch number expected for certificate() method", withdrawalEpochInfo.epoch - 3, epochNumber)
      Some(certificate)
    })

    Mockito.when(wallet.getCswData(ArgumentMatchers.any[Int]())).thenAnswer(args => {
      val epochNumber: Int = args.getArgument(0)
      cswDataMap(epochNumber)
    })

    val cswManagerRef: TestActorRef[CswManager] = TestActorRef(
      Props(new CswManager(mockedSettings, params, mockedSidechainNodeViewHolderRef)))

    val cswManager: CswManager = cswManagerRef.underlyingActor

    val watch = TestProbe()
    watch.watch(cswManagerRef)

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)
    watch.expectNoMessage(timeout.duration)

    assertTrue("Sidechain expected to be ceased.", cswManager.hasSidechainCeased)
    assertTrue("CSW witness data expected to be defined.", cswManager.cswWitnessHolderOpt.isDefined)

    assertEquals("Invalid CSW witness data: certificate", Some(certificate), cswManager.cswWitnessHolderOpt.get.lastActiveCertOpt)

    val expectedUtxoMap = cswDataMap(withdrawalEpochInfo.epoch - 3).flatMap(_ match {
      case utxo: UtxoCswData => Some(new ByteArrayWrapper(utxo.boxId) -> utxo)
      case _ => None
    }).toMap
    assertEquals("Invalid CSW witness data: utxoCswDataMap", expectedUtxoMap, cswManager.cswWitnessHolderOpt.get.utxoCswDataMap)

    val expectedFtMap = (withdrawalEpochInfo.epoch - 2 to withdrawalEpochInfo.epoch).flatMap(epoch => cswDataMap(epoch)).flatMap(_ match {
      case ft: ForwardTransferCswData => Some(new ByteArrayWrapper(ft.boxId) -> ft)
      case _ => None
    }).toMap

    assertEquals("Invalid CSW witness data: ftCswDataMap", expectedFtMap, cswManager.cswWitnessHolderOpt.get.ftCswDataMap)
  }
}
