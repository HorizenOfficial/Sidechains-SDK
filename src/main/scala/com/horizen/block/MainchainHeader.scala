package com.horizen.block

import java.math.BigInteger

import com.horizen.utils.{BytesUtils, Utils}
import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.Try
import java.time.Instant
import com.google.common.primitives.UnsignedInts

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
                       val solution: Array[Byte]              // 1344 bytes + 3 bytes representing length
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
        || solution == null || solution.length != 1344 // Note: actually solution length depends on Equihash (N, K) params, which are different for RegTest
      )
      return false

    // Check if timestamp is valid and not too far in the future
    if(time <= 0 || time > Instant.now.getEpochSecond + 2 * 60 * 60) // 2* 60 * 60 like in Horizen
      return false

    if(!checkProofOfWork())
      return false

    // check equihash for header bytes without solution part
    if(!new Equihash(200, 9).checkEquihashSolution(mainchainHeaderBytes.slice(0, mainchainHeaderBytes.length - 1344 - 3), solution))
      return false
    true
  }

  private def checkProofOfWork(): Boolean = {
    val target: BigInteger = Utils.decodeCompactBits(UnsignedInts.toLong(bits))
    val maxTarget: BigInteger = new BigInteger("0007ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16) // defined in Horizen Consensus::params.powLimit
    val hashTarget: BigInteger = new BigInteger(1, hash)

    // Check that target is not negative and is not below the minimum work defined in Horizen
    if(target.signum() <= 0 || target.compareTo(maxTarget) > 0)
      return false

    // Check that block hash target is not greater than target.
    if(hashTarget.compareTo(target) > 0)
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

    val hashSCMerkleRootsMap: Array[Byte] = version match {
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

    val solutionLength =  BytesUtils.getReversedVarInt(headerBytes, currentOffset)
    currentOffset += solutionLength.size()

    val solution: Array[Byte] = headerBytes.slice(currentOffset, currentOffset + solutionLength.value().intValue())
    currentOffset += solutionLength.value().intValue()

    new MainchainHeader(headerBytes.slice(offset, currentOffset), version, hashPrevBlock, merkleRoot, hashReserved, hashSCMerkleRootsMap, time, bits, nonce, solution)
  }
}

object MainchainHeaderSerializer extends Serializer[MainchainHeader] {
  override def toBytes(obj: MainchainHeader): Array[Byte] = obj.mainchainHeaderBytes

  override def parseBytes(bytes: Array[Byte]): Try[MainchainHeader] = MainchainHeader.create(bytes, 0)
}
