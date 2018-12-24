package com.horizen.block

import com.horizen.ScorexEncoding
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction
import scorex.core.{ModifierId, ModifierTypeId, PersistentNodeViewModifier}
import scorex.core.block.Block
import scorex.core.block.Block.{Timestamp, Version}
import scorex.core.serialization.Serializer

import scala.util.Try

class SidechainBlock extends PersistentNodeViewModifier with Block[BoxTransaction[Proposition, Box[Proposition]]]{

  override type M = SidechainBlock

  override lazy val serializer = SidechainBlockSerializer

  override lazy val version = ???

  override lazy val timestamp = ???

  override def parentId: ModifierId = ???

  override val modifierTypeId: ModifierTypeId = SidechainBlock.ModifierTypeId

  override lazy val id = ???

  //...

  def mainchainBlockHash: Option[Array[Byte]] = ???

  def mainchainBlock: Option[MainchainHeader] = ???

  override def transactions: Seq[BoxTransaction[Proposition, Box[Proposition]]] = ???

  // Fraud notification part
}

object SidechainBlock extends ScorexEncoding {
  val ModifierTypeId: ModifierTypeId = scorex.core.ModifierTypeId @@ 4.toByte

  //...

  // TODO: see PosBlock/PowBlock implementation in HybridApp
}



object SidechainBlockSerializer extends Serializer[SidechainBlock] {
  override def toBytes(obj: SidechainBlock): Array[Version] = ???

  override def parseBytes(bytes: Array[Version]): Try[SidechainBlock] = ???
}