package com.horizen.companion

import com.horizen.transaction._
import com.horizen.account.transaction.AccountTransactionsIdsEnum._

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}
import com.horizen.SidechainTypes
import com.horizen.account.transaction.{AccountTransactionsIdsEnum, EthereumTransactionSerializer}
import com.horizen.utils.DynamicTypedSerializer

case class SidechainAccountTransactionsCompanion(customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]])
  extends DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]](
    new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]]() {{
      // TODO
            //put(MC2SCAggregatedTransactionId.id(), MC2SCAggregatedTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
            put(EthereumTransaction.id(), EthereumTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
    }},
    customAccountTransactionSerializers)