package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import java.io.{File => JFile}

import scala.collection.immutable.Map
import scala.collection
import akka.actor.ActorRef
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
import scorex.core.api.http.{ApiRoute, NodeViewApiRoute, PeersApiRoute, UtilsApiRoute}
import scorex.core.app.Application
import scorex.core.network.{NodeViewSynchronizerRef, PeerFeature}
import scorex.core.network.message.{MessageSpec, SyncInfoMessageSpec}
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.ScorexSettings
import scorex.util.ScorexLogging

import scala.collection.mutable

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

  override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    //ChainApiRoute(settings.restApi, nodeViewHolderRef, miner),
    //TransactionApiRoute(settings.restApi, nodeViewHolderRef),
    //DebugApiRoute(settings.restApi, nodeViewHolderRef, miner),
    //WalletApiRoute(settings.restApi, nodeViewHolderRef),
    //StatsApiRoute(settings.restApi, nodeViewHolderRef),
    UtilsApiRoute(settings.restApi),
    //NodeViewApiRoute[SidechainTypes#SCBT](settings.restApi, nodeViewHolderRef),
    PeersApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi)
  )

  override protected lazy val features: Seq[PeerFeature] = Seq()

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq()

  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]())
  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]())
  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())
  protected val defaultApplicationWallet: ApplicationWallet = new DefaultApplicationWallet()
  protected val defaultApplicationState: ApplicationState = new DefaultApplicationState()

  //TODO change implementation
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
    sidechainTransactionsCompanion, new MainNetParams())

  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(sidechainSettings, sidechainHistoryStorage,
    sidechainStateStorage, sidechainWalletBoxStorage, sidechainSecretStorage, new MainNetParams(), timeProvider,
    defaultApplicationWallet, defaultApplicationState, sidechainSettings.genesisBlock.get)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(NodeViewSynchronizerRef.props[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
      SidechainBlock, SidechainHistory, SidechainMemoryPool]
      (networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider,
       Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]]() //TODO Must be specified
      ))

  override val swaggerConfig: String = ""

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

}

object SidechainApp extends App {
  private val settingsFilename = args.headOption.getOrElse("settings.conf")
  new SidechainApp(settingsFilename).run()
}
