package com.horizen.transaction

abstract class Transaction
  extends sparkz.core.transaction.Transaction
{
  def transactionTypeId: Byte
  def version: Byte
  def size(): Long
}
