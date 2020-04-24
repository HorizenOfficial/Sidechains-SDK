package com.horizen.fixtures.sidechainblock.generation

import java.math.BigInteger
import java.time.Instant
import java.util.Random

import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer, SidechainBlock, SidechainBlockHeader, Ommer}
import com.horizen.box.data.ForgerBoxData
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.fixtures._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition, SchnorrPublicKey, VrfPublicKey}
import com.horizen.secret.PrivateKey25519
import com.horizen.storage.InMemoryStorageAdapter
import com.horizen.transaction.SidechainTransaction
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils
import com.horizen.utils._
import com.horizen.vrf._
import scorex.core.block.Block
import scorex.util.{ModifierId, bytesToId}

import scala.collection.JavaConverters._


case class GeneratedBlockInfo(block: SidechainBlock, forger: SidechainForgingData)
case class FinishedEpochInfo(epochId: ConsensusEpochId, stakeConsensusEpochInfo: StakeConsensusEpochInfo, nonceConsensusEpochInfo: NonceConsensusEpochInfo)

//will be thrown if block generation no longer is possible, for example: nonce no longer can be calculated due no mainchain references in whole epoch
class GenerationIsNoLongerPossible extends IllegalStateException
class GenerationOfIncorrectBlockIsNotPossible extends IllegalStateException

// @TODO consensusDataStorage is shared between generator instances, so data could be already added. Shall be fixed.
class SidechainBlocksGenerator private (val params: NetworkParams,
                                        forgersSet: PossibleForgersSet,
                                        consensusDataStorage: ConsensusDataStorage,
                                        val lastBlockId: Block.BlockId,
                                        lastMainchainBlockId: ByteArrayWrapper,
                                        val nextFreeSlotNumber: ConsensusSlotNumber,
                                        nextEpochNumber: ConsensusEpochNumber,
                                        nextBlockNonceEpochId: ConsensusEpochId,
                                        nextBlockStakeEpochId: ConsensusEpochId,
                                        currentBestMainchainPoW: Option[BigInteger],
                                        rnd: Random) extends TimeToEpochSlotConverter {
  import SidechainBlocksGenerator._

  def getNotSpentBoxes: Set[SidechainForgingData] = forgersSet.getNotSpentSidechainForgingData.map(_.copy())

  def tryToGenerateBlockForCurrentSlot(generationRules: GenerationRules): Option[GeneratedBlockInfo] = {
    checkGenerationRules(generationRules)
    val nextSlot = intToConsensusSlotNumber(Math.min(nextFreeSlotNumber, params.consensusSlotsInEpoch))
    getNextEligibleForgerForCurrentEpoch(generationRules, nextSlot).map{
      case (blockForger, vrfProof, usedSlotNumber) => {
        println(s"Got forger for block: ${blockForger}")
        val newBlock = generateBlock(blockForger, vrfProof, usedSlotNumber, generationRules)
        GeneratedBlockInfo(newBlock, blockForger.forgingData)
      }
    }
  }

  def tryToGenerateCorrectBlock(generationRules: GenerationRules): (SidechainBlocksGenerator, Either[FinishedEpochInfo, GeneratedBlockInfo]) = {
    checkGenerationRules(generationRules)
    getNextEligibleForgerForCurrentEpoch(generationRules, intToConsensusSlotNumber(params.consensusSlotsInEpoch)) match {
      case Some((blockForger, vrfProof, usedSlotNumber)) => {
        println(s"Got forger: ${blockForger}")
        val newBlock: SidechainBlock = generateBlock(blockForger, vrfProof, usedSlotNumber, generationRules)
        val newForgers: PossibleForgersSet = forgersSet.createModified(generationRules)
        val generator = createGeneratorAfterBlock(newBlock, usedSlotNumber, newForgers)
        (generator, Right(GeneratedBlockInfo(newBlock, blockForger.forgingData)))
      }
      case None => {
        val (generator, finishedEpochInfo) = createGeneratorAndFinishedEpochInfo
        (generator, Left(finishedEpochInfo))
      }
    }
  }

  private def checkGenerationRules(generationRules: GenerationRules): Unit = {
    checkInputSidechainForgingData(generationRules.forgingBoxesToAdd, generationRules.forgingBoxesToSpent)
  }

  private def checkInputSidechainForgingData(forgingBoxesToAdd: Set[SidechainForgingData], forgingBoxesToSpent: Set[SidechainForgingData]): Unit = {
    val incorrectBoxesToAdd: Set[SidechainForgingData] = forgingBoxesToAdd & forgersSet.getAvailableSidechainForgingData
    if (incorrectBoxesToAdd.nonEmpty) {
      throw new IllegalArgumentException(s"Try to add already existed SidechainForgingData(s): ${incorrectBoxesToAdd.mkString(",")}")
    }

    val incorrectBoxesToSpent: Set[SidechainForgingData] = forgingBoxesToSpent -- forgersSet.getAvailableSidechainForgingData
    if (incorrectBoxesToSpent.nonEmpty) {
      throw new IllegalArgumentException(s"Try to spent non existed SidechainForgingData(s): ${incorrectBoxesToSpent.mkString(",")}")
    }
  }

  private def getNextEligibleForgerForCurrentEpoch(generationRules: GenerationRules, endSlot: ConsensusSlotNumber): Option[(PossibleForger, VrfProof, ConsensusSlotNumber)] = {
    require(nextFreeSlotNumber <= endSlot + 1)

    val nonceAsBigInteger = new BigInteger(consensusDataStorage.getNonceConsensusEpochInfo(nextBlockNonceEpochId).get.consensusNonce)
    val nonce = nonceAsBigInteger.add(generationRules.corruption.consensusNonceShift)
    val consensusNonce: NonceConsensusEpochInfo = NonceConsensusEpochInfo(bigIntToConsensusNonce(nonce))
    val totalStake = consensusDataStorage.getStakeConsensusEpochInfo(nextBlockStakeEpochId).get.totalStake

    val possibleForger = (nextFreeSlotNumber to endSlot)
      .toStream
      .flatMap{currentSlot =>
        val slotWithShift = intToConsensusSlotNumber(Math.min(currentSlot + generationRules.corruption.consensusSlotShift, params.consensusSlotsInEpoch))
        println(s"Process slot: ${slotWithShift}")
        val res = forgersSet.getEligibleForger(slotWithShift, consensusNonce, totalStake, generationRules.corruption.getStakeCheckCorruptionFunction)
        if (res.isEmpty) {println(s"No forger had been found for slot ${currentSlot}")}
        res.map{case(forger, proof) => (forger, proof, intToConsensusSlotNumber(currentSlot))}
      }
      .headOption

    possibleForger
  }


  private def createGeneratorAfterBlock(newBlock: SidechainBlock,
                                        usedSlot: ConsensusSlotNumber,
                                        newForgers: PossibleForgersSet): SidechainBlocksGenerator = {

    val bestPowInNewBlock: Option[BigInteger] = getMinimalHashOptFromBlock(newBlock)
    val newBestPow: Option[BigInteger] = (bestPowInNewBlock, currentBestMainchainPoW) match {
      case (None, _) => currentBestMainchainPoW
      case (_, None) => bestPowInNewBlock
      case (Some(a), Some(b)) => Option(a.min(b))
    }

    new SidechainBlocksGenerator(
      params = params,
      forgersSet = newForgers,
      consensusDataStorage = consensusDataStorage,
      lastBlockId = newBlock.id,
      lastMainchainBlockId = newBlock.mainchainBlockReferencesData.lastOption.map(data => byteArrayToWrapper(data.headerHash)).getOrElse(lastMainchainBlockId),
      nextFreeSlotNumber = intToConsensusSlotNumber(usedSlot + 1), //in case if current slot is the last in epoch then next try to apply new block will finis epoch
      nextEpochNumber = nextEpochNumber,
      nextBlockNonceEpochId = nextBlockNonceEpochId,
      nextBlockStakeEpochId = nextBlockStakeEpochId,
      currentBestMainchainPoW = newBestPow,
      rnd = rnd
    )
  }

  private def generateBlock(possibleForger: PossibleForger, vrfProof: VrfProof, usedSlotNumber: ConsensusSlotNumber, generationRules: GenerationRules): SidechainBlock = {
    val parentId = generationRules.forcedParentId.getOrElse(lastBlockId)
    val timestamp = generationRules.forcedTimestamp.getOrElse{
      getTimeStampForEpochAndSlot(nextEpochNumber, usedSlotNumber) + generationRules.corruption.timestampShiftInSlots * params.consensusSecondsInSlot}

    val mainchainBlockReferences: Seq[MainchainBlockReference] = generationRules.mcReferenceIsPresent match {
      //generate at least one MC block reference
      case Some(true)  => {
        val necessaryMcBlockReference = mcRefGen.generateMainchainBlockReference(parentOpt = Some(lastMainchainBlockId), rnd = rnd, timestamp = timestamp.toInt)
        mcRefGen.generateMainchainReferences(generated = Seq(necessaryMcBlockReference), rnd = rnd, timestamp = timestamp.toInt)
      }
      //no MC shall not be generated at all
      case Some(false) => Seq()

      //truly random generated MC
      case None        => SidechainBlocksGenerator.mcRefGen.generateMainchainReferences(parentOpt = Some(lastMainchainBlockId), rnd = rnd, timestamp = timestamp.toInt)
    }

    // TODO: we need more complex cases
    val mainchainBlockReferencesData = mainchainBlockReferences.map(_.data)
    val mainchainHeaders = mainchainBlockReferences.map(_.header)

    val forgingData: SidechainForgingData =
      if (generationRules.corruption.getOtherSidechainForgingData) {
        getIncorrectPossibleForger(possibleForger).forgingData
      }
      else {
        possibleForger.forgingData
      }

    val owner: PrivateKey25519 = forgingData.key

    val forgerBox: ForgerBox = generationRules.corruption.forgerBoxCorruptionRules.map(getIncorrectForgerBox(forgingData.forgerBox, _)).getOrElse(forgingData.forgerBox)

    val forgerBoxMerklePath: MerklePath =
      if (generationRules.corruption.merklePathFromPreviousEpoch) {
        getCorruptedMerklePath(possibleForger)
      }
      else {
        possibleForger.merklePathInPrePreviousEpochOpt.get
      }

    val vrfProofInBlock: VrfProof = generationRules.corruption.forcedVrfProof.getOrElse(vrfProof)

    val sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = Seq(
      SidechainBlocksGenerator.txGen.generateRegularTransaction(rnd = rnd, transactionBaseTimeStamp = timestamp, inputTransactionsSize = 1, outputTransactionsSize = 1)
    )

    val ommers: Seq[Ommer] = Seq()

    val sidechainTransactionsMerkleRootHash: Array[Byte] = SidechainBlock.calculateTransactionsMerkleRootHash(sidechainTransactions)
    val mainchainMerkleRootHash: Array[Byte] = SidechainBlock.calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = SidechainBlock.calculateOmmersMerkleRootHash(ommers)

    val unsignedBlockHeader = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgerBox,
      forgerBoxMerklePath,
      vrfProofInBlock,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      ommers.map(_.score).sum,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    val signature = owner.sign(unsignedBlockHeader.messageToSign)

    val signedBlockHeader = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgerBox,
      forgerBoxMerklePath,
      vrfProofInBlock,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      ommers.map(_.score).sum,
      signature
    )
    val generatedBlock = new SidechainBlock(
      signedBlockHeader,
      sidechainTransactions,
      mainchainBlockReferencesData,
      mainchainHeaders,
      ommers,
      companion
    )

    println(s"Generate new unique block with id ${generatedBlock.id}")

    generatedBlock
  }

  private def getIncorrectForgerBox(initialForgerBox: ForgerBox, forgerBoxCorruptionRules: ForgerBoxCorruptionRules): ForgerBox = {
    val proposition: PublicKey25519Proposition = if (forgerBoxCorruptionRules.propositionChanged) {
      val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(rnd.nextLong().toString.getBytes)
      val newProposition: PublicKey25519Proposition = new PublicKey25519Proposition(propositionKeyPair.getValue)
      newProposition
    }
    else {
      initialForgerBox.proposition()
    }

    val nonce: Long = initialForgerBox.nonce() + forgerBoxCorruptionRules.nonceShift
    val value: Long = initialForgerBox.value() + forgerBoxCorruptionRules.valueShift

    val rewardProposition: PublicKey25519Proposition = if (forgerBoxCorruptionRules.rewardPropositionChanged) {
      val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(rnd.nextLong().toString.getBytes)
      val newRewardProposition: PublicKey25519Proposition = new PublicKey25519Proposition(propositionKeyPair.getValue)
      newRewardProposition
    }
    else {
      initialForgerBox.rewardProposition()
    }

    val vrfPubKey: VrfPublicKey = if (forgerBoxCorruptionRules.vrfPubKeyChanged) {
      var corrupted: VrfPublicKey = null

      do {
        val corruptedVrfPublicKeyBytes =
          VrfLoader.vrfFunctions.generatePublicAndSecretKeys(rnd.nextLong().toString.getBytes).get(VrfFunctions.KeyType.PUBLIC)
        corrupted = new VrfPublicKey(corruptedVrfPublicKeyBytes)
        println(s"corrupt VRF public key ${BytesUtils.toHexString(initialForgerBox.vrfPubKey().bytes)} by ${BytesUtils.toHexString(corrupted.bytes)}")
      } while (corrupted.bytes.deep == initialForgerBox.vrfPubKey().bytes.deep)
      corrupted
    }
    else {
      initialForgerBox.vrfPubKey()
    }

    new ForgerBoxData(proposition, value, rewardProposition, vrfPubKey).getBox(nonce)
  }

  private def getIncorrectPossibleForger(initialPossibleForger: PossibleForger): PossibleForger = {
    val possibleIncorrectPossibleForger = forgersSet.getRandomPossibleForger(rnd)
    if (possibleIncorrectPossibleForger.forgingData == initialPossibleForger.forgingData) {
      PossibleForger(
        SidechainForgingData.generate(rnd, rnd.nextLong()),
        Some(MerkleTreeFixture.generateRandomMerklePath(rnd.nextLong())),
        Some(MerkleTreeFixture.generateRandomMerklePath(rnd.nextLong())),
        initialPossibleForger.spentInEpochsAgoOpt
      )
    }
    else {
      possibleIncorrectPossibleForger
    }
  }

  private def getCorruptedMerklePath(possibleForger: PossibleForger): MerklePath = {
    if (possibleForger.merklePathInPreviousEpochOpt.get.bytes().sameElements(possibleForger.merklePathInPrePreviousEpochOpt.get.bytes())) {
      val incorrectPossibleForger = getIncorrectPossibleForger(possibleForger)
      incorrectPossibleForger.merklePathInPrePreviousEpochOpt.getOrElse(MerkleTreeFixture.generateRandomMerklePath(rnd.nextLong()))
    }
    else {
      possibleForger.merklePathInPreviousEpochOpt.get
    }
  }

  private def createGeneratorAndFinishedEpochInfo: (SidechainBlocksGenerator, FinishedEpochInfo) = {
    val finishedEpochId = blockIdToEpochId(lastBlockId)
    val (newForgers, stakeConsensusEpochInfo) = forgersSet.finishCurrentEpoch()

    val nonceConsensusEpochInfo = NonceConsensusEpochInfo(bigIntToConsensusNonce(currentBestMainchainPoW.getOrElse(throw new GenerationIsNoLongerPossible())))
    consensusDataStorage.addNonceConsensusEpochInfo(finishedEpochId, nonceConsensusEpochInfo)

    consensusDataStorage.addStakeConsensusEpochInfo(finishedEpochId, stakeConsensusEpochInfo)

    val newGenerator: SidechainBlocksGenerator = new SidechainBlocksGenerator(
                                                  params = params,
                                                  forgersSet = newForgers,
                                                  consensusDataStorage = consensusDataStorage,
                                                  lastBlockId = lastBlockId,
                                                  lastMainchainBlockId = lastMainchainBlockId,
                                                  nextFreeSlotNumber = intToConsensusSlotNumber(1),
                                                  nextEpochNumber = intToConsensusEpochNumber(nextEpochNumber + 1),
                                                  nextBlockNonceEpochId = blockIdToEpochId(lastBlockId),
                                                  nextBlockStakeEpochId = nextBlockNonceEpochId,
                                                  currentBestMainchainPoW = None,
                                                  rnd = rnd)

    (newGenerator, FinishedEpochInfo(finishedEpochId, stakeConsensusEpochInfo, nonceConsensusEpochInfo))
  }


}


object SidechainBlocksGenerator extends CompanionsFixture {
  private val companion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val merkleTreeSize: Int = 128
  val txGen: TransactionFixture = new TransactionFixture {}
  val mcRefGen: MainchainBlockReferenceFixture = new MainchainBlockReferenceFixture {}
  println("end SidechainBlocksGenerator object")

  def startSidechain(initialValue: Long, seed: Long, params: NetworkParams): (NetworkParams, SidechainBlock, SidechainBlocksGenerator, SidechainForgingData, FinishedEpochInfo) = {
    require(initialValue == SidechainCreation.initialValue) // in future can add any value here, but currently initial forger box is hardcoded
    println("startSidechain")

    val random: Random = new Random(seed)


    val genesisSidechainForgingData: SidechainForgingData = buildGenesisSidechainForgingData(initialValue, seed)

    val vrfProof = VrfGenerator.generateProof(seed) //no VRF proof checking for genesis block!

    val genesisMerkleTree: MerkleTree = buildGenesisMerkleTree(genesisSidechainForgingData.forgerBox)
    val merklePathForGenesisSidechainForgingData: MerklePath = genesisMerkleTree.getMerklePathForLeaf(0)
    val possibleForger: PossibleForger = PossibleForger(
      forgingData = genesisSidechainForgingData,
      merklePathInPreviousEpochOpt = Some(merklePathForGenesisSidechainForgingData),
      merklePathInPrePreviousEpochOpt = Some(merklePathForGenesisSidechainForgingData),
      spentInEpochsAgoOpt = None)

    val genesisSidechainBlock: SidechainBlock = generateGenesisSidechainBlock(params, possibleForger.forgingData, vrfProof, merklePathForGenesisSidechainForgingData)

    val genesisNonce: ConsensusNonce = bigIntToConsensusNonce(getMinimalHashOptFromBlock(genesisSidechainBlock).get)
    val nonceInfo = NonceConsensusEpochInfo(genesisNonce)
    val stakeInfo = StakeConsensusEpochInfo(genesisMerkleTree.rootHash(), possibleForger.forgingData.forgerBox.value())
    val consensusDataStorage = createConsensusDataStorage(genesisSidechainBlock.id, nonceInfo, stakeInfo)

    val networkParams = generateNetworkParams(genesisSidechainBlock, params, random)

    val genesisGenerator = new SidechainBlocksGenerator(
      params = networkParams,
      forgersSet = new PossibleForgersSet(Set(possibleForger)),
      consensusDataStorage = consensusDataStorage,
      lastBlockId = genesisSidechainBlock.id,
      lastMainchainBlockId = genesisSidechainBlock.mainchainBlockReferencesData.last.headerHash,
      nextFreeSlotNumber = intToConsensusSlotNumber(consensusSlotNumber = 1),
      nextEpochNumber = intToConsensusEpochNumber(2),
      nextBlockNonceEpochId = blockIdToEpochId(genesisSidechainBlock.id),
      nextBlockStakeEpochId = blockIdToEpochId(genesisSidechainBlock.id),
      currentBestMainchainPoW = None,
      rnd = random)

    (networkParams, genesisSidechainBlock, genesisGenerator, possibleForger.forgingData, FinishedEpochInfo(blockIdToEpochId(genesisSidechainBlock.id), stakeInfo, nonceInfo))
  }

  private def buildGenesisSidechainForgingData(initialValue: Long, seed: Long): SidechainForgingData = {
    val key = SidechainCreation.genesisSecret
    val forgerBox = SidechainCreation.getHardcodedGenesisForgerBox
    SidechainForgingData(key, forgerBox, SidechainCreation.vrfGenesisSecretKey)
  }

  private def buildGenesisMerkleTree(genesisForgerBox: ForgerBox): MerkleTree = {
    val leave: Array[Byte] = genesisForgerBox.id()

    val initialLeaves = Seq.fill(merkleTreeSize)(leave)
    MerkleTree.createMerkleTree(initialLeaves.asJava)
  }

  private def generateGenesisSidechainBlock(params: NetworkParams, forgingData: SidechainForgingData, vrfProof: VrfProof, merklePath: MerklePath): SidechainBlock = {
    val parentId = bytesToId(new Array[Byte](32))
    val timestamp = if (params.sidechainGenesisBlockTimestamp == 0) {
      Instant.now.getEpochSecond - (params.consensusSecondsInSlot * params.consensusSlotsInEpoch * 100)
    }
    else {
      params.sidechainGenesisBlockTimestamp
    }

    val mainchainBlockReferences = getHardcodedMainchainBlockReferencesWithSidechainCreation
    val mainchainBlockReferencesData = mainchainBlockReferences.map(_.data)
    val mainchainHeaders = mainchainBlockReferences.map(_.header)

    val sidechainTransactionsMerkleRootHash: Array[Byte] = SidechainBlock.calculateTransactionsMerkleRootHash(Seq())
    val mainchainMerkleRootHash: Array[Byte] = SidechainBlock.calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = SidechainBlock.calculateOmmersMerkleRootHash(Seq())

    val owner: PrivateKey25519 = forgingData.key
    val forgerBox = forgingData.forgerBox


    val unsignedBlockHeader = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgerBox,
      merklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      0,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    val signature = owner.sign(unsignedBlockHeader.messageToSign)

    val signedBlockHeader = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgerBox,
      merklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      0,
      signature
    )

    new SidechainBlock(
      signedBlockHeader,
      Seq(),
      mainchainBlockReferencesData,
      mainchainHeaders,
      Seq(),
      companion
    )
  }

  private def createConsensusDataStorage(genesisBlockId: Block.BlockId, nonceInfo: NonceConsensusEpochInfo, stakeInfo: StakeConsensusEpochInfo): ConsensusDataStorage = {
    val consensusDataStorage = new ConsensusDataStorage(new InMemoryStorageAdapter())

    consensusDataStorage.addNonceConsensusEpochInfo(blockIdToEpochId(genesisBlockId), nonceInfo)
    consensusDataStorage.addStakeConsensusEpochInfo(blockIdToEpochId(genesisBlockId), stakeInfo)
    consensusDataStorage
  }

  private def generateNetworkParams(genesisSidechainBlock: SidechainBlock, params: NetworkParams, random: Random): NetworkParams = {
    val mainchainHeight = Math.abs(random.nextInt() % 1024)

    new NetworkParams {
      override val EquihashN: Int = params.EquihashN
      override val EquihashK: Int = params.EquihashK
      override val EquihashVarIntLength: Int = params.EquihashVarIntLength
      override val EquihashSolutionLength: Int = params.EquihashSolutionLength

      override val powLimit: BigInteger = params.powLimit
      override val nPowAveragingWindow: Int = params.nPowAveragingWindow
      override val nPowMaxAdjustDown: Int = params.nPowMaxAdjustDown
      override val nPowMaxAdjustUp: Int = params.nPowMaxAdjustUp
      override val nPowTargetSpacing: Int = params.nPowTargetSpacing

      override val sidechainId: Array[Byte] = params.sidechainId
      override val sidechainGenesisBlockId: ModifierId = genesisSidechainBlock.id
      override val genesisMainchainBlockHash: Array[Byte] = params.genesisMainchainBlockHash
      override val genesisPoWData: Seq[(Int, Int)] = params.genesisPoWData
      override val mainchainCreationBlockHeight: Int = genesisSidechainBlock.mainchainBlockReferencesData.size + mainchainHeight
      override val sidechainGenesisBlockTimestamp: Block.Timestamp = genesisSidechainBlock.timestamp
      override val withdrawalEpochLength: Int = params.withdrawalEpochLength
      override val consensusSecondsInSlot: Int = params.consensusSecondsInSlot
      override val consensusSlotsInEpoch: Int = params.consensusSlotsInEpoch
      override val schnorrPublicKeys: Seq[SchnorrPublicKey] = Seq()
      override val backwardTransferThreshold: Int = 0
      override val provingKeyFilePath: String = ""
    }
  }

  private val getHardcodedMainchainBlockReferencesWithSidechainCreation: Seq[MainchainBlockReference] = {
    val mcBlocksSerializer: ListSerializer[MainchainBlockReference] = new ListSerializer[MainchainBlockReference](
      MainchainBlockReferenceSerializer, SidechainBlock.MAX_MC_BLOCKS_NUMBER)

    /* shall we somehow check time leap between mainchain block time creation and genesis sidechain block creation times? */
    /* @TODO sidechain creation mainchain block shall also be generated, not hardcoded */
    // Genesis MC block hex created in regtest from MC branch as/sc_development on 25.03.2020
    val mcBlockHex: String = "03000000749934fc0003b9a58b4ec224edba014abbc6eacdb85bb0bea37f9f6a0efaf20a6b88102abdf3b51ce57704e2d20d9b21b10b59403b17d2ddb6ea42019c90164e688a6c1790d383ced687150bdd29b818005239f908cdfaed1117204bb98acdbc55cbaf5e030f0f201000970d3013e3add5f230b8331886488c299e2a4f74c14a532254d302f0000024035bb1dd929da924b731a2673af35f3572ec13c0de8ff4d73dbdf71a2bd6dce24e324fc70201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dd000101ffffffff04611bb42c000000001976a914578f7b9829fd5c6cdf3203eb8a7f209505743a5e88ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff05f5939491a16bc8e791d8dade9e8755ed7fe0c059bc245689c38511d80978aeed000000006a47304402207528a1302f0757f2268d0349594d7b715b27f5b12481e48593b582f9dfbb7b84022028125c630b7864226b1f0a7e600fd9e4ec4ba2d8ff300c9e02ba4a1f09a79fe70121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffff24c09e3db567a0a14ab1765bc6331fc30187693cbd7f8a0935ed608a4849c049000000006b483045022100ae79e3eb7a696d8676a2313070491f6085831ac45084a516c083ec18d95b3f2402203158c34a880dee2cc6ed3b44dc484b0db58c924133538d5b19bc8685930330d90121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfefffffff6a8d248a72ddabea0a08041b83f5fa20062058dcc0ab0f9e71a091f93686c8d000000006a4730440220316ccaa2fd7ff9832f90c9e662f4a6f43381cdf6b85e770324334b32f7be4ea80220750b907edd68604e9c5631fbb25bfe3b827effb8782f4e1e3a3771d566e6c8a00121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffff88dbae6499670c0375c78fb5a9a61d14f02578696db56c22a0058293a46aced1000000006b483045022100ac2416d92ad17ce758f8e0a2c03db7c775ad1fbde9814399eb109623c4339e53022025e4584dccf3fc0aeebdddd43c4aff8b1b7276ee21cf124ea04848832bfe37b90121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffffd49753179fedf1b1f3d63a94287193381cf498e057d493d4c829b37e3c878ec2000000006b483045022100d7ab80769ccf6c4588af18179e03c9ee984d9e2af5342a263cda2bae28fa2516022073f62e6a4aaee17f22fe5f45e0e1dc51bfda80f811df85c4faa6ed7ce537a49f0121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffff019f70d21a000000003c76a9145728a6a847dfcccc46dd37044d88ffa861779ed888ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010100000000000000000000000000000000000000000000000000000000000000e803000000f2052a0100000040eec4573bf5ceeb468a5c67808c2218927c9c4411ecd6b7f106b81d8d6e12bf60ae2ac5544b91719ef675c6404d1f969840af4fe8ca67aa2c85555ffb96e351c58b3105cab56c7b0f171532110ab7ec1f879d7a1fe0467f273bf6dd0bc4e016f677289ccfa39d6604736d994c3201f3c0c9d4ac57d4e56859592291b8f8a452730000d200000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001"))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params).get
    Seq(mcRef)
  }
}