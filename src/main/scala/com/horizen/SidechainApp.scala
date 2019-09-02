package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import java.io.{File => JFile}

import scala.collection.immutable.Map
import akka.actor.ActorRef
import com.horizen.api.http.{ApplicationApiGroup, ApplicationApiRoute, FirstSolutionRestApi, MySecondCustomApi, POCApi, SecondSolutionRestApi, SidechainApiErrorHandler, SidechainApiRejectionHandler, SidechainBlockActorRef, SidechainBlockApiRoute, SidechainNodeApiRoute, SidechainRejectionApiRoute, SidechainTransactionActorRef, SidechainTransactionApiRoute, SidechainUtilApiRoute, SidechainWalletApiRoute}
import com.horizen.block.SidechainBlock
import com.horizen.box.BoxSerializer
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.params.{MainNetParams, StorageParams}
import com.horizen.secret.SecretSerializer
import com.horizen.state.{ApplicationState, DefaultApplicationState}
import com.horizen.storage.{IODBStoreAdapter, SidechainHistoryStorage, SidechainSecretStorage, SidechainStateStorage, SidechainWalletBoxStorage, Storage}
import com.horizen.transaction.TransactionSerializer
import com.horizen.wallet.{ApplicationWallet, DefaultApplicationWallet}
import io.iohk.iodb.LSMStore
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.core.api.http.ApiRoute
import scorex.core.app.Application
import scorex.core.network.{NodeViewSynchronizerRef, PeerFeature}
import scorex.core.network.message.MessageSpec
import scorex.core.serialization.{ScorexSerializer, SerializerRegistry}
import scorex.core.settings.ScorexSettings
import scorex.util.{ModifierId, ScorexLogging}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import com.horizen.forge.ForgerRef

import scala.collection.mutable
import scala.io.Source

class SidechainApp(val settingsFilename: String)
  extends Application
  with ScorexLogging
{
  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  private val sidechainSettings = SidechainSettings.read(Some(settingsFilename))
  override implicit lazy val settings: ScorexSettings = SidechainSettings.read(Some(settingsFilename)).scorexSettings

  private val storageList = mutable.ListBuffer[Storage]()

  System.out.println(s"Starting application with settings \n$sidechainSettings")
  log.debug(s"Starting application with settings \n$sidechainSettings")

  override implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  override implicit def rejectionHandler: RejectionHandler = SidechainApiRejectionHandler.rejectionHandler

  override protected lazy val features: Seq[PeerFeature] = Seq()

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq()

  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]())
  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]())
  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())
  protected val defaultApplicationWallet: ApplicationWallet = new DefaultApplicationWallet()
  protected val defaultApplicationState: ApplicationState = new DefaultApplicationState()

  case class CustomParams(override val sidechainGenesisBlockId: ModifierId) extends MainNetParams {

  }
  val params: CustomParams = CustomParams(sidechainSettings.genesisBlock.get.id)

  protected val sidechainSecretStorage = new SidechainSecretStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/secret")),
    sidechainSecretsCompanion)
  protected val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/wallet")),
    sidechainBoxesCompanion)
  protected val sidechainStateStorage = new SidechainStateStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/state")),
    sidechainBoxesCompanion)
  protected val sidechainHistoryStorage = new SidechainHistoryStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/history")),
    sidechainTransactionsCompanion, params)

  //TODO remove these test settings
  sidechainSecretStorage.add(sidechainSettings.targetSecretKey1)
  sidechainSecretStorage.add(sidechainSettings.targetSecretKey2)


  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(sidechainSettings, sidechainHistoryStorage,
    sidechainStateStorage,
    "test seed %s".format(sidechainSettings.scorexSettings.network.nodeName).getBytes(), // To Do: add Wallet group to config file => wallet.seed
    sidechainWalletBoxStorage, sidechainSecretStorage, params, timeProvider,
    defaultApplicationWallet, defaultApplicationState, sidechainSettings.genesisBlock.get)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(NodeViewSynchronizerRef.props[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
      SidechainBlock, SidechainHistory, SidechainMemoryPool]
      (networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider,
        Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]]() //TODO Must be specified
      ))

  val sidechainTransactioActorRef : ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockForgerActorRef : ActorRef = ForgerRef(sidechainSettings, nodeViewHolderRef, sidechainTransactionsCompanion, params)
  val sidechainBlockActorActorRef : ActorRef = SidechainBlockActorRef(sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef)

  //SerializerRecord(SimpleBoxTransaction.simpleBoxEncoder)

  implicit val serializerReg: SerializerRegistry = SerializerRegistry(Seq())

  // I'm a developer and I want to add my custom rest api
  var customApiRoutes : Seq[ApplicationApiGroup] = Seq[ApplicationApiGroup](
    new MySecondCustomApi(sidechainSettings)
  )

  // I'm a developer and I want to exclude from my api some core routes
  var rejectedApiPaths : Seq[(String, String)] = Seq[(String, String)]()

  var rejectedApiRoutes : Seq[SidechainRejectionApiRoute] = Seq[SidechainRejectionApiRoute]()
  rejectedApiPaths.foreach(path => rejectedApiRoutes = rejectedApiRoutes :+ (SidechainRejectionApiRoute(path._1, path._2, settings.restApi, nodeViewHolderRef)))

  // Once received developer's custom api, we need to create, for each of them, a SidechainApiRoute.
  // For do this, we use an instance of ApplicationApiRoute. This is an entry point between SidechainApiRoute and external java api.
  var applicationApiRoutes : Seq[ApplicationApiRoute] = Seq[ApplicationApiRoute]()
  customApiRoutes.foreach(apiRoute => applicationApiRoutes = applicationApiRoutes :+ (ApplicationApiRoute(settings.restApi, nodeViewHolderRef, apiRoute)))

  val coreApiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
//    MainchainBlockApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorActorRef, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactioActorRef),
    SidechainUtilApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef),
    FirstSolutionRestApi(sidechainSettings, settings.restApi, nodeViewHolderRef),
    SecondSolutionRestApi(sidechainSettings, settings.restApi, nodeViewHolderRef),
    POCApi(sidechainSettings, settings.restApi, nodeViewHolderRef)
    //ChainApiRoute(settings.restApi, nodeViewHolderRef, miner),
    //TransactionApiRoute(settings.restApi, nodeViewHolderRef),
    //DebugApiRoute(settings.restApi, nodeViewHolderRef, miner),
    //WalletApiRoute(settings.restApi, nodeViewHolderRef),
    //StatsApiRoute(settings.restApi, nodeViewHolderRef),
    //UtilsApiRoute(settings.restApi),
    //NodeViewApiRoute[SidechainTypes#SCBT](settings.restApi, nodeViewHolderRef),
    //PeersApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi)
  )

  // In order to provide the feature to override core api and exclude some other apis,
  // first we create custom reject routes (otherwise we cannot know which route has to be excluded), second we bind custom apis and then core apis
  override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute]()
    .union(rejectedApiRoutes)
    .union(applicationApiRoutes)
    .union(coreApiRoutes)

  override val swaggerConfig: String = Source.fromResource("api/sidechainApi.yaml").getLines.mkString("\n")

  override def stopAll(): Unit = {
    super.stopAll()
    storageList.foreach(_.close())
  }

  //TODO additional initialization (see HybridApp)
  private def openStorage(storagePath: JFile) : Storage = {
    storagePath.mkdirs()
    val storage = new IODBStoreAdapter(new LSMStore(storagePath, StorageParams.storageKeySize))
    storageList += storage
    storage
  }

  // Note: ignore this at the moment
  // waiting WS client interface
  private def setupMainchainConnection  = ???

  // waiting WS client interface
  private def getMainchainConnectionInfo  = ???
}

object SidechainApp extends App {
  private val settingsFilename = args.headOption.getOrElse("src/main/resources/settings.conf")
  val app = new SidechainApp(settingsFilename)
  app.run()
  app.log.info("Sidechain application successfully started...")
}
