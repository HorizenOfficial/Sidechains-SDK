package com.horizen.fixtures

import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.chain.{MainchainBlockReferenceHash, SidechainBlockInfo, byteArrayToMainchainBlockReferenceHash}
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.utils.{WithdrawalEpochInfo, WithdrawalEpochUtils}
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Random

trait SidechainBlockInfoFixture extends MainchainBlockReferenceFixture {

  def getRandomModifier(): ModifierId = {
    val parentBytes: Array[Byte] = new Array[Byte](32)
    util.Random.nextBytes(parentBytes)
    bytesToId(parentBytes)
  }

  def getRandomModifiersSeq(count: Int): Seq[ModifierId] = {
    var modifiers: Seq[ModifierId] = Seq()
    for (i <- 0 until count)
      modifiers = modifiers :+ getRandomModifier()
    modifiers
  }

  def getRandomModifiersSeq(count: Int, basicSeed: Long): Seq[ModifierId] = {
    util.Random.setSeed(basicSeed)
    getRandomModifiersSeq(count)
  }

  ///////
  private val initialMainchainReference = byteArrayToMainchainBlockReferenceHash(generateBytes())
  private val initialSidechainBlockId = bytesToId(generateBytes())
  private val initialSidechainBlockInfo =
    SidechainBlockInfo(
      1,
      (1L << 32) + 1,
      getRandomModifier(),
      Random.nextLong(),
      ModifierSemanticValidity.Valid,
      generateMainchainReferences(Seq(generateMainchainBlockReference()), parentOpt = Some(initialMainchainReference)).map(id => byteArrayToMainchainBlockReferenceHash(id.header.hash)),
      WithdrawalEpochInfo(1, 1)
    )

  val generatedData =
    new mutable.HashMap[ModifierId, (SidechainBlockInfo, Option[MainchainBlockReferenceHash])]()
  generatedData.put(initialSidechainBlockId, (initialSidechainBlockInfo, Option(initialMainchainReference)))

  private def findParentMainchainReference(id: ModifierId): Option[MainchainBlockReferenceHash] = {
    val data = generatedData.get(id)

    data.flatMap(parentData => parentData._1.mainchainBlockReferenceHashes.lastOption)
      .orElse(data.flatMap(p => p._2))
      .orElse(Some(initialMainchainReference))
  }


  def generateEntry(parent: ModifierId, refs: Seq[MainchainBlockReference] = Seq(), params: NetworkParams = RegTestParams()): (ModifierId, (SidechainBlockInfo, Option[MainchainBlockReferenceHash])) = {
    val id = getRandomModifier()
    val parentData: (SidechainBlockInfo, Option[MainchainBlockReferenceHash]) = generatedData.getOrElseUpdate(parent, generateEntry(initialSidechainBlockId, Seq(), params)._2)
    val parentSidechainBlockInfo = parentData._1

    val generatedScBlockInfo = SidechainBlockInfo(
      parentSidechainBlockInfo.height + 1,
      parentSidechainBlockInfo.score + (refs.size.toLong << 32) + 1,
      parent,
      Random.nextLong(),
      ModifierSemanticValidity.Valid,
      (refs ++ generateMainchainReferences(parentOpt = parentData._2)).map(d => byteArrayToMainchainBlockReferenceHash(d.header.hash)),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(
        new SidechainBlock(null, null, refs, null, null, null),
        parentSidechainBlockInfo.withdrawalEpochInfo,
        params)
    )

    (id, (generatedScBlockInfo, findParentMainchainReference(parent)))
  }

  def getNewDataForParent(parent: ModifierId,
                          refs: Seq[MainchainBlockReference] = Seq(),
                          params: NetworkParams = RegTestParams()): (ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceHash]) = {
    val (newId, data) = generateEntry(parent, refs, params)
    generatedData.put(newId, data)
    generatedData.get(newId).map { case (sbInfo, newParent) =>
      (newId, sbInfo, if (sbInfo.mainchainBlockReferenceHashes.nonEmpty) newParent else None)
    }.get
  }

  def getNewDataForParentNoMainchainReferences(parent: ModifierId,
                                               params: NetworkParams = RegTestParams()): (ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceHash]) = {
    val (newId, data) = generateEntry(parent, Seq(), params)
    val dataWithoutReferences =
      data.copy(_1 = data._1.copy(mainchainBlockReferenceHashes = Seq()), _2 = None)
    generatedData.put(newId, dataWithoutReferences)
    generatedData.get(newId).map { case (sbInfo, newParent) =>
      (newId, sbInfo, if (sbInfo.mainchainBlockReferenceHashes.nonEmpty) newParent else None)
    }.get
  }

  @tailrec
  final def generateDataSequence(count: Int,
                                 generatedData: Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceHash])] = Seq(),
                                 params: NetworkParams = RegTestParams()
                                ): Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceHash])] = {
    if (count > 0) {
      val parent = generatedData.last._1
      generateDataSequence(count - 1, generatedData :+ getNewDataForParent(parent, Seq(), params))
    }
    else {
      generatedData
    }
  }

  final def generateDataSequenceWithGenesisBlock(count: Int,
                                 generatedData: Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceHash])] = Seq(),
                                 params: NetworkParams = RegTestParams()
                                ): Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceHash])] = {
    if (count > 0) {
      generateDataSequence(count - 1, Seq((initialSidechainBlockId, initialSidechainBlockInfo, Some(initialMainchainReference))), params)
    }
    else {
      Seq()
    }
  }
}