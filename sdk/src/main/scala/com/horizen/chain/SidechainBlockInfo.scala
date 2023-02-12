package com.horizen.chain

import com.fasterxml.jackson.annotation._
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.block.{SidechainBlock, SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.serialization.{ModifierSemanticValiditySerializer, Views}
import com.horizen.transaction.Transaction
import com.horizen.utils.{Checker, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import com.horizen.vrf.{VrfOutput, VrfOutputSerializer}
import sparkz.util.serialization.{Reader, Writer}
import sparkz.util.{ModifierId, bytesToId, idToBytes}
import sparkz.core.NodeViewModifier
import sparkz.core.block.Block.Timestamp
import sparkz.core.consensus.ModifierSemanticValidity
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

import scala.collection.mutable.ArrayBuffer

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("serializer", "mainchainHeaderHashes"))
case class SidechainBlockInfo(height: Int,
                              score: Long,
                              parentId: ModifierId,
                              timestamp: Timestamp,
                              @JsonSerialize(using = classOf[ModifierSemanticValiditySerializer]) semanticValidity: ModifierSemanticValidity,
                              mainchainHeaderBaseInfo: Seq[MainchainHeaderBaseInfo],
                              mainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash],
                              withdrawalEpochInfo: WithdrawalEpochInfo,
                              vrfOutputOpt: Option[VrfOutput],
                              lastBlockInPreviousConsensusEpoch: ModifierId) extends BytesSerializable with LinkedElement[ModifierId] {

  override def getParentId: ModifierId = parentId

  override type M = SidechainBlockInfo

  override lazy val serializer: SparkzSerializer[SidechainBlockInfo] = SidechainBlockInfoSerializer

  lazy val mainchainHeaderHashes: Seq[MainchainHeaderHash] = {mainchainHeaderBaseInfo.map(info => info.hash)}
}

object SidechainBlockInfo {
  // clone these methods
  def mainchainHeaderHashesFromBlock(sidechainBlock: SidechainBlock): Seq[MainchainHeaderHash] = {
    sidechainBlock.mainchainHeaders.map(header => byteArrayToMainchainHeaderHash(header.hash))
  }

  def mainchainReferenceDataHeaderHashesFromBlock(b: SidechainBlockBase[_ <: Transaction,_ <: SidechainBlockHeaderBase]): Seq[MainchainHeaderHash] = {
      b.mainchainBlockReferencesData.map(data => byteArrayToMainchainHeaderHash(data.headerHash))
  }

}

object SidechainBlockInfoSerializer extends SparkzSerializer[SidechainBlockInfo] {
  override def serialize(obj: SidechainBlockInfo, w: Writer): Unit = {
    w.putInt(obj.height)
    w.putLong(obj.score)
    w.putBytes(idToBytes(obj.parentId))
    w.putLong(obj.timestamp)
    w.put(obj.semanticValidity.code)
    w.putInt(obj.mainchainHeaderBaseInfo.size)
    obj.mainchainHeaderBaseInfo.foreach(info => info.serializer.serialize(info, w))
    w.putInt(obj.mainchainReferenceDataHeaderHashes.size)
    obj.mainchainReferenceDataHeaderHashes.foreach(id => w.putBytes(id.data))
    WithdrawalEpochInfoSerializer.serialize(obj.withdrawalEpochInfo, w)

    w.putOption(obj.vrfOutputOpt){case (writer: Writer, vrfOutput: VrfOutput) =>
      VrfOutputSerializer.getSerializer.serialize(vrfOutput, writer)
    }

    w.putBytes(idToBytes(obj.lastBlockInPreviousConsensusEpoch))
  }

  private def readMainchainHeadersHashes(r: Reader): Seq[MainchainHeaderHash] = {
    val references: ArrayBuffer[MainchainHeaderHash] = ArrayBuffer()
    val length = Checker.readIntNotLessThanZero(r, "mainchain headers hash length")

    (0 until length).foreach(_ => {
      val bytes = Checker.readBytes(r, mainchainHeaderHashSize, "mainchain header hash")
      references.append(byteArrayToMainchainHeaderHash(bytes))
    })

    references
  }

  private def readMainchainHeadersBaseInfo(r: Reader): Seq[MainchainHeaderBaseInfo] = {
    val references: ArrayBuffer[MainchainHeaderBaseInfo] = ArrayBuffer()
    val length = Checker.readIntNotLessThanZero(r, "mainchain header base info length")

    (0 until length).foreach(_ => {
      references.append(MainchainHeaderBaseInfoSerializer.parse(r))
    })

    references
  }

  override def parse(reader: Reader): SidechainBlockInfo = {
    val height = Checker.readIntNotLessThanZero(reader, "height")
    val score = Checker.readLongNotLessThanZero(reader, "score")
    val parentId = bytesToId(Checker.readBytes(reader, NodeViewModifier.ModifierIdSize, "parent id"))
    val timestamp = Checker.readLongNotLessThanZero(reader, "timestamp")
    val semanticValidityCode = Checker.readByte(reader, "semantic validity code")
    val mainchainHeaderBaseInfos = readMainchainHeadersBaseInfo(reader)
    val mainchainReferenceDataHeaderHashes = readMainchainHeadersHashes(reader)
    val withdrawalEpochInfo = WithdrawalEpochInfoSerializer.parse(reader)
    val vrfOutputOpt = reader.getOption(VrfOutputSerializer.getSerializer.parse(reader))
    val lastBlockInPreviousConsensusEpoch = bytesToId(Checker.readBytes(reader, NodeViewModifier.ModifierIdSize, "last block in previous consensus epoch"))

    SidechainBlockInfo(height, score, parentId, timestamp, ModifierSemanticValidity.restoreFromCode(semanticValidityCode),
      mainchainHeaderBaseInfos, mainchainReferenceDataHeaderHashes, withdrawalEpochInfo, vrfOutputOpt, lastBlockInPreviousConsensusEpoch)
  }
}
