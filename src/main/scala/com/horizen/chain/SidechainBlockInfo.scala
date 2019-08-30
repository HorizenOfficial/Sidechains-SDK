package com.horizen.chain

import java.io.ByteArrayOutputStream
import java.util

import com.google.common.primitives.{Ints, Longs}
import com.horizen.block.SidechainBlock
import com.horizen.utils.BytesUtils
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
                              mainchainBlockReferenceHashes: Seq[MainchainBlockReferenceId]) extends BytesSerializable with ChainData[ModifierId] {

  override def getParentId: ModifierId = parentId

  override type M = SidechainBlockInfo

  override lazy val serializer = SidechainBlockInfoSerializer

  override def hashCode: Int = height.hashCode() + score.hashCode() + semanticValidity.code.toInt + util.Arrays.hashCode(idToBytes(parentId))

  override def bytes: Array[Byte] = SidechainBlockInfoSerializer.toBytes(this)
}

object SidechainBlockInfo {
  def referencesFromBlock(sidechainBlock: SidechainBlock): Seq[MainchainBlockReferenceId] = {
    sidechainBlock.mainchainBlocks.map(d => byteArrayToMainchainBlockReferenceId(d.hash))
  }
}

object SidechainBlockInfoSerializer extends ScorexSerializer[SidechainBlockInfo] {
  override def toBytes(obj: SidechainBlockInfo): Array[Byte] = {

    val mainchainBlockReferencesStream = new ByteArrayOutputStream
    obj.mainchainBlockReferenceHashes.foreach { ref =>
      mainchainBlockReferencesStream.write(ref.data)
    }

    Ints.toByteArray(obj.height) ++
      Longs.toByteArray(obj.score) ++
      idToBytes(obj.parentId) ++
      Array(obj.semanticValidity.code) ++
      mainchainBlockReferencesStream.toByteArray
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
    offset += 1

    var refIds: Seq[MainchainBlockReferenceId] = Seq()
    val remainingBytes = (bytes.length - offset)
    require(remainingBytes % mainchainBlockReferenceIdSize == 0, "Input data is corrupted")
    val remainingElementsCount = remainingBytes / mainchainBlockReferenceIdSize

    (1 to remainingElementsCount).foreach { _ =>
      val reference = byteArrayToMainchainBlockReferenceId(bytes.slice(offset, offset + mainchainBlockReferenceIdSize))
      refIds = refIds :+ reference
      offset += mainchainBlockReferenceIdSize
    }

    SidechainBlockInfo(height, score, parentId, ModifierSemanticValidity.restoreFromCode(semanticValidityCode), refIds)
  }

  //TODO Finish implementation
  override def serialize(obj: SidechainBlockInfo, w: Writer): Unit = {
    w.putInt(obj.height)
    w.putLong(obj.score)
    w.putBytes(idToBytes(obj.parentId))
    w.put(obj.semanticValidity.code)
    obj.mainchainBlockReferenceHashes.foreach(id => w.putBytes(id.data))
  }

  private def readMainchainReferencesIds(r: Reader): Seq[MainchainBlockReferenceId] = {
    val references: Seq[MainchainBlockReferenceId] = Seq()
    val length = r.remaining / mainchainBlockReferenceIdSize
    require((r.remaining % mainchainBlockReferenceIdSize) == 0)

    (0 to length).foreach(references :+ byteArrayToMainchainBlockReferenceId(r.getBytes(mainchainBlockReferenceIdSize)))
    references
  }

  override def parse(r: Reader): SidechainBlockInfo = {
    val height = r.getInt()
    val score = r.getLong()
    val parentId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))
    val semanticValidityCode = r.getByte()
    val mainChainReferences = readMainchainReferencesIds(r)

    SidechainBlockInfo(height, score, parentId, ModifierSemanticValidity.restoreFromCode(semanticValidityCode), mainChainReferences)
  }
}
