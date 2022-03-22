package com.horizen

import java.util
import com.horizen.companion.SidechainBoxesCompanion
import scorex.core.serialization.ScorexSerializer
import scorex.core.{NodeViewModifier, bytesToId, idToBytes}
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}


class WalletBox(val box: SidechainTypes#SCB, val transactionId: ModifierId, val createdAt: Long)
  extends SidechainTypes
  with scorex.core.utils.ScorexEncoding
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
  extends ScorexSerializer[WalletBox]
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
