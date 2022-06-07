package com.horizen.account.api.http

import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.api.http.SidechainApiMockConfiguration
import com.horizen.fixtures._
import com.horizen.node.NodeWalletBase
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.crypto.{Keys, RawTransaction, Sign, SignedRawTransaction}

import java.util

class AccountNodeViewUtilMocks extends MockitoSugar with BoxFixture with CompanionsFixture {

  val transactionList: util.List[EthereumTransaction] = getTransactionList


  def getNodeHistoryMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeAccountHistory = {
    val history: NodeAccountHistory = mock[NodeAccountHistory]
    history
  }


  def getNodeStateMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeAccountState = {
    mock[NodeAccountState]
  }

  def getNodeWalletMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeWalletBase = {
    val wallet: NodeWalletBase = mock[NodeWalletBase]
    wallet
  }

  private def getTransaction(value: java.math.BigInteger): EthereumTransaction = {
    val rawTransaction = RawTransaction.createTransaction(value, value, value, "0x", value, "")
    val tmp = new EthereumTransaction(rawTransaction)
    val message = tmp.messageToSign()

    // Create a key pair, create tx signature and create ethereum Transaction
    val pair = Keys.createEcKeyPair
    val msgSignature = Sign.signMessage(message, pair, true)
    val signedRawTransaction = new SignedRawTransaction(value, value, value, "0x", value, "", msgSignature)
    new EthereumTransaction(signedRawTransaction)
  }

  def getTransactionList: util.List[EthereumTransaction] = {
    val list: util.List[EthereumTransaction] = new util.ArrayList[EthereumTransaction]()
    list.add(getTransaction(ZenConverter.convertZenniesToWei(1))) // 1 Zenny
    list.add(getTransaction(ZenConverter.convertZenniesToWei(12))) // 12 Zennies
    list
  }

  def getNodeMemoryPoolMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeAccountMemoryPool = {
    val memoryPool: NodeAccountMemoryPool = mock[NodeAccountMemoryPool]

    Mockito.when(memoryPool.getTransactions).thenAnswer(_ => transactionList)
    memoryPool
  }


  def getAccountNodeView(sidechainApiMockConfiguration: SidechainApiMockConfiguration): AccountNodeView =
    new AccountNodeView(
      getNodeHistoryMock(sidechainApiMockConfiguration),
      getNodeStateMock(sidechainApiMockConfiguration),
      getNodeWalletMock(sidechainApiMockConfiguration),
      getNodeMemoryPoolMock(sidechainApiMockConfiguration))


}
