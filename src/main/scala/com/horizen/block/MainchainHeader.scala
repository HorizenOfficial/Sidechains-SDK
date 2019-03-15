package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils, VarInt}
import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.Try

//
// Representation of MC header
//
// Note: Horizen MC Block header should be updated by SCMap merkle root hash.
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
                       val hashReserved: Array[Byte],         // 32 bytes
                       val hashSCMerkleRootsMap: Array[Byte], // 32 bytes
                       val time: Int,                         // 4 bytes
                       val bits: Int,                         // 4 bytes
                       val nonce: Array[Byte],                // 32 bytes
                       val solution: Array[Byte]              // 32 bytes
                    ) extends BytesSerializable {

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(mainchainHeaderBytes))

  override type M = MainchainHeader

  override def serializer: Serializer[MainchainHeader] = MainchainHeaderSerializer

  def semanticValidity(): Boolean = {
    if(hashPrevBlock == null || hashPrevBlock.length != 32
        || hashMerkleRoot == null || hashMerkleRoot.length != 32
        || hashReserved == null || hashReserved.length != 32
        || hashSCMerkleRootsMap == null || hashSCMerkleRootsMap.length != 32
        || nonce == null || nonce.length != 32
        || solution == null || solution.length != 32
        || time <= 0
      )
      return false

    true
  }
}


object MainchainHeader {
  val SCMAP_BLOCK_VERSION: Int = 0xFFFFFFFC // -4
  val MIN_HEADER_SIZE: Int = 140 + 3 + 1344 // + 32 (for SCMapHash size)

  def create(headerBytes: Array[Byte], offset: Int): Try[MainchainHeader] = Try {
    if(offset < 0 || headerBytes.length - offset < MIN_HEADER_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val version: Int = BytesUtils.getReversedInt(headerBytes, currentOffset)
    currentOffset += 4

    val hashPrevBlock: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val merkleRoot: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val hashReserved: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val SCMapMerkleRoot: Array[Byte] = version match {
      case SCMAP_BLOCK_VERSION =>
        val tmpOffset = currentOffset
        currentOffset += 32
        headerBytes.slice(tmpOffset, currentOffset)
      case _ =>
        new Array[Byte](32)
    }

    val time: Int = BytesUtils.getReversedInt(headerBytes, currentOffset)
    currentOffset += 4

    val bits: Int = BytesUtils.getReversedInt(headerBytes, currentOffset)
    currentOffset += 4

    val nonce: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    // To Do: expect here a VarInt = 1344, but the value is wrong. Why?
    val solutionLengthBytes: Array[Byte] = headerBytes.slice(currentOffset, currentOffset + 3)
    currentOffset += 3

    val solution: Array[Byte] = headerBytes.slice(currentOffset, currentOffset + 1344)
    currentOffset += 1344

    new MainchainHeader(headerBytes.slice(offset, currentOffset), version, hashPrevBlock, merkleRoot, hashReserved, SCMapMerkleRoot, time, bits, nonce, solution)
  }
}

object MainchainHeaderSerializer extends Serializer[MainchainHeader] {
  override def toBytes(obj: MainchainHeader): Array[Byte] = obj.mainchainHeaderBytes

  override def parseBytes(bytes: Array[Byte]): Try[MainchainHeader] = MainchainHeader.create(bytes, 0)
}
