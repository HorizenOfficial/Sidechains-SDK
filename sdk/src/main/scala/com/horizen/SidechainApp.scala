package com.horizen

import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.{HashMap => JHashMap, List => JList}
import akka.actor.ActorRef
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import com.google.inject.name.Named
import com.google.inject.{Inject, _}
import com.horizen.api.http.{SidechainSubmitterApiRoute, _}
import com.horizen.backup.BoxIterator
import com.horizen.block.{ProofOfWorkVerifier, SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.BoxSerializer
import com.horizen.certificatesubmitter.CertificateSubmitterRef
import com.horizen.certificatesubmitter.network.{CertificateSignaturesManagerRef, CertificateSignaturesSpec, GetCertificateSignaturesSpec}
import com.horizen.companion._
import com.horizen.consensus.ConsensusDataStorage
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.csw.CswManagerRef
import com.horizen.forge.{ForgerRef, MainchainSynchronizer}
import com.horizen.helper.{NodeViewProvider, NodeViewProviderImpl, SecretSubmitProvider, SecretSubmitProviderImpl, TransactionSubmitProvider, TransactionSubmitProviderImpl}
import com.horizen.params._
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, SchnorrProposition, SchnorrPropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import com.horizen.secret.SecretSerializer
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.transaction._
import com.horizen.utils.{BlockUtils, BytesUtils, Pair}
import com.horizen.wallet.ApplicationWallet
import scorex.core.api.http.ApiRoute
import scorex.core.app.Application
import scorex.core.network.message.MessageSpec
import scorex.core.network.PeerFeature
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.ScorexSettings
import scorex.core.transaction.Transaction
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.collection.immutable.Map
import scala.collection.mutable
import scala.io.{Codec, Source}
import com.horizen.network.SidechainNodeViewSynchronizer
import com.horizen.websocket.client.{DefaultWebSocketReconnectionHandler, MainchainNodeChannelImpl, WebSocketChannel, WebSocketCommunicationClient, WebSocketConnector, WebSocketConnectorImpl, WebSocketReconnectionHandler}
import com.horizen.websocket.server.WebSocketServerRef
import com.horizen.serialization.JsonHorizenPublicKeyHashSerializer
import com.horizen.transaction.mainchain.SidechainCreation

import scala.util.{Failure, Success, Try}


class SidechainApp @Inject()
  (@Named("SidechainSettings") val sidechainSettings: SidechainSettings,
   @Named("CustomBoxSerializers") val customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
   @Named("CustomSecretSerializers") val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomTransactionSerializers") val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]],
   @Named("ApplicationWallet") val applicationWallet: ApplicationWallet,
   @Named("ApplicationState") val applicationState: ApplicationState,
   @Named("SecretStorage") val secretStorage: Storage,
   @Named("WalletBoxStorage") val walletBoxStorage: Storage,
   @Named("WalletTransactionStorage") val walletTransactionStorage: Storage,
   @Named("StateStorage") val stateStorage: Storage,
   @Named("StateForgerBoxStorage") val forgerBoxStorage: Storage,
   @Named("StateUtxoMerkleTreeStorage") val utxoMerkleTreeStorage: Storage,
   @Named("HistoryStorage") val historyStorage: Storage,
   @Named("WalletForgingBoxesInfoStorage") val walletForgingBoxesInfoStorage: Storage,
   @Named("WalletCswDataStorage") val walletCswDataStorage: Storage,
   @Named("ConsensusStorage") val consensusStorage: Storage,
   @Named("BackupStorage") val backUpStorage: Storage,
   @Named("CustomApiGroups") val customApiGroups: JList[ApplicationApiGroup],
   @Named("RejectedApiPaths") val rejectedApiPaths : JList[Pair[String, String]],
  )
  extends Application  with ScorexLogging
{


  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  override implicit lazy val settings: ScorexSettings = sidechainSettings.scorexSettings

  private val storageList = mutable.ListBuffer[Storage]()

  log.info(s"Starting application with settings \n$sidechainSettings")

  override implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  override implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  override protected lazy val features: Seq[PeerFeature] = Seq()

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq(
    SidechainSyncInfoMessageSpec,
    // It can be no more Certificate signatures than the public keys for the Threshold Signature Circuit
    new GetCertificateSignaturesSpec(sidechainSettings.withdrawalEpochCertificateSettings.signersPublicKeys.size),
    new CertificateSignaturesSpec(sidechainSettings.withdrawalEpochCertificateSettings.signersPublicKeys.size)
  )

  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers)
  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)
  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)

  // Deserialize genesis block bytes
  val genesisBlock: SidechainBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
    )

  val genesisPowData: Seq[(Int, Int)] = ProofOfWorkVerifier.parsePowData(sidechainSettings.genesisData.powData)

  val signersPublicKeys: Seq[SchnorrProposition] = sidechainSettings.withdrawalEpochCertificateSettings.signersPublicKeys
    .map(bytes => SchnorrPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(bytes)))

  val calculatedSysDataConstant: Array[Byte] = CryptoLibProvider.sigProofThresholdCircuitFunctions.generateSysDataConstant(signersPublicKeys.map(_.bytes()).asJava, sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold)
  log.info(s"calculated sysDataConstant is: ${BytesUtils.toHexString(calculatedSysDataConstant)}")

  val sidechainCreationOutput: SidechainCreation = BlockUtils.tryGetSidechainCreation(genesisBlock) match {
    case Success(output) => output
    case Failure(exception) => throw new IllegalArgumentException("Genesis block specified in the configuration file has no Sidechain Creation info.", exception)
  }

  val forgerList: Seq[(PublicKey25519Proposition, VrfPublicKey)] = sidechainSettings.forger.allowedForgersList.map(el =>
    (PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(el.blockSignProposition)), VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(el.vrfPublicKey))))

  // Init proper NetworkParams depend on MC network
  val params: NetworkParams = sidechainSettings.genesisData.mcNetwork match {
    case "regtest" => RegTestParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      signersPublicKeys = signersPublicKeys,
      signersThreshold = sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold,
      certProvingKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certProvingKeyFilePath,
      certVerificationKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certVerificationKeyFilePath,
      calculatedSysDataConstant = calculatedSysDataConstant,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash),
      cswProvingKeyFilePath = sidechainSettings.csw.cswProvingKeyFilePath,
      cswVerificationKeyFilePath = sidechainSettings.csw.cswVerificationKeyFilePath,
      restrictForgers = sidechainSettings.forger.restrictForgers,
      allowedForgersList = forgerList,
      sidechainCreationVersion = sidechainCreationOutput.getScCrOutput.version
  )

    case "testnet" => TestNetParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      signersPublicKeys = signersPublicKeys,
      signersThreshold = sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold,
      certProvingKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certProvingKeyFilePath,
      certVerificationKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certVerificationKeyFilePath,
      calculatedSysDataConstant = calculatedSysDataConstant,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash),
      cswProvingKeyFilePath = sidechainSettings.csw.cswProvingKeyFilePath,
      cswVerificationKeyFilePath = sidechainSettings.csw.cswVerificationKeyFilePath,
      restrictForgers = sidechainSettings.forger.restrictForgers,
      allowedForgersList = forgerList,
      sidechainCreationVersion = sidechainCreationOutput.getScCrOutput.version
    )

    case "mainnet" => MainNetParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      signersPublicKeys = signersPublicKeys,
      signersThreshold = sidechainSettings.withdrawalEpochCertificateSettings.signersThreshold,
      certProvingKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certProvingKeyFilePath,
      certVerificationKeyFilePath = sidechainSettings.withdrawalEpochCertificateSettings.certVerificationKeyFilePath,
      calculatedSysDataConstant = calculatedSysDataConstant,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash),
      cswProvingKeyFilePath = sidechainSettings.csw.cswProvingKeyFilePath,
      cswVerificationKeyFilePath = sidechainSettings.csw.cswVerificationKeyFilePath,
      restrictForgers = sidechainSettings.forger.restrictForgers,
      allowedForgersList = forgerList,
      sidechainCreationVersion = sidechainCreationOutput.getScCrOutput.version
    )
    case _ => throw new IllegalArgumentException("Configuration file scorex.genesis.mcNetwork parameter contains inconsistent value.")
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
    if (!CryptoLibProvider.sigProofThresholdCircuitFunctions.generateCoboundaryMarlinSnarkKeys(
        sidechainSettings.withdrawalEpochCertificateSettings.maxPks, params.certProvingKeyFilePath, params.certVerificationKeyFilePath)) {
      throw new IllegalArgumentException("Can't generate Cert Coboundary Marlin ProvingSystem snark keys.")
    }
  }
  if (!Files.exists(Paths.get(params.cswVerificationKeyFilePath)) || !Files.exists(Paths.get(params.cswProvingKeyFilePath))) {
    log.info("Generating CSW snark keys. It may take some time.")
    if (!CryptoLibProvider.cswCircuitFunctions.generateCoboundaryMarlinSnarkKeys(
      params.withdrawalEpochLength, params.cswProvingKeyFilePath, params.cswVerificationKeyFilePath)) {
      throw new IllegalArgumentException("Can't generate CSW Coboundary Marlin ProvingSystem snark keys.")
    }
  }

  // Init all storages
  protected val sidechainSecretStorage = new SidechainSecretStorage(
    //openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/secret")),
    registerStorage(secretStorage),
    sidechainSecretsCompanion)
  protected val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(
    //openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/wallet")),
    registerStorage(walletBoxStorage),
    sidechainBoxesCompanion)
  protected val sidechainWalletTransactionStorage = new SidechainWalletTransactionStorage(
    //openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/walletTransaction")),
    registerStorage(walletTransactionStorage),
    sidechainTransactionsCompanion)
  protected val sidechainStateStorage = new SidechainStateStorage(
    //openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/state")),
    registerStorage(stateStorage),
    sidechainBoxesCompanion)
  protected val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(registerStorage(forgerBoxStorage))
  protected val sidechainStateUtxoMerkleTreeStorage = new SidechainStateUtxoMerkleTreeStorage(registerStorage(utxoMerkleTreeStorage))
  protected val sidechainHistoryStorage = new SidechainHistoryStorage(
    //openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/history")),
    registerStorage(historyStorage),
    sidechainTransactionsCompanion, params)
  protected val consensusDataStorage = new ConsensusDataStorage(
    //openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/consensusData")),
    registerStorage(consensusStorage))
  protected val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(registerStorage(walletForgingBoxesInfoStorage))
  protected val sidechainWalletCswDataStorage = new SidechainWalletCswDataStorage(registerStorage(walletCswDataStorage))

  // Append genesis secrets if we start the node first time
  if(sidechainSecretStorage.isEmpty) {
    for(secretHex <- sidechainSettings.wallet.genesisSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretHex)))

    for(secretSchnorr <- sidechainSettings.withdrawalEpochCertificateSettings.signersSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretSchnorr)))
  }

  protected val backupStorage = new BackupStorage(registerStorage(backUpStorage), sidechainBoxesCompanion)

  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(
    sidechainSettings,
    sidechainHistoryStorage,
    consensusDataStorage,
    sidechainStateStorage,
    sidechainStateForgerBoxStorage,
    sidechainStateUtxoMerkleTreeStorage,
    sidechainWalletBoxStorage,
    sidechainSecretStorage,
    sidechainWalletTransactionStorage,
    forgingBoxesMerklePathStorage,
    sidechainWalletCswDataStorage,
    backupStorage,
    params,
    timeProvider,
    applicationWallet,
    applicationState,
    genesisBlock
    ) // TO DO: why not to put genesisBlock as a part of params? REVIEW Params structure

  def modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlock.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(SidechainNodeViewSynchronizer.props(networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider, modifierSerializers))

  // Retrieve information for using a web socket connector
  val communicationClient: WebSocketCommunicationClient = new WebSocketCommunicationClient()
  val webSocketReconnectionHandler: WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(sidechainSettings.websocket)

  // Create the web socket connector and configure it
  val webSocketConnector : WebSocketConnector with WebSocketChannel = new WebSocketConnectorImpl(
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
  val sidechainBlockForgerActorRef: ActorRef = ForgerRef("Forger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer, sidechainTransactionsCompanion, timeProvider, params)

  // Init Transactions and Block actors for Api routes classes
  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef("SidechainBlock", sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef)

  // Init Certificate Submitter
  val certificateSubmitterRef: ActorRef = CertificateSubmitterRef(sidechainSettings, nodeViewHolderRef, params, mainchainNodeChannel)
  val certificateSignaturesManagerRef: ActorRef = CertificateSignaturesManagerRef(networkControllerRef, certificateSubmitterRef, params, sidechainSettings.scorexSettings.network)

  // Init CSW manager
  val cswManager: ActorRef = CswManagerRef(sidechainSettings, params, nodeViewHolderRef)

  //Websocket server for the Explorer
  if(sidechainSettings.websocket.wsServer) {
    val webSocketServerActor: ActorRef = WebSocketServerRef(nodeViewHolderRef,sidechainSettings.websocket.wsServerPort)
  }

  // Init API
  var rejectedApiRoutes : Seq[SidechainRejectionApiRoute] = Seq[SidechainRejectionApiRoute]()
  rejectedApiPaths.asScala.foreach(path => rejectedApiRoutes = rejectedApiRoutes :+ SidechainRejectionApiRoute(path.getKey, path.getValue, settings.restApi, nodeViewHolderRef))

  // Once received developer's custom api, we need to create, for each of them, a SidechainApiRoute.
  // For do this, we use an instance of ApplicationApiRoute. This is an entry point between SidechainApiRoute and external java api.
  var applicationApiRoutes : Seq[ApplicationApiRoute] = Seq[ApplicationApiRoute]()
  customApiGroups.asScala.foreach(apiRoute => applicationApiRoutes = applicationApiRoutes :+ ApplicationApiRoute(settings.restApi, apiRoute, nodeViewHolderRef))

  val boxIterator = backupStorage.getBoxIterator
  var coreApiRoutes: Seq[SidechainApiRoute] = Seq[SidechainApiRoute](
    MainchainBlockApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainSubmitterApiRoute(settings.restApi, certificateSubmitterRef, nodeViewHolderRef),
    SidechainCswApiRoute(settings.restApi, nodeViewHolderRef, cswManager),
    SidechainBackupApiRoute(settings.restApi, nodeViewHolderRef, boxIterator)
  )

  val transactionSubmitProvider : TransactionSubmitProvider = new TransactionSubmitProviderImpl(sidechainTransactionActorRef)
  val nodeViewProvider : NodeViewProvider = new NodeViewProviderImpl(nodeViewHolderRef)
  val secretSubmitProvider: SecretSubmitProvider = new SecretSubmitProviderImpl(nodeViewHolderRef)

  // In order to provide the feature to override core api and exclude some other apis,
  // first we create custom reject routes (otherwise we cannot know which route has to be excluded), second we bind custom apis and then core apis
  override val apiRoutes: Seq[ApiRoute] = Seq[SidechainApiRoute]()
    .union(rejectedApiRoutes)
    .union(applicationApiRoutes)
    .union(coreApiRoutes)

  override val swaggerConfig: String = Source.fromResource("api/sidechainApi.yaml")(Codec.UTF8).getLines.mkString("\n")

  override def stopAll(): Unit = {
    super.stopAll()
    storageList.foreach(_.close())
  }

  private def registerStorage(storage: Storage) : Storage = {
    storageList += storage
    storage
  }

  def getTransactionSubmitProvider: TransactionSubmitProvider = transactionSubmitProvider

  def getNodeViewProvider: NodeViewProvider = nodeViewProvider

  def getSecretSubmitProvider: SecretSubmitProvider = secretSubmitProvider

  actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

}
