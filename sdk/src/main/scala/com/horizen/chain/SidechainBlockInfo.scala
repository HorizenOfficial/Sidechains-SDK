package com.horizen.chain

import java.util

import com.horizen.block.SidechainBlock
import scorex.core.NodeViewModifier
import scorex.core.consensus.ModifierSemanticValidity
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}
import scorex.util.{ModifierId, bytesToId, idToBytes}

case class SidechainBlockInfo(height: Int,
                              score: Long,
                              parentId: ModifierId,
                              semanticValidity: ModifierSemanticValidity,
                              mainchainBlockReferenceHashes: Seq[MainchainBlockReferenceId],
                              withdrawalEpoch: Int, // epoch number, SidechainBlock belongs to. Counted in MC Blocks.
                              withdrawalEpochIndex: Int // position of SidechainBlock in the epoch. Equals to the most recent MC block reference position in current withdrawal epoch.
                             ) extends BytesSerializable with LinkedElement[ModifierId] {

  override def getParentId: ModifierId = parentId

  override type M = SidechainBlockInfo

  override lazy val serializer = SidechainBlockInfoSerializer

  override def hashCode: Int = height.hashCode() + score.hashCode() + semanticValidity.code.toInt + util.Arrays.hashCode(idToBytes(parentId))

  override def bytes: Array[Byte] = SidechainBlockInfoSerializer.toBytes(this)
}

object SidechainBlockInfo {
  def mainchainReferencesFromBlock(sidechainBlock: SidechainBlock): Seq[MainchainBlockReferenceId] = {
    sidechainBlock.mainchainBlocks.map(d => byteArrayToMainchainBlockReferenceId(d.hash))
  }
}

object SidechainBlockInfoSerializer extends ScorexSerializer[SidechainBlockInfo] {
  override def serialize(obj: SidechainBlockInfo, w: Writer): Unit = {
    w.putInt(obj.height)
    w.putLong(obj.score)
    w.putBytes(idToBytes(obj.parentId))
    w.put(obj.semanticValidity.code)
    w.putInt(obj.mainchainBlockReferenceHashes.size)
    obj.mainchainBlockReferenceHashes.foreach(id => w.putBytes(id.data))
    w.putInt(obj.withdrawalEpoch)
    w.putInt(obj.withdrawalEpochIndex)
  }

  private def readMainchainReferencesIds(r: Reader): Seq[MainchainBlockReferenceId] = {
    var references: Seq[MainchainBlockReferenceId] = Seq()
    val length = r.getInt()

    (0 until length).foreach(_ => {
      val bytes = r.getBytes(mainchainBlockReferenceIdSize)
      references = references :+ byteArrayToMainchainBlockReferenceId(bytes)
    })

    references
  }

  override def parse(r: Reader): SidechainBlockInfo = {
    val height = r.getInt()
    val score = r.getLong()
    val parentId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))
    val semanticValidityCode = r.getByte()
    val mainChainReferences = readMainchainReferencesIds(r)
    val withdrawalEpoch = r.getInt()
    val withdrawalEpochIndex = r.getInt()

    SidechainBlockInfo(height, score, parentId, ModifierSemanticValidity.restoreFromCode(semanticValidityCode), mainChainReferences, withdrawalEpoch, withdrawalEpochIndex)
  }
}
