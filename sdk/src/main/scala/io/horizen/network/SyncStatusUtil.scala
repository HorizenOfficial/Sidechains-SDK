package io.horizen.network

import io.horizen.history.AbstractHistory
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.utils.NetworkTimeProvider

class SyncStatusUtil () {}

object SyncStatusUtil {

  private val calculationThreshold: Int = 15000
  private val defaultCorrectionParameter: Double = 0.65
  private val averageWeightFromGenesis: Double = 0.5
  private val averageWeightFromHalf: Double = 1.0

  /*
  The calculateEstimatedHighestBlock will calculate the estimated highest block given a set of input data and reading
  other data from the node view . The method will calculate the block correction performing a weighted average between
  block corrections from genesis and from sidechain half.
  The block correction from genesis block is calculated taking the timestamp between the genesis block and the current
  block and then calculate how many blocks would be present if they had been generated in a period equal to the block
  time (12 seconds). Calculate in the same way the block correction starting from sidechain half.
  Then we calculate the global block correction performing a weighted average between the block corrections from genesis
  with a weight equal to 0.5 and the block correction from sidechain half with a weight of 1.0.
  With the global block correction, the current timestamp and the current block height is finally possible to calculate
  the estimated highest block
   */
  def calculateEstimatedHighestBlock[V <: CurrentView[_<:AbstractHistory[_, _, _, _, _, _], _, _, _]](
      sidechainNodeView: V,
      timeProvider: NetworkTimeProvider,
      consensusSecondsInSlot: Int,
      genesisBlockTimestamp: Long,
      currentBlockHeight: Int,
      currentBlockTimestamp: Long
  ): Int = {

    val blockCorrection = {
      // if the current block height is higher than the threshold value requested calculate the block correction
      if(currentBlockHeight > calculationThreshold) {

        // block correction calculated on genesis block
        var timestampDifference = currentBlockTimestamp - genesisBlockTimestamp
        val maxBlocksInRangeFromGenesis = timestampDifference / consensusSecondsInSlot.toDouble
        val blockCorrectionFromGenesis = currentBlockHeight / maxBlocksInRangeFromGenesis

        // sidechain half height block info
        val sidechainHalfHeight = currentBlockHeight / 2
        val halfBlockId = sidechainNodeView.history.getBlockIdByHeight(sidechainHalfHeight).get()
        val halfBlockInfo = sidechainNodeView.history.getBlockInfoById(halfBlockId).get()

        // block correction calculated on sidechain half height
        timestampDifference = currentBlockTimestamp - halfBlockInfo.timestamp
        val maxBlocksInRangeFromHalf = timestampDifference / consensusSecondsInSlot.toDouble
        val blockCorrectionFromHalf = sidechainHalfHeight / maxBlocksInRangeFromHalf

        // weighted average between slot corrections from genesis and from sidechain half
        val blockCorrection = (((blockCorrectionFromGenesis * averageWeightFromGenesis) + (blockCorrectionFromHalf * averageWeightFromHalf))
            / (averageWeightFromGenesis + averageWeightFromHalf))
        blockCorrection
      }

      // else return the default correction parameter
      else defaultCorrectionParameter
    }

    // calculate the estimated highest block
    val currentTime: Long = timeProvider.time() / 1000
    val timestampDifference = currentTime - currentBlockTimestamp
    val estimatedHighestBlock = ((timestampDifference / consensusSecondsInSlot.toDouble) * blockCorrection).toInt + currentBlockHeight
    estimatedHighestBlock
  }

  // method to retrieve the genesis block timestamp needed in the estimated highest block calculation
  def getGenesisBlockTimestamp[V <: CurrentView[_ <: AbstractHistory[_, _, _, _, _, _], _, _, _]](
       sidechainNodeView: V,
   ): Long = {
    val genesisBlockId = sidechainNodeView.history.getBlockIdByHeight(1).get()
    val genesisBlockInfo = sidechainNodeView.history.getBlockInfoById(genesisBlockId).get()
    genesisBlockInfo.timestamp
  }

}

