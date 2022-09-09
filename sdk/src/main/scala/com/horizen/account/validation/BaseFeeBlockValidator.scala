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
    val expectedBaseFee: BigInteger = FeeUtils.calculateBaseFee(history, block.header.parentId)
    if (baseFee.compareTo(expectedBaseFee) != 0)
      throw new InvalidBaseFeeException(s"Calculated base fee ${baseFee} of block with id ${block.id} is invalid")
  }
}
