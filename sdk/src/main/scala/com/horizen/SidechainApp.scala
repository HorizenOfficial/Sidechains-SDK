package com.horizen

import akka.actor.ActorRef
import com.google.inject.Inject
import com.google.inject.name.Named
import com.horizen.api.http._
import com.horizen.backup.BoxIterator
import com.horizen.block.{SidechainBlock, SidechainBlockBase, SidechainBlockHeader, SidechainBlockSerializer}
import com.horizen.box.BoxSerializer
import com.horizen.certificatesubmitter.CertificateSubmitterRef
import com.horizen.certificatesubmitter.network.CertificateSignaturesManagerRef
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.companion._
import com.horizen.consensus.ConsensusDataStorage
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.csw.CswManagerRef
import com.horizen.forge.ForgerRef
import com.horizen.fork.ForkConfigurator
import com.horizen.helper._
import com.horizen.network.SidechainNodeViewSynchronizer
import com.horizen.node._
import com.horizen.params._
import com.horizen.secret.SecretSerializer
import com.horizen.state.ApplicationState
import com.horizen.storage._
import com.horizen.transaction._
import com.horizen.utils.{BytesUtils, Pair}
import com.horizen.wallet.ApplicationWallet
import com.horizen.websocket.server.WebSocketServerRef
import sparkz.core.api.http.ApiRoute
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.transaction.Transaction
import sparkz.core.{ModifierTypeId, NodeViewModifier}
import java.lang.{Byte => JByte}
import java.nio.file.{Files, Paths}
import java.util.{HashMap => JHashMap, List => JList}

import com.horizen.sc2sc.Sc2ScConfigurator

import scala.collection.JavaConverters._

class SidechainApp @Inject()
  (@Named("SidechainSettings") override val sidechainSettings: SidechainSettings,
   @Named("CustomBoxSerializers") val customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
   @Named("CustomSecretSerializers") override val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]],
   @Named("CustomTransactionSerializers") val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]],
   @Named("ApplicationWallet") val applicationWallet: ApplicationWallet,
   @Named("ApplicationState") val applicationState: ApplicationState,
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
   @Named("BackupStorage") val backUpStorage: Storage,
   @Named("CustomApiGroups") override val customApiGroups: JList[ApplicationApiGroup],
   @Named("RejectedApiPaths") override val rejectedApiPaths : JList[Pair[String, String]],
   @Named("ApplicationStopper") override val applicationStopper : SidechainAppStopper,
   @Named("ForkConfiguration") override val forkConfigurator : ForkConfigurator,
   @Named("Sc2ScConfiguration") override val sc2scConfigurator : Sc2ScConfigurator
  )
  extends AbstractSidechainApp(
    sidechainSettings,
    customSecretSerializers,
    customApiGroups,
    rejectedApiPaths,
    applicationStopper,
    forkConfigurator,
    sc2scConfigurator,
    ChainInfo(
      regtestId = 111,
      testnetId = 222,
      mainnetId = 333)
    )
{

  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  log.info(s"Starting application with settings \n$sidechainSettings")

  protected lazy val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(customTransactionSerializers, circuitType)
  protected lazy val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)

  // Deserialize genesis block bytes
  lazy val genesisBlock: SidechainBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
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
    registerStorage(secretStorage),
    sidechainSecretsCompanion)
  protected val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/wallet")),
    registerStorage(walletBoxStorage),
    sidechainBoxesCompanion)
  protected val sidechainWalletTransactionStorage = new SidechainWalletTransactionStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/walletTransaction")),
    registerStorage(walletTransactionStorage),
    sidechainTransactionsCompanion)
  protected val sidechainStateStorage = new SidechainStateStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/state")),
    registerStorage(stateStorage),
    sidechainBoxesCompanion,
    params)
  protected val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(registerStorage(forgerBoxStorage))
  protected val sidechainStateUtxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider = getSidechainStateUtxoMerkleTreeProvider(registerStorage(utxoMerkleTreeStorage), params)

  protected val sidechainHistoryStorage = new SidechainHistoryStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/history")),
    registerStorage(historyStorage),
    sidechainTransactionsCompanion, params)
  protected val consensusDataStorage = new ConsensusDataStorage(
    //openStorage(new JFile(s"${sidechainSettings.sparkzSettings.dataDir.getAbsolutePath}/consensusData")),
    registerStorage(consensusStorage))
  protected val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(registerStorage(walletForgingBoxesInfoStorage))
  protected val sidechainWalletCswDataProvider: SidechainWalletCswDataProvider = getSidechainWalletCswDataProvider(registerStorage(walletCswDataStorage), params)

  // Append genesis secrets if we start the node first time
  if(sidechainSecretStorage.isEmpty) {
    for(secretHex <- sidechainSettings.wallet.genesisSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretHex)))

    for(secretSchnorr <- sidechainSettings.withdrawalEpochCertificateSettings.signersSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretSchnorr)))
  }

  protected val backupStorage = new BackupStorage(registerStorage(backUpStorage), sidechainBoxesCompanion)

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
  val sidechainBlockForgerActorRef: ActorRef = ForgerRef("Forger", sidechainSettings, nodeViewHolderRef,  mainchainSynchronizer, sidechainTransactionsCompanion, timeProvider, params)

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

  // Init API
  rejectedApiPaths.asScala.foreach(path => rejectedApiRoutes = rejectedApiRoutes :+ SidechainRejectionApiRoute(path.getKey, path.getValue, settings.restApi, nodeViewHolderRef))

  // Once received developer's custom api, we need to create, for each of them, a SidechainApiRoute.
  // For do this, we use an instance of ApplicationApiRoute. This is an entry point between SidechainApiRoute and external java api.
  customApiGroups.asScala.foreach(apiRoute => applicationApiRoutes = applicationApiRoutes :+ ApplicationApiRoute(settings.restApi, apiRoute, nodeViewHolderRef))
  val boxIterator: BoxIterator = backupStorage.getBoxIterator

  coreApiRoutes = Seq[ApiRoute](
    MainchainBlockApiRoute[TX,
      SidechainBlockHeader,PMOD, SidechainFeePaymentsInfo, NodeHistory, NodeState,NodeWallet,NodeMemoryPool,SidechainNodeView](settings.restApi, nodeViewHolderRef),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainTransactionsCompanion, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef, this, params),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactionActorRef, sidechainTransactionsCompanion, params, circuitType),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef, sidechainSecretsCompanion),
    SidechainSubmitterApiRoute(settings.restApi, certificateSubmitterRef, nodeViewHolderRef, circuitType),
    SidechainCswApiRoute(settings.restApi, nodeViewHolderRef, cswManager, params),
    SidechainBackupApiRoute(settings.restApi, nodeViewHolderRef, boxIterator, params)
  )

  // specific to Sidechain app only
  val nodeViewProvider : NodeViewProvider = new NodeViewProviderImpl(nodeViewHolderRef)
  val secretSubmitProvider: SecretSubmitProvider = new SecretSubmitProviderImpl(nodeViewHolderRef)
  val transactionSubmitProvider : TransactionSubmitProvider[TX] = new TransactionSubmitProviderImpl[TX](sidechainTransactionActorRef)

  override def getTransactionSubmitProvider: TransactionSubmitProvider[TX] = transactionSubmitProvider
  def getNodeViewProvider: NodeViewProvider = nodeViewProvider
  def getSecretSubmitProvider: SecretSubmitProvider = secretSubmitProvider

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
