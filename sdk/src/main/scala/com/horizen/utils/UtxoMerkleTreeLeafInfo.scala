package com.horizen.utils

import com.horizen.cryptolibprovider.utils.FieldElementUtils
import com.horizen.librustsidechains.FieldElement
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

case class UtxoMerkleTreeLeafInfo(leaf: Array[Byte], position: Long) extends BytesSerializable {
  require(leaf.length == FieldElementUtils.fieldElementLength(), "Storage must be NOT NULL.")

  override type M = UtxoMerkleTreeLeafInfo

  override def serializer: SparkzSerializer[UtxoMerkleTreeLeafInfo] = UtxoMerkleTreeLeafInfoSerializer

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


object UtxoMerkleTreeLeafInfoSerializer extends SparkzSerializer[UtxoMerkleTreeLeafInfo] {
  override def serialize(obj: UtxoMerkleTreeLeafInfo, w: Writer): Unit = {
    w.putBytes(obj.leaf)
    w.putLong(obj.position)
  }

  override def parse(r: Reader): UtxoMerkleTreeLeafInfo = {
    val leaf = Checker.readBytes(r, FieldElementUtils.fieldElementLength(), "leaf")
    val position = Checker.readIntNotLessThanZero(r, "position")
    UtxoMerkleTreeLeafInfo(leaf, position)
  }
}