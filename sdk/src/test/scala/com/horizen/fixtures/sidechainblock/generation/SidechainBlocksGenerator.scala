package com.horizen.fixtures.sidechainblock.generation

import java.math.BigInteger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.{Random, List => JList}

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer, SidechainBlock}
import com.horizen.box.data.ForgerBoxData
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.fixtures._
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition, VrfPublicKey}
import com.horizen.secret.PrivateKey25519
import com.horizen.storage.InMemoryStorageAdapter
import com.horizen.transaction.SidechainTransaction
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.{SidechainHistory, utils}
import com.horizen.utils._
import com.horizen.vrf._
import scorex.core.block.Block
import scorex.util.serialization.VLQByteBufferReader
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
    val consensusNonce: NonceConsensusEpochInfo = NonceConsensusEpochInfo(ConsensusNonce @@ nonce.toByteArray)
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
    val quietSlotsNumber = params.consensusSlotsInEpoch / 3
    val eligibleSlotsRange = (quietSlotsNumber to params.consensusSlotsInEpoch - quietSlotsNumber)

    new SidechainBlocksGenerator(
      params = params,
      forgersSet = newForgers,
      consensusDataStorage = consensusDataStorage,
      lastBlockId = newBlock.id,
      lastMainchainBlockId = newBlock.mainchainBlocks.lastOption.map(ref => byteArrayToWrapper(ref.header.hash)).getOrElse(lastMainchainBlockId),
      nextFreeSlotNumber = intToConsensusSlotNumber(usedSlot + 1), //in case if current slot is the last in epoch then next try to apply new block will finis epoch
      nextEpochNumber = nextEpochNumber,
      nextBlockNonceEpochId = nextBlockNonceEpochId,
      nextBlockStakeEpochId = nextBlockStakeEpochId,
      allEligibleVrfOutputs = if (eligibleSlotsRange.contains(usedSlot)) allEligibleVrfOutputs :+ newBlock.vrfProof.bytes() else allEligibleVrfOutputs,
      previousEpochId = previousEpochId,
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

    val forgingData: SidechainForgingData =
      if (generationRules.corruption.getOtherSidechainForgingData) {
        getIncorrectPossibleForger(possibleForger).forgingData
      }
      else {
        possibleForger.forgingData
      }

    val owner: PrivateKey25519 = forgingData.key

    val forgerBox: ForgerBox = generationRules.corruption.forgerBoxCorruptionRules.map(getIncorrectForgerBox(forgingData.forgerBox, _)).getOrElse(forgingData.forgerBox)

    val merklePath: MerklePath =
      if (generationRules.corruption.merklePathFromPreviousEpoch) {
        getCorruptedMerklePath(possibleForger)
      }
      else {
        possibleForger.merklePathInPrePreviousEpochOpt.get
      }

    val vrfProofInBlock: VrfProof = generationRules.corruption.forcedVrfProof.getOrElse(vrfProof)

    val tx: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = Seq(
      SidechainBlocksGenerator.txGen.generateRegularTransaction(rnd = rnd, transactionBaseTimeStamp = timestamp, inputTransactionsSize = 1, outputTransactionsSize = 1))


    val unsignedBlock: SidechainBlock = new SidechainBlock(
      parentId,
      timestamp,
      mainchainBlockReferences,
      tx,
      forgerBox,
      vrfProofInBlock,
      merklePath,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)), // empty signature
      companion
    )

    val signature = owner.sign(unsignedBlock.messageToSign)

    val generatedBlock = new SidechainBlock(
      parentId,
      timestamp,
      mainchainBlockReferences,
      tx,
      forgerBox,
      vrfProofInBlock,
      merklePath,
      signature,
      companion)

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
    if (finishedEpochId == previousEpochId) {throw new GenerationIsNoLongerPossible()} //no generated block during whole epoch

    val (newForgers, stakeConsensusEpochInfo) = forgersSet.finishCurrentEpoch()

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

    val previousNonce: Array[Byte] = consensusDataStorage.getNonceConsensusEpochInfo(previousEpochId)
      .getOrElse(throw new IllegalStateException (s"Failed to get nonce info for epoch id ${previousEpochId}"))
      .consensusNonce
    val currentEpochNumberBytes: Array[Byte] = Ints.toByteArray(nextEpochNumber)

    val allNonceBytesAsList = previousNonce :: currentEpochNumberBytes :: allEligibleVrfOutputs
    val newNonce = Utils.doubleSHA256Hash(Bytes.concat(allNonceBytesAsList:_*)) // :_* -- converts list to varargs

    NonceConsensusEpochInfo(byteArrayToConsensusNonce(newNonce))
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
    val networkParams = generateNetworkParams(genesisSidechainBlock, params, random)

    val nonceInfo =
      ConsensusDataProvider.calculateNonceForGenesisBlockInfo(SidechainHistory.calculateGenesisBlockInfo(genesisSidechainBlock, networkParams))
    val stakeInfo =
      StakeConsensusEpochInfo(genesisMerkleTree.rootHash(), possibleForger.forgingData.forgerBox.value())
    val consensusDataStorage = createConsensusDataStorage(genesisSidechainBlock.id, nonceInfo, stakeInfo)


    val genesisGenerator = new SidechainBlocksGenerator(
      params = networkParams,
      forgersSet = new PossibleForgersSet(Set(possibleForger)),
      consensusDataStorage = consensusDataStorage,
      lastBlockId = genesisSidechainBlock.id,
      lastMainchainBlockId = genesisSidechainBlock.mainchainBlocks.last.header.hash,
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

    val owner: PrivateKey25519 = forgingData.key
    val forgerBox = forgingData.forgerBox

    val unsignedBlock: SidechainBlock = new SidechainBlock(
      parentId,
      timestamp,
      mainchainBlockReferences.asScala,
      Seq(),
      forgerBox,
      vrfProof,
      merklePath,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)), // empty signature
      companion
    )

    val signature = owner.sign(unsignedBlock.messageToSign)

    new SidechainBlock(
      parentId,
      timestamp,
      mainchainBlockReferences.asScala,
      Seq(),
      forgerBox,
      vrfProof,
      merklePath,
      signature,
      companion)
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
      override val mainchainCreationBlockHeight: Int = genesisSidechainBlock.mainchainBlocks.size + mainchainHeight
      override val sidechainGenesisBlockTimestamp: Block.Timestamp = genesisSidechainBlock.timestamp
      override val withdrawalEpochLength: Int = params.withdrawalEpochLength
      override val consensusSecondsInSlot: Int = params.consensusSecondsInSlot
      override val consensusSlotsInEpoch: Int = params.consensusSlotsInEpoch
    }
  }

  private val getHardcodedMainchainBlockReferencesWithSidechainCreation: JList[MainchainBlockReference] = {
    val mcBlocksSerializer: ListSerializer[MainchainBlockReference] = new ListSerializer[MainchainBlockReference](
      MainchainBlockReferenceSerializer, SidechainBlock.MAX_MC_BLOCKS_NUMBER)

    /* shall we somehow check time leap between mainchain block time creation and genesis sidechain block creation times? */
    /* @TODO sidechain creation mainchain block shall also be generated, not hardcoded */
    val mcBlockBytes: Array[Byte] = Array[Byte](2,-102,9,-30,2,0,0,0,32,70,-118,-119,28,-34,114,-6,73,-76,-79,-100,-63,-35,-82,-98,97,-126,-24,-71,64,-18,69,36,-124,70,-25,-40,-41,43,26,-47,13,4,15,-48,54,-76,57,4,86,-71,49,-76,-5,95,-19,-95,-119,85,-48,-8,-34,88,61,-25,73,78,4,-107,-106,-120,43,74,107,73,56,59,77,-28,23,-101,-43,-77,48,-112,27,67,-16,-43,-8,-84,77,65,3,-22,-56,-105,-73,-16,60,108,100,71,11,2,108,-92,-126,-46,93,3,15,15,32,8,0,-99,105,-97,-19,-67,84,27,-50,35,115,115,-94,84,115,11,-34,67,41,81,41,97,-88,-7,-32,125,91,93,-117,0,0,36,16,36,-8,-3,81,-117,-47,101,-83,50,97,-71,62,102,118,54,-17,-72,30,87,11,-112,4,-60,37,107,-51,69,-33,-91,-3,53,30,1,97,-66,-84,5,-97,68,-90,108,94,-1,18,82,-126,121,-115,-20,-38,-37,42,12,-75,-97,99,117,124,28,-68,-74,56,45,-78,-73,-28,125,105,9,0,0,0,0,93,-46,-126,-92,0,0,1,42,6,-110,1,-38,1,-38,1,3,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-24,3,0,0,114,-75,81,78,20,-84,72,3,32,5,-38,-52,61,-49,54,-36,59,26,106,-13,-108,42,-2,-74,72,76,-75,78,110,21,-30,65,0,0,0,0,1,0,116,59,-92,11,0,0,0,-65,-56,-17,42,-3,-63,-57,-125,-128,68,-30,122,-122,-95,-26,-58,115,18,16,-14,-34,-64,54,70,63,-85,-55,-98,-67,-101,-123,-75,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,114,-75,81,78,20,-84,72,3,32,5,-38,-52,61,-49,54,-36,59,26,106,-13,-108,42,-2,-74,72,76,-75,78,110,21,-30,65,0,0,0,1,1,0,116,59,-92,11,0,0,0,-107,118,-102,-74,100,-90,81,117,68,119,-34,87,-37,-123,-56,46,-28,-63,74,-84,-92,-122,-114,-122,53,-38,-117,-3,-87,-62,23,-111,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,114,-75,81,78,20,-84,72,3,32,5,-38,-52,61,-49,54,-36,59,26,106,-13,-108,42,-2,-74,72,76,-75,78,110,21,-30,65,0,0,0,2,-128,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,-97,68,-90,108,94,-1,18,82,-126,121,-115,-20,-38,-37,42,12,-75,-97,99,117,124,28,-68,-74,56,45,-78,-73,-28,125,105,9)
    val reader: VLQByteBufferReader = new VLQByteBufferReader(ByteBuffer.wrap(mcBlockBytes))
    mcBlocksSerializer.parse(reader)
  }
}