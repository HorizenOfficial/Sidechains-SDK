package com.horizen.chain

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.block.SidechainBlock
import com.horizen.cryptolibprovider.CumulativeHashFunctions
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import scorex.util.serialization.{Reader, Writer}

import scala.collection.mutable.ArrayBuffer

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("serializer"))
case class MainchainHeaderBaseInfo (hash: MainchainHeaderHash,
                                    cumulativeCommTreeHash: Array[Byte]) extends BytesSerializable {
  override type M = MainchainHeaderBaseInfo

  override lazy val serializer: SparkzSerializer[MainchainHeaderBaseInfo] = MainchainHeaderBaseInfoSerializer

  override def equals(obj: Any): Boolean = {
    obj match {
      case headerBaseInfo:M  => {
        (headerBaseInfo.hash == this.hash) && (headerBaseInfo.cumulativeCommTreeHash.deep == this.cumulativeCommTreeHash.deep)
      }
      case _ => false
    }
  }
}

object MainchainHeaderBaseInfo {
  def getMainchainHeaderBaseInfoSeqFromBlock(sidechainBlock: SidechainBlock, initialCumulativeHash: Array[Byte]): Seq[MainchainHeaderBaseInfo] = {
    val mcHeaderBaseInfoList: ArrayBuffer[MainchainHeaderBaseInfo] = ArrayBuffer()
    var prevCumulativeHash: Array[Byte] = initialCumulativeHash

    sidechainBlock.mainchainHeaders.foreach(header => {
      val cumulativeHash = CumulativeHashFunctions.computeCumulativeHash(prevCumulativeHash, BytesUtils.reverseBytes(header.hashScTxsCommitment))
      mcHeaderBaseInfoList.append(MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(header.hash), cumulativeHash))
      prevCumulativeHash = cumulativeHash
    })

    mcHeaderBaseInfoList
  }
}

object MainchainHeaderBaseInfoSerializer extends SparkzSerializer[MainchainHeaderBaseInfo] {
  override def serialize(obj: MainchainHeaderBaseInfo, w: Writer): Unit = {
    w.putBytes(obj.hash.data)
    w.putBytes(obj.cumulativeCommTreeHash)
  }

  override def parse(r: Reader): MainchainHeaderBaseInfo = {
    val headerHashBytes = r.getBytes(mainchainHeaderHashSize)
    val headerHash: MainchainHeaderHash = (byteArrayToMainchainHeaderHash(headerHashBytes))
    val cumulativeCommTreeHash = r.getBytes(CumulativeHashFunctions.hashLength())
    MainchainHeaderBaseInfo(headerHash, cumulativeCommTreeHash)
  }
}