package com.horizen.consensus

import java.math.BigInteger

import com.horizen.utils._
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class NonceConsensusEpochInfo(consensusNonce: ConsensusNonce) extends BytesSerializable {
  def toBytes: Array[Byte] = consensusNonce

  override def toString: String = s"NonceConsensusEpochInfo(consensusNonce=${BytesUtils.toHexString(consensusNonce)}"

  override def hashCode(): Int =  java.util.Arrays.hashCode(consensusNonce)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: NonceConsensusEpochInfo => consensusNonce.data.sameElements(other.consensusNonce)
      case _ => false
    }
  }

  def getAsStringForVrfBuilding: String = BytesUtils.toHexString(consensusNonce)

  override type M = NonceConsensusEpochInfo

  override def serializer: ScorexSerializer[NonceConsensusEpochInfo] = NonceConsensusEpochInfoSerializer
}


object NonceConsensusEpochInfoSerializer extends ScorexSerializer[NonceConsensusEpochInfo]{
  private def fromBytes(bytes: Array[Byte]): NonceConsensusEpochInfo = {
    NonceConsensusEpochInfo(bigIntToConsensusNonce(new BigInteger(bytes)))
  }

  override def serialize(obj: NonceConsensusEpochInfo, w: Writer): Unit = w.putBytes(obj.toBytes)

  override def parse(r: Reader): NonceConsensusEpochInfo = fromBytes(r.getBytes(r.remaining))
}
