package com.horizen.block

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.consensus.TimeToEpochSlotConverterUtils
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.utils.{MerkleTree, Utils}

import scala.collection.JavaConverters._

@JsonView(Array(classOf[Views.Default]))
trait OmmersContainer {
  val header: SidechainBlockHeader
  val mainchainHeaders: Seq[MainchainHeader]
  val ommers: Seq[Ommer]

  def score: Long = 1L + ommers.map(_.score).sum

  protected def verifyOmmers(params: NetworkParams): Boolean = {
    // Verify ommers score consistency to SidechainBlockHeader
    if (ommers.map(_.score).sum != header.ommersCumulativeScore)
      return false

    // Verify that included ommers are consistent to header.ommersMerkleRootHash.
    if (ommers.isEmpty) {
      if (!header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
      return true
    }
    val calculatedMerkleRootHash = MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    if (!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
      return false

    // Ommers list must be a consistent SidechainBlocks chain.
    for (i <- 1 until ommers.size) {
      if (ommers(i).header.parentId != ommers(i - 1).header.id)
        return false
    }
    // First Ommer must have the same parent as current SidechainBlock.
    if (ommers.head.header.parentId != header.parentId)
      return false

    // Verify that Ommers order is valid in context of OmmersContainer epoch&slot order
    // Last ommer epoch&slot number must be before verified block epoch&slot
    val converter = TimeToEpochSlotConverterUtils(params)
    val timestamps = ommers.map(_.header.timestamp) :+ header.timestamp
    val absoluteSlots = timestamps.map(t => converter.timeStampToAbsoluteSlotNumber(t))
    for(i <- 1 until absoluteSlots.size) {
      if(absoluteSlots(i) <= absoluteSlots(i-1))
        return false
    }

    // Ommers must reference to MainchainHeaders for different chain than current SidechainBlock does.
    // In our case first Ommer should contain non empty headers seq and it should be different to the same length subseq of current SidechainBlock headers.
    val firstOmmerHeaders = ommers.head.mainchainHeaders
    if (mainchainHeaders.isEmpty)
      return false
    if (firstOmmerHeaders.isEmpty || firstOmmerHeaders.equals(mainchainHeaders.take(firstOmmerHeaders.size)))
      return false


    // Verify Ommers mainchainHeaders chain consistency
    val ommersMainchainHeaders: Seq[MainchainHeader] = ommers.flatMap(_.mainchainHeaders)
    for (i <- 1 until ommersMainchainHeaders.size) {
      if (!ommersMainchainHeaders(i).hasParent(ommersMainchainHeaders(i - 1)))
        return false
    }
    // Ommers MC chain must follow the same MC parent as Block MC chain does
    if (!ommersMainchainHeaders.head.hashPrevBlock.sameElements(mainchainHeaders.head.hashPrevBlock))
      return false

    // Total number of MainchainHeaders in current SidechainBlock must be greater than ommers total MainchainHeaders amount.
    if (mainchainHeaders.size <= ommersMainchainHeaders.size)
      return false

    // Verify that each Ommer is semantically valid
    for (ommer <- ommers)
      if (!ommer.semanticValidity(params))
        return false

    true
  }
}
