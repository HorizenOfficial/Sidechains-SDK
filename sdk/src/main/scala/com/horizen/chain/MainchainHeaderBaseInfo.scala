package com.horizen.chain

import com.horizen.block.SidechainBlock
import com.horizen.cryptolibprovider.CumulativeHashFunctions
import com.horizen.librustsidechains.FieldElement
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import scala.collection.mutable.ArrayBuffer

case class MainchainHeaderBaseInfo (hash: MainchainHeaderHash,
                                    cumulativeCommTreeHash: FieldElement) extends BytesSerializable {
  override type M = MainchainHeaderBaseInfo

  override lazy val serializer: ScorexSerializer[MainchainHeaderBaseInfo] = McHeaderBaseInfoSerializer

  override def bytes: Array[Byte] = McHeaderBaseInfoSerializer.toBytes(this)
}

object MainchainHeaderBaseInfo {
  def getSerializer():ScorexSerializer[MainchainHeaderBaseInfo] = { McHeaderBaseInfoSerializer }

  def getMainchainHeaderBaseInfoFromBlock(sidechainBlock: SidechainBlock, initialCumulativeHash: FieldElement): Seq[MainchainHeaderBaseInfo] = {
    val mcHeaderBaseInfoList: ArrayBuffer[MainchainHeaderBaseInfo] = ArrayBuffer()
    var prevCumulativeHash:FieldElement = initialCumulativeHash

    sidechainBlock.mainchainHeaders.foreach(header => {
      val cumulativeHash = CumulativeHashFunctions.computeCumulativeHash(prevCumulativeHash, FieldElement.deserialize(header.hashScTxsCommitment))
      mcHeaderBaseInfoList.append(MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(header.hash), cumulativeHash))
      prevCumulativeHash = cumulativeHash
    })

    mcHeaderBaseInfoList
  }
}

object McHeaderBaseInfoSerializer extends ScorexSerializer[MainchainHeaderBaseInfo] {
  override def serialize(obj: MainchainHeaderBaseInfo, w: Writer): Unit = {
    w.putBytes(obj.hash.data)
    w.putBytes(obj.cumulativeCommTreeHash.serializeFieldElement)
  }

  override def parse(r: Reader): MainchainHeaderBaseInfo = {
    val headerHashBytes = r.getBytes(mainchainHeaderHashSize)
    val headerHash: MainchainHeaderHash = (byteArrayToMainchainHeaderHash(headerHashBytes))
    val cumHashbytes = r.getBytes(CumulativeHashFunctions.hashLength())
    val cumulativeHash = FieldElement.deserialize(cumHashbytes)
    MainchainHeaderBaseInfo(headerHash, cumulativeHash)
  }
}