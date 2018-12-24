package com.horizen
import com.horizen.box.Box
import com.horizen.proposition.{ProofOfKnowledgeProposition, Proposition}
import com.horizen.secret.Secret
import com.horizen.transaction.Transaction
import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.Try

case class WalletBox[P <: Proposition, B <: Box[P]](box: B, transactionId: scorex.core.ModifierId, createdAt: Long)
                                                   (boxDeserializer: Serializer[B]) extends BytesSerializable
  with scorex.core.utils.ScorexEncoding { // To Do: check ScorexEncoding

  override type M = WalletBox[P, B]

  override def serializer: Serializer[WalletBox[P, B]] = new WalletBoxSerializer(boxDeserializer)

  //override def toString: String = s"WalletBox($box, ${encoder.encode(transactionId)}, $createdAt)"
}


class WalletBoxSerializer[P <: Proposition, B <: Box[P]](boxDeserializer: Serializer[B]) extends Serializer[WalletBox[P, B]] {
  override def toBytes(box: WalletBox[P, B]): Array[Byte] = ??? /*{
    Bytes.concat(idToBytes(box.transactionId), Longs.toByteArray(box.createdAt), box.box.bytes)
  }*/

  override def parseBytes(bytes: Array[Byte]): Try[WalletBox[P, B]] = ??? /*Try {
    val txId = bytesToId(bytes.slice(0, NodeViewModifier.ModifierIdSize))
    val createdAt = Longs.fromByteArray(
      bytes.slice(NodeViewModifier.ModifierIdSize, NodeViewModifier.ModifierIdSize + 8))
    val boxB = bytes.slice(NodeViewModifier.ModifierIdSize + 8, bytes.length)
    val box: B = boxDeserializer.parseBytes(boxB).get
    WalletBox[P, B](box, txId, createdAt)(boxDeserializer)
  }*/
}

trait Wallet[S <: Secret, P <: ProofOfKnowledgeProposition[S], TX <: Transaction, PMOD <: scorex.core.block.Block[TX], W <: Wallet[S, P, TX, PMOD, W]]
  extends scorex.core.transaction.wallet.Vault[TX, PMOD, W] {
  self: W =>

  def addSecret(secret: S): Try[W]

  def removeSecret(publicImage: P): Try[W]

  def secret(publicImage: P): Option[S]

  def secrets(): Set[S]

  def boxes(): Seq[WalletBox[P, _ <: Box[P]]]

  def publicKeys(): Set[P]
}
