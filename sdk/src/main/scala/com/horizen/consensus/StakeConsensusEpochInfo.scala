package com.horizen.consensus

import com.google.common.primitives.Longs
import com.horizen.utils._
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

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

  def toBytes: Array[Byte] = {
    rootHash.data ++ Longs.toByteArray(totalStake)
  }

  override def toString: String = {
    s"StakeConsensusEpochInfo(rootHash=${BytesUtils.toHexString(rootHash)}, totalStake=${totalStake})"
  }

  override type M = StakeConsensusEpochInfo

  override def serializer: ScorexSerializer[StakeConsensusEpochInfo] = StakeConsensusEpochInfoSerializer
}

object StakeConsensusEpochInfoSerializer extends ScorexSerializer[StakeConsensusEpochInfo]{
  private def fromBytes(bytes: Array[Byte]): StakeConsensusEpochInfo = {
    val rootHash = bytes.slice(0, merkleTreeHashLen)
    val totalStake: Long = Longs.fromByteArray(bytes.slice(merkleTreeHashLen, bytes.length))
    StakeConsensusEpochInfo(rootHash, totalStake)
  }

  override def serialize(obj: StakeConsensusEpochInfo, w: Writer): Unit = w.putBytes(obj.toBytes)

  override def parse(r: Reader): StakeConsensusEpochInfo = fromBytes(r.getBytes(r.remaining))
}
