package com.horizen.consensus

import java.util.{ArrayList => JArrayList}

import com.horizen.storage.Storage
import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

import scala.collection.mutable
import scala.compat.java8.OptionConverters._
import scala.util.Random

class ConsensusDataStorage(consensusEpochInfoStorage: Storage) extends ScorexLogging {
  def addStakeConsensusEpochInfo(epochId: ConsensusEpochId, epochInfo: StakeConsensusEpochInfo): Unit = {
    log.info(s"Storage with id:${this.hashCode()} -- Add stake to consensus data storage: for epochId ${epochId} stake: ${epochInfo.totalStake}, root hash ${epochInfo.rootHash}")

    require(!getStakeConsensusEpochInfo(epochId).exists(_ != epochInfo)) //StakeConsensusEpochInfo shall NOT be changed, check trying of overwriting value
    addEntry(stakeEpochInfoKey(epochId), epochInfo.toBytes)
  }

  def getStakeConsensusEpochInfo(epochId: ConsensusEpochId): Option[StakeConsensusEpochInfo] = {
    consensusEpochInfoStorage
      .get(stakeEpochInfoKey(epochId))
      .asScala
      .map(byteArray => StakeConsensusEpochInfo.fromBytes(byteArray.data))
  }

  def addNonceConsensusEpochInfo(epochId: ConsensusEpochId, consensusNonce: NonceConsensusEpochInfo): Unit = {
    log.info(s"Storage with id:${this.hashCode()} -- Add nonce to consensus data storage: for epochId ${epochId} nonce: ${consensusNonce.consensusNonce}")

    require(!getNonceConsensusEpochInfo(epochId).exists(_ != consensusNonce)) //NonceConsensusEpochInfo shall NOT be changed, check trying of overwriting value
    addEntry(nonceEpochInfoKey(epochId), consensusNonce.toBytes)
  }

  def getNonceConsensusEpochInfo(epochId: ConsensusEpochId): Option[NonceConsensusEpochInfo] = {
    consensusEpochInfoStorage
      .get(nonceEpochInfoKey(epochId))
      .asScala
      .map(byteArray => NonceConsensusEpochInfo.fromBytes(byteArray.data))
  }

  def getNonceConsensusEpochInfoOrElseUpdate(epochId: ConsensusEpochId, supplier: () => NonceConsensusEpochInfo): NonceConsensusEpochInfo = {
    getNonceConsensusEpochInfo(epochId).getOrElse{
      val newNonceInfo = supplier()
      addNonceConsensusEpochInfo(epochId, newNonceInfo)
      newNonceInfo
    }
  }

  private def nextVersion: Array[Byte] = {
    val version = new Array[Byte](32)
    Random.nextBytes(version)
    version
  }

  private val stakeEpochInfoKeyCache: mutable.HashMap[ConsensusEpochId, ByteArrayWrapper] = mutable.HashMap()
  private def stakeEpochInfoKey(epochId: ConsensusEpochId): ByteArrayWrapper = stakeEpochInfoKeyCache.getOrElseUpdate(epochId, new ByteArrayWrapper(Blake2b256(s"stake$epochId")))

  private val nonceEpochInfoKeyCache: mutable.HashMap[ConsensusEpochId, ByteArrayWrapper] = mutable.HashMap()
  private def nonceEpochInfoKey(epochId: ConsensusEpochId): ByteArrayWrapper = nonceEpochInfoKeyCache.getOrElseUpdate(epochId, new ByteArrayWrapper(Blake2b256(s"nonce$epochId")))

  private def addEntry(key: ByteArrayWrapper, value: Array[Byte]): Unit = {
    val listForUpdate = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    val addedData = new JPair(key, new ByteArrayWrapper(value))
    listForUpdate.add(addedData)
    consensusEpochInfoStorage.update(new ByteArrayWrapper(nextVersion), listForUpdate, java.util.Collections.emptyList())
  }
}
