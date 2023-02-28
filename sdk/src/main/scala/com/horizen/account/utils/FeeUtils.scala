package com.horizen.account.utils

import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import sparkz.core.block.Block.BlockId

import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionalGeneric

object FeeUtils {

  val GAS_LIMIT: BigInteger = BigInteger.valueOf(30000000L)
  val INITIAL_BASE_FEE: BigInteger = BigInteger.valueOf(1000000000)
  val BASE_FEE_CHANGE_DENOMINATOR: BigInteger = BigInteger.valueOf(8)
  val BASE_FEE_ELASTICITY_MULTIPLIER: BigInteger = BigInteger.valueOf(2)

  def calculateBaseFee(history: AccountHistory, parentId: BlockId): BigInteger = {
    // If the current block is the first block, return the InitialBaseFee.
    if (parentId == history.params.sidechainGenesisBlockParentId) {
      return INITIAL_BASE_FEE
    }

    history.getBlockById(parentId).asScala match {
      case None => INITIAL_BASE_FEE
      case Some(block) => calculateBaseFeeForBlock(block)
    }
  }

  def calculateNextBaseFee(block: AccountBlock): BigInteger = {
    if (block == null) INITIAL_BASE_FEE
    else calculateBaseFeeForBlock(block)
  }

  private def calculateBaseFeeForBlock(block: AccountBlock): BigInteger = {
    val blockHeader = block.header
    val gasTarget = blockHeader.gasLimit.divide(BASE_FEE_ELASTICITY_MULTIPLIER)

    // If the parent gasUsed is the same as the target, the baseFee remains unchanged
    if (blockHeader.gasUsed.equals(gasTarget)) {
      return blockHeader.baseFee
    }

    val gasDiff = blockHeader.gasUsed.subtract(gasTarget)

    val baseFeeDiff = gasDiff.abs
      .multiply(blockHeader.baseFee)
      .divide(gasTarget)
      .divide(BASE_FEE_CHANGE_DENOMINATOR)

    if (gasDiff.signum == 1) {
      // If the parent block used more gas than its target, the baseFee should increase
      blockHeader.baseFee.add(baseFeeDiff.max(BigInteger.ONE))
    } else {
      // Otherwise if the parent block used less gas than its target, the baseFee should decrease
      blockHeader.baseFee.subtract(baseFeeDiff).max(BigInteger.ZERO)
    }
  }
}
