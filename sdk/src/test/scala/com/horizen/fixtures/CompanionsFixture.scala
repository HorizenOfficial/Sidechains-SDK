package com.horizen.fixtures

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import com.horizen.SidechainTypes
import com.horizen.account.companion
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.companion.{SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.secret.SecretSerializer
import com.horizen.transaction.{RegularTransactionSerializer, TransactionSerializer}

trait CompanionsFixture
{
  def getDefaultTransactionsCompanion: SidechainTransactionsCompanion = {

    SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]](){{
      put(111.byteValue(), RegularTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
    }})
  }

  def getDefaultAccountTransactionsCompanion: SidechainAccountTransactionsCompanion = {

    companion.SidechainAccountTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]](){{
      put(111.byteValue(), RegularTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
    }})
  }

  def getTransactionsCompanionWithCustomTransactions(customSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]): SidechainTransactionsCompanion = {
    SidechainTransactionsCompanion(customSerializers)
  }

  def getAccountTransactionsCompanionWithCustomTransactions(customSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]]): SidechainAccountTransactionsCompanion = {
    companion.SidechainAccountTransactionsCompanion(customSerializers)
  }

  def getDefaultSecretCompanion: SidechainSecretsCompanion = {
    SidechainSecretsCompanion(new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]())
  }
}

class CompanionsFixtureClass extends CompanionsFixture
