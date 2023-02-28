package com.horizen.utxo.wallet

import com.horizen.SidechainTypes
import com.horizen.utxo.companion.SidechainBoxesCompanion
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.{NodeViewModifier, bytesToId, idToBytes}
import sparkz.util.serialization.{Reader, Writer}
import sparkz.util.{ModifierId, SparkzEncoding}

import java.util


class WalletBox(val box: SidechainTypes#SCB, val transactionId: ModifierId, val createdAt: Long)
  extends SidechainTypes
  with SparkzEncoding
{
  require(transactionId.length == NodeViewModifier.ModifierIdSize * 2,
    "Expected transactionId length is %d, actual length is %d".format(NodeViewModifier.ModifierIdSize * 2, transactionId.length))
  require(createdAt >= 0, "Expected createdAt should be non-negative value, actual value is %d".format(createdAt))

  // Create WalletBox without containing transaction information.
  def this(box: SidechainTypes#SCB, createdAt: Long) {
    this(box, bytesToId(new Array[Byte](NodeViewModifier.ModifierIdSize)), createdAt)
  }

  override def toString: String = s"WalletBox($box, ${encoder.encode(transactionId)}, $createdAt)"

  def serializer (sidechainBoxesCompanion: SidechainBoxesCompanion) : WalletBoxSerializer = new WalletBoxSerializer(sidechainBoxesCompanion)

  override def hashCode: Int = util.Arrays.hashCode(box.id)

  override def equals(obj: Any): Boolean = {
    obj match {
      case wb: WalletBox => box.equals(wb.box) && transactionId.equals(wb.transactionId) && createdAt.equals(wb.createdAt) // TO DO: update
      case _ => false
    }
  }
}

class WalletBoxSerializer(sidechainBoxesCompanion : SidechainBoxesCompanion)
  extends SparkzSerializer[WalletBox]
{
  override def serialize(walletBox: WalletBox, writer: Writer): Unit = {
    writer.putBytes(idToBytes(walletBox.transactionId))
    writer.putLong(walletBox.createdAt)
    sidechainBoxesCompanion.serialize(walletBox.box, writer)
  }

  override def parse(reader: Reader): WalletBox = {
    val txId = bytesToId(reader.getBytes(NodeViewModifier.ModifierIdSize))
    val createdAt = reader.getLong()
    val box = sidechainBoxesCompanion.parse(reader)
    new WalletBox(box, txId, createdAt)
  }
}
