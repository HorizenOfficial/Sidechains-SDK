package com.horizen.consensus

import com.horizen.storage.Storage
import scorex.util.ModifierId

class ConsensusDataStorage(consensusEpochInfoStorage: Storage) {
  def addStakeConsensusEpochInfo(lastBlockInEpoch: ModifierId, epochInfo: StakeConsensusEpochInfo) = ???
  def getStakeConsensusEpochInfo(lastBlockInEpoch: ModifierId): Option[StakeConsensusEpochInfo] = ???

  def addNonceConsensusEpochInfo(lastBlockInEpoch: ModifierId, consensusNonce: NonceConsensusEpochInfo) = ???
  def getNonceConsensusEpochInfo(lastBlockInEpoch: ModifierId): Option[NonceConsensusEpochInfo] = ???

  def getLastBlocksInEpoch(): Seq[ModifierId] = ???
}
