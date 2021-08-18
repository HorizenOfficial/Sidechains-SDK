package com.horizen.certificatesubmitter

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{TestActor, TestActorRef, TestProbe}
import akka.util.Timeout
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, MainchainTxSidechainCreationCrosschainOutput, SidechainBlock}
import com.horizen.box.Box
import com.horizen.certificatesubmitter.CertificateSubmitter.ReceivableMessages.GetCertificateGenerationState
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proposition.{Proposition, SchnorrProposition}
import com.horizen.transaction.MC2SCAggregatedTransaction
import com.horizen.transaction.mainchain.{SidechainCreation, SidechainRelatedMainchainOutput}
import com.horizen.websocket.{ChainTopQualityCertificateInfo, MainchainNodeChannel, MempoolTopQualityCertificateInfo, TopQualityCertificates}
import com.horizen._
import com.horizen.certificatesubmitter.CertificateSubmitter.Timers.CertificateGenerationTimer
import com.horizen.certificatesubmitter.CertificateSubmitter.{CertificateSubmissionStarted, CertificateSubmissionStopped}
import com.horizen.chain.MainchainHeaderInfo
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import com.horizen.utils.WithdrawalEpochInfo
import org.junit.Assert._
import org.junit.{Assert, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import scorex.core.settings.{RESTApiSettings, ScorexSettings}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Random, Try}

class CertificateSubmitterTest extends JUnitSuite with MockitoSugar {
  implicit lazy val actorSystem: ActorSystem = ActorSystem("submitter-actor-test")
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("scorex.executionContext")
  implicit val timeout: Timeout = 100 milliseconds


  private def getMockedSettings(timeoutDuration: FiniteDuration, submitterIsEnabled: Boolean): SidechainSettings = {
    val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
    Mockito.when(mockedRESTSettings.timeout).thenReturn(timeoutDuration)

    val mockedSidechainSettings: SidechainSettings = mock[SidechainSettings]
    Mockito.when(mockedSidechainSettings.scorexSettings).thenAnswer(_ => {
      val mockedScorexSettings: ScorexSettings = mock[ScorexSettings]
      Mockito.when(mockedScorexSettings.restApi).thenAnswer(_ => mockedRESTSettings)
      mockedScorexSettings
    })
    Mockito.when(mockedSidechainSettings.withdrawalEpochCertificateSettings).thenAnswer(_ => {
      val mockedWithdrawalEpochCertificateSettings: WithdrawalEpochCertificateSettings = mock[WithdrawalEpochCertificateSettings]
      Mockito.when(mockedWithdrawalEpochCertificateSettings.submitterIsEnabled).thenReturn(submitterIsEnabled)
      mockedWithdrawalEpochCertificateSettings
    })
    mockedSidechainSettings
  }

  def mockedMcBlockWithScCreation(genSysConstantOpt: Option[Array[Byte]]): MainchainBlockReference = {
    val mockedOutput = mock[MainchainTxSidechainCreationCrosschainOutput]
    Mockito.when(mockedOutput.constantOpt).thenAnswer(_ => genSysConstantOpt)

    val mockedRefData: MainchainBlockReferenceData = mock[MainchainBlockReferenceData]
    Mockito.when(mockedRefData.sidechainRelatedAggregatedTransaction).thenAnswer(_ => {
      val outputs: ListBuffer[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = ListBuffer()
      outputs.append(new SidechainCreation(mockedOutput, new Array[Byte](32), 0))
      Some(new MC2SCAggregatedTransaction(outputs.asJava))
    })
    new MainchainBlockReference(mock[MainchainHeader], mockedRefData)
  }

  @Test
  def initializationFailure_MissingNodeViewHolder(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration, submitterIsEnabled = true)

    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val params: NetworkParams = mock[NetworkParams]
    val mainchainChannel: MainchainNodeChannel = mock[MainchainNodeChannel]

    val certificateSubmitterRef: TestActorRef[CertificateSubmitter] = TestActorRef(
      Props(new CertificateSubmitter(mockedSettings, mockedSidechainNodeViewHolderRef, params, mainchainChannel)))

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    val deathWatch = TestProbe()
    deathWatch.watch(certificateSubmitterRef)
    deathWatch.expectTerminated(certificateSubmitterRef, timeout.duration * 2)
  }

  @Test
  def initializationFailure_InvalidActualConstantData(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration * 100, submitterIsEnabled = true)

    // Initialization should fail because of invalid constant
    val calculatedSysDataConstant = new Array[Byte](32)
    Random.nextBytes(calculatedSysDataConstant)
    val expectedSysDataConstant = new Array[Byte](32)
    Random.nextBytes(expectedSysDataConstant)
    val expectedSysDataConstantOpt: Option[Array[Byte]] = Some(expectedSysDataConstant)

    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          val history: SidechainHistory = mock[SidechainHistory]
          Mockito.when(history.getMainchainBlockReferenceByHash(ArgumentMatchers.any[Array[Byte]]()))
            .thenAnswer(_ => Some(mockedMcBlockWithScCreation(expectedSysDataConstantOpt)).asJava)
          sender ! f(CurrentView(history, mock[SidechainState], mock[SidechainWallet], mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val provingKeyPath: String = ""
    val params: NetworkParams = RegTestParams(
      calculatedSysDataConstant = calculatedSysDataConstant,
      provingKeyFilePath = provingKeyPath)

    val mainchainChannel: MainchainNodeChannel = mock[MainchainNodeChannel]

    val certificateSubmitterRef: TestActorRef[CertificateSubmitter] = TestActorRef(
      Props(new CertificateSubmitter(mockedSettings, mockedSidechainNodeViewHolderRef, params, mainchainChannel)))

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    val deathWatch = TestProbe()
    deathWatch.watch(certificateSubmitterRef)
    deathWatch.expectTerminated(certificateSubmitterRef, timeout.duration * 2)
  }

  @Test
  def initializationFailure_EmptyProvingKeyFilePath(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration * 100, submitterIsEnabled = true)

    val calculatedSysDataConstant = new Array[Byte](32)
    Random.nextBytes(calculatedSysDataConstant)
    val expectedSysDataConstantOpt: Option[Array[Byte]] = Some(calculatedSysDataConstant.clone())

    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          val history: SidechainHistory = mock[SidechainHistory]
          Mockito.when(history.getMainchainBlockReferenceByHash(ArgumentMatchers.any[Array[Byte]]()))
            .thenAnswer(_ => Some(mockedMcBlockWithScCreation(expectedSysDataConstantOpt)).asJava)
          sender ! f(CurrentView(history, mock[SidechainState], mock[SidechainWallet], mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    // Test should fail because of empty path.
    val provingKeyPath: String = ""
    val params: NetworkParams = RegTestParams(
      calculatedSysDataConstant = calculatedSysDataConstant,
      provingKeyFilePath = provingKeyPath)

    val mainchainChannel: MainchainNodeChannel = mock[MainchainNodeChannel]

    val certificateSubmitterRef: TestActorRef[CertificateSubmitter] = TestActorRef(
      Props(new CertificateSubmitter(mockedSettings, mockedSidechainNodeViewHolderRef, params, mainchainChannel)))

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    val deathWatch = TestProbe()
    deathWatch.watch(certificateSubmitterRef)
    deathWatch.expectTerminated(certificateSubmitterRef, timeout.duration * 2)
  }

  @Test
  def initializationFailure_InvalidProvingKeyFilePath(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration * 100, submitterIsEnabled = true)

    val calculatedSysDataConstant = new Array[Byte](32)
    Random.nextBytes(calculatedSysDataConstant)
    val expectedSysDataConstantOpt: Option[Array[Byte]] = Some(calculatedSysDataConstant.clone())

    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          val history: SidechainHistory = mock[SidechainHistory]
          Mockito.when(history.getMainchainBlockReferenceByHash(ArgumentMatchers.any[Array[Byte]]()))
            .thenAnswer(_ => Some(mockedMcBlockWithScCreation(expectedSysDataConstantOpt)).asJava)
          sender ! f(CurrentView(history, mock[SidechainState], mock[SidechainWallet], mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    // Test should fail because of empty path.
    val provingKeyPath: String = "wrong_file_path"
    val params: NetworkParams = RegTestParams(
      calculatedSysDataConstant = calculatedSysDataConstant,
      provingKeyFilePath = provingKeyPath)

    val mainchainChannel: MainchainNodeChannel = mock[MainchainNodeChannel]

    val certificateSubmitterRef: TestActorRef[CertificateSubmitter] = TestActorRef(
      Props(new CertificateSubmitter(mockedSettings, mockedSidechainNodeViewHolderRef, params, mainchainChannel)))

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    val deathWatch = TestProbe()
    deathWatch.watch(certificateSubmitterRef)
    deathWatch.expectTerminated(certificateSubmitterRef, timeout.duration * 2)
  }

  @Test
  def initialization(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration * 100, submitterIsEnabled = true)

    val calculatedSysDataConstant = new Array[Byte](32)
    Random.nextBytes(calculatedSysDataConstant)
    val expectedSysDataConstantOpt: Option[Array[Byte]] = Some(calculatedSysDataConstant.clone())

    val mockedSidechainNodeViewHolder = TestProbe()
    mockedSidechainNodeViewHolder.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case GetDataFromCurrentView(f) =>
          val history: SidechainHistory = mock[SidechainHistory]
          Mockito.when(history.getMainchainBlockReferenceByHash(ArgumentMatchers.any[Array[Byte]]()))
            .thenAnswer(_ => Some(mockedMcBlockWithScCreation(expectedSysDataConstantOpt)).asJava)
          sender ! f(CurrentView(history, mock[SidechainState], mock[SidechainWallet], mock[SidechainMemoryPool]))
      }
      TestActor.KeepRunning
    })
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    //
    // Test should fail because of empty path.
    val provingKeyPath: String = getClass.getClassLoader.getResource("mcblock473173_mainnet").getFile
    val params: NetworkParams = RegTestParams(
      calculatedSysDataConstant = calculatedSysDataConstant,
      provingKeyFilePath = provingKeyPath)

    val mainchainChannel: MainchainNodeChannel = mock[MainchainNodeChannel]

    val certificateSubmitterRef: TestActorRef[CertificateSubmitter] = TestActorRef(
      Props(new CertificateSubmitter(mockedSettings, mockedSidechainNodeViewHolderRef, params, mainchainChannel)))

    actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

    val watch = TestProbe()
    watch.watch(certificateSubmitterRef)
    watch.expectNoMessage(timeout.duration)

    // check if actor is Alive and switched the behaviour to working cycle
    try {
      val certState = Await.result(certificateSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
      assertFalse("Actor expected not submitting at the moment", certState)
    } catch {
      case _ : Throwable => Assert.fail("Actor expected to be initialized and switched to working cycle")
    }
  }

  @Test
  def certificateSubmissionEvents(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration * 100, submitterIsEnabled = true)

    val mockedSidechainNodeViewHolder = TestProbe()
    val mockedSidechainNodeViewHolderRef: ActorRef = mockedSidechainNodeViewHolder.ref

    val mainchainChannel: MainchainNodeChannel = mock[MainchainNodeChannel]

    val certificateSubmitterRef: TestActorRef[CertificateSubmitter] = TestActorRef(
      Props(new CertificateSubmitter(mockedSettings, mockedSidechainNodeViewHolderRef, mock[NetworkParams], mainchainChannel)))

    val submitter: CertificateSubmitter = certificateSubmitterRef.underlyingActor

    // Skip initialization
    submitter.context.become(submitter.workingCycle)

    // Test: check current submission state
    var certState = Await.result(certificateSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
    assertFalse("Certificate generation expected to be disabled", certState)

    // Update the State as Started and check
    actorSystem.eventStream.publish(CertificateSubmissionStarted)
    certState = Await.result(certificateSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
    assertTrue("Certificate generation expected to be enabled", certState)

    // Update the State as Stopped and check
    actorSystem.eventStream.publish(CertificateSubmissionStopped)
    certState = Await.result(certificateSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
    assertFalse("Certificate generation expected to be enabled", certState)
  }

  @Test
  def newBlockArrived(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration * 100, submitterIsEnabled = true)

    // Set 3 keys for the Certificate signatures
    val keyGenerator = SchnorrKeyGenerator.getInstance()
    val schnorrSecrets: Seq[SchnorrSecret] = Seq(
      keyGenerator.generateSecret("seed1".getBytes()),
      keyGenerator.generateSecret("seed2".getBytes()),
      keyGenerator.generateSecret("seed3".getBytes())
    )

    val signersThreshold = 2
    val params: RegTestParams = RegTestParams(
      signersPublicKeys = schnorrSecrets.map(_.publicImage()),
      signersThreshold = signersThreshold
    )

    val mockedMainchainChannel: MainchainNodeChannel = mock[MainchainNodeChannel]

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


    val certificateSubmitterRef: TestActorRef[CertificateSubmitter] = TestActorRef(
      Props(new CertificateSubmitter(mockedSettings, mockedSidechainNodeViewHolderRef, params, mockedMainchainChannel)))

    val submitter: CertificateSubmitter = certificateSubmitterRef.underlyingActor

    val watch = TestProbe()
    watch.watch(certificateSubmitterRef)

    // Skip initialization
    submitter.context.become(submitter.workingCycle)


    // Test 1: block outside the epoch, no timer activated
    val epochNumber = 10
    val referencedEpochNumber = epochNumber - 1
    val epochInfoOutsideWindow = WithdrawalEpochInfo(epochNumber, lastEpochIndex = params.withdrawalEpochLength)
    Mockito.when(state.getWithdrawalEpochInfo).thenAnswer(_ => epochInfoOutsideWindow)

    actorSystem.eventStream.publish(SemanticallySuccessfulModifier(mock[SidechainBlock]))
    watch.expectNoMessage(timeout.duration)

    assertEquals("Signature status expected to be not defined.", None, submitter.signaturesStatus)
    assertFalse("Certificate generation schedule expected to be disabled.", submitter.timers.isTimerActive(CertificateGenerationTimer))


    // Test 2: block inside the epoch first time with 1 Sig out of 2
    val epochInfoInsideWindow = WithdrawalEpochInfo(epochNumber, lastEpochIndex = 0)
    Mockito.reset(state)
    Mockito.when(state.getWithdrawalEpochInfo).thenAnswer(_ => epochInfoInsideWindow)
    Mockito.when(state.withdrawalRequests(ArgumentMatchers.any[Int])).thenAnswer(answer => {
      assertEquals("Invalid referenced epoch number retrieved for state.withdrawalRequests.", referencedEpochNumber, answer.getArgument(0).asInstanceOf[Int])
      Seq()
    })

    Mockito.reset(history)
    Mockito.when(history.getMainchainBlockReferenceInfoByMainchainBlockHeight(ArgumentMatchers.any[Int])).thenAnswer(_ => {
      val randomArray: Array[Byte] = new Array[Byte](CommonParams.mainchainBlockHashLength)
      val info: MainchainBlockReferenceInfo = new MainchainBlockReferenceInfo(randomArray, randomArray, 0, randomArray, randomArray)
      Some(info).asJava
    })
    Mockito.when(history.mainchainHeaderInfoByHash(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => {
      val info: MainchainHeaderInfo = mock[MainchainHeaderInfo]
      Mockito.when(info.cumulativeCommTreeHash).thenReturn(new Array[Byte](32))
      Some(info)
    })

    var walletSecrets = schnorrSecrets.take(1)
    Mockito.when(wallet.secret(ArgumentMatchers.any[Proposition])).thenAnswer(answer => {
      val pubKey = answer.getArgument(0).asInstanceOf[SchnorrProposition]
      walletSecrets.find(s => s.owns(pubKey))
    })

    actorSystem.eventStream.publish(SemanticallySuccessfulModifier(mock[SidechainBlock]))
    watch.expectNoMessage(timeout.duration)

    assertTrue("Signature status expected to be defined.", submitter.signaturesStatus.isDefined)
    assertEquals("Different referenced epoch expected.", referencedEpochNumber, submitter.signaturesStatus.get.referencedEpoch)
    assertEquals("Different signatures number expected.", walletSecrets.size, submitter.signaturesStatus.get.knownSigs.size)
    assertTrue("MessageToSign should be defined.", submitter.signaturesStatus.get.messageToSign.nonEmpty)
    assertFalse("Certificate generation schedule expected to be disabled.", submitter.timers.isTimerActive(CertificateGenerationTimer))
    val sigInfo = submitter.signaturesStatus.get.knownSigs.head
    assertTrue("Signature expected to be valid", sigInfo.signature.isValid(walletSecrets.head.publicImage(), submitter.signaturesStatus.get.messageToSign))


    // Test 3: another block inside the window, check that no signatures were generated.

    // Add one more known key to the wallet
    walletSecrets = schnorrSecrets.take(2)

    actorSystem.eventStream.publish(SemanticallySuccessfulModifier(mock[SidechainBlock]))
    watch.expectNoMessage(timeout.duration)

    // Data expected to be the same
    assertTrue("Signature status expected to be defined.", submitter.signaturesStatus.isDefined)
    assertEquals("Different referenced epoch expected.", referencedEpochNumber, submitter.signaturesStatus.get.referencedEpoch)
    assertEquals("Different signatures number expected.", 1, submitter.signaturesStatus.get.knownSigs.size)
    assertTrue("MessageToSign should be defined.", submitter.signaturesStatus.get.messageToSign.nonEmpty)
    assertFalse("Certificate generation schedule expected to be disabled.", submitter.timers.isTimerActive(CertificateGenerationTimer))


    // Test 4: reset SignatureStatus and test block inside the window that will lead to generating 2 sigs with is exactly the threshold.
    // But in MC the better quality Cert exists -> no cert scheduling
    submitter.signaturesStatus = None

    val mcTopCertQuality: Long = 3
    Mockito.when(mockedMainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String]())).thenAnswer(_ => Try {
      // Cert with mcTopCertQuality presents in the MC
      new TopQualityCertificates(
        MempoolTopQualityCertificateInfo(None, Some(referencedEpochNumber), None, Some(mcTopCertQuality), None),
        ChainTopQualityCertificateInfo(None, Some(referencedEpochNumber), None, None)
      )
    })

    actorSystem.eventStream.publish(SemanticallySuccessfulModifier(mock[SidechainBlock]))
    watch.expectNoMessage(timeout.duration)

    assertTrue("Signature status expected to be defined.", submitter.signaturesStatus.isDefined)
    assertEquals("Different referenced epoch expected.", referencedEpochNumber, submitter.signaturesStatus.get.referencedEpoch)
    assertEquals("Different signatures number expected.", walletSecrets.size, submitter.signaturesStatus.get.knownSigs.size)
    assertTrue("MessageToSign should be defined.", submitter.signaturesStatus.get.messageToSign.nonEmpty)
    assertFalse("Certificate generation schedule expected to be disabled.", submitter.timers.isTimerActive(CertificateGenerationTimer))


    var certState = Await.result(certificateSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
    assertFalse("Actor expected not submitting at the moment", certState)


    // Test 5: reset SignatureStatus and test block inside the window that will lead to generating 2 sigs with is exactly the threshold.
    // Nop better cert quality found
    submitter.signaturesStatus = None

    Mockito.when(mockedMainchainChannel.getTopQualityCertificates(ArgumentMatchers.any[String]())).thenAnswer(_ => Try {
      // No certs in MC
      new TopQualityCertificates(
        MempoolTopQualityCertificateInfo(None, None, None, None, None),
        ChainTopQualityCertificateInfo(None, None, None, None)
      )
    })

    actorSystem.eventStream.publish(SemanticallySuccessfulModifier(mock[SidechainBlock]))
    watch.expectNoMessage(timeout.duration)

    assertTrue("Signature status expected to be defined.", submitter.signaturesStatus.isDefined)
    assertEquals("Different referenced epoch expected.", referencedEpochNumber, submitter.signaturesStatus.get.referencedEpoch)
    assertEquals("Different signatures number expected.", walletSecrets.size, submitter.signaturesStatus.get.knownSigs.size)
    assertTrue("MessageToSign should be defined.", submitter.signaturesStatus.get.messageToSign.nonEmpty)
    assertTrue("Certificate generation schedule expected to be enabled.", submitter.timers.isTimerActive(CertificateGenerationTimer))


    certState = Await.result(certificateSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
    assertTrue("Actor expected being submitting at the moment.", certState)


    // Test 6: block outside the epoch when the cert submission is scheduled
    Mockito.reset(state)
    Mockito.when(state.getWithdrawalEpochInfo).thenAnswer(_ => epochInfoOutsideWindow)

    actorSystem.eventStream.publish(SemanticallySuccessfulModifier(mock[SidechainBlock]))
    watch.expectNoMessage(timeout.duration)

    assertEquals("Signature status expected to be not defined.", None, submitter.signaturesStatus)
    assertFalse("Certificate generation schedule expected to be disabled.", submitter.timers.isTimerActive(CertificateGenerationTimer))
    certState = Await.result(certificateSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
    assertFalse("Actor expected not submitting at the moment.", certState)
  }

  // TODO:
  // Test schedule cert generation -> checkQuality = false case, check certState
  // Test local sig broadcasting event
  // test sig from remote
  // test submitter and sigcreator params modification when available.
}
