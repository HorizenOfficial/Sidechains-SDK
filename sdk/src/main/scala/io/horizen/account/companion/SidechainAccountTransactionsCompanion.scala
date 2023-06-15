package io.horizen.account.companion

import io.horizen.SidechainTypes
import io.horizen.account.transaction.AccountTransactionsIdsEnum.EthereumTransactionId
import io.horizen.account.transaction.EthereumTransactionSerializer
import io.horizen.transaction.TransactionSerializer
import io.horizen.utils.{CheckedCompanion, DynamicTypedSerializer}

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

case class SidechainAccountTransactionsCompanion(customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]])
  extends DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]](
    new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]]() {
      {
        put(EthereumTransactionId.id(), EthereumTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
      }
    },
    customAccountTransactionSerializers
  ) with CheckedCompanion[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]]
