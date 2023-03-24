package io.horizen.chain

import com.fasterxml.jackson.annotation._
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.json.Views
import io.horizen.json.serializer.ModifierSemanticValiditySerializer
import io.horizen.transaction.Transaction
import io.horizen.utils.{WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import io.horizen.utxo.block.SidechainBlock
import io.horizen.vrf.{VrfOutput, VrfOutputSerializer}
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
