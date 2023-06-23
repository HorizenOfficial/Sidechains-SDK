package io.horizen.account.api.http

import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Serializer}
import io.horizen.account.state.{AccountForgingStakeInfo, ForgerPublicKeys, ForgerStakeData, WithdrawalRequest}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.api.http.SidechainApiMockConfiguration
import io.horizen.evm.Address
import io.horizen.fixtures._
import io.horizen.node.NodeWalletBase
import io.horizen.proposition.{MCPublicKeyHashProposition, PublicKey25519Proposition, VrfPublicKey}
import io.horizen.secret
import io.horizen.utils.BytesUtils
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import java.util.Optional
import scala.collection.JavaConverters._
import scala.util.Random

class AccountNodeViewUtilMocks extends MockitoSugar
  with EthereumTransactionFixture
  with SecretFixture {

  val ownerSecret: PrivateKeySecp256k1 = getPrivateKeySecp256k1(2222222)
  val signerSecret: secret.PrivateKey25519 = getPrivateKey25519("signer".getBytes(StandardCharsets.UTF_8))
  val ownerAddress: Address = ownerSecret.publicImage().address()
  val blockSignerPropositionString = "1122334455669988112233445566778811223344556677881122334455667788"
  val vrfPublicKeyString = "aabbddddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234"
  val stakeId = "9e26bd4ff89374e916b369024e882db68a49b824e71008b827c7794e9f4d0170"
  val forgerIndex : Int= 0 // must match the size of Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))


  val transactionList: util.List[EthereumTransaction] = getTransactionList
  val listOfStakes: Seq[AccountForgingStakeInfo] = getListOfStakes
  val listOfWithdrawalRequests: Seq[WithdrawalRequest] = getListOfWithdrawalRequests

  val fittingSecret1: PrivateKeySecp256k1 = PrivateKeySecp256k1Serializer.getSerializer.parseBytes(BytesUtils.fromHexString("00f0f7b743d07bef4b04640ec8f6aaf38e104fb9d0f0f787bc0016dd3528ddc6"))
  val fittingSecret2: PrivateKeySecp256k1 = PrivateKeySecp256k1Serializer.getSerializer.parseBytes(BytesUtils.fromHexString("008d5fa175416759f80871e39307c2e23cd71936d57870bdd5a6c80531047c75"))

  def getNodeHistoryMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeAccountHistory = {
    val history = mock[NodeAccountHistory]
    val block = mock[AccountBlock]
    val header = mock[AccountBlockHeader]
    Mockito.when(history.getBestBlock).thenAnswer(_ => block)
    Mockito.when(block.header).thenAnswer(_ => header)
    Mockito.when(header.baseFee).thenAnswer(_ => BigInteger.valueOf(1234))
    history
  }

  def getNodeStateMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeAccountState = {
    val accountState = mock[NodeAccountState]
    Mockito.when(accountState.getListOfForgersStakes).thenReturn(listOfStakes)
    Mockito.when(accountState.getWithdrawalRequests(ArgumentMatchers.anyInt())).thenReturn(listOfWithdrawalRequests)
    Mockito
      .when(accountState.getBalance(ArgumentMatchers.any[Address]))
      .thenAnswer(answer => {
        val adressStr = BytesUtils.toHexString(answer.getArgument(0).asInstanceOf[Address].toBytes)
        adressStr match {
          case "e891eb898a1ebd7809089ee6532327167fcd064f" => ZenWeiConverter.convertZenniesToWei(5000)
          case _ => ZenWeiConverter.MAX_MONEY_IN_WEI
        }
      })
    Mockito
      .when(accountState.getNonce(ArgumentMatchers.any[Address]))
      .thenReturn(BigInteger.ONE)
    Mockito
      .when(accountState.getNextBaseFee)
      .thenReturn(BigInteger.valueOf(1234))
    Mockito
      .when(accountState.getForgerStakeData(ArgumentMatchers.anyString()))
      .thenAnswer(myStakeId =>
        getListOfStakes
          .find(stake => BytesUtils.toHexString(stake.stakeId).equals(myStakeId.getArgument(0)))
          .map(stakeInfo => stakeInfo.forgerStakeData)
      )
    accountState
  }

  def getNodeWalletMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeWalletBase = {
    val wallet: NodeWalletBase = mock[NodeWalletBase]
    Mockito.when(wallet.secretsOfType(classOf[PrivateKeySecp256k1])).thenAnswer(_ => util.Arrays.asList(fittingSecret1, fittingSecret2))
    Mockito.when(wallet.secretByPublicKey(ownerSecret.publicImage())).thenAnswer(_ => Optional.of(ownerSecret))
    Mockito.when(wallet.secretByPublicKey(signerSecret.publicImage())).thenAnswer(_ => Optional.of(signerSecret))
    wallet
  }

  def getTransactionList: util.List[EthereumTransaction] = {
    val list: util.List[EthereumTransaction] = new util.ArrayList[EthereumTransaction]()
    list.add(createLegacyTransaction(ZenWeiConverter.convertZenniesToWei(1))) // 1 Zenny
    list.add(createLegacyTransaction(ZenWeiConverter.convertZenniesToWei(12))) // 12 Zennies
    list
  }

  def getListOfStakes: Seq[AccountForgingStakeInfo] = {
    val list: util.List[AccountForgingStakeInfo] = new util.ArrayList[AccountForgingStakeInfo]()
    val owner: AddressProposition = new AddressProposition(ownerAddress)
    val blockSignerProposition =
      new PublicKey25519Proposition(BytesUtils.fromHexString(blockSignerPropositionString)) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(vrfPublicKeyString)) // 33 bytes

    val forgerKeys = ForgerPublicKeys(blockSignerProposition, vrfPublicKey)
    val forgingStakeData = ForgerStakeData(forgerKeys, owner, BigInteger.ONE)
    val stake = AccountForgingStakeInfo(BytesUtils.fromHexString(stakeId), forgingStakeData)
    list.add(stake)
    list.asScala
  }

  def getListOfWithdrawalRequests: Seq[WithdrawalRequest] = {
    val list: util.List[WithdrawalRequest] = new util.ArrayList[WithdrawalRequest]()
    val mcAddr = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    val valueInWei = ZenWeiConverter.convertZenniesToWei(123)
    val request = WithdrawalRequest(mcAddr, valueInWei)
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
      getNodeMemoryPoolMock(sidechainApiMockConfiguration)
    )

}
