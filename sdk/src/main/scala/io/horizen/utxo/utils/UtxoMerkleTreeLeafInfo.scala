package io.horizen.utxo.utils

import com.horizen.cryptolibprovider.utils.FieldElementUtils
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
    val leaf = r.getBytes(FieldElementUtils.fieldElementLength())
    val position = r.getLong()
    UtxoMerkleTreeLeafInfo(leaf, position)
  }
}