package com.horizen.companion

import com.horizen.transaction._

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.utils.SerializableCompanion

case class SidechainTransactionsCompanion(customSerializers: JHashMap[JByte, TransactionSerializer[_ <: Transaction]])
  extends SerializableCompanion[Transaction, TransactionSerializer[_ <: Transaction]](
    new JHashMap[JByte, TransactionSerializer[_ <: Transaction]]() {{
      put(RegularTransaction.TRANSACTION_TYPE_ID, RegularTransactionSerializer.getSerializer)
      put(MC2SCAggregatedTransaction.TRANSACTION_TYPE_ID, MC2SCAggregatedTransactionSerializer.getSerializer)
      // put(WithdrawalRequestTransaction.TRANSACTION_TYPE_ID, WithdrawalRequestTransaction.getSerializer)
      // put(CertifierUnlockRequestTransaction.TRANSACTION_TYPE_ID,  CertifierUnlockRequestTransaction.getSerializer)
    }},
    customSerializers)
