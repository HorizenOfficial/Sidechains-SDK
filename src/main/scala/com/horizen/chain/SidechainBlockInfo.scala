package com.horizen.chain

import scorex.core.consensus.ModifierSemanticValidity
import scorex.core.serialization.BytesSerializable
import scorex.core.serialization.Serializer
import scorex.util.ModifierId

import scala.util.Try

case class SidechainBlockInfo(//id: ModifierId,
                              height: Int,
                              score: Long,
                              parentId: ModifierId,
                              semanticValidity: ModifierSemanticValidity) extends BytesSerializable {

  override type M = SidechainBlockInfo

  override lazy val serializer = SidechainBlockInfoSerializer
}


object SidechainBlockInfoSerializer extends Serializer[SidechainBlockInfo] {
  override def toBytes(obj: SidechainBlockInfo): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[SidechainBlockInfo] = ???
}
