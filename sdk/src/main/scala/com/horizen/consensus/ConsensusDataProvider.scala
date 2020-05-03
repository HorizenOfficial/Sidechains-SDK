package com.horizen.consensus

import java.security.MessageDigest

import com.google.common.primitives.{Ints, Longs}
import com.horizen.block.SidechainBlockHeader
import com.horizen.chain.SidechainBlockInfo
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.storage.SidechainBlockInfoProvider
import com.horizen.utils.LruCache
import com.horizen.vrf.VrfProofHash
import scorex.core.block.Block
import scorex.core.block.Block.Timestamp
import scorex.util.{ModifierId, ScorexLogging}

trait ConsensusDataProvider {
  this: TimeToEpochSlotConverter
    with NetworkParamsUtils
    with ScorexLogging {
    val storage: SidechainBlockInfoProvider
    val consensusDataStorage: ConsensusDataStorage
    val params: NetworkParams
  } =>

  def getStakeConsensusEpochInfo(blockTimestamp: Block.Timestamp, parentBlockId: ModifierId): Option[StakeConsensusEpochInfo] = {
    if (isGenesisBlock(blockTimestamp, parentBlockId)) {
      consensusDataStorage.getStakeConsensusEpochInfo(blockIdToEpochId(params.sidechainGenesisBlockId))
    }
    else {
      val lastBlockInPreviousEpoch = getLastBlockInPreviousConsensusEpoch(blockTimestamp, parentBlockId)
      val blockInPrePreviousEpoch = storage.blockInfoById(lastBlockInPreviousEpoch).lastBlockInPreviousConsensusEpoch
      consensusDataStorage.getStakeConsensusEpochInfo(blockIdToEpochId(blockInPrePreviousEpoch))
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
      calculateNonceForNonGenesisEpoch(lastBlockIdInEpoch, lastBlockInfoInEpoch)
    }
  }

  private def calculateNonceForNonGenesisEpoch(lastBlockIdInEpoch: ModifierId, lastBlockInfoInEpoch: SidechainBlockInfo): NonceConsensusEpochInfo = {
    // Hash function is applied to the concatenation of VRF values that are inserted into each block, using values from
    // all blocks up to and including the middle ≈ 8k slots of an epoch that lasts approximately 24k slots in entirety.
    // (The “quiet” periods before and after this central block of slots that sets the nonce will
    // ensure that the stake distribution, determined at the beginning of the epoch, is stable, and likewise
    // that the nonce is stable before the next epoch begins.)
    // https://eprint.iacr.org/2017/573.pdf p.23
    val quietSlotsNumber = params.consensusSlotsInEpoch / 3
    val eligibleSlotsRange = ((quietSlotsNumber + 1) until (params.consensusSlotsInEpoch - quietSlotsNumber))

    val nonceMessageDigest: MessageDigest = createNonceMessageDigest(lastBlockIdInEpoch, lastBlockInfoInEpoch, eligibleSlotsRange)

    //According to https://eprint.iacr.org/2017/573.pdf p.26
    val previousEpoch: ConsensusEpochId = blockIdToEpochId(lastBlockInfoInEpoch.lastBlockInPreviousConsensusEpoch)
    val previousNonce = consensusDataStorage.getNonceConsensusEpochInfo(previousEpoch).getOrElse(calculateNonceForEpoch(previousEpoch)).bytes
    nonceMessageDigest.update(previousNonce)

    val currentEpochNumberBytes = Ints.toByteArray(timeStampToEpochNumber(lastBlockInfoInEpoch.timestamp))
    nonceMessageDigest.update(currentEpochNumberBytes)

    NonceConsensusEpochInfo(byteArrayToConsensusNonce(nonceMessageDigest.digest()))
  }

  private def createNonceMessageDigest(initialBlockId: ModifierId, initialBlockInfo: SidechainBlockInfo, eligibleSlotsRange: Range): MessageDigest = {
    require(!isGenesisBlock(initialBlockId)) //genesis nonce calculation shall be done in other way

    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    var nextBlockId = initialBlockId
    var nextBlockInfo = initialBlockInfo
    var nextBlockSlot = timeStampToSlotNumber(initialBlockInfo.timestamp)
    while (nextBlockId != initialBlockInfo.lastBlockInPreviousConsensusEpoch && nextBlockSlot >= eligibleSlotsRange.start) {
      if (eligibleSlotsRange.contains(nextBlockSlot)) {
        digest.update(nextBlockInfo.vrfProofHash.bytes())
      }
      nextBlockId = nextBlockInfo.parentId
      nextBlockInfo = storage.blockInfoById(nextBlockId)
      nextBlockSlot = timeStampToSlotNumber(nextBlockInfo.timestamp)
    }

    digest
  }

  def getLastBlockInPreviousConsensusEpoch(blockTimestamp: Block.Timestamp, parentId: ModifierId): ModifierId = {
    val parentBlockInfo: SidechainBlockInfo = storage.blockInfoById(parentId)
    val parentBlockEpochNumber: ConsensusEpochNumber = timeStampToEpochNumber(parentBlockInfo.timestamp)

    val currentBlockEpochNumber: ConsensusEpochNumber = timeStampToEpochNumber(blockTimestamp)

    Ints.compare(parentBlockEpochNumber, currentBlockEpochNumber) match {
      case -1 => parentId   //parentBlockEpochNumber < currentBlockEpochNumber
      case  0 => parentBlockInfo.lastBlockInPreviousConsensusEpoch   // parentBlockEpochNumber == currentBlockEpochNumber
      case  _ => throw new IllegalArgumentException("Parent of block shall not be generated before block")   // parentBlockEpochNumber > currentBlockEpochNumber
    }
  }

  def getVrfProofHashForBlockHeader(block: SidechainBlockHeader): VrfProofHash = {
    //try to get cached value, if no in cache then calculate
    val cachedValue = ConsensusDataProvider.vrfProofHashCache.get(block.id)
    if (cachedValue == null) {
      val calculatedVrfProofHash = calculateVrfProofHashForBlockHeader(block)
      ConsensusDataProvider.vrfProofHashCache.put(block.id, calculatedVrfProofHash)
      calculatedVrfProofHash
    }
    else {
      cachedValue
    }
  }

  def calculateVrfProofHashForBlockHeader(block: SidechainBlockHeader): VrfProofHash = {
    val nonceConsensusEpochInfo = getOrCalculateNonceConsensusEpochInfo(block.timestamp, block.parentId)
    val slotNumber = timeStampToSlotNumber(block.timestamp)

    val vrfMessage: VrfMessage = buildVrfMessage(slotNumber, nonceConsensusEpochInfo)

    //@TO DISCUSS
    //We can also as well verify correctness of VrfProof in the block here, and for example return none in case if VrfProof is not correct,
    //it will simplify consensus validator due vrf message shall not be constructed second time, and, more important,
    //creation VrfProof will be done only once, but not twice (during verify VrfProof itself and during VrfProofHash calculation itself)
    //from other point of view:
    //  we will verify consensus data implicitly even without any consensus validator;
    //  all tests of Sidechain history will require correct VrfProof (otherwise SidechainBlockInfo will not be built correctly)
    val calculatedVrfProofHash: VrfProofHash = block.vrfProof.proofToVRFHash(block.forgerBox.vrfPubKey(), vrfMessage)
    calculatedVrfProofHash
  }
}

object ConsensusDataProvider {
  private val vrfProofHashCache: LruCache[ModifierId, VrfProofHash] = new LruCache[ModifierId, VrfProofHash](32) //check cache size

  def calculateNonceForGenesisBlock(params: NetworkParams): NonceConsensusEpochInfo = {
    NonceConsensusEpochInfo(ConsensusNonce(Longs.toByteArray(params.sidechainGenesisBlockTimestamp)))
  }
}
