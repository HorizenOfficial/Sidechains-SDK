package com.horizen.fixtures.sidechainblock.generation

import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.util.Random

import com.google.common.primitives.{Ints, Longs}
import com.horizen.block._
import com.horizen.box.data.ForgerBoxData
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.fixtures._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition, SchnorrProposition, VrfPublicKey}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator, VrfKeyGenerator}
import com.horizen.storage.InMemoryStorageAdapter
import com.horizen.transaction.SidechainTransaction
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils
import com.horizen.utils._
import com.horizen.vrf._
import com.horizen.cryptolibprovider.{VrfFunctions, CryptoLibProvider}
import scorex.core.block.Block
import scorex.util.{ModifierId, bytesToId}

import scala.collection.JavaConverters._


case class GeneratedBlockInfo(block: SidechainBlock, forger: SidechainForgingData)
case class FinishedEpochInfo(epochId: ConsensusEpochId, stakeConsensusEpochInfo: StakeConsensusEpochInfo, nonceConsensusEpochInfo: NonceConsensusEpochInfo)

//will be thrown if block generation no longer is possible, for example: nonce no longer can be calculated due no mainchain references in whole epoch
class GenerationIsNoLongerPossible extends IllegalStateException

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
                                        allEligibleVrfOutputs: List[Array[Byte]],
                                        previousEpochId: ConsensusEpochId,
                                        rnd: Random) extends TimeToEpochSlotConverter {
  import SidechainBlocksGenerator._

  def getNotSpentBoxes: Set[SidechainForgingData] = forgersSet.getNotSpentSidechainForgingData.map(_.copy())

  def tryToGenerateBlockForCurrentSlot(generationRules: GenerationRules): Option[GeneratedBlockInfo] = {
    checkGenerationRules(generationRules)
    val nextSlot = intToConsensusSlotNumber(Math.min(nextFreeSlotNumber, params.consensusSlotsInEpoch))
    getNextEligibleForgerForCurrentEpoch(generationRules, nextSlot).map{
      case (blockForger, vrfProof, vrfOutput, usedSlotNumber) => {
        println(s"Got forger for block: ${blockForger}")
        val newBlock = generateBlock(blockForger, vrfProof, vrfOutput, usedSlotNumber, generationRules)
        GeneratedBlockInfo(newBlock, blockForger.forgingData)
      }
    }
  }

  def tryToGenerateCorrectBlock(generationRules: GenerationRules): (SidechainBlocksGenerator, Either[FinishedEpochInfo, GeneratedBlockInfo]) = {
    checkGenerationRules(generationRules)
    getNextEligibleForgerForCurrentEpoch(generationRules, intToConsensusSlotNumber(params.consensusSlotsInEpoch)) match {
      case Some((blockForger, vrfProof, vrfOutput, usedSlotNumber)) => {
        println(s"Got forger: ${blockForger}")
        val newBlock: SidechainBlock = generateBlock(blockForger, vrfProof, vrfOutput, usedSlotNumber, generationRules)
        val newForgers: PossibleForgersSet = forgersSet.createModified(generationRules)
        val generator = createGeneratorAfterBlock(newBlock, usedSlotNumber, newForgers, vrfOutput)
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

  private def getNextEligibleForgerForCurrentEpoch(generationRules: GenerationRules, endSlot: ConsensusSlotNumber): Option[(PossibleForger, VrfProof, VrfOutput, ConsensusSlotNumber)] = {
    require(nextFreeSlotNumber <= endSlot + 1)

    val initialNonce: Array[Byte] = consensusDataStorage.getNonceConsensusEpochInfo(nextBlockNonceEpochId).get.consensusNonce
    val changedNonceBytes: Long = Longs.fromByteArray(initialNonce.take(Longs.BYTES)) + generationRules.corruption.consensusNonceShift
    val nonce =  Longs.toByteArray(changedNonceBytes) ++ initialNonce.slice(Longs.BYTES, initialNonce.length)

    val consensusNonce: NonceConsensusEpochInfo = NonceConsensusEpochInfo(ConsensusNonce @@ nonce)

    val totalStake = consensusDataStorage.getStakeConsensusEpochInfo(nextBlockStakeEpochId).get.totalStake

    val possibleForger = (nextFreeSlotNumber to endSlot)
      .toStream
      .flatMap{currentSlot =>
        val slotWithShift = intToConsensusSlotNumber(Math.min(currentSlot + generationRules.corruption.consensusSlotShift, params.consensusSlotsInEpoch))
        println(s"Process slot: ${slotWithShift}")
        val res = forgersSet.getEligibleForger(slotWithShift, consensusNonce, totalStake, generationRules.corruption.getStakeCheckCorruptionFunction)
        if (res.isEmpty) {println(s"No forger had been found for slot ${currentSlot}")}
        res.map{case(forger, proof, vrfOutput) => (forger, proof, vrfOutput, intToConsensusSlotNumber(currentSlot))}
      }
      .headOption

    possibleForger
  }


  private def createGeneratorAfterBlock(newBlock: SidechainBlock,
                                        usedSlot: ConsensusSlotNumber,
                                        newForgers: PossibleForgersSet,
                                        vrfOutput: VrfOutput): SidechainBlocksGenerator = {
    val quietSlotsNumber = params.consensusSlotsInEpoch / 3
    val eligibleSlotsRange = ((quietSlotsNumber + 1) until (params.consensusSlotsInEpoch - quietSlotsNumber))

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
      allEligibleVrfOutputs =
        if (eligibleSlotsRange.contains(usedSlot)) allEligibleVrfOutputs :+ vrfOutput.bytes() else allEligibleVrfOutputs,
      previousEpochId = previousEpochId,
      rnd = rnd
    )
  }

  private def generateBlock(possibleForger: PossibleForger, vrfProof: VrfProof, vrfOutput: VrfOutput, usedSlotNumber: ConsensusSlotNumber, generationRules: GenerationRules): SidechainBlock = {
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

    val forgingStake: ForgingStakeInfo = generationRules.corruption.forgingStakeCorruptionRules.map(getIncorrectForgingStake(forgingData.forgingStakeInfo, _)).getOrElse(forgingData.forgingStakeInfo)

    val forgingStakeMerklePath: MerklePath =
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
      forgingStake,
      forgingStakeMerklePath,
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
      forgingStake,
      forgingStakeMerklePath,
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

  private def getIncorrectForgingStake(initialForgingStake: ForgingStakeInfo, forgingStakeCorruptionRules: ForgingStakeCorruptionRules): ForgingStakeInfo = {
    val stakeAmount: Long = initialForgingStake.stakeAmount + forgingStakeCorruptionRules.stakeAmountShift

    val blockSignProposition: PublicKey25519Proposition = if (forgingStakeCorruptionRules.blockSignPropositionChanged) {
      val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(rnd.nextLong().toString.getBytes)
      val newBlockSignProposition: PublicKey25519Proposition = new PublicKey25519Proposition(propositionKeyPair.getValue)
      newBlockSignProposition
    }
    else {
      initialForgingStake.blockSignPublicKey
    }

    val vrfPubKey: VrfPublicKey = if (forgingStakeCorruptionRules.vrfPubKeyChanged) {
      var corrupted: VrfPublicKey = null

      do {
        val corruptedVrfPublicKeyBytes =
          CryptoLibProvider.vrfFunctions.generatePublicAndSecretKeys(rnd.nextLong().toString.getBytes).get(VrfFunctions.KeyType.PUBLIC)
        corrupted = new VrfPublicKey(corruptedVrfPublicKeyBytes)
        println(s"corrupt VRF public key ${BytesUtils.toHexString(initialForgingStake.vrfPublicKey.bytes)} by ${BytesUtils.toHexString(corrupted.bytes)}")
      } while (corrupted.bytes.deep == initialForgingStake.vrfPublicKey.bytes.deep)
      corrupted
    }
    else {
      initialForgingStake.vrfPublicKey
    }

    ForgingStakeInfo(blockSignProposition, vrfPubKey, stakeAmount)
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
    if (finishedEpochId == previousEpochId) {throw new GenerationIsNoLongerPossible()} //no generated block during whole epoch

    val (newForgers, realStakeConsensusEpochInfo) = forgersSet.finishCurrentEpoch()
    //Just to increase chance for forgers, @TODO do it as parameter
    val stakeConsensusEpochInfo = realStakeConsensusEpochInfo.copy(totalStake = realStakeConsensusEpochInfo.totalStake / 20)

    val nonceConsensusEpochInfo = calculateNewNonce()
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
                                                  allEligibleVrfOutputs = List(),
                                                  previousEpochId = finishedEpochId,
                                                  rnd = rnd)

    (newGenerator, FinishedEpochInfo(finishedEpochId, stakeConsensusEpochInfo, nonceConsensusEpochInfo))
  }

  private def calculateNewNonce(): NonceConsensusEpochInfo = {

    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    allEligibleVrfOutputs.foldRight(digest){case (bytes, messageDigest) =>
      messageDigest.update(bytes)
      messageDigest
    }

    val previousNonce: Array[Byte] = consensusDataStorage.getNonceConsensusEpochInfo(previousEpochId)
      .getOrElse(throw new IllegalStateException (s"Failed to get nonce info for epoch id ${previousEpochId}"))
      .consensusNonce
    digest.update(previousNonce)

    val currentEpochNumberBytes: Array[Byte] = Ints.toByteArray(nextEpochNumber)
    digest.update(currentEpochNumberBytes)

    NonceConsensusEpochInfo(byteArrayToConsensusNonce(digest.digest()))
  }

}


object SidechainBlocksGenerator extends CompanionsFixture {
  private val companion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val merkleTreeSize: Int = 128
  val txGen: TransactionFixture = new TransactionFixture {}
  val mcRefGen: MainchainBlockReferenceFixture = new MainchainBlockReferenceFixture {}
  println("end SidechainBlocksGenerator object")

  def startSidechain(initialValue: Long, seed: Long, params: NetworkParams): (NetworkParams, SidechainBlock, SidechainBlocksGenerator, SidechainForgingData, FinishedEpochInfo) = {
    println("startSidechain")

    val random: Random = new Random(seed)


    val genesisSidechainForgingData: SidechainForgingData = buildGenesisSidechainForgingData(initialValue, seed)

    val vrfProof = VrfGenerator.generateProof(seed) //no VRF proof checking for genesis block!

    val genesisMerkleTree: MerkleTree = buildGenesisMerkleTree(genesisSidechainForgingData.forgingStakeInfo)
    val merklePathForGenesisSidechainForgingData: MerklePath = genesisMerkleTree.getMerklePathForLeaf(0)
    val possibleForger: PossibleForger = PossibleForger(
      forgingData = genesisSidechainForgingData,
      merklePathInPreviousEpochOpt = Some(merklePathForGenesisSidechainForgingData),
      merklePathInPrePreviousEpochOpt = Some(merklePathForGenesisSidechainForgingData),
      spentInEpochsAgoOpt = None)

    val genesisSidechainBlock: SidechainBlock = generateGenesisSidechainBlock(params, possibleForger.forgingData, vrfProof, merklePathForGenesisSidechainForgingData)
    val networkParams = generateNetworkParams(genesisSidechainBlock, params, random)

    val nonceInfo = ConsensusDataProvider.calculateNonceForGenesisBlock(networkParams)
    val stakeInfo =
      StakeConsensusEpochInfo(genesisMerkleTree.rootHash(), possibleForger.forgingData.forgingStakeInfo.stakeAmount)
    val consensusDataStorage = createConsensusDataStorage(genesisSidechainBlock.id, nonceInfo, stakeInfo)


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
      allEligibleVrfOutputs = List(),
      previousEpochId = blockIdToEpochId(genesisSidechainBlock.id),
      rnd = random)

    (networkParams, genesisSidechainBlock, genesisGenerator, possibleForger.forgingData, FinishedEpochInfo(blockIdToEpochId(genesisSidechainBlock.id), stakeInfo, nonceInfo))
  }

  private def buildGenesisSidechainForgingData(initialValue: Long, seed: Long): SidechainForgingData = {
    val key = PrivateKey25519Creator.getInstance().generateSecret(seed.toString.getBytes)
    val value = initialValue
    val vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(seed.toString.getBytes)
    val vrfPublicKey = vrfSecretKey.publicImage()

    val forgerBoxData = new ForgerBoxData(key.publicImage(), value, key.publicImage(), vrfPublicKey)

    val nonce = 42L

    val forgerBox = forgerBoxData.getBox(nonce)
    val forgingStake = ForgingStakeInfo(forgerBox.blockSignProposition(), forgerBox.vrfPubKey(), forgerBox.value())

    SidechainForgingData(key, forgingStake, vrfSecretKey)
  }

  private def buildGenesisMerkleTree(genesisForgingStakeInfo: ForgingStakeInfo): MerkleTree = {
    val leave: Array[Byte] = genesisForgingStakeInfo.hash

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
    val forgingStake = forgingData.forgingStakeInfo


    val unsignedBlockHeader = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgingStake,
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
      forgingStake,
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
      override val parentHashOfGenesisMainchainBlock: Array[Byte] = params.parentHashOfGenesisMainchainBlock
      override val genesisPoWData: Seq[(Int, Int)] = params.genesisPoWData
      override val mainchainCreationBlockHeight: Int = genesisSidechainBlock.mainchainBlockReferencesData.size + mainchainHeight
      override val sidechainGenesisBlockTimestamp: Block.Timestamp = genesisSidechainBlock.timestamp
      override val withdrawalEpochLength: Int = params.withdrawalEpochLength
      override val consensusSecondsInSlot: Int = params.consensusSecondsInSlot
      override val consensusSlotsInEpoch: Int = params.consensusSlotsInEpoch
      override val signersPublicKeys: Seq[SchnorrProposition] = params.signersPublicKeys
      override val signersThreshold: Int = params.signersThreshold
      override val provingKeyFilePath: String = params.provingKeyFilePath
      override val verificationKeyFilePath: String = params.verificationKeyFilePath
      override val calculatedSysDataConstant: Array[Byte] = Array() //calculate if we need for some reason that data
    }
  }

  private val getHardcodedMainchainBlockReferencesWithSidechainCreation: Seq[MainchainBlockReference] = {
    /* shall we somehow check time leap between mainchain block time creation and genesis sidechain block creation times? */
    /* @TODO sidechain creation mainchain block shall also be generated, not hardcoded */
    // Genesis MC block hex created in regtest from MC branch betav1 on 12.06.2020
    val mcBlockHex: String = "030000001b199ec4516c4b6021363cd73dcadd5382a1281de6291a46d8a0a6b01a4d490529707a00647aac4ba0f00f64b3b46f6f2d496a030cc23b5808ad78d1fa3606d73622107ce90ea52de0b1b5896459f9b41d0b83076920494643229a5d320a78e6c149e35e030f0f202200b5e4f98dd21ec442183f8bc8ec9a2e483da99e3a773c50390302d7570000240140d14c11a99d48f9244edc0f735729897402236d7a82d9b63fb519d04ab512a2bb39b20201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dc000101ffffffff045724b42c000000001976a914a6dcdfc99033d07beb459e5d14fc903296e0cc6e88ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff09174cdf38ee44a2d9ea76363c5f121afadc69dfc7d9d0c4ab0e35e6970eb99108000000006a4730440220612dba5d3968c27a9d162863f4a30d0ee597e3d1628da6a854fd3020eef66a67022034a37a3fa5f1fecd587e74a3f5d3d9f86af1e4abece4ed4c24bf106d3b0d1c0b012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff64a2205070fe9747fb641b1614027b8bf1fa18a95cc808b05cb5206dc27e66ca000000006b48304502210093a89f7818dd71469ba6d2427f6a7cd59a8502d94b4c90f3eba88e722e1e94c302207625ae9a3c1a5ddb7ade8ade70eb89d772743fcb3b4e79af43bb24b9c66d104e012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffe8ac048bfa7286d87756857d4d3a78291cba719c63a32c070982a5fc2d226e1c000000006a473044022038c5996d46c02269d435db49201ec6dc371e2fe873c258a6fe768bf71ffd00fc02200413e4610c064bbbac529f8d3bb68f118814b00cf6d362af2a866535e149ffc3012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff9c1192ad64c623a0e30edf4cc38e8122f5b47f0c5ce954555999bbcc6d54cb84000000006b483045022100c61768c9315bc074dc089bb74002e2e63cf92b22b6acf9e6200a43bb755825e8022008570a0e68a2bd4da4fee3686efff28a5e65902924571ace2475e256fa23f15e012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff04d9f29d257d289b5693a0da5bf9c01e620b4de046b918f92b22ec3ae2c166fd000000006a4730440220156bc610578fb1c5f8da019760a24c0634e3536225ec4b7f6aa89ad8c56a72f602202034a699c347baaaed72fbbe8f9cb453d9d72fa1e44e2623c6e19ddf0a92da6a012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff97c2f86e46e775b320e22630dbe84e7a04f5103bb1b7f54797bd76ba843ca44e000000006a473044022071b9c641218c8153e855615996803cd0885dc7b0826b1aab3cb4c4c9be638f64022064c3e1a5b22ce38ae566c1f73924666b49876e06c283529a8875d9e74be0300c012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffd1d57576ba121a45e4ad340975abdebf24d0d0dab85dd17440d78aaf13cc0b38000000006a47304402202fd572e2c941feb8710283a6e95a8d39964f01873cf7506dcdc828d914ac4f4302204d0b6bcf9d05e902e9a05cc96d41c2a19af0bf1055177f8e1111b2a8e2e5de2b012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffce4e0c6cd8f1c1b2ab2350d72383aaefe8d925128b126d40d7ef346910a6ea8a000000006a47304402205e2bd14ea3787febea21803a135a882b553fff7758fc2370af8055c1694d8ba3022034382608166beaa057324ecdaa0f6747022f2e0f7f2b3539426b5f62440beb67012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff3d71485da571aacb77f33117e4aa59cf02eec6ad38210c110ca09e9258df1b33000000006b48304502210084dcc8d11fba1b4b2c2b74cca2128b40bdc563ed00a2d725144117b7f3b29c34022074edb63dd8422649237d421e32b6aa3d8db75c0f8bcdf3a809ea1b003621de96012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff01696b7d01000000003c76a9144129792b8ec0606c8ddb53a8c011f257dd2292e288ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010a00000000e40b5402000000acb24b2f08e8e56fe1784f1600879c697c7cd90846e076724b090fd72206b1a5c1dd2de641154fd54de4cf60ea3f5b9e7135787ecb9fcce75de5c41f974fd0cbf70af51ba99b1b8d591d237091414051d2953b7d75e16d89be6fe1cf0bfc63a244f6f51159061875ff1922c3d923d365370ac2605c19e03d674bf64af9e91e00003a6fe5d3f1bcddf09faee1866e453f99d4491e68811bc1a7d5695955e4f8f456627f546bdbbbd026c1b6ee35e2f65659cbcd32406026ebb8f602c86d3f42499f8412dc3ebe664ce188c69360f13dddbd577513171f49423d51ff9578b15901000060b866a7695601aa41cb7775ed16208d0f79e8c19376d99b3cbab937f3a271a7347a9eceab34d14ddcd0aa7936869cc620d5f5a8a44c78a0fc621edfc69e2751ad4192fd57a57eb5d8e798bd15773b20a29cd260755b6c5ec7b051ecd4685e01005e7b462cc84ae0faaa5884bd5c4a5a5edf13db210599aeeb4d273c0f5f32967b7071ce2b4d490b9f08f6ce66a8405735c79197cd6773d1c5aeb2a38da1c102df07b05879c77198e5aafa7feed25d4137e86b3d98d9edd9547a460f1615b10000ee9570fbffedd44170477b37500a0a1cb3f94b6361f10f8a68c4075fbc17542d7174b3d95e12ddb8aea5d6b6c53c1df6c8f60010cd2e69902ba5e89e86747569463a23254730fc8d2aabf39648a505df9dcce461443b181ef3eda46074070000550836db2c97820971db6b1421e348d946ed4d3f255295abea46556615e3123de33ec56f784f70302901a4bc10c79c6a8b1e32477aeff9fba75876592981b678fc5a2703ac0b3055e567a6cb1ebab578fc4f9121fd968680250696cb85790000078fcfb60bdfc79aa1e377cb120480538e0236156f23129a88824ca5a1d77e371e5e98a16e6f32087c91aa02a4f5e00e412e515c3b678f6535141203c6886c637b626a2ada4062d037503359a680979091c68941a307db6e4ed8bc49d21b00002f0e6f88fb69309873fdefb015569e5511fb5399295204876543d065d177bf36ab79183a7c5e504b50691bc5b4ed0293324cfe2555d3fc8e39485822a90a91afcd4ef79ec3aefbd4cbe25cbccd802d8334ce1dce238c3f7505330a14615500001f89fbe1922ab3aa31a28fd29e19673714a7e48050dee59859d68345bb7bee7d5e888d8b798a58d7c650f9138304c05a92b668294c6114185ccb2c67ce0bbbb7e1dcbb6d76f5cacd7c9732a33b21d69bd7a28c9cca68b5735d50413862bc0100308bb0dd0bd53f3d1134966702dd3c7cc8b58b270a6996a646493250b0d5f3978d0c971f8fa7a0c958f3efe2fa5269244973fafb701c2eb66dd25901f93d677ab6c538c1ed11f115e52d3f2c7087ea40c3e8cd089376baa38842e9429b5f0000d19a8d874d791f952f13d3c8ecd92e44009c09815e5ae6a8e5def7ea52fe3de4accfb5ba2aa401fbcec14b069cd0dc0f66ab025b45ef9831a26acf58673db7487043654e7980fcb2b6c1bd7593a4dfff810436f653e309121c7ccf2df70b010000732254ec6df184be360cd9ed383ed7c8c236d7761cfc0ce4e7f0cac5a06f4edab9cfc75a7dc1449c0e18ed9564c974c2e1b6847c637f74e5d391cbc80fc6e672ffd66b5ce4fb73bda8359ab8a0ea1e855df1e07d82f93c935c7e1a9a55c5000065efdbb7c3e82291a482b2f24cbd46f4dd02c370cf6dcfe8fb3c00b8b004b5ad51369b1f1b134a824d1f16d72ca6a27ba2d6190150329139cf2c6d9e5a14722f8d39b96b882c1f60a7b230e929819e2abe1cd9d7f3e8c726b1a94d20c8010100732c396eca6ffa1bf851cef449f2f087edd93e4f641b4bd93a482d9f129e675aedb688993d4e2cee824d2803301364ba10fbb66895927adb53bad8aefe8a1caab6f4ccb45883e414a1223ac7f90a89087cd752dfa0c7b3e19bbae000edd5000028d1d23c627d1252d2a2a20a246af2280f50e3fde667873aadd9893ba6833118358398e7428e717128f764714a8d52b090c1f554f58e25ea815338d7bc7326c949567e74f2f2ab3c88f5075fea75594608b8937c9059a42d712ffbd1bd980100000000000250c1a474689e375a309446e5cdd3a0c26cecdcff5c7b8cdc0728868983f1a35a49e3a1bae6f969c3d47356c08d3d169d2c0a2be908d82cd35f41a23d8c2924a9f790ab3a00d53061d440a176670d6a32de2ecd19cf8a9774729c09a6ea4d0100d8838bf55d95521291da12294b302c66042eda0dc2acc79360a1fdd8c9a366fa790c52bf926c2d96b5ba88a3a443487c5235f7c476f350c2101cfbe3bd0361dd291ebc5e42c097a158704b71006886a3662ca6db7d816b4ad12444835d89000000795ce2b34aef921ccb3d9b9695f5d3fe0a03743c955cfcf01f8a1815a7c8b03de85fe15201d4b4b6f401cb334a6988ea5bde8986a468c47c3c6a5ae96a3160ff15e06699ea82bd40c0d5547fe1be77af7817861bbfcca3f4232f05a9cec800006c216565cee4d57b32d2d70bb3cb8d4a967c0eb5d7137b2ec58466f3d4d3b5375e4baa823bcc29c6ad877d9708cd5dc1c31fa3883a80710431110c4aa22e97b67fa639f54e86cfab87187011270139df7873bed12f6fb8cd9ab48f389338010000007500000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.fromHexString("2f7ed2e07ad78e52f43aafb85e242497f5a1da3539ecf37832a0a31ed54072c3"))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params).get
    Seq(mcRef)
  }
}