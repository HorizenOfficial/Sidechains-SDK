package io.horizen.consensus

import com.google.common.primitives.{Ints, Longs}
import io.horizen.block.SidechainBlockHeaderBase
import io.horizen.chain.SidechainBlockInfo
import io.horizen.fork.ForkManager
import io.horizen.params.{NetworkParams, NetworkParamsUtils}
import io.horizen.storage.SidechainBlockInfoProvider
import io.horizen.utils.{ByteArrayWrapper, LruCache, TimeToEpochUtils, Utils}
import io.horizen.vrf.VrfOutput
import sparkz.core.block.Block
import sparkz.core.block.Block.Timestamp
import sparkz.util.{ModifierId, SparkzLogging}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.compat.java8.OptionConverters._

trait ConsensusDataProvider {
    this: NetworkParamsUtils
    with SparkzLogging {
    val storage: SidechainBlockInfoProvider
    val consensusDataStorage: ConsensusDataStorage
    val params: NetworkParams
  } =>

  def getStakeConsensusEpochInfo(blockTimestamp: Block.Timestamp, parentBlockId: ModifierId): Option[StakeConsensusEpochInfo] = {
    consensusDataStorage.getStakeConsensusEpochInfo(
      blockIdToEpochId(getLastBlockIdOfPrePreviousEpochs(blockTimestamp, parentBlockId)))
  }

  def getLastBlockIdOfPrePreviousEpochs(blockTimestamp: Block.Timestamp, parentBlockId: ModifierId): ModifierId = {
    if (isGenesisBlock(blockTimestamp, parentBlockId)) {
      params.sidechainGenesisBlockId
    }
    else {
      val lastBlockInPreviousEpoch = getLastBlockInPreviousConsensusEpoch(blockTimestamp, parentBlockId)
      val blockInPrePreviousEpoch = storage.blockInfoById(lastBlockInPreviousEpoch).lastBlockInPreviousConsensusEpoch
      blockInPrePreviousEpoch
    }
  }

  def getOrCalculateNonceConsensusEpochInfo(blockTimestamp: Block.Timestamp, parentBlockId: ModifierId): NonceConsensusEpochInfo = {
    if (isGenesisBlock(blockTimestamp, parentBlockId)) {
      consensusDataStorage.getNonceConsensusEpochInfo(blockIdToEpochId(params.sidechainGenesisBlockId))
        .getOrElse(ConsensusDataProvider.calculateNonceForGenesisBlock(params))
    }
    else {
      val lastBlockInPreviousEpoch = getLastBlockInPreviousConsensusEpoch(blockTimestamp, parentBlockId)
      consensusDataStorage.getNonceConsensusEpochInfo(blockIdToEpochId(lastBlockInPreviousEpoch))
        .getOrElse(calculateNonceForEpoch(blockIdToEpochId(getLastBlockInPreviousConsensusEpoch(blockTimestamp, parentBlockId))))
    }
  }

  //Added check of timestamp, otherwise malicious actor could create fake genesis block
  private def isGenesisBlock(blockTimestamp: Block.Timestamp, parentBlockId: ModifierId): Boolean = {
    blockTimestamp == params.sidechainGenesisBlockTimestamp && parentBlockId == params.sidechainGenesisBlockParentId
  }

  def getFullConsensusEpochInfoForBlock(blockTimestamp: Timestamp, parentBlockId: ModifierId): FullConsensusEpochInfo = {
    val stakeConsensusEpochInfo = getStakeConsensusEpochInfo(blockTimestamp, parentBlockId)
      .getOrElse(throw new IllegalStateException(s"Stake was not defined for block ${parentBlockId}"))
    val nonceConsensusEpochInfo = getOrCalculateNonceConsensusEpochInfo(blockTimestamp, parentBlockId)
    FullConsensusEpochInfo(stakeConsensusEpochInfo, nonceConsensusEpochInfo)
  }

  //Check possible stack buffer overflow situations
  def calculateNonceForEpoch(epochId: ConsensusEpochId): NonceConsensusEpochInfo = {
    val lastBlockIdInEpoch: ModifierId = lastBlockIdInEpochId(epochId)
    val lastBlockInfoInEpoch: SidechainBlockInfo = storage.blockInfoById(lastBlockIdInEpoch)

    if (isGenesisBlock(lastBlockIdInEpoch)) {
      ConsensusDataProvider.calculateNonceForGenesisBlock(params)
    }
    else {
      calculateNonceForNonGenesisEpoch(lastBlockIdInEpoch, lastBlockInfoInEpoch, Seq())
    }
  }

  private[horizen] def calculateNonceForNonGenesisEpoch(lastBlockIdInEpoch: ModifierId,
                                                        lastBlockInfoInEpoch: SidechainBlockInfo,
                                                        initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)]): NonceConsensusEpochInfo = {
    // Hash function is applied to the concatenation of VRF values that are inserted into each block, using values from
    // all blocks up to and including the middle ≈ 8k slots of an epoch that lasts approximately 24k slots in entirety.
    // (The “quiet” periods before and after this central block of slots that sets the nonce will
    // ensure that the stake distribution, determined at the beginning of the epoch, is stable, and likewise
    // that the nonce is stable before the next epoch begins.)
    // https://eprint.iacr.org/2017/573.pdf p.23
    val quietSlotsNumber = params.consensusSlotsInEpoch / 3
    val eligibleSlotsRangeStart = quietSlotsNumber + 1
    val eligibleSlotsRangeEnd = params.consensusSlotsInEpoch - quietSlotsNumber - 1

    val nonceMessageDigest: MessageDigest = createNonceMessageDigest(lastBlockIdInEpoch, lastBlockInfoInEpoch, eligibleSlotsRangeStart, eligibleSlotsRangeEnd, initialNonceData)

    //According to https://eprint.iacr.org/2017/573.pdf p.26
    val previousEpoch: ConsensusEpochId = blockIdToEpochId(lastBlockInfoInEpoch.lastBlockInPreviousConsensusEpoch)
    val previousNonce = consensusDataStorage.getNonceConsensusEpochInfo(previousEpoch).getOrElse(calculateNonceForEpoch(previousEpoch)).bytes
    nonceMessageDigest.update(previousNonce)

    val currentEpoch = TimeToEpochUtils.timeStampToEpochNumber(params, lastBlockInfoInEpoch.timestamp)
    val currentEpochNumberBytes = Ints.toByteArray(currentEpoch)
    nonceMessageDigest.update(currentEpochNumberBytes)


    // Calculate hash and return the first 8(32 after the fork) bytes of it.
    // Note: first epoch nonce is a timestamp of genesis block - 8 bytes.
    // And moreover `buildVrfMessage` expect for the concatenated value with the value that fits FieldElement.
    // TODO: think about the usage of VRF inside the circuit.
    val nonceLength = ForkManager.getSidechainFork(currentEpoch).nonceLength
    val resultHash = nonceMessageDigest.digest()
    NonceConsensusEpochInfo(byteArrayToConsensusNonce(resultHash.slice(0, nonceLength)))
  }

  //Message digest for nonce calculation is done in reverse order, i.e. from last eligible slot to first eligible slot
  private def createNonceMessageDigest(initialBlockId: ModifierId,
                                       initialBlockInfo: SidechainBlockInfo,
                                       eligibleSlotsRangeStart: Int,
                                       eligibleSlotsRangeEnd: Int,
                                       initialNonceData: Seq[(VrfOutput, ConsensusSlotNumber)]): MessageDigest = {
    require(!isGenesisBlock(initialBlockId)) //genesis nonce calculation shall be done in other way

    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    // Update digest with accumulated values first, that are stored in reverse order as well.
    for((vrfOutput, slotNumber) <- initialNonceData) {
      if (slotNumber >= eligibleSlotsRangeStart && slotNumber <= eligibleSlotsRangeEnd)
        digest.update(vrfOutput.bytes())
    }

    var nextBlockId = initialBlockId
    var nextBlockInfo = initialBlockInfo
    var nextBlockSlot = TimeToEpochUtils.timeStampToSlotNumber(params, initialBlockInfo.timestamp)
    while (nextBlockId != initialBlockInfo.lastBlockInPreviousConsensusEpoch && nextBlockSlot >= eligibleSlotsRangeStart) {
      if (eligibleSlotsRangeEnd >= nextBlockSlot) {
        digest.update(nextBlockInfo.vrfOutputOpt.getOrElse(throw new IllegalStateException("Try to calculate nonce by using block with incorrect Vrf proof")).bytes())
      }
      nextBlockId = nextBlockInfo.parentId
      nextBlockInfo = storage.blockInfoById(nextBlockId)
      nextBlockSlot = TimeToEpochUtils.timeStampToSlotNumber(params, nextBlockInfo.timestamp)
    }

    digest
  }

  def getLastBlockInPreviousConsensusEpoch(blockTimestamp: Block.Timestamp, parentId: ModifierId): ModifierId = {
    val parentBlockInfo: SidechainBlockInfo = storage.blockInfoById(parentId)
    val parentBlockEpochNumber: ConsensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, parentBlockInfo.timestamp)

    val currentBlockEpochNumber: ConsensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, blockTimestamp)

    Ints.compare(parentBlockEpochNumber, currentBlockEpochNumber) match {
      case -1 => parentId   //parentBlockEpochNumber < currentBlockEpochNumber
      case  0 => parentBlockInfo.lastBlockInPreviousConsensusEpoch   // parentBlockEpochNumber == currentBlockEpochNumber
      case  _ => throw new IllegalArgumentException("Parent of block shall not be generated before block")   // parentBlockEpochNumber > currentBlockEpochNumber
    }
  }

  def getVrfOutput(blockHeader: SidechainBlockHeaderBase, nonceConsensusEpochInfo: NonceConsensusEpochInfo): Option[VrfOutput] = {
    //try to get cached value, if no in cache then calculate
    val key = ConsensusDataProvider.blockIdAndNonceToKey(blockHeader.id, nonceConsensusEpochInfo)
    val cachedValue = ConsensusDataProvider.vrfOutputCache.get(key)
    if (cachedValue == null) {
      calculateVrfOutput(blockHeader, nonceConsensusEpochInfo).map{vrfOutput =>
        ConsensusDataProvider.vrfOutputCache.put(key, vrfOutput)
        vrfOutput
      }
    }
    else {
      Some(cachedValue)
    }
  }

  private def calculateVrfOutput(blockHeader: SidechainBlockHeaderBase, nonceConsensusEpochInfo: NonceConsensusEpochInfo): Option[VrfOutput] = {
    val slotNumber: ConsensusSlotNumber = TimeToEpochUtils.timeStampToSlotNumber(params, blockHeader.timestamp)
    val vrfMessage: VrfMessage = buildVrfMessage(slotNumber, nonceConsensusEpochInfo)

    blockHeader.vrfProof.proofToVrfOutput(blockHeader.forgingStakeInfo.vrfPublicKey, vrfMessage).asScala
  }
}

object ConsensusDataProvider {
  private def blockIdAndNonceToKey(blockId: ModifierId, nonceConsensusEpochInfo: NonceConsensusEpochInfo): ByteArrayWrapper = {
    new ByteArrayWrapper(Utils.doubleSHA256HashOfConcatenation(blockId.getBytes(StandardCharsets.UTF_8), nonceConsensusEpochInfo.consensusNonce))
  }

  private val vrfOutputCache: LruCache[ByteArrayWrapper, VrfOutput] = new LruCache[ByteArrayWrapper, VrfOutput](32) //check cache size

  def calculateNonceForGenesisBlock(params: NetworkParams): NonceConsensusEpochInfo = {
    NonceConsensusEpochInfo(ConsensusNonce(Longs.toByteArray(params.sidechainGenesisBlockTimestamp)))
  }
}
