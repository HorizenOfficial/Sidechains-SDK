package com.horizen.companion

import com.horizen.transaction._
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes
import com.horizen.utils.DynamicTypedSerializer

case class SidechainTransactionsCompanion(customSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]])
  extends DynamicTypedSerializer[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]](
    new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]() {{
      put(RegularTransaction.TRANSACTION_TYPE_ID, RegularTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      put(MC2SCAggregatedTransaction.TRANSACTION_TYPE_ID, MC2SCAggregatedTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      // put(WithdrawalRequestTransaction.TRANSACTION_TYPE_ID, WithdrawalRequestTransaction.getSerializer)
      // put(CertifierUnlockRequestTransaction.TRANSACTION_TYPE_ID,  CertifierUnlockRequestTransaction.getSerializer)
    }},
    customSerializers)
