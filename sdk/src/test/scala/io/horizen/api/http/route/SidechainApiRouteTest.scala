package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper, SerializationFeature}
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages._
import io.horizen._
import io.horizen.account.fixtures.BasicAuthenticationFixture
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
import io.horizen.proposition.Proposition
import io.horizen.secret.SecretSerializer
import io.horizen.storage.StorageIterator
import io.horizen.utils.{ByteArrayWrapper, BytesUtils}
import io.horizen.utxo.SidechainApp
import io.horizen.utxo.api.http
import io.horizen.utxo.api.http.SimpleCustomApi
import io.horizen.utxo.api.http.route._
import io.horizen.utxo.backup.BoxIterator
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.box.{Box, BoxSerializer}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.companion.{SidechainBoxesCompanion, SidechainTransactionsCompanion}
import io.horizen.utxo.csw.CswManager.ReceivableMessages._
import io.horizen.utxo.csw.CswManager.Responses._
import io.horizen.utxo.customtypes.{CustomBox, CustomBoxSerializer}
import io.horizen.utxo.node._
import io.horizen.utxo.transaction.{BoxTransaction, RegularTransaction}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.app.Version
import sparkz.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import sparkz.core.network._
import sparkz.core.network.peer.PeerInfo
import sparkz.core.network.peer.PeerManager.ReceivableMessages._
import sparkz.core.settings.{NetworkSettings, RESTApiSettings, SparkzSettings}
import sparkz.core.utils.NetworkTimeProvider
import sparkz.crypto.hash.Blake2b256
import sparkz.util.{ModifierId, bytesToId}

import java.io.{File, PrintWriter}
import java.lang.{Byte => JByte}
import java.net.{InetAddress, InetSocketAddress}
import java.nio.charset.StandardCharsets
import java.util
import java.util.{HashMap => JHashMap}
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@RunWith(classOf[JUnitRunner])
abstract class SidechainApiRouteTest extends AnyWordSpec with Matchers with ScalatestRouteTest with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes with BasicAuthenticationFixture{

  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler


  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val jsonChecker = new SidechainJSONBOChecker

  private val inetAddr1 = new InetSocketAddress("92.92.92.92", 27017)
  private val inetAddr2 = new InetSocketAddress("93.93.93.93", 27017)
  private val inetAddr3 = new InetSocketAddress("94.94.94.94", 27017)
  val inetAddrBlackListed_1 = new InetSocketAddress("95.95.95.95", 27017)
  val inetAddrBlackListed_2 = new InetSocketAddress("96.96.96.96", 27017)
  val peersInfo: Array[PeerInfo] = Array(
    PeerInfo(PeerSpec("app", Version.initial, "first", Some(inetAddr1), Seq()), System.currentTimeMillis() - 100, Some(Incoming)),
    PeerInfo(PeerSpec("app", Version.initial, "second", Some(inetAddr2), Seq()), System.currentTimeMillis() + 100, Some(Outgoing)),
    PeerInfo(PeerSpec("app", Version.initial, "third", Some(inetAddr3), Seq()), System.currentTimeMillis() + 200, Some(Outgoing))
  )
  val peers: Map[InetSocketAddress, PeerInfo] = Map(
    inetAddr1 -> peersInfo(0),
    inetAddr2 -> peersInfo(1),
    inetAddr3 -> peersInfo(2)
  )

  val connectedPeerHandler: TestProbe = TestProbe()

  val lastMessage1 = 123456
  val lastMessage2 = 11223344

  val connectedPeer: Array[ConnectedPeer] = Array(
    ConnectedPeer(ConnectionId(inetAddr1, inetAddr1, Incoming), connectedPeerHandler.ref, lastMessage1, Some(peersInfo(0))),
    ConnectedPeer(ConnectionId(inetAddr2, inetAddr3, Outgoing), connectedPeerHandler.ref, lastMessage2, Some(peersInfo(1)))
  )

  val connectedPeers: Seq[ConnectedPeer] = Seq(connectedPeer(0), connectedPeer(1))

  val sidechainApiMockConfiguration: SidechainApiMockConfiguration = new SidechainApiMockConfiguration()

  val mapper: ObjectMapper = ApplicationJsonSerializer.getInstance().getObjectMapper
  // DO NOT REMOVE THIS LINE
  mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

  val utilMocks = new SidechainNodeViewUtilMocks()


  val memoryPool: util.List[RegularTransaction] = utilMocks.transactionList
  val allBoxes = utilMocks.allBoxes
  val genesisBlock = utilMocks.genesisBlock
  val genesisBlockInfo = utilMocks.genesisBlockInfo
  val listOfStorageVersions = utilMocks.listOfNodeStorageVersion
  val sidechainId = utilMocks.sidechainId
  val mainchainBlockReferenceInfoRef = utilMocks.mainchainBlockReferenceInfoRef
  val keyRotationProof = utilMocks.keyRotationProof
  val certifiersKeys = utilMocks.certifiersKeys
  val credentials = getBasicAuthCredentials()
  val badCredentials = getBasicAuthCredentials(password = "wrong_password")
  val apiKeyHash = getBasicAuthApiKeyHash(credentials.password())

  val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
  Mockito.when(mockedRESTSettings.timeout).thenAnswer(_ => 1 seconds)
  Mockito.when(mockedRESTSettings.apiKeyHash).thenAnswer(_ => Some(apiKeyHash))

  val mockedNetworkSettings: NetworkSettings = mock[NetworkSettings]
  Mockito.when(mockedNetworkSettings.nodeName).thenAnswer(_ => "node0")
  Mockito.when(mockedNetworkSettings.agentName).thenAnswer(_ => "2-Hop")
  Mockito.when(mockedNetworkSettings.appVersion).thenAnswer(_ => "0.0.1")

  val mockedSidechainSettings: SidechainSettings = mock[SidechainSettings]
  Mockito.when(mockedSidechainSettings.sparkzSettings).thenAnswer(_ => {
    val mockedSparkzSettings: SparkzSettings = mock[SparkzSettings]
    Mockito.when(mockedSparkzSettings.restApi).thenAnswer(_ => mockedRESTSettings)
    Mockito.when(mockedSparkzSettings.logDir).thenAnswer(_ => File.createTempFile("tmp", ""))
    mockedSparkzSettings
  })
  Mockito.when(mockedSidechainSettings.logInfo).thenAnswer(_ => LogInfoSettings())
  Mockito.when(mockedSidechainSettings.forger).thenAnswer(_ => ForgerSettings())
  Mockito.when(mockedSidechainSettings.withdrawalEpochCertificateSettings).thenAnswer(_ => {
    val mockWithdrawalEpochCertificateSettings = mock[WithdrawalEpochCertificateSettings]
    Mockito.when(mockWithdrawalEpochCertificateSettings.submitterIsEnabled).thenReturn(true)
    Mockito.when(mockWithdrawalEpochCertificateSettings.certificateSigningIsEnabled).thenReturn(true)
    mockWithdrawalEpochCertificateSettings
  })

  val mockedSparkzSettings: SparkzSettings = mock[SparkzSettings]
  Mockito.when(mockedSparkzSettings.network).thenAnswer(_ => mockedNetworkSettings)

  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-api-routes")

  val mockedSidechainNodeViewHolder = TestProbe()
  mockedSidechainNodeViewHolder.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case m: GetDataFromCurrentSidechainNodeView[
          SidechainNodeView,
          _] @unchecked=>
          m match {
            case GetDataFromCurrentSidechainNodeView(f) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetDataFromCurrentNodeView_reply()) {
                sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration))
              }
          }
        case GetDataFromCurrentView(f) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetDataFromCurrentView_reply())
            sender ! f(utilMocks.getNodeView(sidechainApiMockConfiguration))
        case m: ApplyFunctionOnNodeView[
          SidechainNodeView,
          _] @unchecked =>
          m match {
            case ApplyFunctionOnNodeView(f) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_ApplyFunctionOnNodeView_reply())
                sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration))
          }
        case m: ApplyBiFunctionOnNodeView[
          SidechainNodeView,
          _,
          _] @unchecked =>
          m match {
            case ApplyBiFunctionOnNodeView(f, funParameter) =>
              if (sidechainApiMockConfiguration.getShould_nodeViewHolder_ApplyBiFunctionOnNodeView_reply())
                sender ! f(utilMocks.getSidechainNodeView(sidechainApiMockConfiguration), funParameter)
          }
        case GenerateSecret(g) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GenerateSecret_reply())
            sender ! Success(g.generateSecret("some_seed".getBytes(StandardCharsets.UTF_8)))
          else sender ! Failure(new Exception("Secret not generated."))
        case LocallyGeneratedSecret(_) =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_LocallyGeneratedSecret_reply())
            sender ! Success(Unit)
          else sender ! Failure(new Exception("Secret not added."))
        case GetStorageVersions =>
          if (sidechainApiMockConfiguration.getShould_nodeViewHolder_GetStorageVersions_reply())
            sender ! listOfStorageVersions
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

  val mockedPeerManagerActor = TestProbe()
  mockedPeerManagerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetAllPeers =>
          if (sidechainApiMockConfiguration.getSshould_peerManager_GetAllPeers_reply())
            sender ! peers
          else sender ! Failure(new Exception("No peers."))
        case GetBlacklistedPeers =>
          if (sidechainApiMockConfiguration.getShould_peerManager_GetBlacklistedPeers_reply())
            sender ! Seq[InetAddress](inetAddrBlackListed_1.getAddress, inetAddrBlackListed_2.getAddress)
          else new Exception("No black listed peers.")
        case GetPeer(addr) =>
          if (sidechainApiMockConfiguration.getShould_peerManager_GetPeer_reply())
            sender ! peers(addr)
        case AddToBlacklist(_, _) =>

        case RemoveFromBlacklist(_) =>
        case RemovePeer(_) =>

      }
      TestActor.KeepRunning
    }
  })
  val mockedPeerManagerRef: ActorRef = mockedPeerManagerActor.ref

  val mockedNetworkControllerActor = TestProbe()
  mockedNetworkControllerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetConnectedPeers =>
          if (sidechainApiMockConfiguration.getShould_networkController_GetConnectedPeers_reply())
            sender ! connectedPeers
          else sender ! Failure(new Exception("No connected peers."))
        case ConnectTo(_) =>
        case DisconnectFromAddress(_) =>
      }
      TestActor.KeepRunning
    }
  })
  val mockedNetworkControllerRef: ActorRef = mockedNetworkControllerActor.ref

  val mockedTimeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]

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
  val mockedsidechainBlockActorRef: ActorRef = mockedSidechainBlockActor.ref

  val mockedCswManagerActor = TestProbe()
  mockedCswManagerActor.setAutoPilot(new testkit.TestActor.AutoPilot {
    override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
      msg match {
        case GetCeasedStatus => {
          sender ! true
        }
        case GetCswBoxIds => {
          sender ! Seq(BytesUtils.fromHexString("1111"), BytesUtils.fromHexString("2222"), BytesUtils.fromHexString("3333"))
        }
        case GetCswInfo(boxId) => {
          val expectedBoxId: Array[Byte] = getRandomBoxId(0)
          if (boxId.deep != expectedBoxId.deep) {
            sender ! Failure(new IllegalArgumentException("CSW info was not found for given box id."))
          } else {
            sender ! Success(CswInfo("UtxoCswData", // pure class name
              42,
              BytesUtils.fromHexString("ABCD"),
              BytesUtils.fromHexString("FFFF"),
              CswProofInfo(Absent, Some(BytesUtils.fromHexString("FBFB")), Some("SomeDestination")),
              Some(BytesUtils.fromHexString("BBBB")),
              BytesUtils.fromHexString("CCCC")))
          }
        }
        case GetBoxNullifier(boxId) => {
          val expectedBoxId: Array[Byte] = getRandomBoxId(0)
          if (boxId.deep != expectedBoxId.deep) {
            sender ! Failure(new IllegalArgumentException("Box was not found for given box id."))
          } else {
            sender ! Success(BytesUtils.fromHexString("FAFA"))
          }
        }
        case GenerateCswProof(boxId, receiverAddress) => {
          val expectedBoxId: Array[Byte] = getRandomBoxId(0)
          if (boxId.deep != expectedBoxId.deep) {
            sender ! NoProofData
          } else {
            sender ! ProofCreationFinished
          }
        }
      }
      TestActor.KeepRunning
    }
  })
  val mockedCswManagerActorRef: Option[ActorRef] = Some(mockedCswManagerActor.ref)

  val mockedSidechainCertActor = TestProbe()
  val mockedCertSubmitterActorRef: ActorRef = mockedSidechainCertActor.ref


  val customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers, false)
  val mockedStorageIterator: StorageIterator = mock[StorageIterator]
  var boxList: ListBuffer[SidechainTypes#SCB] = new ListBuffer[SidechainTypes#SCB]()
  boxList ++= getCustomBoxList(3).asScala.map(_.asInstanceOf[SidechainTypes#SCB])
  val storedBoxList = new ListBuffer[util.Map.Entry[Array[Byte], Array[Byte]]]()
  for (b <- boxList) {
    storedBoxList.append({
      val key = new ByteArrayWrapper(Blake2b256.hash(b.id())).data()
      val value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b)).data()
      val entry: util.Map.Entry[Array[Byte], Array[Byte]] = util.Map.entry(key, value)
      entry
    })
  }

  def mockStorageIterator = {
    Mockito.when(mockedStorageIterator.hasNext).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false)
    Mockito.when(mockedStorageIterator.next()).thenReturn(storedBoxList(0)).thenReturn(storedBoxList(1)).thenReturn(storedBoxList(2))
  }

  mockStorageIterator
  val mockedBoxIterator: BoxIterator = new BoxIterator(mockedStorageIterator, sidechainBoxesCompanion)

  val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
  val sidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)

  implicit def default() = RouteTestTimeout(3.second)

  val params = MainNetParams(sidechainId = utilMocks.sidechainIdArray)
  val sidechainTransactionApiRoute: Route = SidechainTransactionApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainTransactionActorRef,
    sidechainTransactionsCompanion, params, CircuitTypes.NaiveThresholdSignatureCircuit).route
  val sidechainWalletApiRoute: Route = SidechainWalletApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, sidechainSecretsCompanion).route
  val mockedSidechainApp: SidechainApp = mock[SidechainApp]
  val sidechainNodeApiRoute: Route = SidechainNodeApiRoute[BoxTransaction[Proposition, Box[Proposition]],
    SidechainBlockHeader, SidechainBlock, SidechainFeePaymentsInfo, NodeHistory, NodeState, NodeWallet, NodeMemoryPool, SidechainNodeView](mockedPeerManagerRef, mockedNetworkControllerRef, mockedTimeProvider, mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedSidechainApp, params).route
  val sidechainBlockApiRoute: Route = SidechainBlockApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedsidechainBlockActorRef, sidechainTransactionsCompanion, mockedSidechainBlockForgerActorRef, params).route
  val mainchainBlockApiRoute: Route = MainchainBlockApiRoute[BoxTransaction[Proposition, Box[Proposition]],
    SidechainBlockHeader,SidechainBlock,SidechainFeePaymentsInfo, NodeHistory,NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val applicationApiRoute: Route = http.route.SidechainApplicationApiRoute(mockedRESTSettings, new SimpleCustomApi(), mockedSidechainNodeViewHolderRef).route
  val sidechainCswApiRoute: Route = SidechainCswApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedCswManagerActorRef, params).route
  val sidechainBackupApiRoute: Route = SidechainBackupApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolderRef, mockedBoxIterator, params).route
  val walletCoinsBalanceApiRejected: Route = SidechainRejectionApiRoute("wallet", "coinsBalance", mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val walletApiRejected: Route = route.SidechainRejectionApiRoute("wallet", "", mockedRESTSettings, mockedSidechainNodeViewHolderRef).route
  val sidechainSubmitterApiRoute: Route = SidechainSubmitterApiRoute(mockedRESTSettings, params, mockedCertSubmitterActorRef, mockedSidechainNodeViewHolderRef, CircuitTypes.NaiveThresholdSignatureCircuit).route
  val sidechainSubmitterApiRouteWithKeyRotation: Route = SidechainSubmitterApiRoute(mockedRESTSettings, params, mockedCertSubmitterActorRef, mockedSidechainNodeViewHolderRef, CircuitTypes.NaiveThresholdSignatureCircuitWithKeyRotation).route

  val basePath: String

  Mockito.when(mockedSidechainApp.settings).thenAnswer(_ => mockedSparkzSettings)
  Mockito.when(mockedSidechainApp.sidechainSettings).thenAnswer(_ => mockedSidechainSettings)
  Mockito.when(mockedSidechainApp.sidechainSettings.genesisData).thenAnswer(_ => {
    val mockedGenesisData: GenesisDataSettings = mock[GenesisDataSettings]
    Mockito.when(mockedGenesisData.mcNetwork).thenAnswer(_ => "unit test")
    mockedGenesisData
  })

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


  val dumpSecretsFilePath = System.getProperty("user.dir") + "/dumpSecrets"
  val dumpFile = new File(dumpSecretsFilePath)

  protected def createDumpSecretsFile(badSecret: Boolean, badProposition: Boolean): Unit = {
    val secret1 = getPrivateKey25519
    val secret2 = getPrivateKey25519
    val writer = new PrintWriter(dumpFile)
    writer.write("#Title#\n")
    writer.write(BytesUtils.toHexString(sidechainSecretsCompanion.toBytes(secret1)) + " " + BytesUtils.toHexString(secret1.publicImage().bytes()) + "\n")
    writer.write(BytesUtils.toHexString(sidechainSecretsCompanion.toBytes(secret2)) + " " + BytesUtils.toHexString(secret2.publicImage().bytes()) + "\n")
    if (badSecret)
      writer.write("aeaeaeaeaeaeaeaeaeae" + " " + BytesUtils.toHexString(secret2.publicImage().bytes()) + "\n")
    if (badProposition)
      writer.write(BytesUtils.toHexString(sidechainSecretsCompanion.toBytes(secret2)) + " " + BytesUtils.toHexString(secret1.publicImage().bytes()) + "\n")
    writer.close()
  }

}

