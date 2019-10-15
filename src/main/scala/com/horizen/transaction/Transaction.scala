package com.horizen.transaction

abstract class Transaction
  extends scorex.core.transaction.Transaction
{
  def transactionTypeId: Byte
}
