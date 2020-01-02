package com.horizen

import scorex.core.block.Block
import supertagged.TaggedType

package object consensus {
  val rootHashLen: Int = 32

  case class StakeConsensusEpochInfo(rootHash: Array[Byte], totalStake: Long)
  case class NonceConsensusEpochInfo(consensusNonce: ConsensusNonce)

  case class FullConsensusEpochInfo(stakeConsensusEpochInfo: StakeConsensusEpochInfo, nonceConsensusEpochInfo: NonceConsensusEpochInfo)

  object ConsensusEpochNumber extends TaggedType[Int]
  type ConsensusEpochNumber = ConsensusEpochNumber.Type
  def intToConsensusEpochNumber(consensusEpochNumber: Int): ConsensusEpochNumber = ConsensusEpochNumber @@ consensusEpochNumber

  object ConsensusNonce extends TaggedType[Int]
  type ConsensusNonce = ConsensusNonce.Type
  def intToConsensusNonce(consensusNonce: Int): ConsensusNonce = ConsensusNonce @@ consensusNonce


  def timeStampToEpochNumber(timestamp: Block.Timestamp): ConsensusEpochNumber = ???
}
