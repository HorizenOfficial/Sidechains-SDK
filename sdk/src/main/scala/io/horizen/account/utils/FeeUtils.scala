package io.horizen.account.utils

import io.horizen.account.block.AccountBlock
import io.horizen.account.fork.GasFeeFork
import io.horizen.account.history.AccountHistory
import io.horizen.params.NetworkParams
import io.horizen.utils.TimeToEpochUtils
import sparkz.core.block.Block.BlockId

import java.math.BigInteger

object FeeUtils {

  val INITIAL_BASE_FEE: BigInteger = BigInteger.valueOf(1000000000)

  def calculateBaseFee(history: AccountHistory, parentId: BlockId): BigInteger = {
    // If the current block is the first block, return the InitialBaseFee.
    if (parentId == history.params.sidechainGenesisBlockParentId) {
      return INITIAL_BASE_FEE
    }
    history.modifierById(parentId).map(calculateBaseFeeForBlock(_, history.params)).getOrElse(INITIAL_BASE_FEE)
  }

  def calculateNextBaseFee(block: AccountBlock, params: NetworkParams): BigInteger = {
    if (block == null) INITIAL_BASE_FEE
    else calculateBaseFeeForBlock(block, params)
  }

  private def calculateBaseFeeForBlock(block: AccountBlock, params: NetworkParams): BigInteger = {
    val blockHeader = block.header
    val feeFork = GasFeeFork.get(TimeToEpochUtils.timeStampToEpochNumber(params.sidechainGenesisBlockTimestamp, blockHeader.timestamp))
    val gasTarget = blockHeader.gasLimit.divide(feeFork.baseFeeElasticityMultiplier)

    // If the parent gasUsed is the same as the target, the baseFee remains unchanged
    val nextBaseFee = if (blockHeader.gasUsed.equals(gasTarget)) {
      blockHeader.baseFee
    } else {
      val gasDiff = blockHeader.gasUsed.subtract(gasTarget)

      val baseFeeDiff = gasDiff.abs
        .multiply(blockHeader.baseFee)
        .divide(gasTarget)
        .divide(feeFork.baseFeeChangeDenominator)

      if (gasDiff.signum == 1) {
        // If the parent block used more gas than its target, the baseFee should increase
        blockHeader.baseFee.add(baseFeeDiff.max(BigInteger.ONE))
      } else {
        // Otherwise if the parent block used less gas than its target, the baseFee should decrease
        blockHeader.baseFee.subtract(baseFeeDiff)
      }
    }

    // apply a lower limit to the base fee
    nextBaseFee.max(feeFork.baseFeeMinimum)
  }
}
