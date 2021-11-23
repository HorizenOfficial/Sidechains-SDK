package com.horizen.utils

import com.horizen.librustsidechains.FieldElement
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}


sealed trait CswData extends BytesSerializable

case class UtxoCswData(boxId: Array[Byte],
                       spendingPubKey: Array[Byte],
                       amount: Long,
                       nonce: Long,
                       customHash: Array[Byte],
                       utxoMerklePath: Array[Byte]) extends CswData {
  override type M = UtxoCswData

  override def serializer: ScorexSerializer[UtxoCswData] = UtxoCswDataSerializer
}

// TODO: add path to FT in CommitmentTree
case class ForwardTransferCswData(boxId: Array[Byte],
                                  receivedPubKey: Array[Byte],
                                  mcReturnAddress: Array[Byte],
                                  mcTxHash: Array[Byte],
                                  outIdx: Int,
                                  scCommitmentMerklePath: Array[Byte],
                                  btrCommitment: Array[Byte],
                                  certCommitment: Array[Byte],
                                  scCrCommitment: Array[Byte],
                                  ftMerklePath: Array[Byte]) extends CswData {
  override type M = ForwardTransferCswData

  override def serializer: ScorexSerializer[ForwardTransferCswData] = ForwardTransferCswDataSerializer
}

object CswDataSerializer extends ScorexSerializer[CswData] {
  override def serialize(obj: CswData, w: Writer): Unit = {
    obj match {
      case utxo: UtxoCswData =>
        w.putInt(0)
        UtxoCswDataSerializer.serialize(utxo, w)
      case ft: ForwardTransferCswData =>
        w.putInt(1)
        ForwardTransferCswDataSerializer.serialize(ft, w)
    }
  }

  override def parse(r: Reader): CswData = {
    val cswDataType = r.getInt()

    cswDataType match {
      case 0 => UtxoCswDataSerializer.parse(r)
      case 1 => ForwardTransferCswDataSerializer.parse(r)
    }
  }
}

object UtxoCswDataSerializer extends ScorexSerializer[UtxoCswData] {
  override def serialize(obj: UtxoCswData, w: Writer): Unit = {
    w.putBytes(obj.boxId)
    w.putInt(obj.spendingPubKey.length)
    w.putBytes(obj.spendingPubKey)
    w.putLong(obj.amount)
    w.putLong(obj.nonce)
    w.putInt(obj.customHash.length)
    w.putBytes(obj.customHash)
    w.putInt(obj.utxoMerklePath.length)
    w.putBytes(obj.utxoMerklePath)
  }

  override def parse(r: Reader): UtxoCswData = {
    val boxId = r.getBytes(32)

    val spendingPubKeyLength = r.getInt()
    val spendingPubKey = r.getBytes(spendingPubKeyLength)

    val amount = r.getLong()
    val nonce = r.getLong()

    val customHashLength = r.getInt()
    val customHash = r.getBytes(customHashLength)

    val merklePathLength = r.getInt()
    val merklePath = r.getBytes(merklePathLength)
    UtxoCswData(boxId, spendingPubKey, amount, nonce, customHash, merklePath)
  }
}


object ForwardTransferCswDataSerializer extends ScorexSerializer[ForwardTransferCswData] {
  override def serialize(obj: ForwardTransferCswData, w: Writer): Unit = {
    w.putBytes(obj.boxId)
    w.putInt(obj.receivedPubKey.length)
    w.putBytes(obj.receivedPubKey)
    w.putBytes(obj.mcReturnAddress)
    w.putBytes(obj.mcTxHash)
    w.putInt(obj.outIdx)
    w.putInt(obj.scCommitmentMerklePath.length)
    w.putBytes(obj.scCommitmentMerklePath)
    w.putBytes(obj.btrCommitment)
    w.putBytes(obj.certCommitment)
    w.putBytes(obj.scCrCommitment)
    w.putInt(obj.ftMerklePath.length)
    w.putBytes(obj.ftMerklePath)
  }

  override def parse(r: Reader): ForwardTransferCswData = {
    val boxId = r.getBytes(32)

    val receivedPubKeyLength = r.getInt()
    val receivedPubKey = r.getBytes(receivedPubKeyLength)

    val mcReturnAddress = r.getBytes(20)
    val mcTxHash = r.getBytes(32)
    val outIdx = r.getInt()

    val scCommitmentMerklePathLength = r.getInt()
    val scCommitmentMerklePath = r.getBytes(scCommitmentMerklePathLength)

    val btrCommitment = r.getBytes(FieldElement.FIELD_ELEMENT_LENGTH)
    val certCommitment = r.getBytes(FieldElement.FIELD_ELEMENT_LENGTH)
    val scCrCommitment = r.getBytes(FieldElement.FIELD_ELEMENT_LENGTH)

    val ftMerklePathLength = r.getInt()
    val ftMerklePath = r.getBytes(ftMerklePathLength)

    ForwardTransferCswData(boxId, receivedPubKey, mcReturnAddress, mcTxHash, outIdx,
      scCommitmentMerklePath, btrCommitment, certCommitment, scCrCommitment, ftMerklePath)
  }
}