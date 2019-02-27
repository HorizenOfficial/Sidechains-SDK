package com.horizen.block

import akka.actor.FSM.Failure
import com.horizen.utils.{BytesUtils, Utils}
import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.Try

//
// Representation of MC header
//
// Note: Horizen MC Block header should be updated by SCMap merkle root.
// SCMap merkle root is a merkle root of particular SC related transactions merkle roots.
//
// SCMap is a map of <sidechain Id> : <sidechain merkle root hash>
// hashSCMerkleRootsMap calculated as a merkle roots of values only of SCMap sorted by key(<sidechain id>)
//
class MainchainHeader(
                       val mainchainHeaderBytes: Array[Byte], // for Serialization/Deserialization
                       val version: Int,                      // 4 bytes
                       val hashPrevBlock: Array[Byte],        // 32 bytes
                       val hashMerkleRoot: Array[Byte],       // 32 bytes
                       val hashSCMerkleRootsMap: Array[Byte], // 32 bytes
                       val time: Int,                         // 4 bytes
                       val bits: Int,                         // 4 bytes
                       val nonce: Int                         // 4 bytes
                    ) extends BytesSerializable {

  def hash(): Array[Byte] = Utils.doubleSHA256Hash(mainchainHeaderBytes)

  override type M = MainchainHeader

  override def serializer: Serializer[MainchainHeader] = MainchainHeaderSerializer
}


object MainchainHeader {
  val HEADER_SIZE: Int = 112

  def create(headerBytes: Array[Byte]): Try[MainchainHeader] = Try {
    if(headerBytes.length != HEADER_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var offset: Int = 0

    val version: Int = BytesUtils.getInt(headerBytes, offset)
    offset += 4

    val hashPrevBlock: Array[Byte] = headerBytes.slice(offset, offset + 32)
    offset += 32

    val merkleRoot: Array[Byte] = headerBytes.slice(offset, offset + 32)
    offset += 32

    val SCMapMerkleRoot: Array[Byte] = headerBytes.slice(offset, offset + 32)
    offset += 32

    val time: Int = BytesUtils.getInt(headerBytes, offset)
    offset += 4

    val bits: Int = BytesUtils.getInt(headerBytes, offset)
    offset += 4

    val nonce: Int = BytesUtils.getInt(headerBytes, offset)
    offset += 4

    new MainchainHeader(headerBytes, version, hashPrevBlock, merkleRoot, SCMapMerkleRoot, time, bits, nonce)
  }
}

object MainchainHeaderSerializer extends Serializer[MainchainHeader] {
  override def toBytes(obj: MainchainHeader): Array[Byte] = obj.mainchainHeaderBytes

  override def parseBytes(bytes: Array[Byte]): Try[MainchainHeader] = MainchainHeader.create(bytes)
}
