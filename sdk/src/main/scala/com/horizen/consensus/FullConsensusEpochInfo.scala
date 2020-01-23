package com.horizen.consensus

import com.google.common.primitives.{Ints, Longs}
import com.horizen.utils.MerkleTree

case class ConsensusEpochInfo(epoch: ConsensusEpochNumber, merkleTree: MerkleTree, totalStake: Long)

case class StakeConsensusEpochInfo(rootHash: Array[Byte], totalStake: Long) {
  def toBytes: Array[Byte] = {
    rootHash ++ Longs.toByteArray(totalStake)
  }
}

object StakeConsensusEpochInfo {
  def fromBytes(bytes: Array[Byte]): StakeConsensusEpochInfo = {
    val rootHash = bytes.slice(0, rootHashLen)
    val totalStake: Long = Longs.fromByteArray(bytes.slice(rootHashLen, bytes.length))
    StakeConsensusEpochInfo(rootHash, totalStake)
  }
}

case class NonceConsensusEpochInfo(consensusNonce: ConsensusNonce) {
  def toBytes: Array[Byte] = {
    Ints.toByteArray(consensusNonce)
  }
}

object NonceConsensusEpochInfo {
  def fromBytes(bytes: Array[Byte]): NonceConsensusEpochInfo = {
    NonceConsensusEpochInfo(intToConsensusNonce(Ints.fromByteArray(bytes)))
  }
}

case class FullConsensusEpochInfo(stakeConsensusEpochInfo: StakeConsensusEpochInfo, nonceConsensusEpochInfo: NonceConsensusEpochInfo)
