package com.horizen.account.helper

import com.horizen.SidechainTypes
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.helper.TransactionSubmitProviderImplTest

import scala.language.postfixOps

class TransactionSubmitProviderImplForAccountTest extends TransactionSubmitProviderImplTest with EthereumTransactionFixture{
  override type TX = SidechainTypes#SCAT
  
  override  def getTransaction: TX = getEoa2EoaEip1559Transaction
}