package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}

import scala.util.Try
import java.time.Instant

import com.horizen.params.NetworkParams
import scorex.util.serialization.{Reader, Writer}

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
                       val solution: Array[Byte]              // depends on NetworkParams
                    ) extends BytesSerializable {

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(mainchainHeaderBytes))

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  override type M = MainchainHeader

  override def serializer: ScorexSerializer[MainchainHeader] = MainchainHeaderSerializer

  def semanticValidity(params: NetworkParams): Boolean = {
    if(hashPrevBlock == null || hashPrevBlock.length != 32
        || hashMerkleRoot == null || hashMerkleRoot.length != 32
        || hashReserved == null || hashReserved.length != 32
        || hashSCMerkleRootsMap == null || hashSCMerkleRootsMap.length != 32
        || nonce == null || nonce.length != 32
        || solution == null || solution.length != params.EquihashSolutionLength // Note: Solution length depends on Equihash (N, K) params
      )
      return false

    // Check if timestamp is valid and not too far in the future
    if(time <= 0 || time > Instant.now.getEpochSecond + 2 * 60 * 60) // 2 * 60 * 60 like in Horizen
      return false

    if(!ProofOfWorkVerifier.checkProofOfWork(this, params))
      return false

    // check equihash for header bytes without solution part
    if(!new Equihash(params.EquihashN, params.EquihashK).checkEquihashSolution(
        mainchainHeaderBytes.slice(0, mainchainHeaderBytes.length - params.EquihashVarIntLength - params.EquihashSolutionLength),
        solution)
    )
      return false
    true
  }
}


object MainchainHeader {
  val SCMAP_BLOCK_VERSION: Int = 0xFFFFFFFC // -4
  val MIN_HEADER_SIZE: Int = 140 // + 32 (for SCMapHash size)

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

object MainchainHeaderSerializer extends ScorexSerializer[MainchainHeader] {
  override def toBytes(obj: MainchainHeader): Array[Byte] = obj.mainchainHeaderBytes

  override def parseBytesTry(bytes: Array[Byte]): Try[MainchainHeader] = MainchainHeader.create(bytes, 0)

  override def serialize(obj: MainchainHeader, w: Writer): Unit = ???

  override def parse(r: Reader): MainchainHeader = ???
}
