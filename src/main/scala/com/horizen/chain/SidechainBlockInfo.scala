package com.horizen.chain

import java.util
import java.util.Arrays

import com.google.common.primitives.{Ints, Longs}
import com.horizen.utils.BytesUtils
import scorex.core.NodeViewModifier
import scorex.core.consensus.ModifierSemanticValidity
import scorex.core.serialization.BytesSerializable
import scorex.core.serialization.Serializer
import scorex.util.{ModifierId, bytesToId, idToBytes}

import scala.util.Try

case class SidechainBlockInfo(height: Int,
                              score: Long,
                              parentId: ModifierId,
                              semanticValidity: ModifierSemanticValidity) extends BytesSerializable {

  override type M = SidechainBlockInfo

  override lazy val serializer = SidechainBlockInfoSerializer

  override def hashCode: Int = height.hashCode() + score.hashCode() + semanticValidity.code.toInt + util.Arrays.hashCode(idToBytes(parentId))

  override def equals(obj: Any): Boolean = {
    obj match {
      case b: SidechainBlockInfo => height == b.height && score == b.score && parentId == b.parentId && semanticValidity == b.semanticValidity
      case _ => false
    }
  }
}


object SidechainBlockInfoSerializer extends Serializer[SidechainBlockInfo] {
  override def toBytes(obj: SidechainBlockInfo): Array[Byte] = {
    Ints.toByteArray(obj.height) ++ Longs.toByteArray(obj.score) ++ idToBytes(obj.parentId) ++ Array(obj.semanticValidity.code)
  }

  override def parseBytes(bytes: Array[Byte]): Try[SidechainBlockInfo] = Try {
    var offset: Int = 0

    val height: Int = BytesUtils.getInt(bytes, offset)
    offset += 4

    val score: Long = BytesUtils.getLong(bytes, offset)
    offset += 8

    val parentId = bytesToId(bytes.slice(offset, offset + NodeViewModifier.ModifierIdSize))
    offset += NodeViewModifier.ModifierIdSize

    val semanticValidityCode: Byte = bytes(offset)

    SidechainBlockInfo(height, score, parentId, ModifierSemanticValidity.restoreFromCode(semanticValidityCode))
  }
}
