package com.horizen.fixtures

import java.time.Instant

import com.horizen.block.{MainchainBlockReference, MainchainHeader, SidechainBlock}
import com.horizen.chain.{MainchainBlockReferenceId, byteArrayToMainchainBlockReferenceId}
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
    new mutable.HashMap[MainchainBlockReferenceId, MainchainBlockReference]()

  private val initialMainchainBlockReferenceHeader = new MainchainHeader(generateBytes(),
    -1, generateBytes(), new Array[Byte](0), new Array[Byte](0), Instant.now.getEpochSecond.toInt, 0,
    new Array[Byte](0), new Array[Byte](0))
  private val initialMainchainBlockReference =
    new MainchainBlockReference(initialMainchainBlockReferenceHeader, None, None, (None, None), None)
  private var lastGeneratedHash: ByteArrayWrapper = initialMainchainBlockReferenceHeader.hash


  private def addNewReference(ref: MainchainBlockReference): Unit = {
    generatedMainchainBlockReferences.put(byteArrayToMainchainBlockReferenceId(ref.header.hash), ref)
    lastGeneratedHash = ref.hash
  }

  def generateMainchainBlockReference(id: Option[ByteArrayWrapper] = None, blockHash: Option[Array[Byte]] = None): MainchainBlockReference = {
    val mainchainHeaderBytes = generateBytes()

    val parent = id.getOrElse(lastGeneratedHash)
    val headerWithNoSerialization = new MainchainHeader(mainchainHeaderBytes, 1, parent, generateBytes(), generateBytes(), Instant.now.getEpochSecond.toInt, util.Random.nextInt(), generateBytes(), generateBytes(1344))
    val header = new MainchainHeader(
      mainchainHeaderToBytes(headerWithNoSerialization),
      headerWithNoSerialization.version,
      headerWithNoSerialization.hashPrevBlock,
      headerWithNoSerialization.hashMerkleRoot,
      headerWithNoSerialization.hashScTxsCommitment,
      headerWithNoSerialization.time,
      headerWithNoSerialization.bits,
      headerWithNoSerialization.nonce,
      headerWithNoSerialization.solution)

    val newReference = new MainchainBlockReference(header, None, None, (None, None), None) {
      override def semanticValidity(params: NetworkParams): Boolean = true

      override lazy val hash: Array[Byte] = blockHash match {
        case Some(data) => data
        case _ => header.hash // identically to super.hash. super.hash can't be used due compiler limitations
      }
    }

    addNewReference(newReference)
    newReference
  }


  def generateDummyMainchainBlockReference(): MainchainBlockReference = {
      val mainchainHeaderBytes: Array[Byte] = new Array[Byte](16)
      util.Random.nextBytes(mainchainHeaderBytes)
      val header = new MainchainHeader(mainchainHeaderBytes, 1, null, null, null, 0, util.Random.nextInt(), null, null)
      new MainchainBlockReference(header, None, None, (None, None), None)
  }

  def generateMainchainReferences(generated: Seq[MainchainBlockReference] = Seq(), parent: Option[ByteArrayWrapper] = None): Seq[MainchainBlockReference] = {
    @tailrec
    def generateMainchainReferencesLoop(currentReferences: Seq[MainchainBlockReference]): Seq[MainchainBlockReference] = {
      if (util.Random.nextBoolean && currentReferences.size < SidechainBlock.MAX_MC_BLOCKS_NUMBER) {
        val nextReference = generateMainchainBlockReference(parent)
          generateMainchainReferencesLoop(currentReferences :+ nextReference)
      }
      else {
        currentReferences
      }
    }

    generateMainchainReferencesLoop(generated)
  }
}
