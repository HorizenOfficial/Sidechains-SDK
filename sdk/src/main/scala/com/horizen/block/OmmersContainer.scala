package com.horizen.block

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.consensus.TimeToEpochSlotConverterUtils
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.validation.InvalidOmmerDataException

import scala.util.{Failure, Success, Try}

@JsonView(Array(classOf[Views.Default]))
trait OmmersContainer {
  val header: SidechainBlockHeader
  val mainchainHeaders: Seq[MainchainHeader]
  val ommers: Seq[Ommer]

  def score: Long = 1L + ommers.map(_.score).sum

  protected def verifyOmmersSeqData(params: NetworkParams): Try[Unit] = Try {
    // Verify ommers score consistency to SidechainBlockHeader
    if (ommers.map(_.score).sum != header.ommersCumulativeScore)
      throw new InvalidOmmerDataException(s"SidechainBlockHeader ommers cumulative score is different to the actual Ommers score.")

    // Return in case if there no Ommers
    if(ommers.isEmpty)
      return Success()

    // Ommers list must be a consistent SidechainBlocks chain.
    // First Ommer must have the same parent as current SidechainBlock.
    ommers.foldLeft(header.parentId) {
      case (parentId, ommer) =>
        if (parentId != ommer.header.parentId)
          throw new InvalidOmmerDataException(s"OmmerContainer Ommers contain not consistent SidechainBlockHeaders chain.")
        ommer.header.id
    }

    // Verify that Ommers order is valid in context of OmmersContainer epoch&slot order
    // Last ommer epoch&slot number must be before verified block epoch&slot
    val converter = TimeToEpochSlotConverterUtils(params)
    val timestamps = ommers.map(_.header.timestamp) :+ header.timestamp
    val absoluteSlots = timestamps.map(t => converter.timeStampToAbsoluteSlotNumber(t))
    for(i <- 1 until absoluteSlots.size) {
      if(absoluteSlots(i) <= absoluteSlots(i-1))
        throw new InvalidOmmerDataException(s"OmmerContainer Ommers slots are not consistent.")
    }

    // Ommers must reference to MainchainHeaders for different chain than current SidechainBlock does.
    // In our case first Ommer should contain non empty headers seq and it should be different to the same length subseq of current SidechainBlock headers.
    val firstOmmerMainchainHeaders = ommers.head.mainchainHeaders
    if (mainchainHeaders.size < firstOmmerMainchainHeaders.size)
      throw new InvalidOmmerDataException(s"OmmerContainer first ommer contains more MainchainHeaders than container.")
    if (firstOmmerMainchainHeaders.isEmpty || firstOmmerMainchainHeaders.equals(mainchainHeaders.take(firstOmmerMainchainHeaders.size)))
      throw new InvalidOmmerDataException(s"OmmerContainer Ommers don't lead to the orphaned MainchainHeader chain.")


    val ommersMainchainHeaders: Seq[MainchainHeader] = ommers.flatMap(_.mainchainHeaders)
    // Verify the MainchainHeaders are connected, especially between Ommers.
    // Ommers MC chain must follow the same MC parent as Block MC chain does
    ommersMainchainHeaders.foldLeft(mainchainHeaders.head.hashPrevBlock) {
      case (hashPrevBlock, ommerMainchainHeader) =>
        if (!ommerMainchainHeader.hashPrevBlock.sameElements(hashPrevBlock))
          throw new InvalidOmmerDataException(s"OmmerContainer Ommers contains not consistent MainchainHeader chain.")
        ommerMainchainHeader.hash
    }

    // Total number of MainchainHeaders in current SidechainBlock must be greater than ommers total MainchainHeaders amount.
    if (mainchainHeaders.size <= ommersMainchainHeaders.size)
      throw new InvalidOmmerDataException(s"OmmerContainer contains less MainchainHeader than in Ommers.")

    // Verify that each Ommer contains valid data.
    for (ommer <- ommers) {
      ommer.verifyData(params) match {
        case Success(_) =>
        case Failure(e) => throw e
      }
    }
  }
}
