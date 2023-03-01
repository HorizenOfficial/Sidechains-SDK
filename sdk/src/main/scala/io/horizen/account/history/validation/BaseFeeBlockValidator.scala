package io.horizen.account.history.validation

import io.horizen.SidechainTypes
import io.horizen.account.utils.FeeUtils
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.history.AccountHistory
import io.horizen.account.storage.AccountHistoryStorage
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.history.validation.HistoryBlockValidator
import java.math.BigInteger
import scala.util.Try

case class BaseFeeBlockValidator() extends HistoryBlockValidator[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountFeePaymentsInfo, AccountHistoryStorage, AccountHistory] {

  override def validate(block: AccountBlock, history: AccountHistory): Try[Unit] = Try {
    val baseFee = block.header.baseFee
    val expectedBaseFee: BigInteger = FeeUtils.calculateBaseFee(history, block.header.parentId)
    if (!baseFee.equals(expectedBaseFee))
      throw new InvalidBaseFeeException(s"Calculated base fee $baseFee of block with id ${block.id} is invalid (expected: $expectedBaseFee)")
  }
}
