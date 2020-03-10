package com.horizen.fixtures

import java.time.Instant

import com.horizen.block.{MainchainBlockReference, MainchainHeader, SidechainBlock}
import com.horizen.chain.{MainchainBlockReferenceHash, byteArrayToMainchainBlockReferenceHash}
import com.horizen.params.NetworkParams
import com.horizen.utils._

import scala.annotation.tailrec
import scala.collection.mutable

trait MainchainBlockReferenceFixture extends MainchainHeaderFixture {
  def setSeed(seed: Long): Unit = util.Random.setSeed(seed)

  def generateBytes(size: Int = 32): Array[Byte] = {
    val res: Array[Byte] = new Array[Byte](size)
    util.Random.nextBytes(res)
    res
  }

  private val generatedMainchainBlockReferences =
    new mutable.HashMap[MainchainBlockReferenceHash, MainchainBlockReference]()

  private val initialMainchainBlockReferenceHeader = new MainchainHeader(generateBytes(),
    -1, generateBytes(), new Array[Byte](0), new Array[Byte](0), Instant.now.getEpochSecond.toInt, 0,
    new Array[Byte](0), new Array[Byte](0))
  private val initialMainchainBlockReference =
    new MainchainBlockReference(initialMainchainBlockReferenceHeader, None, None)
  private var lastGeneratedHash: ByteArrayWrapper = initialMainchainBlockReferenceHeader.hash


  private def addNewReference(ref: MainchainBlockReference): Unit = {
    generatedMainchainBlockReferences.put(byteArrayToMainchainBlockReferenceHash(ref.header.hash), ref)
    lastGeneratedHash = ref.header.hash
  }

  def generateMainchainBlockReference(parentOpt: Option[ByteArrayWrapper] = None, blockHash: Option[Array[Byte]] = None): MainchainBlockReference = {
    val mainchainHeaderBytes = generateBytes()

    val parent = parentOpt.getOrElse(lastGeneratedHash)
    val headerWithNoSerialization = new MainchainHeader(mainchainHeaderBytes, 1, parent, generateBytes(), generateBytes(), Instant.now.getEpochSecond.toInt, util.Random.nextInt(), generateBytes(), generateBytes(1344))

    val header: MainchainHeader = blockHash match {
      case Some(hashData) => new MainchainHeader(
          mainchainHeaderToBytes(headerWithNoSerialization),
          headerWithNoSerialization.version,
          headerWithNoSerialization.hashPrevBlock,
          headerWithNoSerialization.hashMerkleRoot,
          headerWithNoSerialization.hashSCMerkleRootsMap,
          headerWithNoSerialization.time,
          headerWithNoSerialization.bits,
          headerWithNoSerialization.nonce,
          headerWithNoSerialization.solution) {
          override lazy val hash: Array[Byte] = hashData
        }
      case None => new MainchainHeader(
        mainchainHeaderToBytes(headerWithNoSerialization),
        headerWithNoSerialization.version,
        headerWithNoSerialization.hashPrevBlock,
        headerWithNoSerialization.hashMerkleRoot,
        headerWithNoSerialization.hashSCMerkleRootsMap,
        headerWithNoSerialization.time,
        headerWithNoSerialization.bits,
        headerWithNoSerialization.nonce,
        headerWithNoSerialization.solution)
    }

    val newReference = new MainchainBlockReference(header, None, None) {
      override def semanticValidity(params: NetworkParams): Boolean = true
    }

    addNewReference(newReference)
    newReference
  }


  def generateDummyMainchainBlockReference(): MainchainBlockReference = {
      val mainchainHeaderBytes: Array[Byte] = new Array[Byte](16)
      util.Random.nextBytes(mainchainHeaderBytes)
      val header = new MainchainHeader(mainchainHeaderBytes, 1, null, null, null, 0, util.Random.nextInt(), null, null)
      new MainchainBlockReference(header, null, null)
  }

  @tailrec
  final def generateMainchainReferences(generated: Seq[MainchainBlockReference] = Seq(), parentOpt: Option[ByteArrayWrapper] = None): Seq[MainchainBlockReference] = {
      if (util.Random.nextBoolean && generated.size < SidechainBlock.MAX_MC_BLOCKS_NUMBER) {
        val nextReference = generateMainchainBlockReference(parentOpt.orElse(generated.lastOption.map(lastBlock => byteArrayToWrapper(lastBlock.header.hash))))
        generateMainchainReferences(generated :+ nextReference)
      }
      else {
        generated
      }
  }
}
