package com.horizen.account.utils

import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import sparkz.core.block.Block.BlockId

import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionalGeneric

object FeeUtils {

  val GAS_LIMIT = 30000000
  val INITIAL_BASE_FEE: BigInteger = BigInteger.valueOf(1000000000)

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
    val gasTarget = blockHeader.gasLimit / 2

    // If the parent gasUsed is the same as the target, the baseFee remains unchanged
    if (blockHeader.gasUsed == gasTarget) {
      return blockHeader.baseFee
    }

    if (blockHeader.gasUsed > gasTarget) {
      // If the parent block used more gas than its target, the baseFee should increase
      var baseFeeInc: BigInteger = BigInteger.valueOf(blockHeader.gasUsed - gasTarget)
      baseFeeInc = baseFeeInc.multiply(blockHeader.baseFee)
      baseFeeInc = baseFeeInc.divide(BigInteger.valueOf(gasTarget))
      baseFeeInc = baseFeeInc.divide(BigInteger.valueOf(8))
      blockHeader.baseFee.add(baseFeeInc.max(BigInteger.ONE))
    } else {
      // Otherwise if the parent block used less gas than its target, the baseFee should decrease
      var baseFeeDec: BigInteger = BigInteger.valueOf(gasTarget - blockHeader.gasUsed)
      baseFeeDec = baseFeeDec.multiply(blockHeader.baseFee)
      baseFeeDec = baseFeeDec.divide(BigInteger.valueOf(gasTarget))
      baseFeeDec = baseFeeDec.divide(BigInteger.valueOf(8))
      blockHeader.baseFee.subtract(baseFeeDec).max(BigInteger.ONE)
    }
  }
}
