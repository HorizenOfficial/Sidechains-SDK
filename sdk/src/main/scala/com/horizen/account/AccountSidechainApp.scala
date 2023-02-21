package com.horizen.account

import akka.actor.ActorRef
import com.google.inject.Inject
import com.google.inject.name.Named
import com.horizen.account.api.http.{AccountBlockApiRoute, AccountEthRpcRoute, AccountTransactionApiRoute, AccountWalletApiRoute}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader, AccountBlockSerializer}
import com.horizen.account.certificatesubmitter.AccountCertificateSubmitterRef
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.forger.AccountForgerRef
import com.horizen.account.history.AccountHistory
import com.horizen.account.network.AccountNodeViewSynchronizer
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state.MessageProcessor
import com.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import com.horizen.api.http._
import com.horizen.block.SidechainBlockBase
import com.horizen.certificatesubmitter.network.CertificateSignaturesManagerRef
import com.horizen.consensus.ConsensusDataStorage
import com.horizen.evm.LevelDBDatabase
import com.horizen.fork.ForkConfigurator
import com.horizen.helper.{NodeViewProvider, NodeViewProviderImpl}
import com.horizen.node.NodeWalletBase
import com.horizen.secret.SecretSerializer
import com.horizen.storage._
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import com.horizen.transaction._
import com.horizen.utils.{BytesUtils, Pair}
import com.horizen._
import com.horizen.helper.{TransactionSubmitProvider, TransactionSubmitProviderImpl}
import sparkz.core.api.http.ApiRoute
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.transaction.Transaction
import sparkz.core.{ModifierTypeId, NodeViewModifier}
import java.io.File
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap, List => JList}

import com.horizen.sc2sc.Sc2ScConfigurator

import scala.collection.JavaConverters.asScalaBufferConverter


class AccountSidechainApp @Inject()
  (@Named("SidechainSettings") sidechainSettings: SidechainSettings,
   @Named("CustomSecretSerializers") customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomAccountTransactionSerializers") customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]],
   @Named("CustomApiGroups") customApiGroups: JList[ApplicationApiGroup],
   @Named("RejectedApiPaths") rejectedApiPaths: JList[Pair[String, String]],
   @Named("CustomMessageProcessors") customMessageProcessors: JList[MessageProcessor],
   @Named("ApplicationStopper") applicationStopper: SidechainAppStopper,
   @Named("ForkConfiguration") forkConfigurator: ForkConfigurator,
   @Named("Sc2ScConfiguration") override val sc2scConfigurator : Sc2ScConfigurator,
   @Named("ChainInfo") chainInfo: ChainInfo,
   @Named("ConsensusSecondsInSlot") secondsInSlot: Int
  )
  extends AbstractSidechainApp(
    sidechainSettings,
    customSecretSerializers,
    customApiGroups,
    rejectedApiPaths,
    applicationStopper,
    forkConfigurator,
    sc2scConfigurator,
    chainInfo,
    secondsInSlot
  )
{

  override type TX = SidechainTypes#SCAT
  override type PMOD = AccountBlock
  override type NVHT = AccountSidechainNodeViewHolder

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
  protected val sidechainSecretStorage = new SidechainSecretStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(secretStore)),
    sidechainSecretsCompanion)

  protected val stateMetadataStorage = new AccountStateMetadataStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(metaStateStore)))

  protected val stateDbStorage: LevelDBDatabase = registerClosableResource(new LevelDBDatabase(dataDirAbsolutePath + "/evm-state"))

  protected val sidechainHistoryStorage = new AccountHistoryStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(historyStore)),
    sidechainTransactionsCompanion,
    params)

  protected val consensusDataStorage = new ConsensusDataStorage(
    registerClosableResource(new VersionedLevelDbStorageAdapter(consensusStore)))

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
    sc2scConfigurator,
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
  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef[PMOD, SidechainSyncInfo, AccountHistory]("AccountBlock", sidechainSettings, sidechainBlockForgerActorRef)

  // Init Certificate Submitter
  val certificateSubmitterRef: ActorRef = AccountCertificateSubmitterRef(sidechainSettings, sc2scConfigurator, nodeViewHolderRef, secureEnclaveApiClient, params, mainchainNodeChannel)
  val certificateSignaturesManagerRef: ActorRef = CertificateSignaturesManagerRef(networkControllerRef, certificateSubmitterRef, params, sidechainSettings.sparkzSettings.network)


  override lazy val coreApiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute[TX, AccountBlockHeader, PMOD, AccountFeePaymentsInfo, NodeAccountHistory, NodeAccountState,NodeWalletBase,NodeAccountMemoryPool,AccountNodeView](settings.restApi, nodeViewHolderRef),
    AccountBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainTransactionsCompanion, sidechainBlockForgerActorRef, params),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef, this, params),
    AccountTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params, circuitType),
    AccountWalletApiRoute(settings.restApi, nodeViewHolderRef, sidechainSecretsCompanion),
    SidechainSubmitterApiRoute(settings.restApi, params, certificateSubmitterRef, nodeViewHolderRef, circuitType),
    AccountEthRpcRoute(settings.restApi, nodeViewHolderRef, sidechainSettings, params, sidechainTransactionActorRef, stateMetadataStorage, stateDbStorage, customMessageProcessors.asScala)
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
