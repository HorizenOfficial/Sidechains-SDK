package com.horizen.fixtures

import com.horizen.block.{MainchainBlockReference, MainchainHeader, SidechainBlock}
import com.horizen.utils._

import scala.annotation.tailrec
import scala.collection.mutable

trait MainchainBlockReferenceFixture {
  def setSeed(seed: Long): Unit = util.Random.setSeed(seed)

  private def generateBytes(size: Int = 32): Array[Byte] = {
    val res: Array[Byte] = new Array[Byte](size)
    util.Random.nextBytes(res)
    res
  }

  val generatedMainchainBlockReferences =
    new mutable.HashMap[ByteArrayWrapper, MainchainBlockReference]()

  val initialMainchainBlockReferenceHeader = new MainchainHeader(Array[Byte](42, 0, 33),
    -1, new Array[Byte](0), new Array[Byte](0), new Array[Byte](0), new Array[Byte](0), 0, 0,
    new Array[Byte](0), new Array[Byte](0))
  val initialMainchainBlockReference =
    new MainchainBlockReference(initialMainchainBlockReferenceHeader, None, None)
  var lastGeneratedHash: ByteArrayWrapper = initialMainchainBlockReferenceHeader.hash


  def generateMainchainBlockReference(id: Option[ByteArrayWrapper] = None): MainchainBlockReference = {
    val mainchainHeaderBytes = generateBytes()

    val parent = id.getOrElse(lastGeneratedHash)
    val header = new MainchainHeader(mainchainHeaderBytes, 1, parent, generateBytes(), generateBytes(), generateBytes(), System.currentTimeMillis().asInstanceOf[Int], util.Random.nextInt(), generateBytes(), generateBytes(1344))
    lastGeneratedHash = new ByteArrayWrapper(mainchainHeaderBytes)

    new MainchainBlockReference(header, null, null)
  }


  def generateDummyMainchainBlockReference(): MainchainBlockReference = {
      val mainchainHeaderBytes: Array[Byte] = new Array[Byte](16)
      util.Random.nextBytes(mainchainHeaderBytes)
      val header = new MainchainHeader(mainchainHeaderBytes, 1, null, null, null, null, 0, util.Random.nextInt(), null, null)
      new MainchainBlockReference(header, null, null)
  }

  def generateMainchainReferences(generated: Seq[MainchainBlockReference] = Seq(), parent: Option[ByteArrayWrapper] = None): Seq[MainchainBlockReference] = {
    @tailrec
    def generateMainchainReferencesLoop(currentReferences: Seq[MainchainBlockReference]): Seq[MainchainBlockReference] = {
      if (util.Random.nextBoolean && generated.size < SidechainBlock.MAX_BLOCK_SIZE) {
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
