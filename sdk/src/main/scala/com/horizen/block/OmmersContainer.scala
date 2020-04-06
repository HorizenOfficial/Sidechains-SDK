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
    if (ommers.isEmpty)
      return header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH)
    val calculatedMerkleRootHash = MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    if (!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
      return false

    // Ommers list must be a consistent SidechainBlocks chain.
    // First Ommer must have the same parent as current SidechainBlock.
    ommers.foldLeft(header.parentId) {
      case (parentId, ommer) =>
        if (parentId != ommer.header.parentId)
          return false
        ommer.header.id
    }

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
    val firstOmmerMainchainHeaders = ommers.head.mainchainHeaders
    if (mainchainHeaders.isEmpty)
      return false
    if (firstOmmerMainchainHeaders.isEmpty || firstOmmerMainchainHeaders.equals(mainchainHeaders.take(firstOmmerMainchainHeaders.size)))
      return false


    val ommersMainchainHeaders: Seq[MainchainHeader] = ommers.flatMap(_.mainchainHeaders)
    // Verify the MainchainHeaders are connected, especially between Ommers.
    // Ommers MC chain must follow the same MC parent as Block MC chain does
    ommersMainchainHeaders.foldLeft(mainchainHeaders.head.hashPrevBlock) {
      case (hashPrevBlock, ommerMainchainHeader) =>
        if (!ommerMainchainHeader.hashPrevBlock.sameElements(hashPrevBlock))
          return false
        ommerMainchainHeader.hash
    }

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
