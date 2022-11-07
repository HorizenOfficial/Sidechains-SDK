package com.horizen.chain

import com.fasterxml.jackson.annotation._
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.block.SidechainBlock
import com.horizen.serialization.{ModifierSemanticValiditySerializer, Views}
import com.horizen.utils.{WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import com.horizen.vrf.{VrfOutput, VrfOutputSerializer}
import sparkz.core.NodeViewModifier
import sparkz.core.block.Block.Timestamp
import sparkz.core.consensus.ModifierSemanticValidity
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import scorex.util.serialization.{Reader, Writer}
import scorex.util.{ModifierId, bytesToId, idToBytes}
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
  def mainchainHeaderHashesFromBlock(sidechainBlock: SidechainBlock): Seq[MainchainHeaderHash] = {
    sidechainBlock.mainchainHeaders.map(header => byteArrayToMainchainHeaderHash(header.hash))
  }

  def mainchainReferenceDataHeaderHashesFromBlock(sidechainBlock: SidechainBlock): Seq[MainchainHeaderHash] = {
    sidechainBlock.mainchainBlockReferencesData.map(data => byteArrayToMainchainHeaderHash(data.headerHash))
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
    val length = r.getInt()

    (0 until length).foreach(_ => {
      val bytes = r.getBytes(mainchainHeaderHashSize)
      references.append(byteArrayToMainchainHeaderHash(bytes))
    })

    references
  }

  private def readMainchainHeadersBaseInfo(r: Reader): Seq[MainchainHeaderBaseInfo] = {
    val references: ArrayBuffer[MainchainHeaderBaseInfo] = ArrayBuffer()
    val length = r.getInt()

    (0 until length).foreach(_ => {
      references.append(MainchainHeaderBaseInfoSerializer.parse(r))
    })

    references
  }

  override def parse(r: Reader): SidechainBlockInfo = {
    val height = r.getInt()
    val score = r.getLong()
    val parentId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))
    val timestamp = r.getLong()
    val semanticValidityCode = r.getByte()
    val mainchainHeaderBaseInfos = readMainchainHeadersBaseInfo(r)
    val mainchainReferenceDataHeaderHashes = readMainchainHeadersHashes(r)
    val withdrawalEpochInfo = WithdrawalEpochInfoSerializer.parse(r)
    val vrfOutputOpt = r.getOption(VrfOutputSerializer.getSerializer.parse(r))
    val lastBlockInPreviousConsensusEpoch = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    SidechainBlockInfo(height, score, parentId, timestamp, ModifierSemanticValidity.restoreFromCode(semanticValidityCode),
      mainchainHeaderBaseInfos, mainchainReferenceDataHeaderHashes, withdrawalEpochInfo, vrfOutputOpt, lastBlockInPreviousConsensusEpoch)
  }
}
