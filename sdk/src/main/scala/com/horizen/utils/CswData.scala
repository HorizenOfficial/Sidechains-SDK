package com.horizen.utils

import com.horizen.cryptolibprovider.utils.FieldElementUtils
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.util
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

  override def serializer: SparkzSerializer[UtxoCswData] = UtxoCswDataSerializer

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

  override def serializer: SparkzSerializer[ForwardTransferCswData] = ForwardTransferCswDataSerializer

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

object CswDataSerializer extends SparkzSerializer[CswData] {
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
    val cswDataType = Checker.readIntNotLessThanZero(r, "csw data type")

    cswDataType match {
      case 0 => UtxoCswDataSerializer.parse(r)
      case 1 => ForwardTransferCswDataSerializer.parse(r)
    }
  }
}

object UtxoCswDataSerializer extends SparkzSerializer[UtxoCswData] {
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
    val boxId = Checker.readBytes(r, 32, "box id")

    val spendingPubKeyLength = Checker.readIntNotLessThanZero(r, "spending public key length")
    val spendingPubKey = Checker.readBytes(r, spendingPubKeyLength, "spending public key")

    val amount = Checker.readIntNotLessThanZero(r, "amount")
    val nonce = Checker.readIntNotLessThanZero(r, "nonce")

    val customHashLength = Checker.readIntNotLessThanZero(r, "custom hash length")
    val customHash = Checker.readBytes(r, customHashLength, "custom hash")

    val merklePathLength = Checker.readIntNotLessThanZero(r, "merkle path length")
    val merklePath = Checker.readBytes(r, merklePathLength, "merkle path")
    UtxoCswData(boxId, spendingPubKey, amount, nonce, customHash, merklePath)
  }
}


object ForwardTransferCswDataSerializer extends SparkzSerializer[ForwardTransferCswData] {
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
    val boxId = Checker.readBytes(r, 32, "box id")

    val amount = Checker.readIntNotLessThanZero(r, "amount")

    val receiverPubKeyReversedLength = Checker.readIntNotLessThanZero(r, "receiver public key reversed length")
    val receiverPubKeyReversed = Checker.readBytes(r, receiverPubKeyReversedLength, "receiver public key reserved")

    val paybackAddrDataHash = Checker.readBytes(r, 20, "payback address data hash")
    val txHash = Checker.readBytes(r, 32, "transaction hash")
    val outIdx = Checker.readIntNotLessThanZero(r, "old idx")

    val scCommitmentMerklePathLength = Checker.readIntNotLessThanZero(r, "sidechain commitment merkle path length")
    val scCommitmentMerklePath = Checker.readBytes(r, scCommitmentMerklePathLength, "sidechain commitment merkle path")

    val btrCommitment = Checker.readBytes(r, FieldElementUtils.fieldElementLength(), "btr commitment")
    val certCommitment = Checker.readBytes(r, FieldElementUtils.fieldElementLength(), "cert commitment")
    val scCrCommitment = Checker.readBytes(r, FieldElementUtils.fieldElementLength(), "sidechain sc commitment")

    val ftMerklePathLength = Checker.readIntNotLessThanZero(r, "forward transfer merkle path length")
    val ftMerklePath = Checker.readBytes(r, ftMerklePathLength, "forward transfer merkle path")

    ForwardTransferCswData(boxId, amount, receiverPubKeyReversed, paybackAddrDataHash, txHash, outIdx,
      scCommitmentMerklePath, btrCommitment, certCommitment, scCrCommitment, ftMerklePath)
  }
}