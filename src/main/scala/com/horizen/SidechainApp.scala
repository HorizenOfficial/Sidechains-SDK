package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import akka.actor.ActorRef
import akka.http.scaladsl.server.ExceptionHandler
import com.horizen.api.http._
import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.BoxSerializer
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.forge.ForgerRef
import com.horizen.params.{MainNetParams, StorageParams}
import com.horizen.secret.SecretSerializer
import com.horizen.state.{ApplicationState, DefaultApplicationState}
import com.horizen.storage._
import com.horizen.transaction.TransactionSerializer
import com.horizen.validation.{MainchainPoWValidator, SidechainBlockValidator}
import com.horizen.wallet.{ApplicationWallet, DefaultApplicationWallet}
import scorex.core.api.http.{ApiRoute, PeersApiRoute}
import scorex.core.app.Application
import scorex.core.network.message.MessageSpec
import scorex.core.network.{NodeViewSynchronizerRef, PeerFeature}
import scorex.core.serialization.{ScorexSerializer, SerializerRegistry}
import scorex.core.settings.ScorexSettings
import scorex.core.transaction.Transaction
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.mutable
import scala.collection.immutable.Map

import com.google.inject.Inject
import com.google.inject.name.Named

class SidechainApp @Inject()
  (@Named("SidechainSettings") val sidechainSettings: SidechainSettings,
   @Named("CustomBoxSerializers") val customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
   @Named("CustomSecretSerializers") val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomTransactionSerializers") val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]],
   @Named("ApplicationWallet") val applicationWallet: ApplicationWallet,
   @Named("ApplicationState") val applicationState: ApplicationState,
   @Named("MainNetParams") val mainNetParams: MainNetParams,
   @Named("SecretStorage") val secretStorage: Storage,
   @Named("WalletBoxStorage") val walletBoxStorage: Storage,
   @Named("WalletTransactionStorage") val walletTransactionStorage: Storage,
   @Named("StateStorage") val stateStorage: Storage,
   @Named("HistoryStorage") val historyStorage: Storage,
  )
  extends Application
  with ScorexLogging
{
  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  //private val sidechainSettings = SidechainSettings.read(Some(settingsFilename))
  override implicit lazy val settings: ScorexSettings = sidechainSettings.scorexSettings

  private val storageList = mutable.ListBuffer[Storage]()

  System.out.println(s"Starting application with settings \n$sidechainSettings")
  log.debug(s"Starting application with settings \n$sidechainSettings")

  override implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler
  override protected lazy val features: Seq[PeerFeature] = Seq()

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq(SidechainSyncInfoMessageSpec)


  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)
  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)
  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers)
  //protected val defaultApplicationWallet: ApplicationWallet = new DefaultApplicationWallet()
  //protected val defaultApplicationState: ApplicationState = new DefaultApplicationState()

  //val mainNetParams = new MainNetParams()

  case class CustomParams(override val sidechainGenesisBlockId: ModifierId) extends MainNetParams {

  }

  val params: CustomParams = CustomParams(sidechainSettings.genesisBlock.get.id)

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
  if(sidechainSecretStorage.isEmpty) {
    sidechainSecretStorage.add(sidechainSettings.nodeKey)
  }

  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(sidechainSettings, sidechainHistoryStorage,
    sidechainStateStorage,
    "test seed %s".format(sidechainSettings.scorexSettings.network.nodeName).getBytes(), // To Do: add Wallet group to config file => wallet.seed
    sidechainWalletBoxStorage, sidechainSecretStorage, sidechainWalletTransactionStorage, params, timeProvider,
    applicationWallet, applicationState, sidechainSettings.genesisBlock.get,
    Seq(new SidechainBlockValidator(params)/*, new MainchainPoWValidator(sidechainHistoryStorage, params)*/)
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

  val sidechainTransactioActorRef : ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockForgerActorRef : ActorRef = ForgerRef(sidechainSettings, nodeViewHolderRef, sidechainTransactionsCompanion, params)
  val sidechainBlockActorRef : ActorRef = SidechainBlockActorRef(sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef)

  implicit val serializerReg: SerializerRegistry = SerializerRegistry(Seq())

  override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute(settings.restApi, nodeViewHolderRef, mainNetParams),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactioActorRef),
    SidechainUtilsApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef),
    PeersApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi)
  )

  override val swaggerConfig: String = ""

  override def stopAll(): Unit = {
    super.stopAll()
    storageList.foreach(_.close())
  }

  private def registerStorage(storage: Storage) : Storage = {
    storageList += storage
    storage
  }

  // Note: ignore this at the moment
  // waiting WS client interface
  private def setupMainchainConnection  = ???

  // waiting WS client interface
  private def getMainchainConnectionInfo  = ???
}
