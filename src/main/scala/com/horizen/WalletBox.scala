package com.horizen

import java.util

import com.google.common.primitives.{Bytes, Longs}
import com.horizen.box.Box
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.proposition.Proposition
import com.horizen.utils.BytesUtils
import scorex.core.serialization.ScorexSerializer
import scorex.core.{NodeViewModifier, idToBytes}
import scorex.util.serialization.{Reader, Writer}

import scala.util.{Failure, Success, Try}

class WalletBox(val box: SidechainTypes#SCB, val transactionId: String, val createdAt: Long)
  extends SidechainTypes
  with scorex.core.utils.ScorexEncoding
{
  require(transactionId.length == NodeViewModifier.ModifierIdSize * 2,
    "Expected transactionId length is %d, actual length is %d".format(NodeViewModifier.ModifierIdSize * 2, transactionId.length))
  require(createdAt > 0, "Expected createdAt should be positive value, actual value is %d".format(createdAt))

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
  override def toBytes(walletBox: WalletBox): Array[Byte] = {
    Bytes.concat(BytesUtils.fromHexString(walletBox.transactionId), Longs.toByteArray(walletBox.createdAt),
      sidechainBoxesCompanion.toBytes(walletBox.box))
  }

  override def parseBytesTry(bytes: Array[Byte]): Try[WalletBox] = Try {
    val txIdBytes = bytes.slice(0, NodeViewModifier.ModifierIdSize)
    val createdAt = Longs.fromByteArray(bytes.slice(NodeViewModifier.ModifierIdSize, NodeViewModifier.ModifierIdSize + 8))
    val boxBytes = bytes.slice(NodeViewModifier.ModifierIdSize + 8, bytes.length)
    sidechainBoxesCompanion.parseBytesTry(boxBytes) match {
      case Success(box) => new WalletBox(box, BytesUtils.toHexString(txIdBytes), createdAt)
      case Failure(exception) => throw new Exception(exception)
    }
  }

  override def serialize(obj: WalletBox, w: Writer): Unit = ???

  override def parse(r: Reader): WalletBox = ???
}
