package com.horizen.csw

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{TestActor, TestActorRef, TestProbe}
import akka.util.Timeout
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.csw.CswManager.{ProofInProcess, ProofInQueue}
import com.horizen.csw.CswManager.ReceivableMessages.{GetBoxNullifier, GetCeasedStatus, GetCswBoxIds, GetCswInfo}
import com.horizen.csw.CswManager.Responses.{Absent, CswInfo, CswProofInfo, Generated, InProcess, InQueue}
import com.horizen.cswnative.CswProof
import com.horizen.fixtures.CswDataFixture
import com.horizen.{SidechainAppEvents, SidechainHistory, SidechainMemoryPool, SidechainSettings, SidechainState, SidechainWallet}
import com.horizen.params.NetworkParams
import com.horizen.utils.{ByteArrayWrapper, CswData, ForwardTransferCswData, UtxoCswData, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit.{Assert, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.ChangedState
import scorex.core.settings.{RESTApiSettings, ScorexSettings}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

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


    // Test ChangedState cases
    actorSystem.eventStream.publish(ChangedState(state))
    watch.expectNoMessage(timeout.duration)
    assertTrue("Sidechain expected to be ceased.", cswManager.hasSidechainCeased)
    assertTrue("CSW witness data expected to be defined.", cswManager.cswWitnessHolderOpt.isDefined)

    // Make CswManager alive and emit event again
    cswManager.hasSidechainCeased = false
    cswManager.cswWitnessHolderOpt = None

    actorSystem.eventStream.publish(ChangedState(state))
    watch.expectNoMessage(timeout.duration)
    assertTrue("Sidechain expected to be ceased.", cswManager.hasSidechainCeased)
    assertTrue("CSW witness data expected to be defined.", cswManager.cswWitnessHolderOpt.isDefined)

    // Make state alive again and emit event
    Mockito.reset(state)
    Mockito.when(state.hasCeased).thenReturn(false)

    actorSystem.eventStream.publish(ChangedState(state))
    watch.expectNoMessage(timeout.duration)

    assertFalse("Sidechain expected to be alive.", cswManager.hasSidechainCeased)
    assertFalse("CSW witness data expected to be not defined.", cswManager.cswWitnessHolderOpt.isDefined)
  }

  @Test
  def ceasedState(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration)
    val params: NetworkParams = mock[NetworkParams]

    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref


    val cswManagerRef: TestActorRef[CswManager] = TestActorRef(
      Props(new CswManager(mockedSettings, params, mockedSidechainNodeViewHolderRef)))
    val cswManager: CswManager = cswManagerRef.underlyingActor

    // skip initialization
    cswManager.context.become(cswManager.workingCycle)


    // Test 1: get status while alive
    cswManager.hasSidechainCeased = false
    var hasCeased = Await.result(cswManagerRef ? GetCeasedStatus, timeout.duration).asInstanceOf[Boolean]
    assertFalse("SC expected to be alive", hasCeased)


    // Test 2: get status while ceased
    cswManager.hasSidechainCeased = true
    hasCeased = Await.result(cswManagerRef ? GetCeasedStatus, timeout.duration).asInstanceOf[Boolean]
    assertTrue("SC expected to be ceased", hasCeased)
  }

  @Test
  def boxNullifier(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration)
    val params: NetworkParams = mock[NetworkParams]

    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref


    val cswManagerRef: TestActorRef[CswManager] = TestActorRef(
      Props(new CswManager(mockedSettings, params, mockedSidechainNodeViewHolderRef)))
    val cswManager: CswManager = cswManagerRef.underlyingActor

    // skip initialization
    cswManager.context.become(cswManager.workingCycle)


    val utxoData1: UtxoCswData = getUtxoCswData(1000L)
    val utxoData2: UtxoCswData = getUtxoCswData(2000L)
    val utxoMap = Map(
      new ByteArrayWrapper(utxoData1.boxId) -> utxoData1,
      new ByteArrayWrapper(utxoData2.boxId) -> utxoData2
    )
    val ftData1: ForwardTransferCswData = getForwardTransferCswData(3000L)
    val ftData2: ForwardTransferCswData = getForwardTransferCswData(4000L)
    val ftMap = Map(
      new ByteArrayWrapper(ftData1.boxId) -> ftData1,
      new ByteArrayWrapper(ftData2.boxId) -> ftData2
    )

    // Test 1: sidechain is alive -> failure expected
    cswManager.hasSidechainCeased = false

    var nullifierTry = Await.result(cswManagerRef ? GetBoxNullifier(utxoData1.boxId), timeout.duration).asInstanceOf[Try[Array[Byte]]]
    nullifierTry match {
      case Success(_) => Assert.fail("Failure expected since sc is alive.")
      case Failure(_: IllegalStateException) => // expected case
      case Failure(_) => Assert.fail("Different exception found.")
    }


    // Test 2: sidechain has ceased, but no box id record exists
    cswManager.hasSidechainCeased = true
    cswManager.cswWitnessHolderOpt = Some(CswWitnessHolder(Map(), Map(), None, new Array[Byte](32), Seq(), new Array[Byte](32)))

    nullifierTry = Await.result(cswManagerRef ? GetBoxNullifier(utxoData1.boxId), timeout.duration).asInstanceOf[Try[Array[Byte]]]
    nullifierTry match {
      case Success(_) => Assert.fail("Failure expected since sc is alive.")
      case Failure(_: IllegalArgumentException) => // expected case
      case Failure(_) => Assert.fail("Different exception found.")
    }

    // Set witness holder with ft and utxo csw data entries
    cswManager.cswWitnessHolderOpt = Some(CswWitnessHolder(utxoMap, ftMap, None, new Array[Byte](32), Seq(), new Array[Byte](32)))


    // Test 3: utxo csw data found for box id
    nullifierTry = Await.result(cswManagerRef ? GetBoxNullifier(utxoData1.boxId), timeout.duration).asInstanceOf[Try[Array[Byte]]]
    nullifierTry match {
      case Success(nullifier) => assertArrayEquals("Nullifier is different.", utxoData1.getNullifier, nullifier)
      case Failure(_) => Assert.fail("Exception found.")
    }


    // Test 4: ft csw data found for box id
    nullifierTry = Await.result(cswManagerRef ? GetBoxNullifier(ftData1.boxId), timeout.duration).asInstanceOf[Try[Array[Byte]]]
    nullifierTry match {
      case Success(nullifier) => assertArrayEquals("Nullifier is different.", ftData1.getNullifier, nullifier)
      case Failure(_) => Assert.fail("Exception found.")
    }
  }

  @Test
  def cswBoxIds(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration)
    val params: NetworkParams = mock[NetworkParams]

    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref


    val cswManagerRef: TestActorRef[CswManager] = TestActorRef(
      Props(new CswManager(mockedSettings, params, mockedSidechainNodeViewHolderRef)))
    val cswManager: CswManager = cswManagerRef.underlyingActor

    // skip initialization
    cswManager.context.become(cswManager.workingCycle)


    // Test 1: sidechain is alive -> no box ids
    cswManager.hasSidechainCeased = false
    var boxIds = Await.result(cswManagerRef ? GetCswBoxIds, timeout.duration).asInstanceOf[Seq[Array[Byte]]]
    assertTrue("No box ids expected to be found while sc is alive.", boxIds.isEmpty)


    // Test 2: sidechain has ceased, but no box ids exists for the node
    cswManager.hasSidechainCeased = true
    cswManager.cswWitnessHolderOpt = Some(CswWitnessHolder(Map(), Map(), None, new Array[Byte](32), Seq(), new Array[Byte](32)))

    boxIds = Await.result(cswManagerRef ? GetCswBoxIds, timeout.duration).asInstanceOf[Seq[Array[Byte]]]
    assertTrue("No box ids expected to be found.", boxIds.isEmpty)


    // Test 3: sidechain has ceased, box ids exist
    // Set witness holder with ft and utxo csw data entries
    val utxoData1: UtxoCswData = getUtxoCswData(1000L)
    val utxoData2: UtxoCswData = getUtxoCswData(2000L)
    val utxoMap = Map(
      new ByteArrayWrapper(utxoData1.boxId) -> utxoData1,
      new ByteArrayWrapper(utxoData2.boxId) -> utxoData2
    )
    val ftData1: ForwardTransferCswData = getForwardTransferCswData(3000L)
    val ftData2: ForwardTransferCswData = getForwardTransferCswData(4000L)
    val ftMap = Map(
      new ByteArrayWrapper(ftData1.boxId) -> ftData1,
      new ByteArrayWrapper(ftData2.boxId) -> ftData2
    )
    val expectedBoxIds = Seq(utxoData1.boxId, utxoData2.boxId, ftData1.boxId, ftData2.boxId).map(new ByteArrayWrapper(_))

    cswManager.cswWitnessHolderOpt = Some(CswWitnessHolder(utxoMap, ftMap, None, new Array[Byte](32), Seq(), new Array[Byte](32)))
    boxIds = Await.result(cswManagerRef ? GetCswBoxIds, timeout.duration).asInstanceOf[Seq[Array[Byte]]]

    assertEquals("Box ids expected to be found.", utxoMap.size + ftMap.size, boxIds.size)
    assertEquals("Different box ids found.", expectedBoxIds, boxIds.map(new ByteArrayWrapper(_)))
  }

  @Test
  def cswInfo(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration)
    val params: NetworkParams = mock[NetworkParams]

    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref


    val cswManagerRef: TestActorRef[CswManager] = TestActorRef(
      Props(new CswManager(mockedSettings, params, mockedSidechainNodeViewHolderRef)))
    val cswManager: CswManager = cswManagerRef.underlyingActor

    // skip initialization
    cswManager.context.become(cswManager.workingCycle)


    val utxoData1: UtxoCswData = getUtxoCswData(1000L)
    val utxoData2: UtxoCswData = getUtxoCswData(2000L)
    val utxoMap = Map(
      new ByteArrayWrapper(utxoData1.boxId) -> utxoData1,
      new ByteArrayWrapper(utxoData2.boxId) -> utxoData2
    )
    val ftData1: ForwardTransferCswData = getForwardTransferCswData(3000L)
    val ftData2: ForwardTransferCswData = getForwardTransferCswData(4000L)
    val ftMap = Map(
      new ByteArrayWrapper(ftData1.boxId) -> ftData1,
      new ByteArrayWrapper(ftData2.boxId) -> ftData2
    )


    // Test 1: sidechain is alive -> failure expected
    cswManager.hasSidechainCeased = false

    var cswInfoTry = Await.result(cswManagerRef ? GetCswInfo(utxoData1.boxId), timeout.duration).asInstanceOf[Try[CswInfo]]
    cswInfoTry match {
      case Success(_) => Assert.fail("Failure expected since sc is alive.")
      case Failure(_: IllegalStateException) => // expected case
      case Failure(_) => Assert.fail("Different exception found.")
    }


    // Test 2: sidechain is ceased, but not box id entry found
    cswManager.hasSidechainCeased = true
    cswManager.cswWitnessHolderOpt = Some(CswWitnessHolder(Map(), Map(), None, new Array[Byte](32), Seq(), new Array[Byte](32)))

    cswInfoTry = Await.result(cswManagerRef ? GetCswInfo(utxoData1.boxId), timeout.duration).asInstanceOf[Try[CswInfo]]
    cswInfoTry match {
      case Success(_) => Assert.fail("Failure expected since sc is alive.")
      case Failure(_: IllegalArgumentException) => // expected case
      case Failure(_) => Assert.fail("Different exception found.")
    }


    // Test 3: sidechain is ceased, box id record exists
    cswManager.cswWitnessHolderOpt = Some(CswWitnessHolder(utxoMap, ftMap, None, new Array[Byte](32), Seq(), new Array[Byte](32)))
    cswInfoTry = Await.result(cswManagerRef ? GetCswInfo(utxoData1.boxId), timeout.duration).asInstanceOf[Try[CswInfo]]
    cswInfoTry match {
      case Success(cswInfo) =>
        val expectedProofInfo = CswProofInfo(Absent, None, None)
        assertEquals("Different CswInfo value: cswType", "UtxoCswData", cswInfo.cswType)
        assertEquals("Different CswInfo value: amount", utxoData1.amount, cswInfo.amount)
        assertArrayEquals("Different CswInfo value: amount", params.sidechainId, cswInfo.scId)
        assertArrayEquals("Different CswInfo value: nullifier", utxoData1.getNullifier, cswInfo.nullifier)
        assertEquals("Different CswInfo value: proofInfo", expectedProofInfo, cswInfo.proofInfo)
        assertEquals("Different CswInfo value: activeCertData",  cswManager.cswWitnessHolderOpt.get.lastActiveCertOpt.map(_.certDataHash), cswInfo.activeCertData)
        assertArrayEquals("Different CswInfo value: nullifier", cswManager.cswWitnessHolderOpt.get.mcbScTxsCumComEnd, cswInfo.ceasingCumScTxCommTree)

      case Failure(_) => Assert.fail("Exception found.")
    }


    // Test 4: Add proof info in the queue info
    val senderAddress = "znc3p7CFNTsz1s6CceskrTxKevQLPoDK4cK" // mainnet
    cswManager.proofsInQueue.append(ProofInQueue(new ByteArrayWrapper(utxoData1.boxId), senderAddress))

    cswInfoTry = Await.result(cswManagerRef ? GetCswInfo(utxoData1.boxId), timeout.duration).asInstanceOf[Try[CswInfo]]
    cswInfoTry match {
      case Success(cswInfo) =>
        val expectedProofInfo = CswProofInfo(InQueue, None, Some(senderAddress))
        assertEquals("Different CswInfo value: proofInfo", expectedProofInfo, cswInfo.proofInfo)
      case Failure(_) => Assert.fail("Exception found.")
    }


    // Test 5: Add proof info to in process
    cswManager.proofsInQueue.clear()
    cswManager.proofInProcessOpt = Some(ProofInProcess(new ByteArrayWrapper(utxoData1.boxId), senderAddress))

    cswInfoTry = Await.result(cswManagerRef ? GetCswInfo(utxoData1.boxId), timeout.duration).asInstanceOf[Try[CswInfo]]
    cswInfoTry match {
      case Success(cswInfo) =>
        val expectedProofInfo = CswProofInfo(InProcess, None, Some(senderAddress))
        assertEquals("Different CswInfo value: proofInfo", expectedProofInfo, cswInfo.proofInfo)
      case Failure(_) => Assert.fail("Exception found.")
    }


    // Test 6: Add generated proof
    cswManager.proofInProcessOpt = None
    val expectedProofInfo = CswProofInfo(Generated, Some(new Array[Byte](100)), Some(senderAddress))
    cswManager.generatedProofsMap(new ByteArrayWrapper(utxoData1.boxId)) = expectedProofInfo

    cswInfoTry = Await.result(cswManagerRef ? GetCswInfo(utxoData1.boxId), timeout.duration).asInstanceOf[Try[CswInfo]]
    cswInfoTry match {
      case Success(cswInfo) =>
        assertEquals("Different CswInfo value: proofInfo", expectedProofInfo, cswInfo.proofInfo)
      case Failure(_) => Assert.fail("Exception found.")
    }
  }
}
