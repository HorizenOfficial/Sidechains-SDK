package io.horizen.consensus

import com.horizen.utils._
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class NonceConsensusEpochInfo(consensusNonce: ConsensusNonce) extends BytesSerializable {
  if (!consensusNonceAllowedLengths.contains(consensusNonce.length)) {
      throw new IllegalArgumentException("Incorrect consensus nonce length, %d bytes expected, %d found.".format(consensusNonceAllowedLengths, consensusNonce.length))
  }

  override def toString: String = s"NonceConsensusEpochInfo(consensusNonce=${BytesUtils.toHexString(consensusNonce)}"

  override def hashCode(): Int =  java.util.Arrays.hashCode(consensusNonce)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: NonceConsensusEpochInfo => consensusNonce.data.sameElements(other.consensusNonce)
      case _ => false
    }
  }

  override type M = NonceConsensusEpochInfo

  override def serializer: SparkzSerializer[NonceConsensusEpochInfo] = NonceConsensusEpochInfoSerializer
}


object NonceConsensusEpochInfoSerializer extends SparkzSerializer[NonceConsensusEpochInfo]{
  override def serialize(obj: NonceConsensusEpochInfo, w: Writer): Unit = w.putBytes(obj.consensusNonce)

  override def parse(r: Reader): NonceConsensusEpochInfo = NonceConsensusEpochInfo(ConsensusNonce @@ r.getBytes(r.remaining))
}
