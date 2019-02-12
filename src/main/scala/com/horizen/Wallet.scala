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

import scala.util.Try


case class WalletBox(box: SidechainTypes#B, transactionId: scorex.util.ModifierId, createdAt: Long)
                                                   (boxesCompanion: SidechainBoxesCompanion) extends BytesSerializable
  with scorex.core.utils.ScorexEncoding {

  override type M = WalletBox

  override lazy val serializer: Serializer[WalletBox] = new WalletBoxSerializer(boxesCompanion)

  override def toString: String = s"WalletBox($box, ${encoder.encode(transactionId)}, $createdAt)"
}

class WalletBoxSerializer(boxesCompanion: SidechainBoxesCompanion) extends Serializer[WalletBox] {
  override def toBytes(walletBox: WalletBox): Array[Byte] = {
    Bytes.concat(idToBytes(walletBox.transactionId), Longs.toByteArray(walletBox.createdAt), walletBox.box.bytes)
  }

  override def parseBytes(bytes: Array[Byte]): Try[WalletBox] = Try {
    val txId = bytesToId(bytes.slice(0, NodeViewModifier.ModifierIdSize))
    val createdAt = Longs.fromByteArray(
      bytes.slice(NodeViewModifier.ModifierIdSize, NodeViewModifier.ModifierIdSize + 8))
    val boxB = bytes.slice(NodeViewModifier.ModifierIdSize + 8, bytes.length)
    val box: SidechainTypes#B = boxesCompanion.parseBytes(boxB).get
    WalletBox(box, txId, createdAt)(boxesCompanion)
  }
}


trait Wallet[S <: Secret, P <: Proposition, TX <: Transaction, PMOD <: scorex.core.PersistentNodeViewModifier, W <: Wallet[S, P, TX, PMOD, W]]
  extends scorex.core.transaction.wallet.Vault[TX, PMOD, W] {
  self: W =>

  def addSecret(secret: S): Try[W]

  def removeSecret(publicImage: P): Try[W]

  def secret(publicImage: P): Option[S]

  def secrets(): Set[S]

  def boxes(): Seq[WalletBox]

  def publicKeys(): Set[P]
}

