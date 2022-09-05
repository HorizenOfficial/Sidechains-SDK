package com.horizen.account.validation

import com.horizen.SidechainTypes
import com.horizen.account.FeeUtils
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.validation.HistoryBlockValidator

import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.util.Try

case class BaseFeeBlockValidator() extends HistoryBlockValidator[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountHistoryStorage, AccountHistory] {

  override def validate(block: AccountBlock, history: AccountHistory): Try[Unit] = Try {
    val baseFee = block.header.baseFee
    history.getBlockById(block.header.parentId).asScala match {
      case None =>
        if (history.isGenesisBlock(block.id) && baseFee.compareTo(FeeUtils.INITIAL_BASE_FEE) != 0)
          throw new InvalidBaseFeeException(s"No valid parent block with id ${block.header.parentId} found, base fee ${baseFee} incorrect")
      case Some(parentBlock) =>
        val parentBaseFee = parentBlock.header.baseFee
        val range = parentBaseFee.multiply(BigInteger.valueOf(125)).divide(BigInteger.valueOf(1000))
        val lowerBound = parentBaseFee.subtract(range).max(BigInteger.ONE)
        val upperBound = parentBaseFee.add(range).max(BigInteger.ONE)
        if (baseFee.compareTo(lowerBound) < 0 || baseFee.compareTo(upperBound) > 0)
          throw new InvalidBaseFeeException(s"Calculated base fee ${baseFee} of block with id ${block.id} is out of 12.5% adjustment range")
    }
  }
}
