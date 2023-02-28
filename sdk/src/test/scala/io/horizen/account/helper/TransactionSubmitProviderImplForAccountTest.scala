package io.horizen.account.helper

import io.horizen.SidechainTypes
import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.helper.TransactionSubmitProviderImplTest

import scala.language.postfixOps

class TransactionSubmitProviderImplForAccountTest extends TransactionSubmitProviderImplTest with EthereumTransactionFixture{
  override type TX = SidechainTypes#SCAT
  
  override  def getTransaction: TX = getEoa2EoaEip1559Transaction
}