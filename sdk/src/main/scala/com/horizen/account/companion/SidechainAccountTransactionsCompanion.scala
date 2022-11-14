package com.horizen.account.companion

import com.horizen.SidechainTypes
import com.horizen.account.transaction.AccountTransactionsIdsEnum.EthereumTransactionId
import com.horizen.account.transaction.EthereumTransactionSerializer
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.DynamicTypedSerializer

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

case class SidechainAccountTransactionsCompanion(customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]])
  extends DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]](
    new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]]() {
      {
        put(EthereumTransactionId.id(), EthereumTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
      }
    },
    customAccountTransactionSerializers)
