package io.horizen

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import akka.stream.javadsl.Sink
import io.horizen.account.proposition.AddressProposition
import io.horizen.api.http._
import io.horizen.api.http.client.SecureEnclaveApiClient
import io.horizen.api.http.route.{DisableApiRoute, SidechainRejectionApiRoute}
import io.horizen.block.{ProofOfWorkVerifier, SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.certificatesubmitter.network.{CertificateSignaturesSpec, GetCertificateSignaturesSpec}
import io.horizen.companion._
import io.horizen.consensus.{ConsensusParamsUtil, intToConsensusEpochNumber, intToConsensusSlotNumber}
import io.horizen.cryptolibprovider.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.cryptolibprovider.{CircuitTypes, CommonCircuit, CryptoLibProvider}
import io.horizen.customconfig.CustomAkkaConfiguration
import io.horizen.forge.MainchainSynchronizer
import io.horizen.fork.{ConsensusParamsFork, ConsensusParamsForkInfo, ForkConfigurator, ForkManager, OptionalSidechainFork, SidechainForkConsensusEpoch}
import io.horizen.helper.{SecretSubmitProvider, SecretSubmitProviderImpl, TransactionSubmitProvider}
import io.horizen.json.serializer.JsonHorizenPublicKeyHashSerializer
import io.horizen.metrics.MetricsManager
import io.horizen.params._
import io.horizen.proposition._
import io.horizen.secret.SecretSerializer
import io.horizen.transaction._
import io.horizen.transaction.mainchain.SidechainCreation
import io.horizen.utils.{BlockUtils, BytesUtils, DynamicTypedSerializer, Pair, TimeToEpochUtils, WithdrawalEpochUtils}
import io.horizen.websocket.client._
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.impl.Log4jContextFactory
import org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry
import sparkz.core.api.http.ApiRoute
import sparkz.core.app.Application
import sparkz.core.network.NetworkController.ReceivableMessages.ShutdownNetwork
import sparkz.core.network.PeerFeature
import sparkz.core.network.message.MessageSpec
import sparkz.core.settings.SparkzSettings
import sparkz.util.SparkzLogging

import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}


abstract class AbstractSidechainApp
  (val sidechainSettings: SidechainSettings,
   val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   val rejectedApiPaths : JList[Pair[String, String]],
   val applicationStopper : SidechainAppStopper,
   val forkConfigurator : ForkConfigurator,
   val chainInfo : ChainInfo,
   val mcBlockReferenceDelay : Int,
   val maxHistoryRewriteLength: Int
  )
  extends Application with SparkzLogging
{
  override type TX <: Transaction
  override type PMOD <: SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase]

  override implicit lazy val settings: SparkzSettings = sidechainSettings.sparkzSettings
  override protected implicit lazy val actorSystem: ActorSystem = ActorSystem(settings.network.agentName, CustomAkkaConfiguration.getCustomConfig())

  private val closableResourceList = mutable.ListBuffer[AutoCloseable]()
  protected val sidechainTransactionsCompanion: DynamicTypedSerializer[TX, TransactionSerializer[TX]]
  protected val terminationTimeout: FiniteDuration = Duration(30, TimeUnit.SECONDS)
  protected val maxMcBlockRefDelay = 10


  log.info(s"Starting application with settings \n$sidechainSettings")

  protected val metricsManager = MetricsManager.init(timeProvider);

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


  // Init ForkManager
  // We need to have it initializes before the creation of the SidechainState and ConsensusParamsUtil
  ForkManager.init(forkConfigurator, sidechainSettings.genesisData.mcNetwork)

  val consensusParamsForkList: mutable.Buffer[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
    forkConfigurator.getOptionalSidechainForks.asScala.filter(fork => fork.getValue.isInstanceOf[ConsensusParamsFork])
  val defaultConsensusForks: ConsensusParamsFork = ConsensusParamsFork.DefaultConsensusParamsFork

  private val forgerRewardAddress: Option[AddressProposition] = Option(sidechainSettings.forger.forgerRewardAddress)
    .filter(_.nonEmpty)
    .map(address => new AddressProposition(BytesUtils.fromHexString(address)))

  // Init proper NetworkParams depend on MC network
  lazy val params: NetworkParams = sidechainSettings.genesisData.mcNetwork match {
    case "regtest" =>
      ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(ConsensusParamsForkInfo(0, defaultConsensusForks)) ++ consensusParamsForkList.map(fork => {
        ConsensusParamsForkInfo(fork.getKey.regtest, fork.getValue.asInstanceOf[ConsensusParamsFork])
      }))
      ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq( TimeToEpochUtils.virtualGenesisBlockTimeStamp(genesisBlock.timestamp)) ++ consensusParamsForkList.map(fork => {
        TimeToEpochUtils.getTimeStampForEpochAndSlot(genesisBlock.timestamp, intToConsensusEpochNumber(fork.getKey.regtest), intToConsensusSlotNumber(1))
      }))
      RegTestParams(
        sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
        sidechainGenesisBlockId = genesisBlock.id,
        genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
        parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
        genesisPoWData = genesisPowData,
        mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
        sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
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
        isNonCeasing = sidechainSettings.genesisData.isNonCeasing,
        isHandlingTransactionsEnabled = sidechainSettings.sparkzSettings.network.handlingTransactionsEnabled,
        mcBlockRefDelay = mcBlockReferenceDelay,
        resetModifiersStatus = sidechainSettings.history.resetModifiersStatus,
        maxHistoryRewritingLength = maxHistoryRewriteLength,
        rewardAddress = forgerRewardAddress

      )


    case "testnet" =>
      ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(ConsensusParamsForkInfo(0, defaultConsensusForks)) ++ consensusParamsForkList.map(fork => {
        ConsensusParamsForkInfo(fork.getKey.testnet, fork.getValue.asInstanceOf[ConsensusParamsFork])
      }))
      ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq( TimeToEpochUtils.virtualGenesisBlockTimeStamp(genesisBlock.timestamp)) ++ consensusParamsForkList.map(fork => {
        TimeToEpochUtils.getTimeStampForEpochAndSlot(genesisBlock.timestamp, intToConsensusEpochNumber(fork.getKey.testnet), intToConsensusSlotNumber(1))
      }))
      TestNetParams(
        sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
        sidechainGenesisBlockId = genesisBlock.id,
        genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
        parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
        genesisPoWData = genesisPowData,
        mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
        sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
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
        isNonCeasing = sidechainSettings.genesisData.isNonCeasing,
        isHandlingTransactionsEnabled = sidechainSettings.sparkzSettings.network.handlingTransactionsEnabled,
        mcBlockRefDelay = mcBlockReferenceDelay,
        resetModifiersStatus = sidechainSettings.history.resetModifiersStatus,
        rewardAddress = forgerRewardAddress
      )


     case "mainnet" =>
       ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(ConsensusParamsForkInfo(0, defaultConsensusForks)) ++ consensusParamsForkList.map(fork => {
         ConsensusParamsForkInfo(fork.getKey.mainnet, fork.getValue.asInstanceOf[ConsensusParamsFork])
       }))
       ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq( TimeToEpochUtils.virtualGenesisBlockTimeStamp(genesisBlock.timestamp)) ++ consensusParamsForkList.map(fork => {
         TimeToEpochUtils.getTimeStampForEpochAndSlot(genesisBlock.timestamp, intToConsensusEpochNumber(fork.getKey.mainnet), intToConsensusSlotNumber(1))
       }))
      MainNetParams(
        sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
        sidechainGenesisBlockId = genesisBlock.id,
        genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
        parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
        genesisPoWData = genesisPowData,
        mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
        sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
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
        isNonCeasing = sidechainSettings.genesisData.isNonCeasing,
        isHandlingTransactionsEnabled = sidechainSettings.sparkzSettings.network.handlingTransactionsEnabled,
        mcBlockRefDelay = mcBlockReferenceDelay,
        resetModifiersStatus = sidechainSettings.history.resetModifiersStatus,
        rewardAddress = forgerRewardAddress
      )


    case _ => throw new IllegalArgumentException("Configuration file sparkz.genesis.mcNetwork parameter contains inconsistent value.")
  }

  if (params.isNonCeasing) {
    if (params.withdrawalEpochLength < params.minVirtualWithdrawalEpochLength)
      throw new IllegalArgumentException("Virtual withdrawal epoch length is too short.")

    log.info(s"Sidechain is non ceasing, virtual withdrawal epoch length is ${params.withdrawalEpochLength}.")
  } else {
    if (params.mcBlockRefDelay >= WithdrawalEpochUtils.certificateSubmissionWindowLength(params) - 1)
      throw new IllegalArgumentException(s"Incorrect mainchain block reference delay. Delay must be less than submission window length")

    if (params.mcBlockRefDelay > maxMcBlockRefDelay)
      throw new IllegalArgumentException(s"Incorrect mainchain block reference delay. Delay must be less than %d".format(maxMcBlockRefDelay))

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


  // Retrieve information for using a web socket connector
  lazy val communicationClient: WebSocketCommunicationClient = new WebSocketCommunicationClient()
  lazy val webSocketReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(sidechainSettings.websocketClient)

  // Create the web socket connector and configure it
  if(sidechainSettings.websocketClient.enabled) {
    val webSocketConnector : WebSocketConnector with WebSocketChannel = new WebSocketConnectorImpl(
      sidechainSettings.websocketClient.address,
      sidechainSettings.websocketClient.connectionTimeout,
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
  } else {
    log.info("Websocket client is disabled.")
  }

  // Init Forger with a proper web socket client
  val mainchainNodeChannel = new MainchainNodeChannelImpl(communicationClient, params)
  val mainchainSynchronizer = new MainchainSynchronizer(mainchainNodeChannel)

//  val rejectedApiRoutes: Seq[SidechainRejectionApiRoute]
//  val applicationApiRoutes: Seq[ApplicationApiRoute]

  // Init API
  lazy val rejectedApiRoutes: Seq[SidechainRejectionApiRoute] = rejectedApiPaths.asScala.map(path => route.SidechainRejectionApiRoute(path.getKey, path.getValue, settings.restApi, nodeViewHolderRef))

  // Once received developer's custom api, we need to create, for each of them, a SidechainApiRoute.
  // For do this, we use an instance of ApplicationApiRoute. This is an entry point between SidechainApiRoute and external java api.
  val applicationApiRoutes: Seq[ApiRoute]

  val coreApiRoutes: Seq[ApiRoute]

  // disabledApiRoutes is the list of endpoints from coreApiRoutes that may need to be disabled when certain criteria
  // are met (e.g. seeder node)
  lazy val disabledApiRoutes: Seq[SidechainRejectionApiRoute] = coreApiRoutes.flatMap{
    case route: DisableApiRoute => route.listOfDisabledEndpoints(params).map{case (prefix, path, errOpt) =>
      SidechainRejectionApiRoute(prefix, path, settings.restApi, nodeViewHolderRef, errOpt)}
    case _ => Seq.empty[SidechainRejectionApiRoute]}

  // In order to provide the feature to override core api and exclude some other apis,
  // first we create custom reject routes (otherwise we cannot know which route has to be excluded), second we bind custom apis and then core apis
  lazy override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute]()
    .union(rejectedApiRoutes)
    .union(disabledApiRoutes)
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
    Http().newServerAt(bindAddress.getAddress.getHostAddress,bindAddress.getPort).connectionSource().to(Sink.foreach { connection =>
      log.info("New REST api connection from address :: %s".format(connection.remoteAddress.toString))
      connection.handleWithAsyncHandler(combinedRoute)
    }).run()



    //Remove the Logger shutdown hook
    LogManager.getFactory match {
      case contextFactory: Log4jContextFactory =>
        contextFactory.getShutdownCallbackRegistry.asInstanceOf[DefaultShutdownCallbackRegistry].stop()
      case _ => // do nothing
    }

    //Add a new Shutdown hook that closes all the storages and stops all the interfaces and actors.
    Runtime.getRuntime.addShutdownHook(shutdownHookThread)
  }


  // this method does not override stopAll(), but it rewrites part of its contents
  def sidechainStopAll(fromEndpoint: Boolean = false): Unit = synchronized {
    val currentThreadId      = Thread.currentThread.getId
    val shutdownHookThreadId = shutdownHookThread.getId

    // remove the shutdown hook for avoiding being called twice when we eventually call System.exit()
    // (unless we are executiexecuting the hook thread itself)
    if (currentThreadId != shutdownHookThreadId)
      Runtime.getRuntime.removeShutdownHook(shutdownHookThread)

    log.info("Stopping network services")
    networkControllerRef ! ShutdownNetwork

    log.info("Stopping actors")
    actorSystem.terminate()
    Try(Await.result(actorSystem.whenTerminated, terminationTimeout))
      .recover { case _ => log.info(s"Actor system failed to terminate in $terminationTimeout") }
      .map { _ =>
        synchronized {
          log.info("Calling custom application stopAll...")
          applicationStopper.stopAll()

          log.info("Closing all closable resources...")
          closableResourceList.foreach(_.close())

          log.info("Shutdown the logger...")
          LogManager.shutdown()

          if (fromEndpoint) {
            System.exit(0)
          }
        }
      }
  }

  protected def registerClosableResource[S <: AutoCloseable](closableResource: S) : S = {
    closableResourceList += closableResource
    closableResource
  }

  def getTransactionSubmitProvider: TransactionSubmitProvider[TX]

}
