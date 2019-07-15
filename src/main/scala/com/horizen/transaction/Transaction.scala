package com.horizen.transaction

import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId
import scorex.util.bytesToId


abstract class Transaction
  extends scorex.core.transaction.Transaction
{
  def transactionTypeId: Byte
}
