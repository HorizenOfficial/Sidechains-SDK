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

    val blockSignProposition: PublicKey25519Proposition = if (forgerBoxCorruptionRules.blockSignPropositionChanged) {
      val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(rnd.nextLong().toString.getBytes)
      val newBlockSignProposition: PublicKey25519Proposition = new PublicKey25519Proposition(propositionKeyPair.getValue)
      newBlockSignProposition
    }
    else {
      initialForgerBox.blockSignProposition()
    }

    val vrfPubKey: VrfPublicKey = if (forgerBoxCorruptionRules.vrfPubKeyChanged) {
      var corrupted: VrfPublicKey = null

      do {
        val corruptedVrfPublicKeyBytes =
          CryptoLibProvider.vrfFunctions.generatePublicAndSecretKeys(rnd.nextLong().toString.getBytes).get(VrfFunctions.KeyType.PUBLIC)
        corrupted = new VrfPublicKey(corruptedVrfPublicKeyBytes)
        println(s"corrupt VRF public key ${BytesUtils.toHexString(initialForgerBox.vrfPubKey().bytes)} by ${BytesUtils.toHexString(corrupted.bytes)}")
      } while (corrupted.bytes.deep == initialForgerBox.vrfPubKey().bytes.deep)
      corrupted
    }
    else {
      initialForgerBox.vrfPubKey()
    }

    new ForgerBoxData(proposition, value, blockSignProposition, vrfPubKey).getBox(nonce)
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

    val genesisMerkleTree: MerkleTree = buildGenesisMerkleTree(genesisSidechainForgingData.forgerBox)
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
      StakeConsensusEpochInfo(genesisMerkleTree.rootHash(), possibleForger.forgingData.forgerBox.value())
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
    SidechainForgingData(key, forgerBox, vrfSecretKey)
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
      override val parentHashOfGenesisMainchainBlock: Array[Byte] = params.parentHashOfGenesisMainchainBlock
      override val genesisPoWData: Seq[(Int, Int)] = params.genesisPoWData
      override val mainchainCreationBlockHeight: Int = genesisSidechainBlock.mainchainBlockReferencesData.size + mainchainHeight
      override val sidechainGenesisBlockTimestamp: Block.Timestamp = genesisSidechainBlock.timestamp
      override val withdrawalEpochLength: Int = params.withdrawalEpochLength
      override val consensusSecondsInSlot: Int = params.consensusSecondsInSlot
      override val consensusSlotsInEpoch: Int = params.consensusSlotsInEpoch
      override val signersPublicKeys: Seq[SchnorrProposition] = Seq()
      override val signersThreshold: Int = 0
      override val provingKeyFilePath: String = ""
      override val verificationKeyFilePath: String = ""
    }
  }

  private val getHardcodedMainchainBlockReferencesWithSidechainCreation: Seq[MainchainBlockReference] = {
    /* shall we somehow check time leap between mainchain block time creation and genesis sidechain block creation times? */
    /* @TODO sidechain creation mainchain block shall also be generated, not hardcoded */
    // Genesis MC block hex created in regtest from MC branch beta_v1 on 25.05.2020
    val mcBlockHex: String = "030000002905b49f5bcd0b8e29f91170467e6900a82f4ec9a51e6d6ff12bfd4156b6110191fdc3a11d8a7014e3cf25197a56d3e0199ec92fc70cd0e91d49ff7c270fb435ae628657d19a111490839a6a66c4dd09a422c94a2a486e411d0fe54e858eb353afd4cb5e030f0f2017006d83d7e2ebd3c45ad11831e620d5f8b7b5c4f4c912cdc9f7c33a5daa0000240975303f82ac016f1418a4d3f6151fc753c11ed498d635ee2b1fc22a2e4e370306b14ee10201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dd000101ffffffff04c21bb42c000000001976a914c5be5df5a7b85620af7324891f4107adf2e05a7888ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff0523e3e0ffb44e0084187db48ad78b854b034271e0d52de1db710455875bc2d9d5000000006b483045022100b7146011794140b618cd99b026f2bdb4b7a3f8b5850748583714793a5e6dd8ed02203cc86851619f0219a51b24df0120ed9efa8fb708ac0f27a178789af0304a63f70121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff3e3b7f63715ddd6451de91104c503c5ce0400151eed36e24f9d4fbfda7ed77ab000000006b483045022100a83e9e5e2736909805015058b7b8f1926f22d879e674f08f2bfd798b441bbcff022055eb00442e872fd03fb3399c48b4f5b6ac72d2d42ad098b9f51fa1b0696c8c2c0121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff714dcef46d1b8944f8562cd8fc528c15474938f2f88cd2a94964c4d17970379c000000006a473044022002eb0b187e5335c946699fa66177bfa8bfc6e426fc0606bc304e9bb11e2a66b902206dca231c7cb52289ef2bd0333d4ebc1324c03587e9d6d60176aff20f44425acc0121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffffff4646d63099c44b1e5be6dfc2ab8cb3f2db6bd55f49eade7b25dc138a55417d000000006a4730440220766b9fc27888c9ebb3f54d5e2c27b6cb93789c1d8682052944d567c6bacff7ab02201a0e220d86a33f9f83ded0dd166b0152a3180d2c563eb9946ee24dbdb2768fb80121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff2b02c1d304db30119d69fa07751b386ee2e9019aad8cc0abdda217ed674244bc000000006b483045022100ff309d03234ca8afe7e5a79e406590746c47043736e44b44ed21d15cc302999602204712d0dda3fa5f65f834acbfdc7e76f97878a3b732a74623f4efacff915786680121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff013e70d21a000000003c76a91457aa4f67e7d37b86d7c75b8bde5c1124a64dc30d88ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010100000000000000000000000000000000000000000000000000000000000000e803000000f2052a01000000ddea6ff950e5efff4da66306155bcc1219bf2a3776804458c4fc166cd75f46a4c13c158597376bccf305f7a363f7ba2b04e91e393eaedaa95a5e6284cd3607b58014a631e9e955dbdb2352a5b679e2d1269abeb59f81ef815d93280d8b46630b625f64e7c83e366c2bbd483ed0134322168ae8a6e1e0dd3d7f99ea038fe3df00003c29f7bdb83189a56e3ede091575f278b00d2754a842e0df517b8067304eada8b4bd31b3a51b4aa5365ebd35496102e8fb131684cb539d795f3b2624f84e0d82dc810075618c7b8259280ce14d8b03681ecc01fc69bb7bf0985b98140d0601000000d200000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001"))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params).get
    Seq(mcRef)
  }
}