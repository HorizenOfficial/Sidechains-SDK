package com.horizen.block

import java.math.BigInteger

import com.google.common.primitives.UnsignedInts
import com.horizen.SidechainHistory
import com.horizen.params.NetworkParams
import com.horizen.utils.Utils

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

  // TODO
  def checkNextWorkRequired(newHeader: MainchainHeader, sidechainHistory: SidechainHistory, params: NetworkParams): Boolean = {
    // 1. TO DO: Loop through last params.nPowAveragingWindow Mainchain Blocks
    // 2. TO DO: Sum nBits values as BigInteger

    // NOTE: we should process situation for first params.nPowAveragingWindow MC blocks in SC.
    // SC developer should provide genesis nPowAveragingWindowIndex and nPowAveragingWindowValue

    // NOTE: SidechainHistory must have and interface for retrieving various info about included MC Blocks
    var bitsTotal: BigInteger = null
    var lastMainchainHeader: MainchainHeader = null // previous to newHeader
    var firstMainchainHeader: MainchainHeader = null // header with height equals to <lastMainchainHeader height> - params.nPowAveragingWindow

    // 3. calculate bitsAvg for last params.nPowAveragingWindow
    val bitsAvg = bitsTotal.divide(BigInteger.valueOf(params.nPowAveragingWindow))
    // 4. verify
    val res = ProofOfWorkVerifier.calculateNextWorkRequired(bitsAvg, lastMainchainHeader.time, firstMainchainHeader.time, params)
    res.equals(newHeader.bits)
  }

  def calculateNextWorkRequired(bitsAvg: BigInteger, lastBlockTime: Int, firstBlockTime: Int, params: NetworkParams): Int = {
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
