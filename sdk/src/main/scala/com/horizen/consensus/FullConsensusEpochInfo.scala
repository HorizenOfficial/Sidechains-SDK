package com.horizen.consensus

import com.google.common.primitives.{Ints, Longs}
import com.horizen.utils._

case class ConsensusEpochInfo(epoch: ConsensusEpochNumber, forgersBoxIds: MerkleTree, forgersStake: Long)

case class StakeConsensusEpochInfo(rootHash: ByteArrayWrapper, totalStake: Long) {
  def toBytes: Array[Byte] = {
    rootHash.data ++ Longs.toByteArray(totalStake)
  }

  override def toString: String = {
    s"StakeConsensusEpochInfo(rootHash: ${rootHash}, totalStake: ${totalStake})"
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
