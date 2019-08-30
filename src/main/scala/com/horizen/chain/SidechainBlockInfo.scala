package com.horizen.chain

import java.util

import com.google.common.primitives.{Ints, Longs}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.core.NodeViewModifier
import scorex.core.consensus.ModifierSemanticValidity
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}
import scorex.util.{ModifierId, bytesToId, idToBytes}

import scala.util.Try

case class SidechainBlockInfo(height: Int,
                              score: Long,
                              parentId: ModifierId,
                              semanticValidity: ModifierSemanticValidity,
                              mainchainBlockReferenceHashes: Seq[ByteArrayWrapper] = Seq()) extends BytesSerializable with ChainData[ModifierId] {

  override def getParentId: ModifierId = parentId

  override type M = SidechainBlockInfo

  override lazy val serializer = SidechainBlockInfoSerializer

  override def hashCode: Int = height.hashCode() + score.hashCode() + semanticValidity.code.toInt + util.Arrays.hashCode(idToBytes(parentId))

  // Shall we delete it due case class generate his own equals?
  override def equals(obj: Any): Boolean = {
    obj match {
      case b: SidechainBlockInfo => height == b.height && score == b.score && parentId == b.parentId && semanticValidity == b.semanticValidity && mainchainBlockReferenceHashes.equals(b.mainchainBlockReferenceHashes)
      case _ => false
    }
  }
}


object SidechainBlockInfoSerializer extends ScorexSerializer[SidechainBlockInfo] {
  override def toBytes(obj: SidechainBlockInfo): Array[Byte] = {
    Ints.toByteArray(obj.height) ++ Longs.toByteArray(obj.score) ++ idToBytes(obj.parentId) ++ Array(obj.semanticValidity.code)
  }

  override def parseBytesTry(bytes: Array[Byte]): Try[SidechainBlockInfo] = Try {
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

  //TODO Finish implementation
  override def serialize(obj: SidechainBlockInfo, w: Writer): Unit = {
    w.putInt(obj.height)
    w.putLong(obj.score)
    w.putBytes(idToBytes(obj.parentId))
    w.put(obj.semanticValidity.code)
  }

  override def parse(r: Reader): SidechainBlockInfo = {
    val height = r.getInt()
    val score = r.getLong()
    val parentId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))
    val semanticValidityCode = r.getByte()

    SidechainBlockInfo(height, score, parentId, ModifierSemanticValidity.restoreFromCode(semanticValidityCode))
  }
}
