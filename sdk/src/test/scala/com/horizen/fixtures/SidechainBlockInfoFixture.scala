package com.horizen.fixtures

import com.horizen.block.MainchainBlockReference
import com.horizen.chain.{MainchainBlockReferenceId, SidechainBlockInfo, byteArrayToMainchainBlockReferenceId}
import com.horizen.params.{NetworkParams, RegTestParams}
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
    for (i <- 0 until count)
      modifiers = modifiers :+ getRandomModifier()
    modifiers
  }

  def getRandomModifiersSeq(count: Int, basicSeed: Long): Seq[ModifierId] = {
    util.Random.setSeed(basicSeed)
    getRandomModifiersSeq(count)
  }

  ///////
  private val initialMainchainReference = byteArrayToMainchainBlockReferenceId(generateBytes())
  private val initialSidechainBlockId = bytesToId(generateBytes())
  private val initialSidechainBlockInfo =
    SidechainBlockInfo(
      1,
      (1L << 32) + 1,
      getRandomModifier(),
      ModifierSemanticValidity.Valid,
      generateMainchainReferences(Seq(generateMainchainBlockReference()), parent = Some(initialMainchainReference)).map(id => byteArrayToMainchainBlockReferenceId(id.hash)),
      1,
      1
    )

  val generatedData =
    new mutable.HashMap[ModifierId, (SidechainBlockInfo, Option[MainchainBlockReferenceId])]()
  generatedData.put(initialSidechainBlockId, (initialSidechainBlockInfo, Option(initialMainchainReference)))

  private def findParentMainchainReference(id: ModifierId): Option[MainchainBlockReferenceId] = {
    val data = generatedData.get(id)

    data.flatMap(parentData => parentData._1.mainchainBlockReferenceHashes.lastOption)
      .orElse(data.flatMap(p => p._2))
      .orElse(Some(initialMainchainReference))
  }


  def generateEntry(parent: ModifierId, refs: Seq[MainchainBlockReference] = Seq(), params: NetworkParams = RegTestParams()): (ModifierId, (SidechainBlockInfo, Option[MainchainBlockReferenceId])) = {
    val id = getRandomModifier()
    val parentData: (SidechainBlockInfo, Option[MainchainBlockReferenceId]) = generatedData.getOrElseUpdate(parent, generateEntry(initialSidechainBlockId, Seq(), params)._2)
    val parentSidechainBlockInfo = parentData._1

    val withdrawalEpoch: Int =
      if(parentSidechainBlockInfo.withdrawalEpochIndex == params.withdrawalEpochLength) // Parent block is the last SC Block of withdrawal epoch.
        parentSidechainBlockInfo.withdrawalEpoch + 1
      else // Continue current withdrawal epoch
        parentSidechainBlockInfo.withdrawalEpoch

    val withdrawalEpochIndex: Int =
      if(withdrawalEpoch > parentSidechainBlockInfo.withdrawalEpoch) // New withdrawal epoch started
        refs.size // Note: in case of empty MC Block ref list index should be 0.
      else // Continue current withdrawal epoch
        parentSidechainBlockInfo.withdrawalEpochIndex + refs.size // Note: in case of empty MC Block ref list index should be the same as for previous SC block.

    val generatedScBlockInfo = SidechainBlockInfo(
      parentSidechainBlockInfo.height + 1,
      parentSidechainBlockInfo.score + (refs.size.toLong << 32) + 1,
      parent,
      ModifierSemanticValidity.Valid,
      (refs ++ generateMainchainReferences(parent = parentData._2)).map(d => byteArrayToMainchainBlockReferenceId(d.hash)),
      withdrawalEpoch,
      withdrawalEpochIndex
    )

    (id, (generatedScBlockInfo, findParentMainchainReference(parent)))
  }

  def getNewDataForParent(parent: ModifierId,
                          refs: Seq[MainchainBlockReference] = Seq(),
                          params: NetworkParams = RegTestParams()): (ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceId]) = {
    val (newId, data) = generateEntry(parent, refs, params)
    generatedData.put(newId, data)
    generatedData.get(newId).map { case (sbInfo, newParent) =>
      (newId, sbInfo, if (sbInfo.mainchainBlockReferenceHashes.nonEmpty) newParent else None)
    }.get
  }

  def getNewDataForParentNoMainchainReferences(parent: ModifierId,
                                               params: NetworkParams = RegTestParams()): (ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceId]) = {
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
                                 generatedData: Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceId])] = Seq(),
                                 params: NetworkParams = RegTestParams()
                                ): Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceId])] = {
    if (count > 0) {
      val parent = generatedData.last._1
      generateDataSequence(count - 1, generatedData :+ getNewDataForParent(parent, Seq(), params))
    }
    else {
      generatedData
    }
  }

  final def generateDataSequenceWithGenesisBlock(count: Int,
                                 generatedData: Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceId])] = Seq(),
                                 params: NetworkParams = RegTestParams()
                                ): Seq[(ModifierId, SidechainBlockInfo, Option[MainchainBlockReferenceId])] = {
    if (count > 0) {
      generateDataSequence(count - 1, Seq((initialSidechainBlockId, initialSidechainBlockInfo, Some(initialMainchainReference))), params)
    }
    else {
      Seq()
    }
  }
}