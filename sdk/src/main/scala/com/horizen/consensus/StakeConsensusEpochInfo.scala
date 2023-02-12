package com.horizen.consensus

import com.horizen.utils._
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class StakeConsensusEpochInfo(rootHash: Array[Byte], totalStake: Long) extends BytesSerializable {
  require(rootHash.length == merkleTreeHashLen)

  override def hashCode(): Int =  {
    val rhHash: Integer = java.util.Arrays.hashCode(rootHash) * 31
    rhHash + totalStake.toInt
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: StakeConsensusEpochInfo => rootHash.sameElements(other.rootHash) && totalStake == other.totalStake
      case _ => false
    }
  }

  override def toString: String = {
    s"StakeConsensusEpochInfo(rootHash=${BytesUtils.toHexString(rootHash)}, totalStake=${totalStake})"
  }

  override type M = StakeConsensusEpochInfo

  override def serializer: SparkzSerializer[StakeConsensusEpochInfo] = StakeConsensusEpochInfoSerializer
}

object StakeConsensusEpochInfoSerializer extends SparkzSerializer[StakeConsensusEpochInfo]{
  override def serialize(obj: StakeConsensusEpochInfo, w: Writer): Unit = {
    w.putBytes(obj.rootHash)
    w.putLong(obj.totalStake)
  }

  override def parse(r: Reader): StakeConsensusEpochInfo = {
    val rootHash = Checker.readBytes(r, merkleTreeHashLen, "root hash")
    val totalStake: Long = Checker.readIntNotLessThanZero(r, "total stake")
    StakeConsensusEpochInfo(rootHash, totalStake)
  }
}
