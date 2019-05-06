package com.horizen.companion

import com.horizen.box._
import com.horizen.proposition._

import scorex.core.serialization.Serializer

import scala.util.{Failure, Try}
import com.google.common.primitives.Bytes

import scala.collection.mutable.{Map, HashMap}

case class SidechainBoxesCompanion(customBoxSerializers: Map[Byte, BoxSerializer[_ <: Box[_ <: Proposition]]])
    extends Serializer[Box[_ <: Proposition]]
{

  val coreBoxSerializers: Map[Byte, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(RegularBox.BOX_TYPE_ID -> RegularBoxSerializer.getSerializer,
      CertifierRightBox.BOX_TYPE_ID -> CertifierRightBoxSerializer.getSerializer)

  val customBoxType : Byte =  Byte.MaxValue

  override def toBytes(box: Box[_ <: Proposition]): Array[Byte] = {
    box match {
      case b: RegularBox => Bytes.concat(Array(b.boxTypeId()),
        RegularBoxSerializer.getSerializer.toBytes(b))
      case b: CertifierRightBox => Bytes.concat(Array(b.boxTypeId()),
        CertifierRightBoxSerializer.getSerializer.toBytes(b))
      case _ => customBoxSerializers.get(box.boxTypeId()) match {
        case Some(serializer) => Bytes.concat(Array(customBoxType), Array(box.boxTypeId()),
          serializer.asInstanceOf[Serializer[Box[_ <: Proposition]]].toBytes(box))
        case None => throw new IllegalArgumentException("Unknown box type - " + box)
      }
    }
  }

  // TO DO: do like in SidechainTransactionsCompanion
  override def parseBytes(bytes: Array[Byte]): Try[Box[_ <: Proposition]] = {
    val boxType = bytes(0)
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

