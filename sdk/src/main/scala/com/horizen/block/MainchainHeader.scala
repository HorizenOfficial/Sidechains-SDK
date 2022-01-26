package com.horizen.block

import java.time.Instant

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.utils.{BytesUtils, Utils}
import com.horizen.validation.{InvalidMainchainHeaderException, MainchainHeaderTimestampInFutureException}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

import scala.util.Try

// Representation of MC header
@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("mainchainHeaderBytes", "hashHex"))
class MainchainHeader(
                       val mainchainHeaderBytes: Array[Byte], // for Serialization/Deserialization
                       val version: Int, // 4 bytes
                       val hashPrevBlock: Array[Byte], // 32 bytes
                       val hashMerkleRoot: Array[Byte], // 32 bytes
                       val hashScTxsCommitment: Array[Byte], // 32 bytes
                       val time: Int, // 4 bytes
                       val bits: Int, // 4 bytes
                       val nonce: Array[Byte], // 32 bytes
                       val solution: Array[Byte] // depends on NetworkParams
                     )
  extends BytesSerializable {

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(mainchainHeaderBytes))

  lazy val hashHex: String = BytesUtils.toHexString(hash)

  override type M = MainchainHeader

  override def serializer: ScorexSerializer[MainchainHeader] = MainchainHeaderSerializer

  // IMPORTANT:
  // Current method must firstly check for critical errors, that will permanently invalidate MainchainHeader.
  // Only than for non-critical errors, that will temporary invalidate MainchainHeader.
  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    if(hashPrevBlock == null || hashPrevBlock.length != 32
      || hashMerkleRoot == null || hashMerkleRoot.length != 32
      || hashScTxsCommitment == null || hashScTxsCommitment.length != 32
      || nonce == null || nonce.length != 32
      || solution == null || solution.length != params.EquihashSolutionLength // Note: Solution length depends on Equihash (N, K) params
      || time <= 0)
      throw new InvalidMainchainHeaderException("MainchainHeader contains null or out of bound fields.")

    if (!ProofOfWorkVerifier.checkProofOfWork(this, params))
      throw new InvalidMainchainHeaderException(s"MainchainHeader $hashHex PoW is invalid.")

    // check equihash for header bytes without solution part
    if (!new Equihash(params.EquihashN, params.EquihashK).checkEquihashSolution(
      mainchainHeaderBytes.slice(0, mainchainHeaderBytes.length - params.EquihashVarIntLength - params.EquihashSolutionLength),
      solution)
    )
      throw new InvalidMainchainHeaderException(s"MainchainHeader $hashHex Equihash solution is invalid.")

    // Check if timestamp is not too far in the future
    if (time > Instant.now.getEpochSecond + 2 * 60 * 60) // 2 * 60 * 60 like in Horizen
      throw new MainchainHeaderTimestampInFutureException(s"MainchainHeader $hashHex time $time is too far in future.")
  }

  def isParentOf(header: MainchainHeader): Boolean = header.hashPrevBlock.sameElements(hash)

  override def hashCode(): Int = java.util.Arrays.hashCode(mainchainHeaderBytes)

  override def equals(obj: Any): Boolean = {
    obj match {
      case header: MainchainHeader => hash.sameElements(header.hash)
      case _ => false
    }
  }


  override def toString = s"MainchainHeader($mainchainHeaderBytes, $version, $hashPrevBlock, $hashMerkleRoot, $hashScTxsCommitment, $time, $bits, $nonce, $solution)"
}


object MainchainHeader {
  val HEADER_MIN_SIZE: Int = 140 // HEADER_SIZE = 140 + equihash size

  def create(headerBytes: Array[Byte], offset: Int): Try[MainchainHeader] = Try {
    if(offset < 0 || headerBytes.length - offset < HEADER_MIN_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val version: Int = BytesUtils.getReversedInt(headerBytes, currentOffset)
    currentOffset += 4

    val hashPrevBlock: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val merkleRoot: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val hashScTxsCommitment: Array[Byte] = BytesUtils.reverseBytes(headerBytes.slice(currentOffset, currentOffset + 32))
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

    new MainchainHeader(headerBytes.slice(offset, currentOffset), version, hashPrevBlock, merkleRoot, hashScTxsCommitment, time, bits, nonce, solution)
  }
}

object MainchainHeaderSerializer extends ScorexSerializer[MainchainHeader] {
  override def serialize(obj: MainchainHeader, w: Writer): Unit = w.putBytes(obj.mainchainHeaderBytes)

  override def parse(r: Reader): MainchainHeader = MainchainHeader.create(r.getBytes(r.remaining), 0).get
}
