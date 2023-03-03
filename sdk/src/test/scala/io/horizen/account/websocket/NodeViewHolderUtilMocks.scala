package io.horizen.account.websocket

import io.horizen.SidechainTypes
import io.horizen.account.block.AccountBlock
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.fixtures.{AccountBlockFixture, EthereumTransactionFixture}
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.PrivateKeySecp256k1
import io.horizen.account.state.receipt.{EthereumConsensusDataLog, EthereumConsensusDataReceipt, EthereumReceipt}
import io.horizen.account.state.{AccountState, AccountStateView, GasUtil}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.{Bloom, EthereumTransactionUtils}
import io.horizen.account.wallet.AccountWallet
import io.horizen.evm.{Address, Hash}
import io.horizen.fixtures.CompanionsFixture
import io.horizen.utils.BytesUtils
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.block.Block

import java.math.BigInteger
import java.util.Optional

class NodeViewHolderUtilMocks extends MockitoSugar with CompanionsFixture with AccountBlockFixture with EthereumTransactionFixture{

  val sidechainAccountTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion

  val genesisBlock: AccountBlock = AccountBlockFixture.generateAccountBlock(
    sidechainAccountTransactionsCompanion
  )

  val secret1: PrivateKeySecp256k1 = new PrivateKeySecp256k1(BytesUtils.fromHexString("98ae51c0a48a9ded8eb11af0e4dc30f4e97478f99d1071b97a7c3e0fc61557b6"))
  val secret2: PrivateKeySecp256k1 = new PrivateKeySecp256k1(BytesUtils.fromHexString("ef94d885059609d57defc94bf199e4b5456796a3835515c19490406ae82856ba"))
  val nonIncludedSecret: PrivateKeySecp256k1 = new PrivateKeySecp256k1(BytesUtils.fromHexString("e49aed4713e1b130244c39fe5be8fa0f3e453c3b1472750d8357aec35e412384"))

  val pubKeys1: AddressProposition = secret1.publicImage
  val pubKeys2: AddressProposition = secret2.publicImage
  val nonIncludedPubKeys: AddressProposition = nonIncludedSecret.publicImage

  val exampleTransaction1: EthereumTransaction = createEIP1559Transaction(BigInteger.valueOf(10), keyOpt = Some(secret1))
  val exampleTransaction2: EthereumTransaction = createEIP1559Transaction(BigInteger.valueOf(10), keyOpt = Some(secret2))
  val nonIncludedTransaction: EthereumTransaction = createEIP1559Transaction(BigInteger.valueOf(10), keyOpt = Some(nonIncludedSecret))

  val transactionTopic0 = new Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
  val transactionTopic1 = new Hash("0x00000000000000000000000053e53e5d0bedbd9d13a0a0e0441597db24f255c3")
  val transactionTopic2 = new Hash("0x000000000000000000000000b3eb3c0bf99677d0c9ff18030c66e1bb78967994")

  val transactionAddress = new Address("0x90dc4f6c07c2ecb76768a70276206436e77a6645")
  val transactionLog = new EthereumConsensusDataLog(
    transactionAddress,
    Array(transactionTopic0, transactionTopic1, transactionTopic2),
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001")
  )
  val transactionLog2 = new EthereumConsensusDataLog(
    transactionAddress,
    Array(transactionTopic0),
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001")
  )

  val transactionReceipt: EthereumReceipt = EthereumReceipt(
    new EthereumConsensusDataReceipt(0,
      1,
      new BigInteger("52321"),
      Seq(transactionLog, transactionLog2)),
    BytesUtils.fromHexString("0a5af1ca72ce63cbd07a86bba39d1aa88ae499a3c3eaa142ac2c2882874b6f6a"),
    0,
    BytesUtils.fromHexString("733e67a2770d7e7fef8507c2654ff5491946fb016b757daf001e0e862848ffa7"),
    5,
    new BigInteger("52321"),
    Option.empty
  )

  val transactionWithLogs = new EthereumTransaction(
    1997L,
    EthereumTransactionUtils.getToAddressFromString("0x1234567890123456789012345678901234567890"),
    BigInteger.ZERO,
    GasUtil.TxGas,
    BigInteger.valueOf(10000),
    BigInteger.valueOf(10000),
    BigInteger.ZERO,
    BytesUtils.fromHexString("a9059cbb000000000000000000000000b3eb3c0bf99677d0c9ff18030c66e1bb789679940000000000000000000000000000000000000000000000000000000000000001"),
    null
  )

  val blockBloom = new Bloom()
  blockBloom.add(transactionAddress.toBytes)
  blockBloom.add(transactionTopic0.toBytes)
  blockBloom.add(transactionTopic1.toBytes)
  blockBloom.add(transactionTopic2.toBytes)

  def getNextBlockWithTransaction(prevBlockId: Option[Block.BlockId] = None): AccountBlock = {
    AccountBlockFixture.generateAccountBlock(
      sidechainAccountTransactionsCompanion,
      parentOpt = prevBlockId,
      transactions = Some(Seq(transactionWithLogs.asInstanceOf[SidechainTypes#SCAT])),
      bloom = Some(blockBloom)
    )
  }


  def getNodeHistoryMock: AccountHistory = {
    val history: AccountHistory = mock[AccountHistory]

    Mockito.when(history.getBlockHeightById(ArgumentMatchers.any[String])).thenAnswer(_ => Optional.of(0))
    history
  }

  def getNodeStateMock: AccountState = {
    val state: AccountState = mock[AccountState]
    val stateView: AccountStateView = mock[AccountStateView]

    Mockito.when(state.getView).thenAnswer(_ => stateView)
    Mockito.when(stateView.getTransactionReceipt(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => Option.apply(transactionReceipt))

    state
  }

  def getNodeWalletMock: AccountWallet = {
    val wallet: AccountWallet = mock[AccountWallet]

    Mockito.when(wallet.publicKeys()).thenAnswer(_ => Set(pubKeys1, pubKeys2))

    wallet
  }

  def getNodeMemoryPoolMock: AccountMemoryPool = {
    val memoryPool: AccountMemoryPool = mock[AccountMemoryPool]

    memoryPool
  }

  def getNodeView: CurrentView[Any, Any, Any, Any] = {
    CurrentView[Any, Any, Any, Any](
      getNodeHistoryMock,
      getNodeStateMock,
      getNodeWalletMock,
      getNodeMemoryPoolMock
    )
  }
}