package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.serialization.{JsonMerkleRootsSerializer, Views}
import com.horizen.transaction.{MC2SCAggregatedTransaction, MC2SCAggregatedTransactionSerializer}
import com.horizen.utils.ByteArrayWrapper
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import scala.collection.mutable

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("headerHash"))
case class MainchainBlockReferenceData(
                                   headerHash: Array[Byte],
                                   sidechainRelatedAggregatedTransaction: Option[MC2SCAggregatedTransaction],
                                   @JsonProperty("merkleRoots")
                                   @JsonSerialize(using = classOf[JsonMerkleRootsSerializer])
                                   sidechainsMerkleRootsMap: Option[mutable.Map[ByteArrayWrapper, Array[Byte]]]
                                 ) extends BytesSerializable {
  override type M = MainchainBlockReferenceData

  override def serializer: ScorexSerializer[MainchainBlockReferenceData] = MainchainBlockReferenceDataSerializer

  override def hashCode(): Int = java.util.Arrays.hashCode(headerHash)

  override def equals(obj: Any): Boolean = {
    obj match {
      case data: MainchainBlockReferenceData => bytes.sameElements(data.bytes)
      case _ => false
    }
  }
}


object MainchainBlockReferenceDataSerializer extends ScorexSerializer[MainchainBlockReferenceData] {
  val HASH_BYTES_LENGTH: Int = 32

  override def serialize(obj: MainchainBlockReferenceData, w: Writer): Unit = {
    w.putBytes(obj.headerHash)

    obj.sidechainRelatedAggregatedTransaction match {
      case Some(tx) =>
        w.putInt(tx.bytes().length)
        w.putBytes(tx.bytes())
      case _ =>
        w.putInt(0)
    }

    obj.sidechainsMerkleRootsMap match {
      case Some(scmap) =>
        w.putInt(scmap.size * HASH_BYTES_LENGTH * 2)
        scmap.foreach {
          case (k, v) =>
            w.putBytes(k.data)
            w.putBytes(v)
        }
      case _ =>
        w.putInt(0)
    }
  }

  override def parse(r: Reader): MainchainBlockReferenceData = {
    val headerHash: Array[Byte] = r.getBytes(HASH_BYTES_LENGTH)

    val mc2scAggregatedTransactionSize: Int = r.getInt()

    val mc2scTx: Option[MC2SCAggregatedTransaction] = {
      if (mc2scAggregatedTransactionSize > 0)
        Some(MC2SCAggregatedTransactionSerializer.getSerializer.parseBytes(r.getBytes(mc2scAggregatedTransactionSize)))
      else
        None
    }

    val SCMapSize: Int = r.getInt()

    if(SCMapSize != r.remaining)
      throw new IllegalArgumentException("Input data corrupted.")

    val SCMap: Option[mutable.Map[ByteArrayWrapper, Array[Byte]]] = {
      if(SCMapSize > 0) {
        val scmap = mutable.Map[ByteArrayWrapper, Array[Byte]]()
        while(r.remaining > 0) {
          scmap.put(new ByteArrayWrapper(r.getBytes(HASH_BYTES_LENGTH)), r.getBytes(HASH_BYTES_LENGTH))
        }
        Some(scmap)
      }
      else
        None
    }

    MainchainBlockReferenceData(headerHash, mc2scTx, SCMap)
  }
}