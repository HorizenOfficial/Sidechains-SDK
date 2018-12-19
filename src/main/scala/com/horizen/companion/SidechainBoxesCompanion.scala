package com.horizen.companion

import com.horizen.box.{Box, BoxSerializer, RegularBox, RegularBoxSerializer}
import com.horizen.proposition.Proposition
import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.Try

case class SidechainBoxesCompanion(customBoxSerializers: Map[scorex.core.ModifierTypeId, BoxSerializer[_ <: Box[_ <: Proposition]]])
    extends Serializer[Box[_ <: Proposition]] {

  val coreBoxSerializers: Map[scorex.core.ModifierTypeId, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(new RegularBox(/*args*/).boxTypeId() -> new RegularBoxSerializer())

  val customBoxId = ModifierTypeId @@ 0xFF // TODO: think about proper value

  // TO DO: do like in SidechainTransactionsCompanion
  override def toBytes(obj: Box[_ <: Proposition]): Array[Byte] = ???

  // TO DO: do like in SidechainTransactionsCompanion
  override def parseBytes(bytes: Array[Byte]): Try[Box[_ <: Proposition]] = ???
}

