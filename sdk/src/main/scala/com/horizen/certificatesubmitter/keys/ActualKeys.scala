package com.horizen.certificatesubmitter.keys

import com.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import com.horizen.utils.MerkleTree
import scorex.crypto.hash.Sha256
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

import scala.collection.JavaConverters.seqAsJavaListConverter

case class ActualKeys(signingKeys: Vector[SchnorrProposition], masterKeys: Vector[SchnorrProposition]) extends BytesSerializable {
  override type M = ActualKeys

  override def serializer: SparkzSerializer[ActualKeys] = ActualKeysSerializer

  def getMerkleRootOfPublicKeys: Array[Byte] = {
    val hashes = (for(i <- signingKeys.indices) yield {
      Sha256.hash(signingKeys(i).pubKeyBytes(), masterKeys(i).pubKeyBytes()).asInstanceOf[Array[Byte]]
    }).toList.asJava
    MerkleTree.createMerkleTree(hashes).rootHash()
  }
}

object ActualKeysSerializer extends SparkzSerializer[ActualKeys] {
  override def serialize(actualKeys: ActualKeys, writer: Writer): Unit = {
    writer.putInt(actualKeys.signingKeys.length)
    actualKeys.signingKeys.foreach(SchnorrPropositionSerializer.getSerializer.serialize(_, writer))
    writer.putInt(actualKeys.masterKeys.length)
    actualKeys.masterKeys.foreach(SchnorrPropositionSerializer.getSerializer.serialize(_, writer))
  }

  override def parse(reader: Reader): ActualKeys = {
    val signingKeysArraySize: Int = reader.getInt()
    val signingKeys: Seq[SchnorrProposition] = for(_ <- 0 until signingKeysArraySize) yield {
      SchnorrPropositionSerializer.getSerializer.parse(reader)
    }

    val masterKeysArraySize: Int = reader.getInt()
    val masterKeys: Seq[SchnorrProposition] = for (_ <- 0 until masterKeysArraySize) yield {
      SchnorrPropositionSerializer.getSerializer.parse(reader)
    }

    ActualKeys(signingKeys.toVector, masterKeys.toVector)
  }
}
