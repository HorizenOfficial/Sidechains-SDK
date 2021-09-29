package com.horizen.fixtures

import java.time.Instant
import java.util.Random

import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, SidechainBlock}
import com.horizen.chain.{MainchainHeaderHash, byteArrayToMainchainHeaderHash}
import com.horizen.params.NetworkParams
import com.horizen.utils._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Success, Try}

trait MainchainBlockReferenceFixture extends MainchainHeaderFixture {
  def setSeed(seed: Long): Unit = util.Random.setSeed(seed)

  def generateBytes(size: Int = 32, rnd: Random = new Random()): Array[Byte] = {
    val res: Array[Byte] = new Array[Byte](size)
    rnd.nextBytes(res)
    res
  }

  def generateMainchainHeaderHash(seed: Long): MainchainHeaderHash = {
    val rnd: Random = new Random(seed)
    byteArrayToMainchainHeaderHash(generateBytes(32, rnd))
  }

  private val generatedMainchainBlockReferences =
    new mutable.HashMap[MainchainHeaderHash, MainchainBlockReference]()

  private val initialMainchainBlockReferenceHeader = new MainchainHeader(generateBytes(),
    -1, generateBytes(), new Array[Byte](0), new Array[Byte](0), Instant.now.getEpochSecond.toInt, 0,
    new Array[Byte](0), new Array[Byte](0))

  private var lastGeneratedHash: ByteArrayWrapper = initialMainchainBlockReferenceHeader.hash


  private def addNewReference(ref: MainchainBlockReference): Unit = {
    generatedMainchainBlockReferences.put(byteArrayToMainchainHeaderHash(ref.header.hash), ref)
    lastGeneratedHash = ref.header.hash
  }

  def generateMainchainBlockReference(parentOpt: Option[ByteArrayWrapper] = None,
                                      blockHash: Option[Array[Byte]] = None,
                                      rnd: Random = new Random(),
                                      timestamp: Int = Instant.now.getEpochSecond.toInt
                                     ): MainchainBlockReference = {
    val mainchainHeaderBytes = generateBytes(rnd = rnd)

    val parent = parentOpt.getOrElse(lastGeneratedHash)
    val headerWithNoSerialization = new MainchainHeader(
      mainchainHeaderBytes,
      version = 1,
      parent,
      generateBytes(rnd = rnd),
      new Array[Byte](32),
      timestamp,
      rnd.nextInt(),
      generateBytes(rnd = rnd),
      generateBytes(size = 1344, rnd = rnd))

    val header: MainchainHeader = blockHash match {
      case Some(hashData) => new MainchainHeader(
          mainchainHeaderToBytes(headerWithNoSerialization),
          headerWithNoSerialization.version,
          headerWithNoSerialization.hashPrevBlock,
          headerWithNoSerialization.hashMerkleRoot,
          headerWithNoSerialization.hashScTxsCommitment,
          headerWithNoSerialization.time,
          headerWithNoSerialization.bits,
          headerWithNoSerialization.nonce,
          headerWithNoSerialization.solution) {
            val h = hashData
            override lazy val hash: Array[Byte] = h
            override def semanticValidity(params: NetworkParams): Try[Unit] = Success(Unit)
        }
      case None => new MainchainHeader(
        mainchainHeaderToBytes(headerWithNoSerialization),
        headerWithNoSerialization.version,
        headerWithNoSerialization.hashPrevBlock,
        headerWithNoSerialization.hashMerkleRoot,
        headerWithNoSerialization.hashScTxsCommitment,
        headerWithNoSerialization.time,
        headerWithNoSerialization.bits,
        headerWithNoSerialization.nonce,
        headerWithNoSerialization.solution) {
          override def semanticValidity(params: NetworkParams):  Try[Unit] = Success(Unit)
      }
    }

    val newReference = new MainchainBlockReference(header, MainchainBlockReferenceData(header.hash, None, None, None, Seq(), None)) {
      override def semanticValidity(params: NetworkParams): Try[Unit] = Success(Unit)
    }

    addNewReference(newReference)
    newReference
  }

  @tailrec
  final def generateMainchainReferences(generated: Seq[MainchainBlockReference] = Seq(),
                                        parentOpt: Option[ByteArrayWrapper] = None,
                                        rnd: Random = new Random(),
                                        timestamp: Int = Instant.now.getEpochSecond.toInt
                                       ): Seq[MainchainBlockReference] = {
    val maxMcBlockRefDataPerBlock = 3
    if (rnd.nextBoolean && generated.size < maxMcBlockRefDataPerBlock) {
      val parentReference = parentOpt.orElse(generated.lastOption.map(lastBlock => byteArrayToWrapper(lastBlock.header.hash)))
      val nextReference = generateMainchainBlockReference(parentOpt = parentReference, rnd = rnd, timestamp = timestamp)
      generateMainchainReferences(generated :+ nextReference, rnd = rnd, timestamp = timestamp)
    }
    else {
      generated
    }
  }
}
