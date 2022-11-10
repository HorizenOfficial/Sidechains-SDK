package com.horizen.consensus

import java.util.{ArrayList => JArrayList}
import com.horizen.storage.{SidechainStorageInfo, Storage}
import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}
import com.horizen.utils.Utils
import sparkz.crypto.hash.Blake2b256
import sparkz.util.SparkzLogging

import scala.compat.java8.OptionConverters._

class ConsensusDataStorage(consensusEpochInfoStorage: Storage)
  extends SparkzLogging
  with SidechainStorageInfo {
  def addStakeConsensusEpochInfo(epochId: ConsensusEpochId, stakeEpochInfo: StakeConsensusEpochInfo): Unit = {
    log.info(s"Storage with id:${this.hashCode()} -- Add stake to consensus data storage: for epochId ${epochId} stake info: ${stakeEpochInfo}")

    //StakeConsensusEpochInfo shall NOT be changed, check trying of overwriting value
    require(getStakeConsensusEpochInfo(epochId).forall(stakeInfo => stakeInfo.equals(stakeEpochInfo)),
      s"StakeConsensusEpochInfo shall not be redefined for epoch ${epochId}")

    addEntry(stakeEpochInfoKey(epochId), StakeConsensusEpochInfoSerializer.toBytes(stakeEpochInfo))
  }

  def getStakeConsensusEpochInfo(epochId: ConsensusEpochId): Option[StakeConsensusEpochInfo] = {
    consensusEpochInfoStorage
      .get(stakeEpochInfoKey(epochId))
      .asScala
      .map(byteArray => StakeConsensusEpochInfoSerializer.parseBytes(byteArray.data))
  }

  def addNonceConsensusEpochInfo(epochId: ConsensusEpochId, consensusNonce: NonceConsensusEpochInfo): Unit = {
    log.info(s"Storage with id:${this.hashCode()} -- Add nonce to consensus data storage: for epochId ${epochId} nonce info: ${consensusNonce}")

    //NonceConsensusEpochInfo shall NOT be changed, check trying of overwriting value
    require(getNonceConsensusEpochInfo(epochId).forall(nonceInfo => nonceInfo.equals(consensusNonce)),
      s"NonceConsensusEpochInfo shall not be redefined for epoch ${epochId}")

    addEntry(nonceEpochInfoKey(epochId), NonceConsensusEpochInfoSerializer.toBytes(consensusNonce))
  }

  def getNonceConsensusEpochInfo(epochId: ConsensusEpochId): Option[NonceConsensusEpochInfo] = {
    consensusEpochInfoStorage
      .get(nonceEpochInfoKey(epochId))
      .asScala
      .map(byteArray => NonceConsensusEpochInfoSerializer.parseBytes(byteArray.data))
  }

  private def stakeEpochInfoKey(epochId: ConsensusEpochId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"stake$epochId"))

  private def nonceEpochInfoKey(epochId: ConsensusEpochId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"nonce$epochId"))

  private def addEntry(key: ByteArrayWrapper, value: Array[Byte]): Unit = {
    val listForUpdate = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    val addedData = new JPair(key, new ByteArrayWrapper(value))
    listForUpdate.add(addedData)
    val version = new ByteArrayWrapper(Utils.nextVersion)
    consensusEpochInfoStorage.update(version, listForUpdate, java.util.Collections.emptyList())
    log.debug("Consensus data storage updated with version: " + version)
  }

  override def lastVersionId: Option[ByteArrayWrapper] = {
    consensusEpochInfoStorage.lastVersionID().asScala
  }
}
