package com.horizen.transaction

import com.horizen.transaction.exception.TransactionSemanticValidityException

abstract class Transaction
  extends sparkz.core.transaction.Transaction
{
  def transactionTypeId: Byte
  def version: Byte

  @throws[TransactionSemanticValidityException]
  def semanticValidity(): Unit

  def size(): Long
}
