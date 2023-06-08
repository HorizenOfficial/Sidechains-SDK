package io.horizen.account.performance

import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.forger.AccountForgeMessageBuilder
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.state._
import io.horizen.account.state.receipt.EthereumConsensusDataReceipt
import io.horizen.account.state.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.account.wallet.AccountWallet
import io.horizen.block.MainchainBlockReferenceData
import io.horizen.evm.{Address, Hash}
import io.horizen.utils.WithdrawalEpochInfo
import io.horizen.{AccountMempoolSettings, SidechainTypes}
import org.junit.Assert.assertEquals
import org.junit.{Ignore, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.NodeViewHolder.CurrentView

import java.io.{BufferedWriter, FileWriter}
import java.math.BigInteger
import java.util.Calendar
import scala.util.Try

class AccountForgeMessageBuilderPerfTest extends MockitoSugar with EthereumTransactionFixture {

  val stateView: AccountStateView = mock[AccountStateView]
  Mockito
    .when(
      stateView.applyTransaction(
        ArgumentMatchers.any[SidechainTypes#SCAT],
        ArgumentMatchers.any[Int],
        ArgumentMatchers.any[GasPool],
        ArgumentMatchers.any[BlockContext]
      )
    )
    .thenAnswer(asw => {
      Try {
        val gasPool = asw.getArgument(2).asInstanceOf[GasPool]
        if (gasPool.getGas.compareTo(GasUtil.TxGas) < 0) {
          throw GasLimitReached()
        }
        gasPool.subGas(GasUtil.TxGas)
        new EthereumConsensusDataReceipt(2, ReceiptStatus.SUCCESSFUL.id, gasPool.getUsedGas, Seq.empty)
      }
    })

  val state: AccountState = mock[AccountState]
  Mockito
    .when(state.getBalance(ArgumentMatchers.any[Address]))
    .thenReturn(ZenWeiConverter.MAX_MONEY_IN_WEI) // Has always enough balance
  Mockito.when(state.getNextBaseFee).thenReturn(BigInteger.ZERO)

  Mockito.when(state.getNonce(ArgumentMatchers.any[Address])).thenReturn(BigInteger.ZERO)

  val nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool] =
    mock[CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]]

  val blockContext = new BlockContext(
    Address.ZERO,
    1000,
    BigInteger.ZERO,
    DefaultGasFeeFork.blockGasLimit,
    11,
    2,
    3,
    1L,
    MockedHistoryBlockHashProvider,
    Hash.ZERO
  )

  /*
  This method is used for testing what is the impact on forging of getting txs from the mem pool.
  The txs are no more retrieved from the mem pool already ordered by effective tip and nonce, but an iterator
  that orders them "on demand" is used instead. With the older implementation, the time needed for getting
  the txs from the mem pool was entirely spent inside collectTransactionsFromMemPool method, while with the iterator
  implementation the same time is spent inside computeBlockInfo. So, in order to compare the performance of the 2 solutions,
   the measurement is taken as the time spent by both methods.
   */
  @Test
  @Ignore
  def testComputeBlockInfo(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/computeBlockInfoTest.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("*********************************************************************\n\n")
      out.write("*        ComputeBlockInfo performance test          *\n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")

      val numOfAccounts = 10000
      val numOfTxsPerAccount = 10
      val numOfTxs = numOfAccounts * numOfTxsPerAccount

      out.write(s"Total number of transactions:                    $numOfTxs\n")
      out.write(s"Number of accounts:                              $numOfAccounts\n")
      out.write(s"Number of transactions for each account:         $numOfTxsPerAccount\n")
      val mempool = AccountMemoryPool.createEmptyMempool(
        () => state,
        () => state,
        AccountMempoolSettings(maxNonceGap = numOfTxsPerAccount, maxAccountSlots = numOfTxsPerAccount, maxMemPoolSlots = numOfTxs),
        () => mock[AccountEventNotifier])
      Mockito.when(nodeView.pool).thenReturn(mempool)

      println("Creating transactions...")
      val listOfTxs = createTransactions(numOfAccounts, numOfTxsPerAccount)

      println("Adding transactions to mempool...")
      listOfTxs.foreach { tx =>
        mempool.put(tx.asInstanceOf[SidechainTypes#SCAT])
      }

      // Sanity check
      assertEquals(numOfTxs, mempool.size)
      val forger = new AccountForgeMessageBuilder(null, null, null, false)

      println("Starting test")

      val startTime = System.currentTimeMillis()
      val listOfExecTxs = forger.collectTransactionsFromMemPool(
        nodeView,
        1500,
        Seq.empty[MainchainBlockReferenceData],
        WithdrawalEpochInfo(0, 0),
        100,
        Seq.empty[SidechainTypes#SCAT]
      )

      val (_, appliedTxs, _) = forger.computeBlockInfo(stateView, listOfExecTxs, Seq.empty, Seq.empty, blockContext, null, 100L)
      val totalTime = System.currentTimeMillis() - startTime

      val maxNumOfTxsInBlock = blockContext.blockGasLimit.divide(GasUtil.TxGas).intValue()
      val expectedNumOfAppliedTxs = if (numOfTxs < maxNumOfTxsInBlock) numOfTxs else maxNumOfTxsInBlock

      assertEquals(expectedNumOfAppliedTxs, appliedTxs.size)

      println(s"Total time $totalTime ms")
      out.write(s"\n********************* Test results *********************\n")
      out.write(s"Duration of the test:                      $totalTime ms\n")

    } finally {
      out.close()
    }

  }

}
