package com.horizen.block

import java.math.BigInteger

import com.google.common.primitives.UnsignedInts
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.utils.{BytesUtils, Utils}

import scala.util.control.Breaks._

object ProofOfWorkVerifier {

  private case class OmmersContainerNextWorkRequiredResult(isValid: Boolean, actualTimeBitsData: List[Tuple2[Int, Int]], actualBitsTotal: BigInteger)

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

  // Check that PoW target (bits) is correct for all MainchainHeaders and Ommers' MainchainHeaders (recursively) included into SidechainBlock.
  // The order of MainchainHeaders in Block (both active and orphaned) verified in block semantic validity method
  def checkNextWorkRequired(block: SidechainBlock, sidechainHistoryStorage: SidechainHistoryStorage, params: NetworkParams): Boolean = {
    if(block.mainchainHeaders.isEmpty)
      return true

    // Collect information of time and bits for last "params.nPowAveragingWindow + params.nMedianTimeSpan" MainchainBlockReferences
    // already presented in a current chain of SidechainBlocks.
    var timeBitsData = List[Tuple2[Int, Int]]()
    // Take firt MC Ref header if exists, else get first nextMCHeader
    var currentHeader = block.mainchainHeaders.head
    var currentBlock: SidechainBlock = block
    breakable {
      while (true) {
        if (currentHeader.hash.sameElements(params.genesisMainchainBlockHash)) {
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

        // check for mainchain headers and their order, and collect data from them.
        if(currentBlock.mainchainHeaders.nonEmpty) {
          for(header <- currentBlock.mainchainHeaders.reverse) {
            if(!header.hash.sameElements(currentHeader.hashPrevBlock))
              return false
            timeBitsData = Tuple2[Int, Int](header.time, header.bits) :: timeBitsData
            currentHeader = header
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

    // verify next work for each MainchainHeader in the requested block
    if(!checkOmmersContainerNextWorkRequired(block, timeBitsData, bitsTotal, params).isValid)
      return false

    true
  }

  private def checkOmmersContainerNextWorkRequired(ommersContainer: OmmersContainer,
                                                   initialTimeBitsData: List[Tuple2[Int, Int]],
                                                   initialBitsTotal: BigInteger,
                                                   params: NetworkParams): OmmersContainerNextWorkRequiredResult = {
    var timeBitsData = initialTimeBitsData
    var bitsTotal = initialBitsTotal

    for(mainchainHeader <- ommersContainer.mainchainHeaders) {
      val timeData: Seq[Int] = timeBitsData.map(data => data._1)
      val bitsAvg = bitsTotal.divide(BigInteger.valueOf(params.nPowAveragingWindow))

      val res = ProofOfWorkVerifier.calculateNextWorkRequired(
        bitsAvg,
        geMedianTimePast(timeData, timeData.size - params.nPowAveragingWindow, params),
        geMedianTimePast(timeData, timeData.size, params),
        params)

      // TO DO: BigInteger has a higher precision than uint256 on divide operation, that's why our result can be bigger (a bit), than actual in nBits value
      // Precision should be decreased after any divide operation. See commented code in calculateNextWorkRequired and in BitcoinJ implementation.
      if(Math.abs(res - mainchainHeader.bits) > 1)
        return OmmersContainerNextWorkRequiredResult(false, timeBitsData, bitsTotal)

      // subtract oldest MC block target data and add current one
      bitsTotal = bitsTotal
        .subtract(Utils.decodeCompactBits(UnsignedInts.toLong(timeBitsData(timeBitsData.size - params.nPowAveragingWindow)._2)))
        .add(Utils.decodeCompactBits(UnsignedInts.toLong(mainchainHeader.bits)))
      // remove oldest time/bits data info, append with current block info
      timeBitsData = timeBitsData.tail :+ Tuple2[Int, Int](mainchainHeader.time, mainchainHeader.bits)
    }

    // check Ommers NextWorkRequired one by one
    var ommersTimeBitsData = initialTimeBitsData
    var ommersBitsTotal = initialBitsTotal
    for(ommer <- ommersContainer.ommers) {
      val res: OmmersContainerNextWorkRequiredResult = checkOmmersContainerNextWorkRequired(ommer, ommersTimeBitsData, ommersBitsTotal, params)
      if(!res.isValid)
        return res
      ommersTimeBitsData = res.actualTimeBitsData
      ommersBitsTotal = res.actualBitsTotal
    }

    OmmersContainerNextWorkRequiredResult(true, timeBitsData, bitsTotal)
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

    // TO DO: The calculated difficulty is to a higher precision than received, so reduce here.
    /*if(nextBits.isDefined) {
      val accuracyBytes = (nextBits.get >>> 24) - 3
      val mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8)
      bitsNew = bitsNew.and(mask)
    }*/

    Utils.encodeCompactBits(bitsNew).toInt
  }

  // Expect powData hex representation from MC RPC getscgenesisinfo
  def parsePowData(powData: String): Seq[(Int, Int)] = {
    var res: Seq[(Int, Int)] = Seq()
    val powDataBytes: Array[Byte] = BytesUtils.fromHexString(powData)
    var offset = 0
    while(offset < powDataBytes.length) {
      res = res :+ (
        BytesUtils.getReversedInt(powDataBytes, offset),
        BytesUtils.getReversedInt(powDataBytes, offset + 4)
      )
      offset += 8
    }
    res.reverse
  }
}
