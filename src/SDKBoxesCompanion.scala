import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.Try

case class SDKBoxesCompanion(customBoxSerializers: Map[scorex.core.ModifierTypeId, BoxSerializer[_ <: Box[_ <: Proposition]]])
    extends Serializer[Box[_ <: Proposition]] {

  val coreBoxSerializers: Map[scorex.core.ModifierTypeId, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(new RegularBox(/*args*/).boxTypeId() -> new RegularBoxSerializer())

  val customBoxId = ModifierTypeId @@ 0xFF // TODO: think about proper value

  // TO DO: do like in SDKTransactionsCompanion
  override def toBytes(obj: Box[_ <: Proposition]): Array[Byte] = ???

  // TO DO: do like in SDKTransactionsCompanion
  override def parseBytes(bytes: Array[Byte]): Try[Box[_ <: Proposition]] = ???
}

