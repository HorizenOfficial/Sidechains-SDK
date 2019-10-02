package com.horizen.block

import java.math.BigInteger

import com.google.common.primitives.UnsignedInts
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.utils.Utils

import scala.util.control.Breaks._

object ProofOfWorkVerifier {

  def checkProofOfWork(header: MainchainHeader, params: NetworkParams): Boolean = {
    val target: BigInteger = Utils.decodeCompactBits(UnsignedInts.toLong(header.bits))
    val hashTarget: BigInteger = new BigInteger(1, header.hash)

    // Check that target is not negative and is not below the minimum work defined in Horizen
    if(target.signum() <= 0 || target.compareTo(params.powLimit) > 0)
      return false

    // Check that block hash target is not greater than target.
    if(hashTarget.compareTo(target) > 0)
      return false

    true
  }

  // Check that PoW target (bits) is correct for all MainchainBlockReferences included into SidechainBlock.
  def checkNextWorkRequired(block: SidechainBlock, sidechainHistoryStorage: SidechainHistoryStorage, params: NetworkParams): Boolean = {
    if(block.mainchainBlocks.isEmpty)
      return true

    // Check MainchainBlockReferences order in current block
    for(i <- 1 until block.mainchainBlocks.size) {
      if(!block.mainchainBlocks(i).header.hashPrevBlock.sameElements(block.mainchainBlocks(i-1).hash))
        return false
    }

    // Collect information of time and bits for last "params.nPowAveragingWindow + params.nMedianTimeSpan" MainchainBlockReferences
    // already presented in a current chain of SidechainBlocks.
    var timeBitsData = List[Tuple2[Int, Int]]()
    var currentMCBlockReference = block.mainchainBlocks.head
    var currentBlock: SidechainBlock = block
    breakable {
      while (true) {
        if (currentMCBlockReference.hash.sameElements(params.genesisMainchainBlockHash)) {
          // We reached the genesis MC block reference. So get the rest of (time, bits) pairs from genesis pow data.
          for(timeBitsTuple <- params.genesisPoWData.reverse) {
            timeBitsData = timeBitsTuple :: timeBitsData
            if(timeBitsData.size == params.nPowAveragingWindow + params.nMedianTimeSpan)
              break
          }
          break
        }

        // get previous block
        currentBlock = sidechainHistoryStorage.blockById(currentBlock.parentId) match {
          case b: Some[SidechainBlock] => b.get
          case _ => return false
        }

        // check for mainchain block references and their order, and collect data from them.
        if(currentBlock.mainchainBlocks.nonEmpty) {
          for(mcref <- currentBlock.mainchainBlocks.reverse) {
            if(!mcref.hash.sameElements(currentMCBlockReference.header.hashPrevBlock))
              return false
            timeBitsData = Tuple2[Int, Int](mcref.header.time, mcref.header.bits) :: timeBitsData
            currentMCBlockReference = mcref
            if(timeBitsData.size == params.nPowAveragingWindow + params.nMedianTimeSpan)
              break
          }
        }
      }
    }

    // check that we have enough data for next pow verification
    if(timeBitsData.size != params.nPowAveragingWindow + params.nMedianTimeSpan)
      return false

    // calculate totalBits for last params.nPowAveragingWindow blocks
    var bitsTotal: BigInteger = BigInteger.ZERO
    for(i <- timeBitsData.size - params.nPowAveragingWindow until timeBitsData.size) {
      bitsTotal = bitsTotal.add(Utils.decodeCompactBits(UnsignedInts.toLong(timeBitsData(i)._2)))
    }

    // verify next work for each MC block reference in the requested block
    for(mcref <- block.mainchainBlocks) {
      val timeData: Seq[Int] = timeBitsData.map(timeBitsData => timeBitsData._1)
      val bitsAvg = bitsTotal.divide(BigInteger.valueOf(params.nPowAveragingWindow))

      val res = ProofOfWorkVerifier.calculateNextWorkRequired(
        bitsAvg,
        geMedianTimePast(timeData, timeData.size - params.nPowAveragingWindow, params),
        geMedianTimePast(timeData, timeData.size, params),
        params)

      if(!res.equals(mcref.header.bits))
        return false

      // subtract oldest MC block target data and add current one
      bitsTotal = bitsTotal
        .subtract(Utils.decodeCompactBits(UnsignedInts.toLong(timeBitsData(timeBitsData.size - params.nPowAveragingWindow)._2)))
        .add(Utils.decodeCompactBits(UnsignedInts.toLong(mcref.header.bits)))
      // remove oldest time/bits data info, append with current block info
      timeBitsData = timeBitsData.drop(1) :+ Tuple2[Int, Int](mcref.header.time, mcref.header.bits)
    }

    true
  }

  def geMedianTimePast(times: Seq[Int], index: Int, params: NetworkParams): Int = {
    val median = times.slice(index - params.nMedianTimeSpan, index).sortWith((a, b) => a < b)
    median(params.nMedianTimeSpan / 2)
  }

  def calculateNextWorkRequired(bitsAvg: BigInteger, firstBlockTime: Int, lastBlockTime: Int, params: NetworkParams): Int = {
    var actualTimespan: Int = lastBlockTime - firstBlockTime

    // Limit the adjustment step.
    // Use medians to prevent time-warp attacks
    actualTimespan = params.averagingWindowTimespan + (actualTimespan - params.averagingWindowTimespan) / 4

    if (actualTimespan < params.MinActualTimespan)
      actualTimespan = params.MinActualTimespan
    if (actualTimespan > params.MaxActualTimespan)
      actualTimespan = params.MaxActualTimespan

    var bitsNew: BigInteger = bitsAvg.multiply(BigInteger.valueOf(actualTimespan)).divide(BigInteger.valueOf(params.averagingWindowTimespan))
    if(bitsNew.compareTo(params.powLimit) > 0)
      bitsNew = params.powLimit

    Utils.encodeCompactBits(bitsNew).toInt
  }
}
