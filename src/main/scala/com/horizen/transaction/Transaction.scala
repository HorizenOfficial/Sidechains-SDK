package com.horizen.transaction

import com.horizen.serialization.JsonSerializable

abstract class Transaction
  extends scorex.core.transaction.Transaction
  with JsonSerializable
{
  def transactionTypeId: Byte
}
