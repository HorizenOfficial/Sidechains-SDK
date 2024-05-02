package io.horizen.utxo

import akka.actor.ActorRef
import com.google.inject.Inject
import com.google.inject.name.Named
import io.horizen.account.api.http.route
import io.horizen.api.http._
import io.horizen.api.http.route.{MainchainBlockApiRoute, SidechainNodeApiRoute, SidechainSubmitterApiRoute}
import io.horizen.block.SidechainBlockBase
import io.horizen.certificatesubmitter.network.CertificateSignaturesManagerRef
import io.horizen.consensus.ConsensusDataStorage
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.fork.ForkConfigurator
import io.horizen.helper._
import io.horizen.params._
import io.horizen.secret.SecretSerializer
import io.horizen.storage._
import io.horizen.transaction.TransactionSerializer
import io.horizen.utils.{BytesUtils, Pair}
import io.horizen.utxo.api.http
import io.horizen.utxo.api.http.SidechainApplicationApiGroup
import io.horizen.utxo.api.http.route.{SidechainBackupApiRoute, SidechainBlockApiRoute, SidechainCswApiRoute, SidechainTransactionApiRoute, SidechainWalletApiRoute}
import io.horizen.utxo.backup.BoxIterator
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader, SidechainBlockSerializer}
import io.horizen.utxo.box.BoxSerializer
import io.horizen.utxo.certificatesubmitter.CertificateSubmitterRef
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.companion.{SidechainBoxesCompanion, SidechainTransactionsCompanion}
import io.horizen.utxo.csw.CswManagerRef
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.network.SidechainNodeViewSynchronizer
import io.horizen.utxo.node._
import io.horizen.utxo.state.{ApplicationState, SidechainStateUtxoMerkleTreeProvider, SidechainUtxoMerkleTreeProviderCSWDisabled, SidechainUtxoMerkleTreeProviderCSWEnabled}
import io.horizen.utxo.storage._
import io.horizen.utxo.wallet.{ApplicationWallet, SidechainWalletCswDataProvider, SidechainWalletCswDataProviderCSWDisabled, SidechainWalletCswDataProviderCSWEnabled}
import io.horizen.utxo.websocket.server.WebSocketServerRef
import io.horizen.{AbstractSidechainApp, ChainInfo, SidechainAppEvents, SidechainAppStopper, SidechainSettings, SidechainSyncInfo, SidechainSyncInfoMessageSpec, SidechainTypes, WebSocketServerSettings}
import sparkz.core.api.http.ApiRoute
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.transaction.Transaction
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.{HashMap => JHashMap, List => JList}
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.io.{Codec, Source}

class SidechainApp @Inject()
  (@Named("SidechainSettings") override val sidechainSettings: SidechainSettings,
   @Named("CustomBoxSerializers") customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
   @Named("CustomSecretSerializers") override val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomTransactionSerializers") customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]],
   @Named("ApplicationWallet") val applicationWallet: ApplicationWallet,
   @Named("ApplicationState") val applicationState: ApplicationState,
   @Named("SecretStorage") secretStorage: Storage,
   @Named("WalletBoxStorage") walletBoxStorage: Storage,
   @Named("WalletTransactionStorage") walletTransactionStorage: Storage,
   @Named("StateStorage") stateStorage: Storage,
   @Named("StateForgerBoxStorage") forgerBoxStorage: Storage,
   @Named("StateUtxoMerkleTreeStorage") utxoMerkleTreeStorage: Storage,
   @Named("HistoryStorage") historyStorage: Storage,
   @Named("WalletForgingBoxesInfoStorage") walletForgingBoxesInfoStorage: Storage,
   @Named("WalletCswDataStorage") walletCswDataStorage: Storage,
   @Named("ConsensusStorage") consensusStorage: Storage,
   @Named("BackupStorage") backUpStorage: Storage,
   @Named("CustomApiGroups") val customApiGroups: JList[SidechainApplicationApiGroup],
   @Named("RejectedApiPaths") override val rejectedApiPaths: JList[Pair[String, String]],
   @Named("ApplicationStopper") override val applicationStopper: SidechainAppStopper,
   @Named("ForkConfiguration") override val forkConfigurator: ForkConfigurator,
   @Named("AppVersion") appVersion: String,
   @Named("MainchainBlockReferenceDelay") mcBlockReferenceDelay : Int,
   @Named("MaxHistoryRewriteLength") maxHistoryRewriteLength : Int
  )
  extends AbstractSidechainApp(
    sidechainSettings,
    customSecretSerializers,
    rejectedApiPaths,
    applicationStopper,
    forkConfigurator,
    //ChainInfo is used in Account model and it has no sense for UTXO but it still requested by AbstractSidechainApp.
    //TODO In the future we may think about how to make Params to have model specific part.
    ChainInfo(
      regtestId = 111,
      testnetId = 222,
      mainnetId = 333),
    mcBlockReferenceDelay,
    maxHistoryRewriteLength
    )
{

  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  log.info(s"Starting application with settings \n$sidechainSettings")

  override val swaggerConfig: String = Source.fromResource("utxo/api/sidechainApi.yaml")(Codec.UTF8).getLines.mkString("\n")

  override protected lazy val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers, circuitType)
  protected lazy val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)

  // Deserialize genesis block bytes
  override lazy val genesisBlock: SidechainBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
    )

  if (isCSWEnabled) {
    log.info("Ceased Sidechain Withdrawal (CSW) is enabled")
    if (Option(params.cswVerificationKeyFilePath).forall(_.trim.isEmpty)){
      log.error("CSW Verification Key file path is not defined.")
      throw new IllegalArgumentException("CSW Verification Key file path is not defined.")
    }
    if (Option(params.cswProvingKeyFilePath).forall(_.trim.isEmpty)){
      log.error("CSW Proving Key file path is not defined.")
      throw new IllegalArgumentException("CSW Proving Key file path is not defined.")
    }

    if (!Files.exists(Paths.get(params.cswVerificationKeyFilePath)) || !Files.exists(Paths.get(params.cswProvingKeyFilePath))) {
      log.info("Generating CSW snark keys. It may take some time.")
      if (!CryptoLibProvider.cswCircuitFunctions.generateCoboundaryMarlinSnarkKeys(
        params.withdrawalEpochLength, params.cswProvingKeyFilePath, params.cswVerificationKeyFilePath)) {
        throw new IllegalArgumentException("Can't generate CSW Coboundary Marlin ProvingSystem snark keys.")
      }

    }
  }
  else {
    log.warn("******** Ceased Sidechain Withdrawal (CSW) is DISABLED ***********")
  }

  // Init all storages
  protected val sidechainSecretStorage = new SidechainSecretStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/secret")),
    registerClosableResource(secretStorage),
    sidechainSecretsCompanion)
  protected val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/wallet")),
    registerClosableResource(walletBoxStorage),
    sidechainBoxesCompanion)
  protected val sidechainWalletTransactionStorage = new SidechainWalletTransactionStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/walletTransaction")),
    registerClosableResource(walletTransactionStorage),
    sidechainTransactionsCompanion)
  protected val sidechainStateStorage = new SidechainStateStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/state")),
    registerClosableResource(stateStorage),
    sidechainBoxesCompanion,
    params)
  protected val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(registerClosableResource(forgerBoxStorage))
  protected val sidechainStateUtxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider = getSidechainStateUtxoMerkleTreeProvider(registerClosableResource(utxoMerkleTreeStorage), params)

  protected val sidechainHistoryStorage = new SidechainHistoryStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/history")),
    registerClosableResource(historyStorage),
    sidechainTransactionsCompanion, params)
  protected val consensusDataStorage = new ConsensusDataStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/consensusData")),
    registerClosableResource(consensusStorage))
  protected val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(registerClosableResource(walletForgingBoxesInfoStorage))
  protected val sidechainWalletCswDataProvider: SidechainWalletCswDataProvider = getSidechainWalletCswDataProvider(registerClosableResource(walletCswDataStorage), params)

  // Append genesis secrets if we start the node first time
  if(sidechainSecretStorage.isEmpty) {
    for(secretHex <- sidechainSettings.wallet.genesisSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretHex)))

    for(secretSchnorr <- sidechainSettings.withdrawalEpochCertificateSettings.signersSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretSchnorr)))
  }

  protected val backupStorage = new BackupStorage(registerClosableResource(backUpStorage), sidechainBoxesCompanion)

  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(
    sidechainSettings,
    sidechainHistoryStorage,
    consensusDataStorage,
    sidechainStateStorage,
    sidechainStateForgerBoxStorage,
    sidechainStateUtxoMerkleTreeProvider,
    sidechainWalletBoxStorage,
    sidechainSecretStorage,
    sidechainWalletTransactionStorage,
    forgingBoxesMerklePathStorage,
    sidechainWalletCswDataProvider,
    backupStorage,
    params,
    timeProvider,
    applicationWallet,
    applicationState,
    genesisBlock
    ) // TO DO: why not to put genesisBlock as a part of params? REVIEW Params structure

  def modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlockBase.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(SidechainNodeViewSynchronizer.props(networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider, modifierSerializers))

  // Init Forger with a proper web socket client
  val sidechainBlockForgerActorRef: ActorRef = forge.ForgerRef("Forger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer, sidechainTransactionsCompanion, timeProvider, params)

  // Init Transactions and Block actors for Api routes classes
  val sidechainTransactionActorRef: ActorRef = if (sidechainSettings.apiRateLimiter.enabled) {
    val rateLimiterActorRef: ActorRef = SidechainTransactionRateLimiterActorRef(nodeViewHolderRef, sidechainSettings.apiRateLimiter)
    SidechainTransactionActorRef(rateLimiterActorRef)
  } else {
    SidechainTransactionActorRef(nodeViewHolderRef)
  }
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef[PMOD, SidechainSyncInfo, SidechainHistory]("SidechainBlock", sidechainSettings, sidechainBlockForgerActorRef)

  // Init Certificate Submitter
  // Depends on params.isNonCeasing submitter will choose a proper strategy.
  val certificateSubmitterRef: ActorRef = CertificateSubmitterRef(sidechainSettings, nodeViewHolderRef, secureEnclaveApiClient, params, mainchainNodeChannel)
  val certificateSignaturesManagerRef: ActorRef = CertificateSignaturesManagerRef(networkControllerRef, certificateSubmitterRef, params, sidechainSettings.sparkzSettings.network)

  // Init CSW manager
  val cswManager: Option[ActorRef] = if (isCSWEnabled) Some(CswManagerRef(sidechainSettings, params, nodeViewHolderRef)) else None

  //Websocket server for the Explorer
  val websocketServerSettings: WebSocketServerSettings = sidechainSettings.websocketServer
  if(websocketServerSettings.wsServer) {
    val webSocketServerActor: ActorRef = WebSocketServerRef(nodeViewHolderRef,sidechainSettings.websocketServer.wsServerPort)
  }

  val boxIterator: BoxIterator = backupStorage.getBoxIterator

  override lazy val applicationApiRoutes: Seq[ApiRoute] = customApiGroups.asScala.map(apiRoute => http.route.SidechainApplicationApiRoute(settings.restApi, apiRoute, nodeViewHolderRef))

  override lazy val coreApiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute[TX,
      SidechainBlockHeader,PMOD, SidechainFeePaymentsInfo, NodeHistory, NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainTransactionsCompanion, sidechainBlockForgerActorRef, params, timeProvider),
    SidechainNodeApiRoute[TX,
      SidechainBlockHeader, PMOD, SidechainFeePaymentsInfo, NodeHistory, NodeState, NodeWallet, NodeMemoryPool, SidechainNodeView](peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef, this, params, appVersion),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params, circuitType),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef, sidechainSecretsCompanion),
    SidechainSubmitterApiRoute(settings.restApi, params, certificateSubmitterRef, nodeViewHolderRef, circuitType),
    SidechainCswApiRoute(settings.restApi, nodeViewHolderRef, cswManager, params),
    SidechainBackupApiRoute(settings.restApi, nodeViewHolderRef, boxIterator, params)
  )
  override lazy val metricsApiRoute: ApiRoute =  null  //TODO: add metrics also for UXO

  val nodeViewProvider: NodeViewProvider[
    TX,
    SidechainBlockHeader,
    PMOD,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] = new NodeViewProviderImpl(nodeViewHolderRef)

  def getNodeViewProvider: NodeViewProvider[
    TX,
    SidechainBlockHeader,
    PMOD,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] = nodeViewProvider

  val transactionSubmitProvider : TransactionSubmitProvider[TX] = new TransactionSubmitProviderImpl[TX](sidechainTransactionActorRef)

  override def getTransactionSubmitProvider: TransactionSubmitProvider[TX] = transactionSubmitProvider

  private def getSidechainStateUtxoMerkleTreeProvider(utxoMerkleTreeStorage: Storage, params: NetworkParams) = {
    if (params.isCSWEnabled) {
      SidechainUtxoMerkleTreeProviderCSWEnabled(new SidechainStateUtxoMerkleTreeStorage(utxoMerkleTreeStorage))
    }
    else
      SidechainUtxoMerkleTreeProviderCSWDisabled()
  }


  private def getSidechainWalletCswDataProvider(cswDataStorage: Storage, params: NetworkParams) = {
    if (params.isCSWEnabled) {
      SidechainWalletCswDataProviderCSWEnabled(new SidechainWalletCswDataStorage(cswDataStorage))
    }
    else
      SidechainWalletCswDataProviderCSWDisabled()
  }


  actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

}
