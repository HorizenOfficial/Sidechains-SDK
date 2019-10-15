package com.horizen.block

import java.time.Instant

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.utils.{BytesUtils, Utils}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.core.utils.ScorexEncoder
import scorex.util.serialization.{Reader, Writer}

import scala.collection.mutable
import scala.util.Try

//
// Representation of MC header
//
// hashSCMerkleRootsMap is a merkle root hash of particular SC related crosschain outputs data merkle roots.
//
// SCMap is a map of <sidechain Id> : <sidechain merkle root hash>
// hashSCMerkleRootsMap calculated as a merkle roots of values only of SCMap sorted by key(<sidechain id>)
//
@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("hash", "hashHex"))
class MainchainHeader(
                       val mainchainHeaderBytes: Array[Byte], // for Serialization/Deserialization
                       val version: Int,                      // 4 bytes
                       val hashPrevBlock: Array[Byte],        // 32 bytes
                       val hashMerkleRoot: Array[Byte],       // 32 bytes
                       val hashSCMerkleRootsMap: Array[Byte], // 32 bytes
                       val time: Int, // 4 bytes
                       val bits: Int, // 4 bytes
                       val nonce: Array[Byte], // 32 bytes
                       val solution: Array[Byte] // depends on NetworkParams
                     )
  extends BytesSerializable {

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(mainchainHeaderBytes))

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  override type M = MainchainHeader
  /*override type J = MainchainHeader*/

  override def serializer: ScorexSerializer[MainchainHeader] = MainchainHeaderSerializer

  def semanticValidity(params: NetworkParams): Boolean = {
    if(hashPrevBlock == null || hashPrevBlock.length != 32
        || hashMerkleRoot == null || hashMerkleRoot.length != 32
        || hashSCMerkleRootsMap == null || hashSCMerkleRootsMap.length != 32
        || nonce == null || nonce.length != 32
        || solution == null || solution.length != params.EquihashSolutionLength // Note: Solution length depends on Equihash (N, K) params
      )
      return false

    // Check if timestamp is valid and not too far in the future
    if (time <= 0 || time > Instant.now.getEpochSecond + 2 * 60 * 60) // 2 * 60 * 60 like in Horizen
      return false

    if (!ProofOfWorkVerifier.checkProofOfWork(this, params))
      return false

    // check equihash for header bytes without solution part
    if (!new Equihash(params.EquihashN, params.EquihashK).checkEquihashSolution(
      mainchainHeaderBytes.slice(0, mainchainHeaderBytes.length - params.EquihashVarIntLength - params.EquihashSolutionLength),
      solution)
    )
      return false
    true
  }

  override def bytes: Array[Byte] = mainchainHeaderBytes
}


object MainchainHeader {
  val HEADER_SIZE: Int = 140

  def create(headerBytes: Array[Byte], offset: Int): Try[MainchainHeader] = Try {
    if(offset < 0 || headerBytes.length - offset < HEADER_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val version: Int = BytesUtils.getReversedInt(headerBytes, currentOffset)
    currentOffset += 4

    val hashPrevBlock: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val merkleRoot: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val hashSCMerkleRootsMap: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val time: Int = BytesUtils.getReversedInt(headerBytes, currentOffset)
    currentOffset += 4

    val bits: Int = BytesUtils.getReversedInt(headerBytes, currentOffset)
    currentOffset += 4

    val nonce: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    // @TODO check: getReversedVarInt works correctly with BytesUtils.fromVarInt (not reversed)
    val solutionLength = BytesUtils.getReversedVarInt(headerBytes, currentOffset)
    currentOffset += solutionLength.size()

    val solution: Array[Byte] = headerBytes.slice(currentOffset, currentOffset + solutionLength.value().intValue())
    currentOffset += solutionLength.value().intValue()

    new MainchainHeader(headerBytes.slice(offset, currentOffset), version, hashPrevBlock, merkleRoot, hashSCMerkleRootsMap, time, bits, nonce, solution)
  }
}

object MainchainHeaderSerializer extends ScorexSerializer[MainchainHeader] {
  override def serialize(obj: MainchainHeader, w: Writer): Unit = w.putBytes(obj.bytes)

  override def parse(r: Reader): MainchainHeader = MainchainHeader.create(r.getBytes(r.remaining), 0).get
}
