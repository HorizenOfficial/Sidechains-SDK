package com.horizen.fixtures

import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.chain.{MainchainHeaderBaseInfo, MainchainHeaderHash, SidechainBlockInfo, byteArrayToMainchainHeaderHash}
import com.horizen.cryptolibprovider.utils.CumulativeHashFunctions
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.utils.{WithdrawalEpochInfo, WithdrawalEpochUtils, BytesUtils}
import sparkz.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Random
import scala.collection.mutable.ArrayBuffer

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

  def getMainchainBaseInfoFromReferences(references: Seq[MainchainBlockReference], initialCumulativeHash: Array[Byte]): Seq[MainchainHeaderBaseInfo] = {
    var prevCumulativeHash = initialCumulativeHash
    references.map(ref => {
      val headerHash = byteArrayToMainchainHeaderHash(ref.data.headerHash)
      val cumulativeHash = CumulativeHashFunctions.computeCumulativeHash(prevCumulativeHash, BytesUtils.reverseBytes(ref.header.hashScTxsCommitment))
      prevCumulativeHash = cumulativeHash
      MainchainHeaderBaseInfo(headerHash, cumulativeHash)
    })
  }

  ///////
  private val initialMainchainReference = byteArrayToMainchainHeaderHash(generateBytes())
  private val initialSidechainBlockId = bytesToId(generateBytes())
  val initialCumScTxCommTreeHash = FieldElementFixture.generateFieldElement()
  val mainchainReferences: Seq[MainchainBlockReference] = generateMainchainReferences(Seq(generateMainchainBlockReference()), parentOpt = Some(initialMainchainReference))
  val mainchainHeadersHashes = mainchainReferences.map(ref => byteArrayToMainchainHeaderHash(ref.header.hash))
  val mainchainHeaderBaseInfo = getMainchainBaseInfoFromReferences(mainchainReferences, initialCumScTxCommTreeHash)
  val mainchainReferencesDataHeadersHashes = mainchainReferences.map(ref => byteArrayToMainchainHeaderHash(ref.data.headerHash))
  private val initialSidechainBlockInfo =
    SidechainBlockInfo(
      1,
      (1L << 32) + 1,
      getRandomModifier(),
      Random.nextLong(),
      ModifierSemanticValidity.Valid,
      mainchainHeaderBaseInfo,
      mainchainReferencesDataHeadersHashes,
      WithdrawalEpochInfo(1, 1),
      Option(VrfGenerator.generateVrfOutput(Random.nextLong())),
      getRandomModifier()
    )

  val generatedData =
    new mutable.HashMap[ModifierId, (SidechainBlockInfo, Option[MainchainHeaderHash])]()
  generatedData.put(initialSidechainBlockId, (initialSidechainBlockInfo, Option(initialMainchainReference)))

  private def findParentMainchainReference(id: ModifierId): Option[MainchainHeaderHash] = {
    val data = generatedData.get(id)

    data.flatMap(parentData => parentData._1.mainchainHeaderHashes.lastOption)
      .orElse(data.flatMap(p => p._2))
      .orElse(Some(initialMainchainReference))
  }


  // TODO: add support of Data and Headers as inputs of method
  def generateEntry(parent: ModifierId, refs: Seq[MainchainBlockReference] = Seq(), params: NetworkParams = RegTestParams(initialCumulativeCommTreeHash = FieldElementFixture.generateFieldElement())): (ModifierId, (SidechainBlockInfo, Option[MainchainHeaderHash])) = {
    val id = getRandomModifier()
    val parentData: (SidechainBlockInfo, Option[MainchainHeaderHash]) = generatedData.getOrElseUpdate(parent, generateEntry(initialSidechainBlockId, Seq(), params)._2)
    val parentSidechainBlockInfo = parentData._1
    val allRefs: Seq[MainchainBlockReference] = refs ++ generateMainchainReferences(parentOpt = parentData._2)
    val allRefsDataHeadersHashes = allRefs.map(d => byteArrayToMainchainHeaderHash(d.data.headerHash))
    //val allRefsMainchainHeaderBaseInfo: Seq[MainchainHeaderBaseInfo] = getMainchainBaseInfoFromReferences(allRefs, parentSidechainBlockInfo.mainchainHeaderBaseInfo.last.cumulativeCommTreeHash)
    val allRefsMainchainHeaderBaseInfo: Seq[MainchainHeaderBaseInfo] = getMainchainBaseInfoFromReferences(allRefs, params.initialCumulativeCommTreeHash)
    val generatedScBlockInfo = SidechainBlockInfo(
      parentSidechainBlockInfo.height + 1,
      parentSidechainBlockInfo.score + (refs.size.toLong << 32) + 1,
      parent,
      Random.nextLong(),
      ModifierSemanticValidity.Valid,
      allRefsMainchainHeaderBaseInfo,
      allRefsDataHeadersHashes,
      WithdrawalEpochUtils.getWithdrawalEpochInfo(
        allRefs.map(_.data).size,
        parentSidechainBlockInfo.withdrawalEpochInfo,
        params),
      Option(VrfGenerator.generateVrfOutput(Random.nextLong())),
      parent
    )

    (id, (generatedScBlockInfo, findParentMainchainReference(parent)))
  }

  def getNewDataForParent(parent: ModifierId,
                          refs: Seq[MainchainBlockReference] = Seq(),
                          params: NetworkParams = RegTestParams()): (ModifierId, SidechainBlockInfo, Option[MainchainHeaderHash]) = {
    val (newId, data) = generateEntry(parent, refs, params)
    generatedData.put(newId, data)
    generatedData.get(newId).map { case (sbInfo, newParent) =>
      (newId, sbInfo, if (sbInfo.mainchainHeaderHashes.nonEmpty) newParent else None)
    }.get
  }

  def getNewDataForParentNoMainchainReferences(parent: ModifierId,
                                               params: NetworkParams = RegTestParams()): (ModifierId, SidechainBlockInfo, Option[MainchainHeaderHash]) = {
    val (newId, data) = generateEntry(parent, Seq(), params)
    val dataWithoutReferences =
      data.copy(_1 = data._1.copy(mainchainHeaderBaseInfo = Seq(), mainchainReferenceDataHeaderHashes = Seq()), _2 = None)
    generatedData.put(newId, dataWithoutReferences)
    generatedData.get(newId).map { case (sbInfo, newParent) =>
      (newId, sbInfo, if (sbInfo.mainchainHeaderHashes.nonEmpty) newParent else None)
    }.get
  }

  @tailrec
  final def generateDataSequence(count: Int,
                                 generatedData: Seq[(ModifierId, SidechainBlockInfo, Option[MainchainHeaderHash])] = Seq(),
                                 params: NetworkParams = RegTestParams()
                                ): Seq[(ModifierId, SidechainBlockInfo, Option[MainchainHeaderHash])] = {
    if (count > 0) {
      val parent = generatedData.last._1
      generateDataSequence(count - 1, generatedData :+ getNewDataForParent(parent, Seq(), params))
    }
    else {
      generatedData
    }
  }

  final def generateDataSequenceWithGenesisBlock(count: Int,
                                                 generatedData: Seq[(ModifierId, SidechainBlockInfo, Option[MainchainHeaderHash])] = Seq(),
                                                 params: NetworkParams = RegTestParams()
                                ): Seq[(ModifierId, SidechainBlockInfo, Option[MainchainHeaderHash])] = {
    if (count > 0) {
      generateDataSequence(count - 1, Seq((initialSidechainBlockId, initialSidechainBlockInfo, Some(initialMainchainReference))), params)
    }
    else {
      Seq()
    }
  }
}