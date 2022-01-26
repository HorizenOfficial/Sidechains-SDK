package com.horizen.fixtures.sidechainblock.generation

import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.util.Random

import com.google.common.primitives.{Ints, Longs}
import com.horizen.block._
import com.horizen.box.data.ForgerBoxData
import com.horizen.box.{ForgerBox, Box}
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
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
import com.horizen.cryptolibprovider.{CryptoLibProvider, VrfFunctions}
import com.horizen.librustsidechains.FieldElement
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
                                        rnd: Random) {
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
    val nonce = Longs.toByteArray(changedNonceBytes)

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
      TimeToEpochUtils.getTimeStampForEpochAndSlot(params, nextEpochNumber, usedSlotNumber) + generationRules.corruption.timestampShiftInSlots * params.consensusSecondsInSlot}

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

    val sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]] = Seq(
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

    NonceConsensusEpochInfo(byteArrayToConsensusNonce(digest.digest().slice(0, Longs.BYTES)))
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
      override val certProvingKeyFilePath: String = params.certProvingKeyFilePath
      override val certVerificationKeyFilePath: String = params.certVerificationKeyFilePath
      override val calculatedSysDataConstant: Array[Byte] = new Array[Byte](32) //calculate if we need for some reason that data
      override val initialCumulativeCommTreeHash: Array[Byte] = params.initialCumulativeCommTreeHash
      override val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig] = Seq()
      override val cswProvingKeyFilePath: String = params.cswProvingKeyFilePath
      override val cswVerificationKeyFilePath: String = params.cswVerificationKeyFilePath
    }
  }

  private val getHardcodedMainchainBlockReferencesWithSidechainCreation: Seq[MainchainBlockReference] = {
    /* shall we somehow check time leap between mainchain block time creation and genesis sidechain block creation times? */
    /* @TODO sidechain creation mainchain block shall also be generated, not hardcoded */
    // Genesis MC block hex created in regtest by STF sc_bootstrap.py test on 01.06.2021
    val mcBlockHex: String = "030000009bc83df13e66a80aceb53007db2a6c631cffc4d0714dfc26969104a2fa0b8f064cb5e7ed1591f35e2ad680ec2bdb4a1e4a472f1f2b7674175ebe32f1c2e305f15447785754469c34f3044fe8ddf825916f8017dfb3afedce1a3a6ff870a60d30f927b660030f0f2005008c5d2d185fe3665516070bec32fb2773045f7fba21cd092823e164ae0000240a46209311d6309d143e7cd7544aee6b17f7186e562d553f495ad732c450d42afdd745f40201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dc000101ffffffff04da20b42c000000001976a914bdbef23725aa04893cece385d80baf5ae775cf0288ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff095833fe3031763d3425d037d5bb45d18192ed36118de9a3e9588af7f96646c3cb000000006a4730440220466745be0f6665f74bf1bcb814b6c7d7404a4f49872a04fad8fead41e5fdced60220121c874e79c1b35697fc877a4649606cc658b74ccb72df138b9e4f3f4e9ebc4f01210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff08b790eef51bbdb46e4c1719fc7d25847298132f9a95c15b7e6f9e2a2c541e6e000000006b48304502210088e177d863d89fdcf22e29f6093cf1b4fba3d7451daa856bc6890d1fdc32a2e7022011314f14bd24e9b953416f953e7532801aa89082a7983e23db509ebff8b07a3401210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197fefffffff663819d3337940dde5697a86c418d3a452085601b12fa855c42f3d94dde8054000000006a4730440220300d522429faafcf675a64da33561385663731eb8084fe5477e85d65f5d394b2022029a30083b5ae89a7900db6804b915378b67e2be0ccbb9e8d1b2f8e90faadd20b01210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff42c105768ad3759de038bc2f8b5b9716414cf59a3426ca64cc8e4d4d1e59370f000000006b483045022100bc55db6e37832d58d1ec89e56ae29041ffb2c28121a2913a653209f1519deab6022031e33164f2c91f96e4289b22ba7025b5bdca3d73951c54c7fd1e4a39f764569701210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff1dea5bf1398e4a96363bc8d11f6442f31d6c2962788a173f4f67a465bfb0009b000000006b483045022100d687f9edf25d09a50af11d182122fdb46a0ace25a2fbe7aefedf2c38b8ba273302203223483b9cd5782ef5d1b7306fc36770213722a8d418a25fbbad33758e3533b901210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff7fba1a938fcda6335d0ee234e7efd03bf52198aceeb7fb24b2052e18e41c75a2000000006a47304402205e404de5a60acc973db0750e14efcba5bbbed1510c27af4bd45acfc0e172486202204a61dea05dd63442bc55777443e293d8156f1367735d1fb02fb01ef97b3e098601210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff7e5ddfc882e825032f61a06940e41ee537cef1d234aa123f9bf4d9c78e6f97ae000000006a47304402201ee4ac01d9f5bb7d676d867311383172ddb5e0c14b2e5457ae3221891ff2015802203ddc30bee5ec98852165b402d49ad442a6bedf167020fd1758103ba461ff934801210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff75f7acf5074453e029f98a2c6561a6437146cb331bb007c241f5e3f4e134e91a000000006a4730440220051cf52cdbc6a3b1dc31dcdabf385368ccec9f6d0bb1c67ccfab938867f627f5022043ae888c522e0f7e1ca8c3cb698eca27dbe44b6402c604b9513cbaf30786634301210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffffd627b8b5cddc7c26f219a591f60628d577158978191187db385da19fb7198244000000006a47304402204cfbb07a0eb5b4341278ef8e076fba026f1f832ff7fd4afce9aa13f1f003f87b02207780916d69352f5b4750f873db141f86375726165074e38939b90513181ff6ff01210286b23be25f96b12cc85ed7d8e9ae0bbd66b2b0119bcb954737724b37b8796197feffffff01e66e7d01000000003c76a914f73d9399c7bfc956daec93b94725768733e74acb88ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b40001e803000000e40b5402000000acb24b2f08e8e56fe1784f1600879c697c7cd90846e076724b090fd72206b1a521894dba38c1b29a58900abac445c01201f677615f741773a6a5c8c8c9c705842b8001205ebec91ed4b34dba38c88f072be677d55932086185d0cb27542d387ab7ddd337fd51030231840000000000003184000000000000ec230200000000000c000000000000000289ee1d3e10eb2b9371398f343e5103e098555f9c016bdda911c4925ede85ea35804cac36c9112c4eed9ee8e305ecadb141cf74c354fdbc25186d760dcdd224f9190000027b364cb46a3fccf2798a4a3e7c12620e6ecefa2047cbd5ca2b801670fcce663880799badd0ed62419d24b71a26390ccc2283884a51da6b9e67e61b09116bb3fb0b800002dea9d31b7d4e1d749199a28a422b23087ab6d189adf452bc04bc7183cad55600004b1ca68282d835c71a74ae432d4ea78cb083a0e40257559f99c865ef9dbe8b2b000002d76cea2dd78ef1eb68281837ec8abfb3919212df8a21aea34eff5c58cb132817806067fbcd4ac4414cc96fba953a03d20689fac637adfbcfe366b7e04b684d4f2e800002f8062a62e9d101dd7854e9451e176129ebb27ec79219bfbafd4abf78d239e51f805a7ea318b15237db138e9d8520a0126357bcf1a64aee88f94957ffffa1f06c198000024eeee6aa2656f25b3513b9c2b0988d82d0778de2e6fa02811d1c78517ee14c0200e34fa134dff33b749771375f1caeaa2e3482c57febf0855620208510fbe2fd33800002952560eb67c3c1d28183bada0ca1553c2e8f2a9e7e6ebb17c9427d259cb7cd2a80bea17528344a1fa934dae0c27a23b6f36821795ddd2e96060b2abe1ef4bfb22200000236d51c2f6800f6a3ffd095c066d34a9a922b144fc6144ff08922570387d2e03980ee9870e6b200611522e50731f6baeab7607acdf5f3df9ee57eadc514182044218000024a401f7048b4f52d9132ad304debbb7f5b9e24b90590636f78b086769a9e2e3b008ff02b3d3d945af0ab6e2c1f162156422e718f5b4f007e9e2473a38f126b5e190000028d44bba3d03a691d378c01b2c49b02ee8d89dc5549c1da8080d7099fe0eb381f8032670e46621cd77f48e944553e2af43dd227f9c2f87e7f0227d3ed687c41e426800002c040b38baab12f1aec8b4679e60f721e0641139e0da28b3fb483c8893d952a3380adc51c8fae4e32c2dd8728098751a5f39a48676ad4639c001099deea69bf0e35800002383166c101405262498fbdd30dacade60c525350ebcc13b55cb663131c37ef30002373b397189d93fa820d92f194f693ea91414e03a368089bbc0513a3db642405800000000000000000000000000000000000000000000000c100000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.reverseBytes(BytesUtils.fromHexString("10eaeed096570c6d97c1b3bfb5edda550dcfc070fce0c0563afe78431e5971c0")))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params).get
    Seq(mcRef)
  }
}