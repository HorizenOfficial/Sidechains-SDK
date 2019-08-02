package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import scala.collection.immutable.Map
import akka.actor.ActorRef
import com.horizen.api.http.{MainchainBlockApiRoute, SidechainBlockActorRef, SidechainBlockApiRoute, SidechainNodeApiRoute, SidechainTransactionActorRef, SidechainTransactionApiRoute, SidechainUtilApiRoute, SidechainWalletApiRoute}
import com.horizen.block.SidechainBlock
import com.horizen.box.BoxSerializer
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.params.MainNetParams
import com.horizen.secret.SecretSerializer
import com.horizen.state.{ApplicationState, DefaultApplicationState}
import com.horizen.transaction.TransactionSerializer
import com.horizen.wallet.{ApplicationWallet, DefaultApplicationWallet}
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.core.api.http.ApiRoute
import scorex.core.app.Application
import scorex.core.network.{NodeViewSynchronizerRef, PeerFeature}
import scorex.core.network.message.MessageSpec
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.ScorexSettings
import scorex.util.ScorexLogging

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

  System.out.println(s"Starting application with settings \n$sidechainSettings")
  log.debug(s"Starting application with settings \n$sidechainSettings")

  override protected lazy val features: Seq[PeerFeature] = Seq()

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq()

  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]())
  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]())
  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())
  protected val defaultApplicationWallet: ApplicationWallet = new DefaultApplicationWallet()
  protected val defaultApplicationState: ApplicationState = new DefaultApplicationState()

  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(sidechainSettings, new MainNetParams(), timeProvider, sidechainBoxesCompanion,
    sidechainSecretsCompanion, sidechainTransactionsCompanion, defaultApplicationWallet, defaultApplicationState)

  val sidechainTransactioActor : ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
//  val sidechainBlockActor : ActorRef = SidechainBlockActorRef(nodeViewHolderRef)

  override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef),//, sidechainBlockActor),
    SidechainNodeApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactioActor),
    SidechainUtilApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef),
    //ChainApiRoute(settings.restApi, nodeViewHolderRef, miner),
    //TransactionApiRoute(settings.restApi, nodeViewHolderRef),
    //DebugApiRoute(settings.restApi, nodeViewHolderRef, miner),
    //WalletApiRoute(settings.restApi, nodeViewHolderRef),
    //StatsApiRoute(settings.restApi, nodeViewHolderRef),
    //UtilsApiRoute(settings.restApi),
    //NodeViewApiRoute[SidechainTypes#SCBT](settings.restApi, nodeViewHolderRef),
    //PeersApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi)
  )

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(NodeViewSynchronizerRef.props[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
      SidechainBlock, SidechainHistory, SidechainMemoryPool]
      (networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider,
       Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]]() //TODO Must be specified
      ))

  override val swaggerConfig: String = Source.fromResource("api/testApi.yaml").getLines.mkString("\n")


  // waiting WS client interface
  private def setupMainchainConnection  = ???

  // waiting WS client interface
  private def getMainchainConnectionInfo  = ???

  //TODO additional initialization (see HybridApp)
}

object SidechainApp extends App {
  private val settingsFilename = args.headOption.getOrElse("src/main/resources/settings.conf")
  val sidechainSettings = SidechainSettings.read(Some(settingsFilename))
  val  app = new SidechainApp(settingsFilename)
  app.log.info("Sidechain application successfully started...")
}
