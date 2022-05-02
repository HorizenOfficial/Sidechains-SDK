package com.horizen.utils

import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import java.util
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.fwtnative.ForwardTransferOutput
import com.horizen.scutxonative.ScUtxoOutput


sealed trait CswData extends BytesSerializable {
  val amount: Long
  val getNullifier: Array[Byte]
}

case class UtxoCswData(boxId: Array[Byte],
                       spendingPubKey: Array[Byte],
                       amount: Long,
                       nonce: Long,
                       customHash: Array[Byte],
                       utxoMerklePath: Array[Byte]) extends CswData {
  override type M = UtxoCswData

  override def serializer: ScorexSerializer[UtxoCswData] = UtxoCswDataSerializer

  override lazy val getNullifier: Array[Byte] = {
    val utxo: ScUtxoOutput = new ScUtxoOutput(spendingPubKey, amount, nonce, customHash)
    val nullifierFe = utxo.getNullifier
    val nullifier = nullifierFe.serializeFieldElement()
    nullifierFe.freeFieldElement()
    nullifier
  }

  override def hashCode(): Int = {
    var result = util.Arrays.hashCode(boxId)
    result = 31 * result + util.Arrays.hashCode(spendingPubKey)
    result = 31 * result + amount.hashCode()
    result = 31 * result + nonce.hashCode()
    result = 31 * result + util.Arrays.hashCode(customHash)
    result = 31 * result + util.Arrays.hashCode(utxoMerklePath)
    result
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: UtxoCswData => boxId.sameElements(other.boxId) && spendingPubKey.sameElements(other.spendingPubKey) &&
        amount == other.amount && nonce == other.nonce && customHash.sameElements(other.customHash) &&
        utxoMerklePath.sameElements(other.utxoMerklePath)
      case _ => false
    }
  }

  override def toString = s"UtxoCswData(boxID = ${BytesUtils.toHexString(boxId)})"
}

case class ForwardTransferCswData(boxId: Array[Byte],
                                  amount: Long,
                                  receiverPubKeyReversed: Array[Byte], // PubKey bytes as they are stored and used in MC.
                                  paybackAddrDataHash: Array[Byte],
                                  txHash: Array[Byte],
                                  outIdx: Int,
                                  scCommitmentMerklePath: Array[Byte],
                                  btrCommitment: Array[Byte],
                                  certCommitment: Array[Byte],
                                  scCrCommitment: Array[Byte],
                                  ftMerklePath: Array[Byte]) extends CswData {
  override type M = ForwardTransferCswData

  override def serializer: ScorexSerializer[ForwardTransferCswData] = ForwardTransferCswDataSerializer

  override lazy val getNullifier: Array[Byte] = {
    val ft: ForwardTransferOutput = new ForwardTransferOutput(amount, BytesUtils.reverseBytes(receiverPubKeyReversed), paybackAddrDataHash, txHash, outIdx)
    val nullifierFe = ft.getNullifier
    val nullifier = nullifierFe.serializeFieldElement()
    nullifierFe.freeFieldElement()
    nullifier
  }

  override def hashCode(): Int = {
    var result = util.Arrays.hashCode(boxId)
    result = 31 * result + amount.hashCode()
    result = 31 * result + util.Arrays.hashCode(receiverPubKeyReversed)
    result = 31 * result + util.Arrays.hashCode(paybackAddrDataHash)
    result = 31 * result + util.Arrays.hashCode(txHash)
    result = 31 * result + outIdx
    result = 31 * result + util.Arrays.hashCode(scCommitmentMerklePath)
    result = 31 * result + util.Arrays.hashCode(btrCommitment)
    result = 31 * result + util.Arrays.hashCode(certCommitment)
    result = 31 * result + util.Arrays.hashCode(scCrCommitment)
    result = 31 * result + util.Arrays.hashCode(ftMerklePath)
    result
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ForwardTransferCswData => boxId.sameElements(other.boxId) && amount == other.amount &&
        receiverPubKeyReversed.sameElements(other.receiverPubKeyReversed) && paybackAddrDataHash.sameElements(other.paybackAddrDataHash) &&
        txHash.sameElements(other.txHash) && outIdx == other.outIdx && scCommitmentMerklePath.sameElements(other.scCommitmentMerklePath) &&
        btrCommitment.sameElements(other.btrCommitment) && certCommitment.sameElements(other.certCommitment) &&
        scCrCommitment.sameElements(other.scCrCommitment) && ftMerklePath.sameElements(other.ftMerklePath)
      case _ => false
    }
  }

  override def toString = s"ForwardTransferCswData(boxID = ${BytesUtils.toHexString(boxId)})"
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
    w.putLong(obj.amount)
    w.putInt(obj.receiverPubKeyReversed.length)
    w.putBytes(obj.receiverPubKeyReversed)
    w.putBytes(obj.paybackAddrDataHash)
    w.putBytes(obj.txHash)
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

    val amount = r.getLong()

    val receiverPubKeyReversedLength = r.getInt()
    val receiverPubKeyReversed = r.getBytes(receiverPubKeyReversedLength)

    val paybackAddrDataHash = r.getBytes(20)
    val txHash = r.getBytes(32)
    val outIdx = r.getInt()

    val scCommitmentMerklePathLength = r.getInt()
    val scCommitmentMerklePath = r.getBytes(scCommitmentMerklePathLength)

    val btrCommitment = r.getBytes(FieldElementUtils.fieldElementLength())
    val certCommitment = r.getBytes(FieldElementUtils.fieldElementLength())
    val scCrCommitment = r.getBytes(FieldElementUtils.fieldElementLength())

    val ftMerklePathLength = r.getInt()
    val ftMerklePath = r.getBytes(ftMerklePathLength)

    ForwardTransferCswData(boxId, amount, receiverPubKeyReversed, paybackAddrDataHash, txHash, outIdx,
      scCommitmentMerklePath, btrCommitment, certCommitment, scCrCommitment, ftMerklePath)
  }
}