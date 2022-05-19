package com.horizen.companion

import com.horizen.transaction._
import com.horizen.transaction.CoreTransactionsIdsEnum._
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes
import com.horizen.utils.DynamicTypedSerializer

case class SidechainAccountTransactionsCompanion(customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]])
  extends DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]](
    new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]]() {{
      // TODO
      //      put(MC2SCAggregatedTransactionId.id(), MC2SCAggregatedTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
      //      put(SidechainCoreTransactionId.id(), SidechainCoreTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
    }},
    customTransactionSerializers)