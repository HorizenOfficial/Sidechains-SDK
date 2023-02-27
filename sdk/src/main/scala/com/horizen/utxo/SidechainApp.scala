package com.horizen.utxo

import akka.actor.ActorRef
import com.google.inject.Inject
import com.google.inject.name.Named
import com.horizen.api.http._
import com.horizen.api.http.route.{MainchainBlockApiRoute, SidechainNodeApiRoute, SidechainSubmitterApiRoute}
import com.horizen.block.SidechainBlockBase
import com.horizen.certificatesubmitter.network.CertificateSignaturesManagerRef
import com.horizen.companion._
import com.horizen.consensus.ConsensusDataStorage
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.fork.ForkConfigurator
import com.horizen.helper._
import com.horizen.params._
import com.horizen.secret.SecretSerializer
import com.horizen.storage._
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.{BytesUtils, Pair}
import com.horizen.utxo.api.http._
import com.horizen.utxo.backup.BoxIterator
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader, SidechainBlockSerializer}
import com.horizen.utxo.box.BoxSerializer
import com.horizen.utxo.certificatesubmitter.CertificateSubmitterRef
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.csw.CswManagerRef
import com.horizen.utxo.network.SidechainNodeViewSynchronizer
import com.horizen.utxo.node._
import com.horizen.utxo.state.ApplicationState
import com.horizen.utxo.storage._
import com.horizen.utxo.wallet.ApplicationWallet
import com.horizen.utxo.websocket.server.WebSocketServerRef
import com.horizen.{AbstractSidechainApp, ChainInfo, SidechainAppEvents, SidechainAppStopper, SidechainSettings, SidechainSyncInfo, SidechainSyncInfoMessageSpec, SidechainTypes}
import sparkz.core.api.http.ApiRoute
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.transaction.Transaction
import sparkz.core.{ModifierTypeId, NodeViewModifier}

import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.{HashMap => JHashMap, List => JList}

class SidechainApp @Inject()
  (@Named("SidechainSettings") override val sidechainSettings: SidechainSettings,
   @Named("CustomBoxSerializers") customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
   @Named("CustomSecretSerializers") override val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomTransactionSerializers") customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]],
   @Named("ApplicationWallet") val applicationWallet: ApplicationWallet,
   @Named("ApplicationState") val applicationState: ApplicationState,
   @Named("SecretStorage") secretStorage: Storage,
   @Named("WalletBoxStorage") walletBoxStorage: Storage,
   @Named("WalletTransactionStorage") walletTransactionStorage: Storage,
   @Named("StateStorage") stateStorage: Storage,
   @Named("StateForgerBoxStorage") forgerBoxStorage: Storage,
   @Named("StateUtxoMerkleTreeStorage") utxoMerkleTreeStorage: Storage,
   @Named("HistoryStorage") historyStorage: Storage,
   @Named("WalletForgingBoxesInfoStorage") walletForgingBoxesInfoStorage: Storage,
   @Named("WalletCswDataStorage") walletCswDataStorage: Storage,
   @Named("ConsensusStorage") consensusStorage: Storage,
   @Named("BackupStorage") backUpStorage: Storage,
   @Named("CustomApiGroups") override val customApiGroups: JList[ApplicationApiGroup],
   @Named("RejectedApiPaths") override val rejectedApiPaths: JList[Pair[String, String]],
   @Named("ApplicationStopper") override val applicationStopper: SidechainAppStopper,
   @Named("ForkConfiguration") override val forkConfigurator: ForkConfigurator,
   @Named("ConsensusSecondsInSlot") secondsInSlot: Int
  )
  extends AbstractSidechainApp(
    sidechainSettings,
    customSecretSerializers,
    customApiGroups,
    rejectedApiPaths,
    applicationStopper,
    forkConfigurator,
    //ChainInfo is used in Account model and it has no sense for UTXO but it still requested by AbstractSidechainApp.
    //TODO In the future we may think about how to make Params to have model specific part.
    ChainInfo(
      regtestId = 111,
      testnetId = 222,
      mainnetId = 333),
    secondsInSlot
    )
{

  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  log.info(s"Starting application with settings \n$sidechainSettings")

  override protected lazy val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers, circuitType)
  protected lazy val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)

  // Deserialize genesis block bytes
  override lazy val genesisBlock: SidechainBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
    )

  if (isCSWEnabled) {
    log.info("Ceased Sidechain Withdrawal (CSW) is enabled")
    if (Option(params.cswVerificationKeyFilePath).forall(_.trim.isEmpty)){
      log.error("CSW Verification Key file path is not defined.")
      throw new IllegalArgumentException("CSW Verification Key file path is not defined.")
    }
    if (Option(params.cswProvingKeyFilePath).forall(_.trim.isEmpty)){
      log.error("CSW Proving Key file path is not defined.")
      throw new IllegalArgumentException("CSW Proving Key file path is not defined.")
    }

    if (!Files.exists(Paths.get(params.cswVerificationKeyFilePath)) || !Files.exists(Paths.get(params.cswProvingKeyFilePath))) {
      log.info("Generating CSW snark keys. It may take some time.")
      if (!CryptoLibProvider.cswCircuitFunctions.generateCoboundaryMarlinSnarkKeys(
        params.withdrawalEpochLength, params.cswProvingKeyFilePath, params.cswVerificationKeyFilePath)) {
        throw new IllegalArgumentException("Can't generate CSW Coboundary Marlin ProvingSystem snark keys.")
      }

    }
  }
  else {
    log.warn("******** Ceased Sidechain Withdrawal (CSW) is DISABLED ***********")
  }

  // Init all storages
  protected val sidechainSecretStorage = new SidechainSecretStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/secret")),
    registerClosableResource(secretStorage),
    sidechainSecretsCompanion)
  protected val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/wallet")),
    registerClosableResource(walletBoxStorage),
    sidechainBoxesCompanion)
  protected val sidechainWalletTransactionStorage = new SidechainWalletTransactionStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/walletTransaction")),
    registerClosableResource(walletTransactionStorage),
    sidechainTransactionsCompanion)
  protected val sidechainStateStorage = new SidechainStateStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/state")),
    registerClosableResource(stateStorage),
    sidechainBoxesCompanion,
    params)
  protected val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(registerClosableResource(forgerBoxStorage))
  protected val sidechainStateUtxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider = getSidechainStateUtxoMerkleTreeProvider(registerClosableResource(utxoMerkleTreeStorage), params)

  protected val sidechainHistoryStorage = new SidechainHistoryStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/history")),
    registerClosableResource(historyStorage),
    sidechainTransactionsCompanion, params)
  protected val consensusDataStorage = new ConsensusDataStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/consensusData")),
    registerClosableResource(consensusStorage))
  protected val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(registerClosableResource(walletForgingBoxesInfoStorage))
  protected val sidechainWalletCswDataProvider: SidechainWalletCswDataProvider = getSidechainWalletCswDataProvider(registerClosableResource(walletCswDataStorage), params)

  // Append genesis secrets if we start the node first time
  if(sidechainSecretStorage.isEmpty) {
    for(secretHex <- sidechainSettings.wallet.genesisSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretHex)))

    for(secretSchnorr <- sidechainSettings.withdrawalEpochCertificateSettings.signersSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretSchnorr)))
  }

  protected val backupStorage = new BackupStorage(registerClosableResource(backUpStorage), sidechainBoxesCompanion)

  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(
    sidechainSettings,
    sidechainHistoryStorage,
    consensusDataStorage,
    sidechainStateStorage,
    sidechainStateForgerBoxStorage,
    sidechainStateUtxoMerkleTreeProvider,
    sidechainWalletBoxStorage,
    sidechainSecretStorage,
    sidechainWalletTransactionStorage,
    forgingBoxesMerklePathStorage,
    sidechainWalletCswDataProvider,
    backupStorage,
    params,
    timeProvider,
    applicationWallet,
    applicationState,
    genesisBlock
    ) // TO DO: why not to put genesisBlock as a part of params? REVIEW Params structure

  def modifierSerializers: Map[ModifierTypeId, SparkzSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlockBase.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(SidechainNodeViewSynchronizer.props(networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider, modifierSerializers))

  // Init Forger with a proper web socket client
  val sidechainBlockForgerActorRef: ActorRef = forge.ForgerRef("Forger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer, sidechainTransactionsCompanion, timeProvider, params)

  // Init Transactions and Block actors for Api routes classes
  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockActorRef: ActorRef = SidechainBlockActorRef[PMOD, SidechainSyncInfo, SidechainHistory]("SidechainBlock", sidechainSettings, sidechainBlockForgerActorRef)

  // Init Certificate Submitter
  // Depends on params.isNonCeasing submitter will choose a proper strategy.
  val certificateSubmitterRef: ActorRef = CertificateSubmitterRef(sidechainSettings, nodeViewHolderRef, secureEnclaveApiClient, params, mainchainNodeChannel)
  val certificateSignaturesManagerRef: ActorRef = CertificateSignaturesManagerRef(networkControllerRef, certificateSubmitterRef, params, sidechainSettings.sparkzSettings.network)

  // Init CSW manager
  val cswManager: Option[ActorRef] = if (isCSWEnabled) Some(CswManagerRef(sidechainSettings, params, nodeViewHolderRef)) else None

  //Websocket server for the Explorer
  if(sidechainSettings.websocket.wsServer) {
    val webSocketServerActor: ActorRef = WebSocketServerRef(nodeViewHolderRef,sidechainSettings.websocket.wsServerPort)
  }

  val boxIterator: BoxIterator = backupStorage.getBoxIterator

  override lazy val coreApiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute[TX,
      SidechainBlockHeader,PMOD, SidechainFeePaymentsInfo, NodeHistory, NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainTransactionsCompanion, sidechainBlockForgerActorRef, params),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef, this, params),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params, circuitType),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef, sidechainSecretsCompanion),
    SidechainSubmitterApiRoute(settings.restApi, params, certificateSubmitterRef, nodeViewHolderRef, circuitType),
    SidechainCswApiRoute(settings.restApi, nodeViewHolderRef, cswManager, params),
    SidechainBackupApiRoute(settings.restApi, nodeViewHolderRef, boxIterator, params)
  )

  val nodeViewProvider: NodeViewProvider[
    TX,
    SidechainBlockHeader,
    PMOD,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] = new NodeViewProviderImpl(nodeViewHolderRef)

  def getNodeViewProvider: NodeViewProvider[
    TX,
    SidechainBlockHeader,
    PMOD,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] = nodeViewProvider

  val transactionSubmitProvider : TransactionSubmitProvider[TX] = new TransactionSubmitProviderImpl[TX](sidechainTransactionActorRef)

  override def getTransactionSubmitProvider: TransactionSubmitProvider[TX] = transactionSubmitProvider

  private def getSidechainStateUtxoMerkleTreeProvider(utxoMerkleTreeStorage: Storage, params: NetworkParams) = {
    if (params.isCSWEnabled) {
      SidechainUtxoMerkleTreeProviderCSWEnabled(new SidechainStateUtxoMerkleTreeStorage(utxoMerkleTreeStorage))
    }
    else
      SidechainUtxoMerkleTreeProviderCSWDisabled()
  }


  private def getSidechainWalletCswDataProvider(cswDataStorage: Storage, params: NetworkParams) = {
    if (params.isCSWEnabled) {
      SidechainWalletCswDataProviderCSWEnabled(new SidechainWalletCswDataStorage(cswDataStorage))
    }
    else
      SidechainWalletCswDataProviderCSWDisabled()
  }


  actorSystem.eventStream.publish(SidechainAppEvents.SidechainApplicationStart)

}
