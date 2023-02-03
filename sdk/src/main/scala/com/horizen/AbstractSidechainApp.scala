package com.horizen


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import akka.stream.ActorMaterializer
import com.horizen.api.http._
import com.horizen.api.http.client.SecureEnclaveApiClient
import com.horizen.block.{ProofOfWorkVerifier, SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.certificatesubmitter.network.{CertificateSignaturesSpec, GetCertificateSignaturesSpec}
import com.horizen.companion._
import com.horizen.cryptolibprovider.utils.CircuitTypes
import com.horizen.cryptolibprovider.utils.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import com.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider}
import com.horizen.customconfig.CustomAkkaConfiguration
import com.horizen.forge.MainchainSynchronizer
import com.horizen.fork.{ForkConfigurator, ForkManager}
import com.horizen.helper.TransactionSubmitProvider
import com.horizen.helper.{SecretSubmitProvider, SecretSubmitProviderImpl}
import com.horizen.params._
import com.horizen.proposition._
import com.horizen.secret.SecretSerializer
import com.horizen.serialization.JsonHorizenPublicKeyHashSerializer
import com.horizen.transaction._
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BlockUtils, BytesUtils, DynamicTypedSerializer, Pair}
import com.horizen.websocket.client._
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.impl.Log4jContextFactory
import org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry
import sparkz.util.SparkzLogging
import sparkz.core.api.http.ApiRoute
import sparkz.core.app.Application
import sparkz.core.network.NetworkController.ReceivableMessages.ShutdownNetwork
import sparkz.core.network.PeerFeature
import sparkz.core.network.message.MessageSpec
import sparkz.core.settings.SparkzSettings

import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}


abstract class AbstractSidechainApp
  (val sidechainSettings: SidechainSettings,
   val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   val customApiGroups: JList[ApplicationApiGroup],
   val rejectedApiPaths : JList[Pair[String, String]],
   val applicationStopper : SidechainAppStopper,
   val forkConfigurator : ForkConfigurator,
   val chainInfo : ChainInfo,
   val consensusSecondsInSlot: Int
  )
  extends Application with SparkzLogging
{
  override type TX <: Transaction
  override type PMOD <: SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase]

  override implicit lazy val settings: SparkzSettings = sidechainSettings.sparkzSettings
  override protected implicit lazy val actorSystem: ActorSystem = ActorSystem(settings.network.agentName, CustomAkkaConfiguration.getCustomConfig())

  private val closableResourceList = mutable.ListBuffer[AutoCloseable]()
  protected val sidechainTransactionsCompanion: DynamicTypedSerializer[TX, TransactionSerializer[TX]]


  log.info(s"Starting application with settings \n$sidechainSettings")

  override implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  override implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  override protected lazy val features: Seq[PeerFeature] = Seq()

  val stopAllInProgress: AtomicBoolean = new AtomicBoolean(false)

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq(
    SidechainSyncInfoMessageSpec,
    // It can be no more Certificate signatures than the public keys for the Threshold Signature Circuit
    new GetCertificateSignaturesSpec(sidechainSettings.withdrawalEpochCertificateSettings.signersPublicKeys.size),
    new CertificateSignaturesSpec(sidechainSettings.withdrawalEpochCertificateSettings.signersPublicKeys.size)
  )

  val circuitType: CircuitTypes = sidechainSettings.withdrawalEpochCertificateSettings.circuitType


  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)

  // Deserialize genesis block bytes
  val genesisBlock: PMOD

  val genesisPowData: Seq[(Int, Int)] = ProofOfWorkVerifier.parsePowData(sidechainSettings.genesisData.powData)

  val signersPublicKeys: Seq[SchnorrProposition] = sidechainSettings.withdrawalEpochCertificateSettings.signersPublicKeys
    .map(bytes => SchnorrPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(bytes)))

  var mastersPublicKeys: Seq[SchnorrProposition] = Seq()

  val calculatedSysDataConstant: Array[Byte] = circuitType match {
    case NaiveThresholdSignatureCircuit =>
      CryptoLibProvider.sigProofThresholdCircuitFunctions.generateSysDataConstant(signersPublicKeys.map(_.bytes()).asJava, sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold)
    case NaiveThresholdSignatureCircuitWithKeyRotation =>
      mastersPublicKeys = sidechainSettings.withdrawalEpochCertificateSettings.mastersPublicKeys
        .map(bytes => SchnorrPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(bytes)))
      CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateSysDataConstant(signersPublicKeys.map(_.bytes()).asJava, mastersPublicKeys.map(_.bytes()).asJava, sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold)
  }
  log.info(s"calculated sysDataConstant is: ${BytesUtils.toHexString(calculatedSysDataConstant)}")


  lazy val sidechainCreationOutput: SidechainCreation = BlockUtils.tryGetSidechainCreation(genesisBlock) match {
    case Success(output) => output
    case Failure(exception) => throw new IllegalArgumentException("Genesis block specified in the configuration file has no Sidechain Creation info.", exception)
  }

  lazy val isCSWEnabled: Boolean = sidechainCreationOutput.getScCrOutput.ceasedVkOpt.isDefined

  if (circuitType.equals(CircuitTypes.NaiveThresholdSignatureCircuitWithKeyRotation) && isCSWEnabled)
    throw new IllegalArgumentException("Invalid Configuration file: With key rotation circuit CSW feature is not allowed.")

  // Init Secure Enclave Api Client
  val secureEnclaveApiClient = new SecureEnclaveApiClient(sidechainSettings.remoteKeysManagerSettings)

  lazy val forgerList: Seq[(PublicKey25519Proposition, VrfPublicKey)] = sidechainSettings.forger.allowedForgersList.map(el =>
    (PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(el.blockSignProposition)), VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(el.vrfPublicKey))))

  if (consensusSecondsInSlot < consensus.minSecondsInSlot || consensusSecondsInSlot > consensus.maxSecondsInSlot) {
    throw new IllegalArgumentException(s"Consensus seconds in slot is out of range. It should be no less than ${consensus.minSecondsInSlot} and be less or equal to ${consensus.maxSecondsInSlot}. " +
      s"Current value: ${consensusSecondsInSlot}")
  }

  // Init proper NetworkParams depend on MC network
  lazy val params: NetworkParams = sidechainSettings.genesisData.mcNetwork match {
    case "regtest" => RegTestParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      consensusSecondsInSlot = consensusSecondsInSlot,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      signersPublicKeys = signersPublicKeys,
      mastersPublicKeys = mastersPublicKeys,
      circuitType = circuitType,
      signersThreshold = sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold,
      certProvingKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certProvingKeyFilePath,
      certVerificationKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certVerificationKeyFilePath,
      calculatedSysDataConstant = calculatedSysDataConstant,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash),
      cswProvingKeyFilePath = sidechainSettings.csw.cswProvingKeyFilePath,
      cswVerificationKeyFilePath = sidechainSettings.csw.cswVerificationKeyFilePath,
      restrictForgers = sidechainSettings.forger.restrictForgers,
      allowedForgersList = forgerList,
      sidechainCreationVersion = sidechainCreationOutput.getScCrOutput.version,
      chainId = chainInfo.regtestId,
      isCSWEnabled = isCSWEnabled,
      isNonCeasing = sidechainSettings.genesisData.isNonCeasing
    )

    case "testnet" => TestNetParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      consensusSecondsInSlot = consensusSecondsInSlot,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      signersPublicKeys = signersPublicKeys,
      mastersPublicKeys = mastersPublicKeys,
      circuitType = circuitType,
      signersThreshold = sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold,
      certProvingKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certProvingKeyFilePath,
      certVerificationKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certVerificationKeyFilePath,
      calculatedSysDataConstant = calculatedSysDataConstant,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash),
      cswProvingKeyFilePath = sidechainSettings.csw.cswProvingKeyFilePath,
      cswVerificationKeyFilePath = sidechainSettings.csw.cswVerificationKeyFilePath,
      restrictForgers = sidechainSettings.forger.restrictForgers,
      allowedForgersList = forgerList,
      sidechainCreationVersion = sidechainCreationOutput.getScCrOutput.version,
      chainId = chainInfo.testnetId,
      isCSWEnabled = isCSWEnabled,
      isNonCeasing = sidechainSettings.genesisData.isNonCeasing
    )

    case "mainnet" => MainNetParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      consensusSecondsInSlot = consensusSecondsInSlot,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      signersPublicKeys = signersPublicKeys,
      mastersPublicKeys = mastersPublicKeys,
      circuitType = circuitType,
      signersThreshold = sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold,
      certProvingKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certProvingKeyFilePath,
      certVerificationKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certVerificationKeyFilePath,
      calculatedSysDataConstant = calculatedSysDataConstant,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash),
      cswProvingKeyFilePath = sidechainSettings.csw.cswProvingKeyFilePath,
      cswVerificationKeyFilePath = sidechainSettings.csw.cswVerificationKeyFilePath,
      restrictForgers = sidechainSettings.forger.restrictForgers,
      allowedForgersList = forgerList,
      sidechainCreationVersion = sidechainCreationOutput.getScCrOutput.version,
      chainId = chainInfo.mainnetId,
      isCSWEnabled = isCSWEnabled,
      isNonCeasing = sidechainSettings.genesisData.isNonCeasing
    )
    case _ => throw new IllegalArgumentException("Configuration file sparkz.genesis.mcNetwork parameter contains inconsistent value.")
  }

  if (params.isNonCeasing) {
    if (params.withdrawalEpochLength < params.minVirtualWithdrawalEpochLength)
      throw new IllegalArgumentException("Virtual withdrawal epoch length is too short.")

    log.info(s"Sidechain is non ceasing, virtual withdrawal epoch length is ${params.withdrawalEpochLength}.")
  } else {
    log.info(s"Sidechain is ceasing, withdrawal epoch length is ${params.withdrawalEpochLength}.")
  }

  // Configure Horizen address json serializer specifying proper network type.
  JsonHorizenPublicKeyHashSerializer.setNetworkType(params)

  // Generate Coboundary Marlin Proving System dlog keys
  log.info(s"Generating Coboundary Marlin Proving System dlog keys. It may take some time.")
  if(!CryptoLibProvider.commonCircuitFunctions.generateCoboundaryMarlinDLogKeys()) {
    throw new IllegalArgumentException("Can't generate Coboundary Marlin ProvingSystem dlog keys.")
  }

  // Generate snark keys only if were not present before.
  if (!Files.exists(Paths.get(params.certVerificationKeyFilePath)) || !Files.exists(Paths.get(params.certProvingKeyFilePath))) {
    log.info("Generating Cert snark keys. It may take some time.")
    val expectedNumOfCustomFields = circuitType match {
      case NaiveThresholdSignatureCircuitWithKeyRotation =>
        CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION
      case NaiveThresholdSignatureCircuit =>
        if (params.isCSWEnabled) {
          CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW
        } else {
          CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_NO_KEY_ROTATION
        }
    }
    val result: Boolean = circuitType match {
      case NaiveThresholdSignatureCircuit =>
        CryptoLibProvider.sigProofThresholdCircuitFunctions.generateCoboundaryMarlinSnarkKeys(sidechainSettings.withdrawalEpochCertificateSettings.maxPks, params.certProvingKeyFilePath, params.certVerificationKeyFilePath, expectedNumOfCustomFields)
      case NaiveThresholdSignatureCircuitWithKeyRotation =>
        CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateCoboundaryMarlinSnarkKeys(sidechainSettings.withdrawalEpochCertificateSettings.maxPks, params.certProvingKeyFilePath, params.certVerificationKeyFilePath)
    }
    if (!result)
      throw new IllegalArgumentException("Can't generate Cert Coboundary Marlin ProvingSystem snark keys.")
  }

  // Init ForkManager
  // We need to have it initializes before the creation of the SidechainState
  ForkManager.init(forkConfigurator, sidechainSettings.genesisData.mcNetwork) match {
    case Success(_) =>
    case Failure(exception) => throw exception
  }

  // Retrieve information for using a web socket connector
  lazy val communicationClient: WebSocketCommunicationClient = new WebSocketCommunicationClient()
  lazy val webSocketReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(sidechainSettings.websocket)

  // Create the web socket connector and configure it
  lazy val webSocketConnector : WebSocketConnector with WebSocketChannel = new WebSocketConnectorImpl(
    sidechainSettings.websocket.address,
    sidechainSettings.websocket.connectionTimeout,
    communicationClient,
    webSocketReconnectionHandler
  )

  // Start the web socket connector
  val connectorStarted : Try[Unit] = webSocketConnector.start()

  // If the web socket connector can be started, maybe we would to associate a client to the web socket channel created by the connector
  if(connectorStarted.isSuccess)
    communicationClient.setWebSocketChannel(webSocketConnector)
  else if (sidechainSettings.withdrawalEpochCertificateSettings.submitterIsEnabled)
    throw new RuntimeException("Unable to connect to websocket. Certificate submitter needs connection to Mainchain.")

  // Init Forger with a proper web socket client
  val mainchainNodeChannel = new MainchainNodeChannelImpl(communicationClient, params)
  val mainchainSynchronizer = new MainchainSynchronizer(mainchainNodeChannel)

  override val swaggerConfig: String = Source.fromResource("api/sidechainApi.yaml")(Codec.UTF8).getLines.mkString("\n")

//  val rejectedApiRoutes: Seq[SidechainRejectionApiRoute]
//  val applicationApiRoutes: Seq[ApplicationApiRoute]

  // Init API
  lazy val rejectedApiRoutes: Seq[SidechainRejectionApiRoute] = rejectedApiPaths.asScala.map(path => SidechainRejectionApiRoute(path.getKey, path.getValue, settings.restApi, nodeViewHolderRef))

  // Once received developer's custom api, we need to create, for each of them, a SidechainApiRoute.
  // For do this, we use an instance of ApplicationApiRoute. This is an entry point between SidechainApiRoute and external java api.
  lazy val applicationApiRoutes: Seq[ApplicationApiRoute] = customApiGroups.asScala.map(apiRoute => ApplicationApiRoute(settings.restApi, apiRoute, nodeViewHolderRef))

  val coreApiRoutes: Seq[ApiRoute]

  // In order to provide the feature to override core api and exclude some other apis,
  // first we create custom reject routes (otherwise we cannot know which route has to be excluded), second we bind custom apis and then core apis
  lazy override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute]()
    .union(rejectedApiRoutes)
    .union(applicationApiRoutes)
    .union(coreApiRoutes)

  lazy val secretSubmitProvider: SecretSubmitProvider = new SecretSubmitProviderImpl(nodeViewHolderRef)
  def getSecretSubmitProvider: SecretSubmitProvider = secretSubmitProvider

  val shutdownHookThread: Thread = new Thread("ShutdownHook-Thread") {
    override def run(): Unit = {
      log.error("Unexpected shutdown")
      sidechainStopAll()
    }
  }

  // we rewrite (by overriding) the base class run() method, just to customizing the shutdown hook thread
  // not to call the stopAll() method
  override def run(): Unit = {
    require(settings.network.agentName.length <= Application.ApplicationNameLimit,
      s"Agent name ${settings.network.agentName} length exceeds limit ${Application.ApplicationNameLimit}")

    log.debug(s"Available processors: ${Runtime.getRuntime.availableProcessors}")
    log.debug(s"Max memory available: ${Runtime.getRuntime.maxMemory}")
    log.debug(s"RPC is allowed at ${settings.restApi.bindAddress.toString}")

    val bindAddress = settings.restApi.bindAddress
    Http().newServerAt(bindAddress.getAddress.getHostAddress,bindAddress.getPort).bind(combinedRoute)

    //Remove the Logger shutdown hook
    val factory = LogManager.getFactory
    if (factory.isInstanceOf[Log4jContextFactory]) {
      val contextFactory = factory.asInstanceOf[Log4jContextFactory]
      contextFactory.getShutdownCallbackRegistry.asInstanceOf[DefaultShutdownCallbackRegistry].stop()
    }

    //Add a new Shutdown hook that closes all the storages and stops all the interfaces and actors.
    Runtime.getRuntime.addShutdownHook(shutdownHookThread)
  }


  // this method does not override stopAll(), but it rewrites part of its contents
  def sidechainStopAll(fromEndpoint: Boolean = false): Unit = synchronized {
    val currentThreadId     = Thread.currentThread.getId
    val shutdownHookThreadId = shutdownHookThread.getId

    // remove the shutdown hook for avoiding being called twice when we eventually call System.exit()
    // (unless we are executiexecuting the hook thread itself)
    if (currentThreadId != shutdownHookThreadId)
      Runtime.getRuntime.removeShutdownHook(shutdownHookThread)

    log.info("Stopping network services")
    networkControllerRef ! ShutdownNetwork

    log.info("Stopping actors")
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, Duration(5, TimeUnit.SECONDS))

    synchronized {
      log.info("Calling custom application stopAll...")
      applicationStopper.stopAll()

      log.info("Closing all closable resources...")
      closableResourceList.foreach(_.close())

      log.info("Shutdown the logger...")
      LogManager.shutdown()

      if(fromEndpoint) {
        System.exit(0)
      }
    }
  }

  protected def registerClosableResource[S <: AutoCloseable](closableResource: S) : S = {
    closableResourceList += closableResource
    closableResource
  }

  def getTransactionSubmitProvider: TransactionSubmitProvider[TX]

}
