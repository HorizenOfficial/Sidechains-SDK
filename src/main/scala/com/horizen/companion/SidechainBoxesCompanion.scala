package com.horizen.companion

import com.horizen.box._
import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.{Failure, Try}
import com.google.common.primitives.Bytes

import scala.collection.mutable.{Map, HashMap}

case class SidechainBoxesCompanion(customBoxSerializers: Map[Byte, BoxSerializer[_ <: Box[_]]])
    extends Serializer[Box[_]]
{

  val coreBoxSerializers: Map[Byte, _ <: BoxSerializer[Box[_]]] =
    Map(RegularBox.BOX_TYPE_ID -> RegularBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[Box[_]]],
      CertifierRightBox.BOX_TYPE_ID -> CertifierRightBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[Box[_]]])

  val customBoxType : Byte =  Byte.MaxValue // TO DO: think about proper value

  // TO DO: do like in SidechainTransactionsCompanion
  override def toBytes(box: Box[_]): Array[Byte] = {
    box match {
      case b: RegularBox => Bytes.concat(Array(box.boxTypeId()),
        box.serializer().asInstanceOf[BoxSerializer[Box[_]]].toBytes(box))
      case b: CertifierRightBox => Bytes.concat(Array(box.boxTypeId()),
        box.serializer().asInstanceOf[BoxSerializer[Box[_]]].toBytes(box))
      case _ => Bytes.concat(Array(customBoxType), Array(box.boxTypeId()),
        box.serializer().asInstanceOf[BoxSerializer[Box[_]]].toBytes(box))
    }
  }

  // TO DO: do like in SidechainTransactionsCompanion
  override def parseBytes(bytes: Array[Byte]): Try[Box[_]] = {
    val boxType = bytes(0)
    //val boxTypeId = bytes(1)
    boxType match {
      case `customBoxType` => customBoxSerializers.get(bytes(1)) match {
        case Some(b) => b.parseBytes(bytes.drop(2))
        case None => Failure(new MatchError("Unknown custom box type id"))
      }
      case _ => coreBoxSerializers.get(boxType) match {
        case Some(b) => b.parseBytes(bytes.drop(1))
        case None => Failure(new MatchError("Unknown core box type id"))
      }
    }
  }
}

object SidechainBoxesCompanion {
  val sidechainBoxesCompanion : SidechainBoxesCompanion = new SidechainBoxesCompanion(new HashMap[scorex.core.ModifierTypeId.Raw, BoxSerializer[_ <: Box[_]]]())
}

