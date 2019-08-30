package com.horizen.fixtures

import com.horizen.block.MainchainBlockReference
import com.horizen.chain.SidechainBlockInfo
import com.horizen.utils._
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId}

import scala.annotation.tailrec
import scala.collection.mutable

trait SidechainBlockInfoFixture extends MainchainBlockReferenceFixture {

  def getRandomModifier(): ModifierId = {
    val parentBytes: Array[Byte] = new Array[Byte](32)
    util.Random.nextBytes(parentBytes)
    bytesToId(parentBytes)
  }

  def getRandomModifiersSeq(count: Int): Seq[ModifierId] = {
    var modifiers: Seq[ModifierId] = Seq()
    for(i <- 0 until count)
      modifiers = modifiers :+ getRandomModifier()
    modifiers
  }

  def getRandomModifiersSeq(count: Int, basicSeed: Long): Seq[ModifierId] = {
    util.Random.setSeed(basicSeed)
    getRandomModifiersSeq(count)
  }

  ///////
  val initialMainchainReference = new ByteArrayWrapper(new Array[Byte](0))
  val initialSidechainBlock =
    SidechainBlockInfo(1, 1, getRandomModifier(), ModifierSemanticValidity.Valid, Seq())

  val generatedData =
    new mutable.HashMap[ModifierId, (SidechainBlockInfo, Option[ByteArrayWrapper])]()

  private def findParentMainchainReference(id: ModifierId): Option[ByteArrayWrapper] = {
    val data = generatedData.get(id)

    data.flatMap(parentData => parentData._1.mainchainBlockReferenceHashes.lastOption)
      .orElse(data.flatMap(p => p._2))
      .orElse(Some(initialMainchainReference))
  }

  def generateData(parent: ModifierId, refs: Seq[MainchainBlockReference] = Seq()): (ModifierId, (SidechainBlockInfo, Option[ByteArrayWrapper])) = {
    val id = getRandomModifier()
    val parentData: (SidechainBlockInfo, Option[ByteArrayWrapper]) = generatedData.getOrElse(parent, (initialSidechainBlock, None))
    val parentSidechainBlock = parentData._1
    val data = SidechainBlockInfo(
      parentSidechainBlock.height + 1,
      parentSidechainBlock.score + 1,
      parent,
      ModifierSemanticValidity.Valid,
      (refs ++ generateMainchainReferences()).map(d => d.hash)
    )
    (id, (data, findParentMainchainReference(parent)))
  }

  def getNewDataForParent(parent: ModifierId,
                          refs: Seq[MainchainBlockReference] = Seq()): (ModifierId, SidechainBlockInfo, Option[ByteArrayWrapper]) = {
    val (newId, data) = generateData(parent, refs)
    generatedData.put(newId, data)
    generatedData.get(newId).map { case (sbInfo, newParent) =>
      (newId, sbInfo, if (sbInfo.mainchainBlockReferenceHashes.nonEmpty) newParent else None)}.get
  }

  def getNewDataForParentNoMainchainReferences(parent: ModifierId): (ModifierId, SidechainBlockInfo, Option[ByteArrayWrapper]) = {
    val (newId, data) = generateData(parent)
    val dataWithoutReferences =
      data.copy(_1 = data._1.copy(mainchainBlockReferenceHashes = Seq()), _2 = None)
    generatedData.put(newId, dataWithoutReferences)
    generatedData.get(newId).map { case (sbInfo, newParent) =>
      (newId, sbInfo, if (sbInfo.mainchainBlockReferenceHashes.nonEmpty) newParent else None)}.get
  }

  @tailrec
  final def generateDataSequence(count: Int,
                                 generatedData: Seq[(ModifierId, SidechainBlockInfo, Option[ByteArrayWrapper])] = Seq()
                                ): Seq[(ModifierId, SidechainBlockInfo, Option[ByteArrayWrapper])] = {
    if (count > 0) {
      val parent = generatedData.lastOption.getOrElse((ModifierId(""), initialSidechainBlock, initialMainchainReference))._1
      generateDataSequence(count - 1, generatedData :+ getNewDataForParent(parent))
    }
    else {
      generatedData
    }
  }

}
