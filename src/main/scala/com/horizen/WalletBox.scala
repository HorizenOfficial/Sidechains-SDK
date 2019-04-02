package com.horizen

import com.google.common.primitives.{Bytes, Longs}

import com.horizen.box.Box
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.transaction.Transaction

import scorex.core.serialization.{BytesSerializable, Serializer}
import scorex.util.{bytesToId, idToBytes}
import scorex.core.NodeViewModifier

import scala.util.{Try, Success, Failure}

case class WalletBox(box: SidechainTypes#B, transactionId: Array[Byte], createdAt: Long)
//  extends BytesSerializable
  extends scorex.core.utils.ScorexEncoding
{

  //override type M = WalletBox

  override def toString: String = s"WalletBox($box, ${encoder.encode(transactionId)}, $createdAt)"

  def serializer (sidechainBoxesCompanion: SidechainBoxesCompanion) : WalletBoxSerializer = {
    new WalletBoxSerializer(sidechainBoxesCompanion)
  }

}

class WalletBoxSerializer(sidechainBoxesCompanion : SidechainBoxesCompanion)
  extends Serializer[WalletBox]
{
  override def toBytes(walletBox: WalletBox): Array[Byte] = {
    Bytes.concat(walletBox.transactionId, Longs.toByteArray(walletBox.createdAt),
      sidechainBoxesCompanion.toBytes(walletBox.box))
  }

  override def parseBytes(bytes: Array[Byte]): Try[WalletBox] = {
    try {
      val txId = bytes.slice(0, NodeViewModifier.ModifierIdSize)
      val createdAt = Longs.fromByteArray(
        bytes.slice(NodeViewModifier.ModifierIdSize, NodeViewModifier.ModifierIdSize + 8))
      val boxBytes = bytes.slice(NodeViewModifier.ModifierIdSize + 8, bytes.length)
      val box = sidechainBoxesCompanion.parseBytes(boxBytes)
      if (box.isSuccess)
        Success(WalletBox(box.get.asInstanceOf[SidechainTypes#B], txId, createdAt))
      else
        Failure(box.asInstanceOf[Failure[Box[_]]].exception)
    } catch {
      case e : Throwable => Failure(e)
    }
  }
}
