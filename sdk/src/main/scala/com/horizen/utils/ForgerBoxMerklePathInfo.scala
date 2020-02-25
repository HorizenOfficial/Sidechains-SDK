package com.horizen.utils

import com.horizen.box.{ForgerBox, ForgerBoxSerializer}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}


case class ForgerBoxMerklePathInfo(forgerBox: ForgerBox, merklePath: MerklePath) extends BytesSerializable {

  override type M = ForgerBoxMerklePathInfo

  override def serializer: ScorexSerializer[ForgerBoxMerklePathInfo] = ForgerBoxMerklePathInfoSerializer
}

object ForgerBoxMerklePathInfoSerializer extends ScorexSerializer[ForgerBoxMerklePathInfo] {
  override def serialize(obj: ForgerBoxMerklePathInfo, w: Writer): Unit = {
    val forgerBoxBytes = ForgerBoxSerializer.getSerializer.toBytes(obj.forgerBox)
    w.putInt(forgerBoxBytes.length)
    w.putBytes(forgerBoxBytes)
    val merklePathBytes = obj.merklePath.bytes()
    w.putInt(merklePathBytes.length)
    w.putBytes(merklePathBytes)
  }

  override def parse(r: Reader): ForgerBoxMerklePathInfo = {
    val forgerBoxBytesLength = r.getInt()
    val forgerBox = ForgerBoxSerializer.getSerializer.parseBytes(r.getBytes(forgerBoxBytesLength))

    val merklePathBytesLength = r.getInt()
    val merklePath = MerklePath.parseBytes(r.getBytes(merklePathBytesLength))
    ForgerBoxMerklePathInfo(forgerBox, merklePath)
  }
}