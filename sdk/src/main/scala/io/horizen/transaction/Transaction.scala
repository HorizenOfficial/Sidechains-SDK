package io.horizen.transaction

import io.horizen.transaction.exception.TransactionSemanticValidityException

abstract class Transaction
  extends sparkz.core.transaction.Transaction
{
  def transactionTypeId: Byte
  def version: Byte

  @throws[TransactionSemanticValidityException]
  def semanticValidity(): Unit

  def size(): Long
}
