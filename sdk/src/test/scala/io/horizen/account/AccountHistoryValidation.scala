package io.horizen.account

import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.account.block.{AccountBlock, AccountBlockSerializer}
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.history.AccountHistory
import io.horizen.account.storage.AccountHistoryStorage
import io.horizen.account.utils.{AccountMockDataHelper, FeeUtils}
import io.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import io.horizen.consensus.{ConsensusDataProvider, ConsensusDataStorage, ForgingStakeInfo, FullConsensusEpochInfo, HistoryConsensusChecker, StakeConsensusEpochInfo}
import io.horizen.cryptolibprovider.CircuitTypes.CircuitTypes
import io.horizen.fixtures.VrfGenerator
import io.horizen.fixtures.sidechainblock.generation.{FinishedEpochInfo, ForgingStakeCorruptionRules, GenerationRules, PossibleForger, SidechainBlocksGenerator, SidechainForgingData}
import io.horizen.fork.{ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.history.validation.ConsensusValidator
import io.horizen.params.{NetworkParams, TestNetParams}
import io.horizen.proposition.SchnorrProposition
import io.horizen.secret.{PrivateKey25519Creator, VrfKeyGenerator}
import io.horizen.storage.InMemoryStorageAdapter
import io.horizen.utils.{BytesUtils, MerklePath, MerkleTree}
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.box.data.ForgerBoxData
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.storage.SidechainHistoryStorage
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.web3j.utils.Numeric
import sparkz.core.block.Block
import sparkz.util.{ModifierId, bytesToId}
import scala.collection.JavaConverters._

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Random
import scala.collection.mutable
import scala.util.{Failure, Success, Try}


class AccountHistoryValidation extends JUnitSuite with HistoryConsensusChecker {
  val rnd = new Random(20)
  val maximumAvailableShift = 2

  ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")

  private def createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots: Int, slotLengthInSeconds: Int, totalBlocksCount: Int, blocksInHistoryCount: Int):
  (AccountHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock]) = {
    var res: Option[(AccountHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock])] =  None
    var iteration = 0
    while (res.isEmpty) {
      val resTry = Try(createHistoryWithBlocksNoForks(new Random(rnd.nextLong()), epochSizeInSlots, slotLengthInSeconds, totalBlocksCount, blocksInHistoryCount))
      resTry match {
        case (Success(_)) => res = resTry.toOption
        case Failure(exception) => {
          println(exception.printStackTrace())
          iteration = iteration + 1
        }
      }

      if (resTry.isFailure) println(resTry.failed.get.printStackTrace())
      res = resTry.toOption

      require(iteration < 500, "Cannot generate blocks chain for test, block generation is broken")
    }
    (res.get._1, res.get._2, res.get._3)
  }

  def createAccountHistory(params: NetworkParams, genesisBlock: AccountBlock, finishedEpochInfo: FinishedEpochInfo): AccountHistory = {
    val boxCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion

    val sidechainHistoryStorage: AccountHistoryStorage = new AccountHistoryStorage(new InMemoryStorageAdapter(), boxCompanion, params)
    AccountHistory
      .createGenesisHistory(
        sidechainHistoryStorage,
        new ConsensusDataStorage(new InMemoryStorageAdapter()),
        params,
        genesisBlock,
        Seq(),
        Seq(new ConsensusValidator(timeProvider)),
        finishedEpochInfo.stakeConsensusEpochInfo)
      .get
  }

  private def generateNetworkParams(genesisSidechainBlock: AccountBlock, params: NetworkParams, random: Random): NetworkParams = {
    val mainchainHeight = Math.abs(random.nextInt() % 1024)

    new NetworkParams {
      override val EquihashN: Int = params.EquihashN
      override val EquihashK: Int = params.EquihashK
      override val EquihashCompactSizeLength: Int = params.EquihashCompactSizeLength
      override val EquihashSolutionLength: Int = params.EquihashSolutionLength

      override val powLimit: BigInteger = params.powLimit
      override val nPowAveragingWindow: Int = params.nPowAveragingWindow
      override val nPowMaxAdjustDown: Int = params.nPowMaxAdjustDown
      override val nPowMaxAdjustUp: Int = params.nPowMaxAdjustUp
      override val nPowTargetSpacing: Int = params.nPowTargetSpacing

      override val sidechainId: Array[Byte] = params.sidechainId
      override val sidechainGenesisBlockId: ModifierId = genesisSidechainBlock.id
      override val genesisMainchainBlockHash: Array[Byte] = params.genesisMainchainBlockHash
      override val parentHashOfGenesisMainchainBlock: Array[Byte] = params.parentHashOfGenesisMainchainBlock
      override val genesisPoWData: Seq[(Int, Int)] = params.genesisPoWData
      override val mainchainCreationBlockHeight: Int = genesisSidechainBlock.mainchainBlockReferencesData.size + mainchainHeight
      override val sidechainGenesisBlockTimestamp: Block.Timestamp = genesisSidechainBlock.timestamp
      override val withdrawalEpochLength: Int = params.withdrawalEpochLength
      override val consensusSecondsInSlot: Int = params.consensusSecondsInSlot
      override val consensusSlotsInEpoch: Int = params.consensusSlotsInEpoch
      override val signersPublicKeys: Seq[SchnorrProposition] = params.signersPublicKeys
      override val mastersPublicKeys: Seq[SchnorrProposition] = params.mastersPublicKeys
      override val circuitType: CircuitTypes = params.circuitType
      override val signersThreshold: Int = params.signersThreshold
      override val certProvingKeyFilePath: String = params.certProvingKeyFilePath
      override val certVerificationKeyFilePath: String = params.certVerificationKeyFilePath
      override val calculatedSysDataConstant: Array[Byte] = new Array[Byte](32) //calculate if we need for some reason that data
      override val initialCumulativeCommTreeHash: Array[Byte] = params.initialCumulativeCommTreeHash
      override val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig] = Seq()
      override val cswProvingKeyFilePath: String = params.cswProvingKeyFilePath
      override val cswVerificationKeyFilePath: String = params.cswVerificationKeyFilePath
      override val sidechainCreationVersion: SidechainCreationVersion = params.sidechainCreationVersion
      override val chainId: Long = 11111111
      override val isCSWEnabled: Boolean = params.isCSWEnabled
      override val isNonCeasing: Boolean = params.isNonCeasing
      override val minVirtualWithdrawalEpochLength: Int = 10
    }
  }

  private def buildGenesisSidechainForgingData(initialValue: Long, seed: Long): SidechainForgingData = {
    val key = PrivateKey25519Creator.getInstance().generateSecret(seed.toString.getBytes(StandardCharsets.UTF_8))
    val value = initialValue
    val vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(seed.toString.getBytes(StandardCharsets.UTF_8))
    val vrfPublicKey = vrfSecretKey.publicImage()

    val forgerBoxData = new ForgerBoxData(key.publicImage(), value, key.publicImage(), vrfPublicKey)

    val nonce = 42L

    val forgerBox = forgerBoxData.getBox(nonce)
    val forgingStake = ForgingStakeInfo(forgerBox.blockSignProposition(), forgerBox.vrfPubKey(), forgerBox.value())

    SidechainForgingData(key, forgingStake, vrfSecretKey)
  }

  private def buildGenesisMerkleTree(genesisForgingStakeInfo: ForgingStakeInfo): MerkleTree = {
    val leave: Array[Byte] = genesisForgingStakeInfo.hash
    val merkleTreeSize: Int = 128

    val initialLeaves = Seq.fill(merkleTreeSize)(leave)
    MerkleTree.createMerkleTree(initialLeaves.asJava)
  }

  private def createHistoryWithBlocksNoForks(rnd: Random, epochSizeInSlots: Int, slotLengthInSeconds: Int, totalBlockCount: Int, blocksInHistoryCount: Int):
  (AccountHistory, mutable.Buffer[SidechainBlocksGenerator], mutable.Buffer[SidechainBlock]) = {

    val genesisTimestamp: Long = Instant.now.getEpochSecond - (slotLengthInSeconds * totalBlockCount)

    val initialParams = TestNetParams(
      consensusSlotsInEpoch = epochSizeInSlots,
      consensusSecondsInSlot = slotLengthInSeconds,
      sidechainGenesisBlockTimestamp = genesisTimestamp)

    val (params, genesisBlock, genesisGenerator, genesisForgingData, genesisEndEpochInfo) = SidechainBlocksGenerator.startSidechain(10000000000L, rnd.nextInt(), initialParams)

    val mockedGenesisBlock: AccountBlock = AccountMockDataHelper(true).getMockedBlock(FeeUtils.INITIAL_BASE_FEE, 0, DefaultGasFeeFork.blockGasLimit, bytesToId(Numeric.hexStringToByteArray("123")), bytesToId(new Array[Byte](32)))
    val random: Random = new Random(rnd.nextInt())


    val genesisSidechainForgingData: SidechainForgingData = buildGenesisSidechainForgingData(10000000000L, rnd.nextInt())

    val vrfProof = VrfGenerator.generateProof(rnd.nextInt()) //no VRF proof checking for genesis block!

    val genesisMerkleTree: MerkleTree = buildGenesisMerkleTree(genesisSidechainForgingData.forgingStakeInfo)
    val merklePathForGenesisSidechainForgingData: MerklePath = genesisMerkleTree.getMerklePathForLeaf(0)
    val possibleForger: PossibleForger = PossibleForger(
      forgingData = genesisSidechainForgingData,
      merklePathInPreviousEpochOpt = Some(merklePathForGenesisSidechainForgingData),
      merklePathInPrePreviousEpochOpt = Some(merklePathForGenesisSidechainForgingData),
      spentInEpochsAgoOpt = None)
//
//    val genesisSidechainBlock: SidechainBlock = generateGenesisSidechainBlock(params, possibleForger.forgingData, vrfProof, merklePathForGenesisSidechainForgingData)
    val networkParams = generateNetworkParams(mockedGenesisBlock, initialParams, random)
//
    val nonceInfo = ConsensusDataProvider.calculateNonceForGenesisBlock(networkParams)
    val stakeInfo =
      StakeConsensusEpochInfo(genesisMerkleTree.rootHash(), possibleForger.forgingData.forgingStakeInfo.stakeAmount)
//    val consensusDataStorage = createConsensusDataStorage(genesisSidechainBlock.id, nonceInfo, stakeInfo)
//
//
//    val genesisGenerator = new SidechainBlocksGenerator(
//      params = networkParams,
//      forgersSet = new PossibleForgersSet(Set(possibleForger)),
//      consensusDataStorage = consensusDataStorage,
//      lastBlockId = genesisSidechainBlock.id,
//      lastMainchainBlockId = genesisSidechainBlock.mainchainBlockReferencesData.last.headerHash,
//      nextFreeSlotNumber = intToConsensusSlotNumber(consensusSlotNumber = 1),
//      nextEpochNumber = intToConsensusEpochNumber(2),
//      nextBlockNonceEpochId = blockIdToEpochId(genesisSidechainBlock.id),
//      nextBlockStakeEpochId = blockIdToEpochId(genesisSidechainBlock.id),
//      allEligibleVrfOutputs = List(),
//      previousEpochId = blockIdToEpochId(genesisSidechainBlock.id),
//      rnd = random)

//     (networkParams, genesisSidechainBlock, genesisGenerator, possibleForger.forgingData, FinishedEpochInfo(blockIdToEpochId(genesisSidechainBlock.id), stakeInfo, nonceInfo))



    val history: AccountHistory = createAccountHistory(params, mockedGenesisBlock, genesisEndEpochInfo)
    history.applyFullConsensusInfo(genesisBlock.id, FullConsensusEpochInfo(genesisEndEpochInfo.stakeConsensusEpochInfo, genesisEndEpochInfo.nonceConsensusEpochInfo))
    println(s"//////////////// Genesis epoch ${genesisBlock.id} had been ended ////////////////")

    val boxCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion

    val sidechainHistoryStorage: AccountHistoryStorage = new AccountHistoryStorage(new InMemoryStorageAdapter(), boxCompanion, params)
    AccountHistory
      .createGenesisHistory(
        sidechainHistoryStorage,
        new ConsensusDataStorage(new InMemoryStorageAdapter()),
        params,
        mockedGenesisBlock,
        Seq(),
        Seq(new ConsensusValidator(timeProvider)),
        stakeInfo)
      .get

    var lastGenerator: SidechainBlocksGenerator = genesisGenerator
    val generators = mutable.Buffer(genesisGenerator)
    val generatedBlocks = mutable.Buffer(genesisBlock)

//    for (i <- 1 to totalBlockCount) {
//      val generationRules = GenerationRules.generateCorrectGenerationRules(rnd, lastGenerator.getNotSpentBoxes)
//
//      val (gens, generatedBlock) = generateBlock(generationRules, lastGenerator, history)
//      if (i < blocksInHistoryCount) {
//        history = historyUpdateShallBeSuccessful(history, generatedBlock)
//        generatedBlocks.append(generatedBlock)
//        generators.appendAll(gens)
//        println(s"Generate normal block ${generatedBlock.id}")
//      }
//      else {
//        println(s"Generate extra block ${generatedBlock.id}")
//      }
//
//      lastGenerator = gens.last
//    }

    (history, generators, generatedBlocks)
  }

  @Test
  def anotherFeeBlockCheck(): Unit = {
    val epochSizeInSlots = 15
    val slotLengthInSeconds = 20
    val totalBlocks = epochSizeInSlots * 4
    val (history: AccountHistory, generators: Seq[SidechainBlocksGenerator], blocks) = createHistoryWithBlocksNoForksAndPossibleNextForger(epochSizeInSlots, slotLengthInSeconds, totalBlocks, totalBlocks - maximumAvailableShift)

    val lastGenerator = generators.last

    val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion
    val blockBytes = BytesUtils.fromHexString("02e1eceb9f9e4d390ee34063b573b90123f0b36d7ff1b3120f6d5b2fb10f289627f6dddccc0c6e3bda4dfddf67e293362514c36142f70862dab22cd3609face526aec9b1c809dbfb30791dbc1b1d0140fea9c49cd2ca0d6aade8139ee919cc4795e11ae9c10400808cb0e1490201104469c8cd0addeff670801fa8dd9bc69536df036d50ed772bb2cae4e7b37b07432f5977e5e6cb239fb20084b1bd614b90e0adcc55ede058d20986e66de8e03800cfc4787f5f0ac8558d44311ea846412ce44c1c8dd42b135bad31e016b4f41a3be703817d8afc936da39b56be29d31dd37c9e509c45a710401d15de373503b51471882991cb1728b4668aeb2ed7170857cf72474413ed5be9bdb81958869c331556e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b42100000000000000000000000000000000000000000000000000000000000000002e22ffcfdaa460d18b598bb7cf5b3fc31052d0ab746a2857dc93e91f4cdca2a156e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b42100c8f107a09cd4f463afc2f1e6e5bf6022ad46000a04a817c80002000801c9c38000000000000000000000000000000000000000000000000000000000000000000056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b4210000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000027cc0eba54469b2fe62a2724dc4b961a0253c5aa001f5b070a345a29067f90bb4400848e4ecc804f64be56a52a2c94098c4aa9ef840d700083535510cb0f950700000000")
    val serializer = new AccountBlockSerializer(sidechainTransactionsCompanion)
    val block = serializer.parseBytes(blockBytes)

    history.append(block).failed.get match {
      case expected: IllegalArgumentException => assert(expected.getMessage == "Block had been generated before parent block had been generated")
      case nonExpected => assert(false, s"Got incorrect exception: ${nonExpected}")
    }
  }
}
