package com.horizen.consensus

import java.util.{ArrayList => JArrayList}

import com.horizen.storage.Storage
import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}
import scorex.crypto.hash.Blake2b256

import scala.compat.java8.OptionConverters._
import scala.util.Random

class ConsensusDataStorage(consensusEpochInfoStorage: Storage) {
  def addStakeConsensusEpochInfo(epochId: ConsensusEpochId, epochInfo: StakeConsensusEpochInfo): Unit = {
    println(s"${this.hashCode()} Add stake to consensus data storage: for epochId ${epochId} stake: ${epochInfo.totalStake}, root hash ${epochInfo.rootHash}")

    require(!getStakeConsensusEpochInfo(epochId).exists(_ != epochInfo)) //StakeConsensusEpochInfo shall NOT be changed, check trying to overwrite it value
    addEntry(stakeEpochInfoKey(epochId), epochInfo.toBytes)
  }

  def getStakeConsensusEpochInfo(epochId: ConsensusEpochId): Option[StakeConsensusEpochInfo] = {
    consensusEpochInfoStorage.get(stakeEpochInfoKey(epochId)).asScala
      .map(byteArray => StakeConsensusEpochInfo.fromBytes(byteArray.data))
  }

  def addNonceConsensusEpochInfo(epochId: ConsensusEpochId, consensusNonce: NonceConsensusEpochInfo): Unit = {
    println(s"${this.hashCode()} Add nonce to consensus data storage: for epochId ${epochId} nonce: ${consensusNonce.consensusNonce}")
    require(!getNonceConsensusEpochInfo(epochId).exists(_ != consensusNonce)) //NonceConsensusEpochInfo shall NOT be changed, check trying to overwrite it value
    addEntry(nonceEpochInfoKey(epochId), consensusNonce.toBytes)
  }

  def getNonceConsensusEpochInfo(epochId: ConsensusEpochId): Option[NonceConsensusEpochInfo] = {
    consensusEpochInfoStorage.get(nonceEpochInfoKey(epochId)).asScala
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

  private def stakeEpochInfoKey(epochId: ConsensusEpochId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"stake$epochId"))

  private def nonceEpochInfoKey(epochId: ConsensusEpochId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"nonce$epochId"))

  private def addEntry(key: ByteArrayWrapper, value: Array[Byte]): Unit = {
    val listForUpdate = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    val addedData = new JPair(key, new ByteArrayWrapper(value))
    listForUpdate.add(addedData)
    consensusEpochInfoStorage.update(new ByteArrayWrapper(nextVersion), listForUpdate, java.util.Collections.emptyList())
  }
}
