package com.horizen.account

import akka.actor.ActorRef
import com.google.inject.Inject
import com.google.inject.name.Named
import com.horizen._
import com.horizen.account.api.http.{AccountBlockApiRoute, AccountEthRpcRoute, AccountTransactionApiRoute, AccountWalletApiRoute}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader, AccountBlockSerializer}
import com.horizen.account.certificatesubmitter.AccountCertificateSubmitterRef
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.forger.AccountForgerRef
import com.horizen.account.history.AccountHistory
import com.horizen.account.network.AccountNodeViewSynchronizer
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state.{AccountState, MessageProcessor}
import com.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import com.horizen.{AbstractSidechainApp, ChainInfo, SidechainAppEvents, SidechainSettings, SidechainSyncInfoMessageSpec, SidechainTypes}
import com.horizen.api.http._
import com.horizen.block.SidechainBlockBase
import com.horizen.certificatesubmitter.network.CertificateSignaturesManagerRef
import com.horizen.consensus.ConsensusDataStorage
import com.horizen.evm.LevelDBDatabase
import com.horizen.fork.ForkConfigurator
import com.horizen.node.NodeWalletBase
import com.horizen.secret.SecretSerializer
import com.horizen.storage._
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import com.horizen.transaction._
import com.horizen.utils.{BytesUtils, Pair}
import sparkz.core.api.http.ApiRoute
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.transaction.Transaction
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import java.io.File
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable


class AccountSidechainApp @Inject()
  (@Named("SidechainSettings") sidechainSettings: SidechainSettings,
   @Named("CustomSecretSerializers") customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomAccountTransactionSerializers") val customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]],
   @Named("CustomApiGroups") customApiGroups: JList[ApplicationApiGroup],
   @Named("RejectedApiPaths") rejectedApiPaths : JList[Pair[String, String]],
   @Named("CustomMessageProcessors") customMessageProcessors: JList[MessageProcessor],
   @Named("ApplicationStopper") applicationStopper : SidechainAppStopper,
   @Named("ForkConfiguration") forkConfigurator : ForkConfigurator,
   @Named("ChainInfo") chainInfo : ChainInfo
  )
  extends AbstractSidechainApp(
    sidechainSettings,
    customSecretSerializers,
    customApiGroups,
    rejectedApiPaths,
    applicationStopper,
    forkConfigurator,
    chainInfo
  )
{

  override type TX = SidechainTypes#SCAT
  override type PMOD = AccountBlock
  override type NVHT = AccountSidechainNodeViewHolder

  private val storageList = mutable.ListBuffer[Storage]()

  log.info(s"Starting account application with settings \n$sidechainSettings")

  protected lazy val sidechainAccountTransactionsCompanion: SidechainAccountTransactionsCompanion = SidechainAccountTransactionsCompanion(customAccountTransactionSerializers)

  // Deserialize genesis block bytes
  lazy val genesisBlock: AccountBlock = new AccountBlockSerializer(sidechainAccountTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
    )

  // It is a fast and dirty workaround to set 12 sec block rate for EvmApp
  override lazy val consensusSecondsInSlot: Int = 12

  require (!isCSWEnabled, "Ceased Sidechain Withdrawal (CSW) should not be enabled in AccountSidechainApp!")

  val dataDirAbsolutePath: String = sidechainSettings.sparkzSettings.dataDir.getAbsolutePath
  val secretStore = new File(dataDirAbsolutePath + "/secret")
  val metaStateStore = new File(dataDirAbsolutePath + "/state")
  val historyStore = new File(dataDirAbsolutePath + "/history")
  val consensusStore = new File(dataDirAbsolutePath + "/consensusData")

  // Init all storages
  protected val sidechainSecretStorage = new SidechainSecretStorage(
    registerStorage(new VersionedLevelDbStorageAdapter(secretStore)),
    sidechainSecretsCompanion)

  protected val stateMetadataStorage = new AccountStateMetadataStorage(
    registerStorage(new VersionedLevelDbStorageAdapter(metaStateStore)))

  // TODO for the time being not registered
  protected val stateDbStorage = new LevelDBDatabase(dataDirAbsolutePath + "/evm-state")

  protected val sidechainHistoryStorage = new AccountHistoryStorage(
    registerStorage(new VersionedLevelDbStorageAdapter(historyStore)),
    sidechainAccountTransactionsCompanion,
    params)

  protected val consensusDataStorage = new ConsensusDataStorage(
    registerStorage(new VersionedLevelDbStorageAdapter(consensusStore)))

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
    Map(SidechainBlockBase.ModifierTypeId -> new AccountBlockSerializer(sidechainAccountTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainAccountTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(AccountNodeViewSynchronizer.props(networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider, modifierSerializers))

  // Init Forger with a proper web socket client
  val sidechainBlockForgerActorRef: ActorRef = AccountForgerRef("AccountForger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer,
     sidechainAccountTransactionsCompanion, timeProvider, params)

  // Init Transactions and Block actors for Api routes classes
  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef[PMOD, SidechainSyncInfo, AccountHistory]("AccountBlock", sidechainSettings, sidechainBlockForgerActorRef)

  // Init Certificate Submitter
  val certificateSubmitterRef: ActorRef = AccountCertificateSubmitterRef(sidechainSettings, nodeViewHolderRef, params, mainchainNodeChannel)
  val certificateSignaturesManagerRef: ActorRef = CertificateSignaturesManagerRef(networkControllerRef, certificateSubmitterRef, params, sidechainSettings.sparkzSettings.network)

  // Init API
  rejectedApiPaths.asScala.foreach(path => rejectedApiRoutes = rejectedApiRoutes :+ SidechainRejectionApiRoute(path.getKey, path.getValue, settings.restApi, nodeViewHolderRef))

  // Once received developer's custom api, we need to create, for each of them, a SidechainApiRoute.
  // For do this, we use an instance of ApplicationApiRoute. This is an entry point between SidechainApiRoute and external java api.
  customApiGroups.asScala.foreach(apiRoute => applicationApiRoutes = applicationApiRoutes :+ ApplicationApiRoute(settings.restApi, apiRoute, nodeViewHolderRef))

  coreApiRoutes = Seq[ApiRoute](
    MainchainBlockApiRoute[TX, AccountBlockHeader, PMOD, AccountFeePaymentsInfo, NodeAccountHistory, NodeAccountState,NodeWalletBase,NodeAccountMemoryPool,AccountNodeView](settings.restApi, nodeViewHolderRef),
    AccountBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainAccountTransactionsCompanion, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef, this, params),
    AccountTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainAccountTransactionsCompanion, params),
    AccountWalletApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainSubmitterApiRoute(settings.restApi, certificateSubmitterRef, nodeViewHolderRef),
    AccountEthRpcRoute(settings.restApi, nodeViewHolderRef, sidechainSettings, params, sidechainTransactionActorRef, stateMetadataStorage, stateDbStorage, customMessageProcessors.asScala)
  )

  /*
  override def stopAll(): Unit = {
    super.stopAll()
    storageList.foreach(_.close())
  }
   */

  actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)
}
