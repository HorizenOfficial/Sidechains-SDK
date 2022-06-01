package com.horizen

import akka.actor.ActorRef
import com.google.inject.Inject
import com.google.inject.name.Named
import com.horizen.api.http._
import com.horizen.block.{SidechainBlock, SidechainBlockBase, SidechainBlockHeader, SidechainBlockSerializer}
import com.horizen.box.BoxSerializer
import com.horizen.certificatesubmitter.CertificateSubmitterRef
import com.horizen.certificatesubmitter.network.CertificateSignaturesManagerRef
import com.horizen.companion._
import com.horizen.consensus.ConsensusDataStorage
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.csw.CswManagerRef
import com.horizen.forge.ForgerRef
import com.horizen.helper._
import com.horizen.network.SidechainNodeViewSynchronizer
import com.horizen.node._
import com.horizen.secret.SecretSerializer
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.transaction._
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BlockUtils, BytesUtils, Pair}
import com.horizen.wallet.ApplicationWallet
import com.horizen.websocket.server.WebSocketServerRef
import scorex.core.api.http.ApiRoute
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.ScorexSettings
import scorex.core.transaction.Transaction
import scorex.core.{ModifierTypeId, NodeViewModifier}

import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.{HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.{Codec, Source}
import scala.util.{Failure, Success}


class SidechainApp @Inject()
  (@Named("SidechainSettings") sidechainSettings: SidechainSettings,
   @Named("CustomBoxSerializers") val customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
   @Named("CustomSecretSerializers") customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomTransactionSerializers") val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]],
   @Named("ApplicationWallet") applicationWallet: ApplicationWallet,
   @Named("ApplicationState") applicationState: ApplicationState,
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
   @Named("CustomApiGroups") customApiGroups: JList[ApplicationApiGroup],
   @Named("RejectedApiPaths") rejectedApiPaths : JList[Pair[String, String]]
  )
  extends AbstractSidechainApp(
    sidechainSettings,
    customSecretSerializers,
    customApiGroups,
    rejectedApiPaths
  )
{

  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  override implicit lazy val settings: ScorexSettings = sidechainSettings.scorexSettings

  private val storageList = mutable.ListBuffer[Storage]()

  log.info(s"Starting application with settings \n$sidechainSettings")

  protected lazy val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers)
  protected lazy val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)

  // Deserialize genesis block bytes
  lazy val genesisBlock: SidechainBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
    )

  lazy val sidechainCreationOutput: SidechainCreation = BlockUtils.tryGetSidechainCreation(genesisBlock) match {
    case Success(output) => output
    case Failure(exception) => throw new IllegalArgumentException("Genesis block specified in the configuration file has no Sidechain Creation info.", exception)
  }

  if (!Files.exists(Paths.get(params.cswVerificationKeyFilePath)) || !Files.exists(Paths.get(params.cswProvingKeyFilePath))) {
    log.info("Generating CSW snark keys. It may take some time.")
    if (!CryptoLibProvider.cswCircuitFunctions.generateCoboundaryMarlinSnarkKeys(
      params.withdrawalEpochLength, params.cswProvingKeyFilePath, params.cswVerificationKeyFilePath)) {
      throw new IllegalArgumentException("Can't generate CSW Coboundary Marlin ProvingSystem snark keys.")
    }
  }

  // Init CSW manager
  lazy val cswManager: ActorRef = CswManagerRef(sidechainSettings, params, nodeViewHolderRef)

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
    params,
    timeProvider,
    applicationWallet,
    applicationState,
    genesisBlock
    ) // TO DO: why not to put genesisBlock as a part of params? REVIEW Params structure

  def modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlockBase.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(SidechainNodeViewSynchronizer.props(networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider, modifierSerializers))

  // Init Forger with a proper web socket client
  val sidechainBlockForgerActorRef: ActorRef = ForgerRef("Forger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer, sidechainTransactionsCompanion, timeProvider, params)

  // Init Transactions and Block actors for Api routes classes
  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef[PMOD,SidechainSyncInfo,SidechainHistory]("SidechainBlock", sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef)

  // Init Certificate Submitter
  val certificateSubmitterRef: ActorRef = CertificateSubmitterRef(sidechainSettings, nodeViewHolderRef, params, mainchainNodeChannel)
  val certificateSignaturesManagerRef: ActorRef = CertificateSignaturesManagerRef(networkControllerRef, certificateSubmitterRef, params, sidechainSettings.scorexSettings.network)

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

  var coreApiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute[TX,
      SidechainBlockHeader,PMOD,NodeHistory,NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute[TX,
      SidechainBlockHeader,PMOD,NodeHistory,NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainSubmitterApiRoute(settings.restApi, certificateSubmitterRef, nodeViewHolderRef),
    SidechainCswApiRoute(settings.restApi, nodeViewHolderRef, cswManager)
  )

  val nodeViewProvider : NodeViewProvider = new NodeViewProviderImpl(nodeViewHolderRef)
  val secretSubmitProvider: SecretSubmitProvider = new SecretSubmitProviderImpl(nodeViewHolderRef)
  val transactionSubmitProvider : TransactionSubmitProvider = new TransactionSubmitProviderImpl(sidechainTransactionActorRef)

  // In order to provide the feature to override core api and exclude some other apis,
  // first we create custom reject routes (otherwise we cannot know which route has to be excluded), second we bind custom apis and then core apis
  override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute]()
    .union(rejectedApiRoutes)
    .union(applicationApiRoutes)
    .union(coreApiRoutes)

  override val swaggerConfig: String = Source.fromResource("api/sidechainApi.yaml")(Codec.UTF8).getLines.mkString("\n")

  override def stopAll(): Unit = {
    super.stopAll()
    storageList.foreach(_.close())
  }

  def getTransactionSubmitProvider: TransactionSubmitProvider = transactionSubmitProvider
  def getNodeViewProvider: NodeViewProvider = nodeViewProvider
  def getSecretSubmitProvider: SecretSubmitProvider = secretSubmitProvider

  actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)
}
