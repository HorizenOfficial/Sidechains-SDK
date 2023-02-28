package io.horizen.fixtures

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import io.horizen.SidechainTypes
import io.horizen.account.companion
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.secret.SecretSerializer
import io.horizen.transaction.TransactionSerializer
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.utxo.transaction.RegularTransactionSerializer

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
