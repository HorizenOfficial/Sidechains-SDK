package io.horizen.utxo.fixtures

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import akka.stream.ActorMaterializer
import io.horizen.api.http.{SidechainApiErrorHandler, SidechainTransactionActorRef}
import io.horizen.block.ProofOfWorkVerifier
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.consensus.ConsensusDataStorage
import io.horizen.cryptolibprovider.CircuitTypes
import io.horizen.customconfig.CustomAkkaConfiguration
import io.horizen.fixtures.{CompanionsFixture, StoreFixture}
import io.horizen.fork.{ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.params.{MainNetParams, NetworkParams, RegTestParams, TestNetParams}
import io.horizen.secret.SecretSerializer
import io.horizen.storage.SidechainSecretStorage
import io.horizen.utils.BytesUtils
import io.horizen.utxo.SidechainNodeViewHolderRef
import io.horizen.utxo.api.http.route.SidechainTransactionApiRoute
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockSerializer}
import io.horizen.utxo.box.BoxSerializer
import io.horizen.utxo.companion.{SidechainBoxesCompanion, SidechainTransactionsCompanion}
import io.horizen.utxo.customtypes.{DefaultApplicationState, DefaultApplicationWallet}
import io.horizen.utxo.state.{ApplicationState, SidechainUtxoMerkleTreeProviderCSWEnabled}
import io.horizen.utxo.storage._
import io.horizen.utxo.wallet.{ApplicationWallet, SidechainWalletCswDataProvider, SidechainWalletCswDataProviderCSWEnabled}
import io.horizen.{SidechainSettings, SidechainSettingsReader, SidechainTypes}
import sparkz.core.api.http.ApiRejectionHandler
import sparkz.core.utils.NetworkTimeProvider

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import scala.concurrent.ExecutionContext

trait SidechainNodeViewHolderFixture
  extends StoreFixture
    with CompanionsFixture {

  val classLoader: ClassLoader = getClass.getClassLoader

  val sidechainSettings: SidechainSettings = SidechainSettingsReader.read(classLoader.getResource("sc_node_holder_fixter_settings.conf").getFile, None)

  val simpleForkConfigurator = new SimpleForkConfigurator
  ForkManagerUtil.initializeForkManager(simpleForkConfigurator, "regtest")

  implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler

  implicit def rejectionHandler: RejectionHandler = ApiRejectionHandler.rejectionHandler

  implicit val actorSystem: ActorSystem = ActorSystem(sidechainSettings.sparkzSettings.network.agentName, CustomAkkaConfiguration.getCustomConfig())
  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup("sparkz.executionContext")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val timeProvider = new NetworkTimeProvider(sidechainSettings.sparkzSettings.ntp)

  val sidechainBoxesCompanion: SidechainBoxesCompanion = SidechainBoxesCompanion(new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]](), false)
  val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]())
  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val defaultApplicationWallet: ApplicationWallet = new DefaultApplicationWallet()
  val defaultApplicationState: ApplicationState = new DefaultApplicationState()

  val genesisBlock: SidechainBlock = new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
    BytesUtils.fromHexString(sidechainSettings.genesisData.scGenesisBlockHex)
  )
  val genesisPowData: Seq[(Int, Int)] = ProofOfWorkVerifier.parsePowData(sidechainSettings.genesisData.powData)

  val params: NetworkParams = sidechainSettings.genesisData.mcNetwork match {
    case "regtest" => RegTestParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash)
    )
    case "testnet" => TestNetParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash)
    )
    case "mainnet" => MainNetParams(
      sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainSettings.genesisData.scId)),
      sidechainGenesisBlockId = genesisBlock.id,
      genesisMainchainBlockHash = genesisBlock.mainchainHeaders.head.hash,
      parentHashOfGenesisMainchainBlock = genesisBlock.mainchainHeaders.head.hashPrevBlock,
      genesisPoWData = genesisPowData,
      mainchainCreationBlockHeight = sidechainSettings.genesisData.mcBlockHeight,
      sidechainGenesisBlockTimestamp = genesisBlock.timestamp,
      withdrawalEpochLength = sidechainSettings.genesisData.withdrawalEpochLength,
      initialCumulativeCommTreeHash = BytesUtils.fromHexString(sidechainSettings.genesisData.initialCumulativeCommTreeHash)
    )
    case _ => throw new IllegalArgumentException("Configuration file sparkz.genesis.mcNetwork parameter contains inconsistent value.")
  }

  val sidechainSecretStorage = new SidechainSecretStorage(getStorage(), sidechainSecretsCompanion)
  val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(getStorage(), sidechainBoxesCompanion)
  val sidechainStateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion, params)
  val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(getStorage())
  val sidechainStateUtxoMerkleTreeProvider: SidechainUtxoMerkleTreeProviderCSWEnabled = SidechainUtxoMerkleTreeProviderCSWEnabled(new SidechainStateUtxoMerkleTreeStorage(getStorage()))

  val sidechainHistoryStorage = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
  val consensusDataStorage = new ConsensusDataStorage(getStorage())
  val sidechainWalletTransactionStorage = new SidechainWalletTransactionStorage(getStorage(), sidechainTransactionsCompanion)
  val forgingBoxesMerklePathStorage = new ForgingBoxesInfoStorage(getStorage())
  val cswDataProvider: SidechainWalletCswDataProvider = SidechainWalletCswDataProviderCSWEnabled(new SidechainWalletCswDataStorage(getStorage()))
  val backupStorage = new BackupStorage(getStorage(), sidechainBoxesCompanion)

  // Append genesis secrets if we start the node first time
  if (sidechainSecretStorage.isEmpty) {
    for (secretHex <- sidechainSettings.wallet.genesisSecrets)
      sidechainSecretStorage.add(sidechainSecretsCompanion.parseBytes(BytesUtils.fromHexString(secretHex)))
  }

  val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(
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
    cswDataProvider,
    backupStorage,
    params,
    timeProvider,
    defaultApplicationWallet,
    defaultApplicationState,
    genesisBlock)

  val sidechainTransactionActorRef: ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)

  def getSidechainNodeViewHolderRef: ActorRef = {
    nodeViewHolderRef
  }

  def getSidechainTransactionApiRoute: SidechainTransactionApiRoute = {
    SidechainTransactionApiRoute(sidechainSettings.sparkzSettings.restApi, nodeViewHolderRef,
      sidechainTransactionActorRef, sidechainTransactionsCompanion, params, CircuitTypes.NaiveThresholdSignatureCircuit)
  }

}
