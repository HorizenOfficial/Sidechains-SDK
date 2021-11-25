package com.horizen.utils

import com.horizen.librustsidechains.FieldElement
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class UtxoMerkleTreeLeafInfo(leaf: Array[Byte], position: Long) extends BytesSerializable {
  require(leaf.length == FieldElement.FIELD_ELEMENT_LENGTH, "Storage must be NOT NULL.")

  override type M = UtxoMerkleTreeLeafInfo

  override def serializer: ScorexSerializer[UtxoMerkleTreeLeafInfo] = UtxoMerkleTreeLeafInfoSerializer

  override def hashCode(): Int = java.util.Arrays.hashCode(leaf) + position.hashCode()

  override def equals(obj: Any): Boolean = {
    obj match {
      case info: UtxoMerkleTreeLeafInfo =>
        info.position == this.position &&
          info.leaf.sameElements(this.leaf)
      case _ => false
    }
  }
}


object UtxoMerkleTreeLeafInfoSerializer extends ScorexSerializer[UtxoMerkleTreeLeafInfo] {
  override def serialize(obj: UtxoMerkleTreeLeafInfo, w: Writer): Unit = {
    w.putBytes(obj.leaf)
    w.putLong(obj.position)
  }

  override def parse(r: Reader): UtxoMerkleTreeLeafInfo = {
    val leaf = r.getBytes(FieldElement.FIELD_ELEMENT_LENGTH)
    val position = r.getLong()
    UtxoMerkleTreeLeafInfo(leaf, position)
  }
}