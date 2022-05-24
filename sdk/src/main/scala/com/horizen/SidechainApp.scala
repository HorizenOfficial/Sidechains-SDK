package com.horizen

import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.{HashMap => JHashMap, List => JList}
import akka.actor.ActorRef
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import com.google.inject.name.Named
import com.google.inject.{Inject, _}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.api.http.{SidechainSubmitterApiRoute, _}
import com.horizen.block.{ProofOfWorkVerifier, SidechainBlock, SidechainBlockBase, SidechainBlockSerializer}
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
   @Named("RejectedApiPaths") rejectedApiPaths : JList[Pair[String, String]],
  )
  extends AbstractSidechainApp(
    sidechainSettings,
    customSecretSerializers,
    applicationWallet,
    applicationState,
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

  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers)
  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)

  // Deserialize genesis block bytes
  lazy val genesisBlock: SidechainBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
    )

  lazy val sidechainCreationOutput: SidechainCreation = BlockUtils.tryGetSidechainCreation(genesisBlock) match {
    case Success(output) => output
    case Failure(exception) => throw new IllegalArgumentException("Genesis block specified in the configuration file has no Sidechain Creation info.", exception)
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
    registerStorage(historyStorage),
    sidechainTransactionsCompanion,
    params)
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

  // If the web socket connector can be started, maybe we would to associate a client to the web socket channel created by the connector
  if(connectorStarted.isSuccess)
    communicationClient.setWebSocketChannel(webSocketConnector)
  else if (sidechainSettings.withdrawalEpochCertificateSettings.submitterIsEnabled)
    throw new RuntimeException("Unable to connect to websocket. Certificate submitter needs connection to Mainchain.")

  // Init Forger with a proper web socket client
  val sidechainBlockForgerActorRef: ActorRef = ForgerRef("Forger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer, sidechainTransactionsCompanion, timeProvider, params)

  // Init Transactions and Block actors for Api routes classes
  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef("SidechainBlock", sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef)

  //Websocket server for the Explorer
  if(sidechainSettings.websocket.wsServer) {
    val webSocketServerActor: ActorRef = WebSocketServerRef(nodeViewHolderRef,sidechainSettings.websocket.wsServerPort)
  }

  var coreApiRoutes: Seq[SidechainApiRoute] = Seq[SidechainApiRoute](
    MainchainBlockApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainSubmitterApiRoute(settings.restApi, certificateSubmitterRef, nodeViewHolderRef),
    SidechainCswApiRoute(settings.restApi, nodeViewHolderRef, cswManager)
  )

  val transactionSubmitProvider : TransactionSubmitProvider = new TransactionSubmitProviderImpl(sidechainTransactionActorRef)

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

  actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)
}
