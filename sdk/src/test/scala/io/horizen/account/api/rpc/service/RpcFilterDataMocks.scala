package io.horizen.account.api.rpc.service

import io.horizen.SidechainTypes
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.fixtures.{AccountBlockFixture, EthereumTransactionFixture}
import io.horizen.account.state.{AccountStateView, GasUtil}
import io.horizen.account.state.receipt.{EthereumConsensusDataLog, EthereumConsensusDataReceipt, EthereumReceipt}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.{Bloom, EthereumTransactionUtils}
import io.horizen.evm.{Address, Hash}
import io.horizen.fixtures.CompanionsFixture
import io.horizen.utils.BytesUtils
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar

import java.math.BigInteger

class RpcFilterDataMocks extends MockitoSugar with CompanionsFixture with AccountBlockFixture with EthereumTransactionFixture {
  val sidechainAccountTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion

  val transactionTopic0 = new Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
  val transactionTopic1 = new Hash("0x00000000000000000000000053e53e5d0bedbd9d13a0a0e0441597db24f255c3")
  val transactionTopic2 = new Hash("0x000000000000000000000000b3eb3c0bf99677d0c9ff18030c66e1bb78967994")
  val transactionTopic3 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000002")

  val transactionAddress = new Address("0x90dc4f6c07c2ecb76768a70276206436e77a6645")
  val transactionAddress2 = new Address("0x0022002200220022002200220022002200220022")
  val unusedTransactionAddress = new Address("0x0033003300330033003300330033003300330033")

  val transactionLog0 = new EthereumConsensusDataLog(
    transactionAddress,
    Array(transactionTopic0, transactionTopic1, transactionTopic2),
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001")
  )
  val transactionLog1 = new EthereumConsensusDataLog(
    transactionAddress,
    Array(transactionTopic0),
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001")
  )
  val transactionLog2 = new EthereumConsensusDataLog(
    transactionAddress,
    Array(transactionTopic3),
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001")
  )
  val transactionLog3 = new EthereumConsensusDataLog(
    transactionAddress2,
    Array(transactionTopic0),
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001")
  )

  val transactionReceipt: EthereumReceipt = EthereumReceipt(
    new EthereumConsensusDataReceipt(0,
      1,
      new BigInteger("52321"),
      Seq(transactionLog0, transactionLog1, transactionLog2, transactionLog3)),
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
  blockBloom.add(transactionAddress2.toBytes)
  blockBloom.add(transactionTopic0.toBytes)
  blockBloom.add(transactionTopic1.toBytes)
  blockBloom.add(transactionTopic2.toBytes)
  blockBloom.add(transactionTopic3.toBytes)

  val mockedBlock = AccountBlockFixture.generateAccountBlock(
      sidechainAccountTransactionsCompanion,
      transactions = Some(Seq(transactionWithLogs.asInstanceOf[SidechainTypes#SCAT])),
      bloom = Some (blockBloom)
  )

  def getNodeStateMock: AccountStateView = {
    val stateView: AccountStateView = mock[AccountStateView]

    Mockito.when(stateView.getTransactionReceipt(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => Option.apply(transactionReceipt))

    stateView
  }
}
