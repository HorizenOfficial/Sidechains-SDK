package com.horizen.companion

import com.horizen.transaction._
import com.horizen.transaction.CoreTransactionsIdsEnum._
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes
import com.horizen.utils.DynamicTypedSerializer


case class SidechainTransactionsCompanion(customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]])
  extends DynamicTypedSerializer[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]](
    new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]() {{
      put(MC2SCAggregatedTransactionId.id(), MC2SCAggregatedTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      put(SidechainCoreTransactionId.id(), SidechainCoreTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
    }},
    customTransactionSerializers)
