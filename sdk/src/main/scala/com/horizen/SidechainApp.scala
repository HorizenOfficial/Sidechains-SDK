package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import java.io.{File => JFile}
import java.net.InetSocketAddress

import akka.actor.ActorRef
import com.horizen.api.http._
import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.BoxSerializer
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.params.{MainNetParams, NetworkParams, RegTestParams, StorageParams}
import com.horizen.secret.SecretSerializer
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.transaction.TransactionSerializer
import scorex.core.{ModifierTypeId, NodeViewModifier}
import com.horizen.validation.{MainchainPoWValidator, SidechainBlockValidator}
import com.horizen.wallet.ApplicationWallet
import scorex.core.network.message.MessageSpec
import scorex.core.network.{NodeViewSynchronizerRef, PeerFeature}
import scorex.core.serialization.{ScorexSerializer, SerializerRegistry}
import scorex.core.settings.ScorexSettings
import scorex.util.ModifierId
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import scorex.core.api.http.ApiRoute
import scorex.core.app.Application
import akka.http.scaladsl.server.ExceptionHandler
import com.horizen.forge.{ForgerRef, MainchainSynchronizer}
import com.horizen.websocket._
import scorex.core.transaction.Transaction
import scorex.util.ScorexLogging

import scala.collection.mutable
import scala.util.Try
import scala.concurrent.duration._
import scala.collection.immutable.Map
import scala.io.Source
import com.google.inject.Inject
import com.google.inject.name.Named
import com.horizen.utils.BytesUtils

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
   @Named("HistoryStorage") val historyStorage: Storage,
  )
  extends Application with ScorexLogging
{
  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  override implicit lazy val settings: ScorexSettings = sidechainSettings.scorexSettings

  private val storageList = mutable.ListBuffer[Storage]()

  System.out.println(s"Starting application with settings \n$sidechainSettings")
  log.debug(s"Starting application with settings \n$sidechainSettings")

  override implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  override implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  override protected lazy val features: Seq[PeerFeature] = Seq()

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq(SidechainSyncInfoMessageSpec)

  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)
  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)
  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers)

  val params: NetworkParams = sidechainSettings.genesisData.mcNetwork match {
    case "regtest" => RegTestParams(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scId),
      sidechainSettings.genesisBlock.get.id,
      sidechainSettings.genesisBlock.get.mainchainBlocks.head.hash,
      sidechainSettings.genesisPowData,
      sidechainSettings.genesisData.mcBlockHeight
    )
    case "mainnet" | "testnet" => MainNetParams(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scId),
      sidechainSettings.genesisBlock.get.id,
      sidechainSettings.genesisBlock.get.mainchainBlocks.head.hash,
      sidechainSettings.genesisPowData,
      sidechainSettings.genesisData.mcBlockHeight
    )
    case _ => throw new IllegalArgumentException("Configuration file scorex.genesis.mcNetwork parameter contains inconsistent value.")
  }

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
  protected val sidechainHistoryStorage = new SidechainHistoryStorage(
    //openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/history")),
    registerStorage(historyStorage),
    sidechainTransactionsCompanion, params)

  //TODO remove these test settings
  if (sidechainSettings.scorexSettings.network.nodeName.equals("testNode1")) {
    sidechainSecretStorage.add(sidechainSettings.targetSecretKey1)
    sidechainSecretStorage.add(sidechainSettings.targetSecretKey2)
  }

  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(sidechainSettings, sidechainHistoryStorage,
    sidechainStateStorage,
    sidechainSettings.wallet.seed.getBytes(),
    sidechainWalletBoxStorage, sidechainSecretStorage, sidechainWalletTransactionStorage, params, timeProvider,
    applicationWallet, applicationState, sidechainSettings.genesisBlock.get,
    Seq(new SidechainBlockValidator(params), new MainchainPoWValidator(sidechainHistoryStorage, params))
  )


  def modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlock.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(NodeViewSynchronizerRef.props[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
      SidechainBlock, SidechainHistory, SidechainMemoryPool]
      (networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider,
        modifierSerializers
      ))

  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)

  // retrieve information for using a web socket connector
  val webSocketMessageHandler : WebSocketMessageHandler = new WebSocketCommunicationClient()
  val webSocketReconnectionHandler : WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(sidechainSettings.websocket)

  // create the cweb socket connector and configure it
  val webSocketConnector : WebSocketConnector = new WebSocketConnectorImpl(
    sidechainSettings.websocket.bindAddress,
    sidechainSettings.websocket.connectionTimeout,
    webSocketMessageHandler,
    webSocketReconnectionHandler
  )

  // start the web socket connector
  val connectorStarted : Try[Unit] = webSocketConnector.start()

  // if the web socket connector can be started, maybe we would to associate a client to the web socket channel created by the connector
  if(connectorStarted.isSuccess)
  {
    val communicationClient : WebSocketCommunicationClient = webSocketMessageHandler.asInstanceOf[WebSocketCommunicationClient]
    communicationClient.setWebSocketChannel(webSocketConnector)
  }
  val mainchainNodeChannel = new MainchainNodeChannelImpl(webSocketMessageHandler.asInstanceOf[WebSocketCommunicationClient], params)
  val mainchainSynchronizer = new MainchainSynchronizer(mainchainNodeChannel)
  val sidechainBlockForgerActorRef: ActorRef = ForgerRef(sidechainSettings, nodeViewHolderRef, mainchainSynchronizer, sidechainTransactionsCompanion, params)
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef(sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef)

  implicit val serializerReg: SerializerRegistry = SerializerRegistry(Seq())

  // I'm a developer and I want to add my custom rest api
  var customApiRoutes : Seq[ApplicationApiGroup] = Seq[ApplicationApiGroup](
    //new MySecondCustomApi(sidechainSettings),
    //new MyCustomApi()
  )

  // I'm a developer and I want to exclude from my api some core routes
  var rejectedApiPaths : Seq[(String, String)] = Seq[(String, String)]()

  var rejectedApiRoutes : Seq[SidechainRejectionApiRoute] = Seq[SidechainRejectionApiRoute]()
  rejectedApiPaths.foreach(path => rejectedApiRoutes = rejectedApiRoutes :+ (SidechainRejectionApiRoute(path._1, path._2, settings.restApi, nodeViewHolderRef)))

  // Once received developer's custom api, we need to create, for each of them, a SidechainApiRoute.
  // For do this, we use an instance of ApplicationApiRoute. This is an entry point between SidechainApiRoute and external java api.
  var applicationApiRoutes : Seq[ApplicationApiRoute] = Seq[ApplicationApiRoute]()
  customApiRoutes.foreach(apiRoute => applicationApiRoutes = applicationApiRoutes :+ (ApplicationApiRoute(settings.restApi, nodeViewHolderRef, apiRoute)))

  val coreApiRoutes: Seq[SidechainApiRoute] = Seq[SidechainApiRoute](
    MainchainBlockApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef)
  )

  // In order to provide the feature to override core api and exclude some other apis,
  // first we create custom reject routes (otherwise we cannot know which route has to be excluded), second we bind custom apis and then core apis
  override val apiRoutes: Seq[ApiRoute] = Seq[SidechainApiRoute]()
    .union(rejectedApiRoutes)
    .union(applicationApiRoutes)
    .union(coreApiRoutes)

  override val swaggerConfig: String = Source.fromResource("api/sidechainApi.yaml").getLines.mkString("\n")


  override def stopAll(): Unit = {
    super.stopAll()
    storageList.foreach(_.close())
  }

  private def registerStorage(storage: Storage) : Storage = {
    storageList += storage
    storage
  }
}
