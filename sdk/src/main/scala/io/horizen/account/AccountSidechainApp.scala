package io.horizen.account

import akka.actor.ActorRef
import com.google.inject.Inject
import com.google.inject.name.Named
import io.horizen._
import io.horizen.account.api.http.route.{AccountApplicationApiRoute, AccountBlockApiRoute, AccountTransactionApiRoute, AccountWalletApiRoute}
import io.horizen.account.api.http.{AccountApplicationApiGroup, route}
import io.horizen.account.api.rpc.handler.RpcHandler
import io.horizen.account.api.rpc.service.{EthService, RpcProcessor, RpcUtils}
import io.horizen.account.block.{AccountBlock, AccountBlockHeader, AccountBlockSerializer}
import io.horizen.account.certificatesubmitter.AccountCertificateSubmitterRef
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.forger.AccountForgerRef
import io.horizen.account.history.AccountHistory
import io.horizen.account.network.AccountNodeViewSynchronizer
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.account.state.MessageProcessor
import io.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import io.horizen.account.websocket.WebSocketAccountServerRef
import io.horizen.api.http._
import io.horizen.api.http.route.{MainchainBlockApiRoute, SidechainNodeApiRoute, SidechainSubmitterApiRoute}
import io.horizen.block.SidechainBlockBase
import io.horizen.certificatesubmitter.network.CertificateSignaturesManagerRef
import io.horizen.consensus.ConsensusDataStorage
import io.horizen.evm.LevelDBDatabase
import io.horizen.fork.ForkConfigurator
import io.horizen.helper.{NodeViewProvider, NodeViewProviderImpl, TransactionSubmitProvider, TransactionSubmitProviderImpl}
import io.horizen.network.SyncStatusActorRef
import io.horizen.node.NodeWalletBase
import io.horizen.secret.SecretSerializer
import io.horizen.storage._
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import io.horizen.transaction._
import io.horizen.utils.{BytesUtils, Pair}
import sparkz.core.api.http.ApiRoute
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.transaction.Transaction
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import java.io.File
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.io.{Codec, Source}


class AccountSidechainApp @Inject()
  (@Named("SidechainSettings") sidechainSettings: SidechainSettings,
   @Named("CustomSecretSerializers") customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomAccountTransactionSerializers") customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]],
   @Named("CustomApiGroups") customApiGroups: JList[AccountApplicationApiGroup],
   @Named("RejectedApiPaths") rejectedApiPaths: JList[Pair[String, String]],
   @Named("CustomMessageProcessors") customMessageProcessors: JList[MessageProcessor],
   @Named("ApplicationStopper") applicationStopper: SidechainAppStopper,
   @Named("ForkConfiguration") forkConfigurator: ForkConfigurator,
   @Named("ChainInfo") chainInfo: ChainInfo,
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
    chainInfo,
    mcBlockReferenceDelay,
    maxHistoryRewriteLength
  )
{

  override type TX = SidechainTypes#SCAT
  override type PMOD = AccountBlock
  override type NVHT = AccountSidechainNodeViewHolder

  override val swaggerConfig: String = Source.fromResource("account/api/accountApi.yaml")(Codec.UTF8).getLines.mkString("\n")

  protected lazy val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = SidechainAccountTransactionsCompanion(customAccountTransactionSerializers)

  // Deserialize genesis block bytes
  override lazy val genesisBlock: AccountBlock = new AccountBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
    )

  require (!isCSWEnabled, "Ceased Sidechain Withdrawal (CSW) should not be enabled in AccountSidechainApp!")

  val dataDirAbsolutePath: String = sidechainSettings.sparkzSettings.dataDir.getAbsolutePath
  val secretStore = new File(dataDirAbsolutePath + "/secret")
  val metaStateStore = new File(dataDirAbsolutePath + "/state")
  val historyStore = new File(dataDirAbsolutePath + "/history")
  val consensusStore = new File(dataDirAbsolutePath + "/consensusData")

  // Init all storages
  protected val sidechainHistoryStorage = new AccountHistoryStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(historyStore, 5)),
    sidechainTransactionsCompanion,
    params)

  protected val sidechainSecretStorage = new SidechainSecretStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(secretStore, 5)),
    sidechainSecretsCompanion)

  protected val stateMetadataStorage = new AccountStateMetadataStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(metaStateStore, params.maxHistoryRewritingLength * 2)))

  protected val stateDbStorage: LevelDBDatabase = registerClosableResource(new LevelDBDatabase(dataDirAbsolutePath + "/evm-state"))

  protected val consensusDataStorage = new ConsensusDataStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(consensusStore, 5)))

  // Append genesis secrets if we start the node first time
  if(sidechainSecretStorage.isEmpty) {
    for(secretHex <- sidechainSettings.wallet.genesisSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretHex)))

    for(secretSchnorr <- sidechainSettings.withdrawalEpochCertificateSettings.signersSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretSchnorr)))
  }

  override val nodeViewHolderRef: ActorRef = AccountNodeViewHolderRef(
    sidechainSettings,
    sidechainHistoryStorage,
    consensusDataStorage,
    stateMetadataStorage,
    stateDbStorage,
    customMessageProcessors.asScala,
    sidechainSecretStorage,
    params,
    timeProvider,
    genesisBlock
    ) // TO DO: why not to put genesisBlock as a part of params? REVIEW Params structure

  def modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlockBase.ModifierTypeId -> new AccountBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(AccountNodeViewSynchronizer.props(networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider, modifierSerializers))

  // Init Forger with a proper web socket client
  val sidechainBlockForgerActorRef: ActorRef = AccountForgerRef("AccountForger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer,
     sidechainTransactionsCompanion, timeProvider, params)

  // Init Transactions and Block actors for Api routes classes
  val sidechainTransactionActorRef: ActorRef = if (sidechainSettings.apiRateLimiter.enabled) {
    val rateLimiterActorRef: ActorRef = SidechainTransactionRateLimiterActorRef(nodeViewHolderRef, sidechainSettings.apiRateLimiter)
    SidechainTransactionActorRef(rateLimiterActorRef)
  } else {
    SidechainTransactionActorRef(nodeViewHolderRef)
  }
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef[PMOD, SidechainSyncInfo, AccountHistory]("AccountBlock", sidechainSettings, sidechainBlockForgerActorRef)

  // Init Certificate Submitter
  val certificateSubmitterRef: ActorRef = AccountCertificateSubmitterRef(sidechainSettings, nodeViewHolderRef, secureEnclaveApiClient, params, mainchainNodeChannel)
  val certificateSignaturesManagerRef: ActorRef = CertificateSignaturesManagerRef(networkControllerRef, certificateSubmitterRef, params, sidechainSettings.sparkzSettings.network)

  // Init Sync Status actor
  val syncStatusActorRef: ActorRef = SyncStatusActorRef("SyncStatus", sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef, params, timeProvider)

  //rpcHandler
  val rpcHandler = new RpcHandler(
    new EthService(
      nodeViewHolderRef,
      networkControllerRef,
      settings.restApi.timeout,
      params,
      sidechainSettings.ethService,
      sidechainSettings.sparkzSettings.network.maxIncomingConnections,
      RpcUtils.getClientVersion(appVersion),
      sidechainTransactionActorRef,
      syncStatusActorRef,
      sidechainTransactionsCompanion
    )
  )
  //Initialize RpcProcessor object with the rpcHandler
  val rpcProcessor: RpcProcessor = RpcProcessor(rpcHandler)
  
  if(sidechainSettings.websocketServer.wsServer) {
    val webSocketServerActor: ActorRef = WebSocketAccountServerRef(nodeViewHolderRef, rpcProcessor, sidechainSettings.websocketServer)
  }

  override lazy val applicationApiRoutes: Seq[ApiRoute] = customApiGroups.asScala.map(apiRoute => AccountApplicationApiRoute(settings.restApi, apiRoute, nodeViewHolderRef))

  override lazy val coreApiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute[TX, AccountBlockHeader, PMOD, AccountFeePaymentsInfo, NodeAccountHistory, NodeAccountState, NodeWalletBase, NodeAccountMemoryPool,AccountNodeView](settings.restApi, nodeViewHolderRef),
    AccountBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainTransactionsCompanion, sidechainBlockForgerActorRef, params, timeProvider),
    SidechainNodeApiRoute[TX, AccountBlockHeader, PMOD, AccountFeePaymentsInfo, NodeAccountHistory, NodeAccountState,NodeWalletBase,NodeAccountMemoryPool,AccountNodeView](peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef, this, params, appVersion),
    AccountTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params, circuitType),
    AccountWalletApiRoute(settings.restApi, nodeViewHolderRef, sidechainSecretsCompanion),
    SidechainSubmitterApiRoute(settings.restApi, params, certificateSubmitterRef, nodeViewHolderRef, circuitType),
    route.AccountEthRpcRoute(settings.restApi, nodeViewHolderRef, rpcProcessor)
  )

  val nodeViewProvider: NodeViewProvider[
    TX,
    AccountBlockHeader,
    PMOD,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] = new NodeViewProviderImpl(nodeViewHolderRef)

  def getNodeViewProvider: NodeViewProvider[
    TX,
    AccountBlockHeader,
    PMOD,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] = nodeViewProvider

  actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

  val transactionSubmitProvider: TransactionSubmitProvider[TX] = new TransactionSubmitProviderImpl[TX](sidechainTransactionActorRef)

  override def getTransactionSubmitProvider: TransactionSubmitProvider[TX] = transactionSubmitProvider

}
