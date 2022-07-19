package com.horizen.account.api.http

import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state.{AccountForgingStakeInfo, ForgerPublicKeys, ForgerStakeData, WithdrawalRequest}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.api.http.SidechainApiMockConfiguration
import com.horizen.fixtures._
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.{MCPublicKeyHashProposition, PublicKey25519Proposition, VrfPublicKey}
import com.horizen.utils.BytesUtils
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import org.web3j.crypto.{Keys, RawTransaction, Sign, SignedRawTransaction}

import java.math.BigInteger
import java.util
import scala.collection.JavaConverters._
import scala.util.Random

class AccountNodeViewUtilMocks extends MockitoSugar with BoxFixture with CompanionsFixture with SecretFixture{

  val transactionList: util.List[EthereumTransaction] = getTransactionList
  val listOfStakes: Seq[AccountForgingStakeInfo] = getListOfStakes
  val listOfWithdrawalRequests: Seq[WithdrawalRequest] = getListOfWithdrawalRequests

  val ownerString = "00ddbbcc9900aabbcc9900aabbcc9900aabbcc99"
  val blockSignerPropositionString = "1122334455669988112233445566778811223344556677881122334455667788"
  val vrfPublicKeyString = "aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234"
  val fittingSecret =  getPrivateKeySecp256k1(10344)


  def getNodeHistoryMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeAccountHistory = {
    val history: NodeAccountHistory = mock[NodeAccountHistory]
    history
  }


  def getNodeStateMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeAccountState = {
    val accountState = mock[NodeAccountState]
    Mockito.when(accountState.getListOfForgerStakes).thenAnswer(_ => listOfStakes)
    Mockito.when(accountState.withdrawalRequests(ArgumentMatchers.anyInt())).thenAnswer(_ => listOfWithdrawalRequests)
    Mockito.when(accountState.getBalance(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => ZenWeiConverter.MAX_MONEY_IN_WEI)//It has always enough money
    Mockito.when(accountState.getNonce(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => BigInteger.ONE)//It has always enough money

    accountState
  }

  def getNodeWalletMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeWalletBase = {
    val wallet: NodeWalletBase = mock[NodeWalletBase]
    Mockito.when(wallet.secretsOfType(classOf[PrivateKeySecp256k1])).thenAnswer(_ => util.Arrays.asList(fittingSecret))

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
    list.add(getTransaction(ZenWeiConverter.convertZenniesToWei(1))) // 1 Zenny
    list.add(getTransaction(ZenWeiConverter.convertZenniesToWei(12))) // 12 Zennies
    list
  }

  def getListOfStakes: Seq[AccountForgingStakeInfo] = {
    val list: util.List[AccountForgingStakeInfo] = new util.ArrayList[AccountForgingStakeInfo]()
    val owner: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    val forgerKeys = new ForgerPublicKeys(blockSignerProposition, vrfPublicKey)
    val forgingStakeData = new ForgerStakeData(forgerKeys, owner, BigInteger.ONE)
    val stake = new AccountForgingStakeInfo(new Array[Byte](32), forgingStakeData)
    list.add(stake)
    list.asScala
  }


  def getListOfWithdrawalRequests: Seq[WithdrawalRequest] = {
    val list: util.List[WithdrawalRequest] = new util.ArrayList[WithdrawalRequest]()
    val mcAddr = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    val valueInWei = ZenWeiConverter.convertZenniesToWei(123)
    val request = new WithdrawalRequest(mcAddr, valueInWei)
    list.add(request)
    list.asScala

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
