package com.horizen.account.performance

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestActorRef
import com.horizen.account.AccountSidechainNodeViewHolder
import com.horizen.account.block.AccountBlock
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.{AccountState, AccountStateView, MessageProcessor, MockedHistoryBlockHashProvider}
import com.horizen.account.storage.{AccountHistoryStorage, AccountStateMetadataStorage}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.account.wallet.AccountWallet
import com.horizen.consensus.ConsensusDataStorage
import com.horizen.cryptolibprovider.utils.CircuitTypes.NaiveThresholdSignatureCircuit
import com.horizen.evm.Database
import com.horizen.evm.utils.Address
import com.horizen.fixtures._
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainSecretStorage
import com.horizen.utils.BytesUtils
import com.horizen.{AccountMempoolSettings, SidechainSettings, SidechainTypes, WalletSettings}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Before, Ignore, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.core.VersionTag
import sparkz.core.utils.NetworkTimeProvider
import sparkz.util.SparkzEncoding

import java.io.{BufferedWriter, FileWriter}
import java.math.BigInteger
import java.util.Calendar
import scala.collection.concurrent.TrieMap
import scala.util.Random

/*
  This class is used for testing performance related to modifications to the memory pool.
 */

class AccountSidechainNodeViewHolderPerfTest
    extends JUnitSuite
      with EthereumTransactionFixture
      with StoreFixture
      with SparkzEncoding {
  var historyMock: AccountHistory = _
  var state: AccountState = _
  var stateViewMock: AccountStateView = _
  var wallet: AccountWallet = _
  var mempool: AccountMemoryPool = _

  implicit val actorSystem: ActorSystem = ActorSystem("sc_nvh_mocked")
  var mockedNodeViewHolderRef: ActorRef = _

  val mockStateDbNonces:TrieMap[Address, BigInteger]  = TrieMap[Address, BigInteger]()

  @Before
  def setUp(): Unit = {
    historyMock = mock[AccountHistory]

    stateViewMock = mock[AccountStateView]
    Mockito
      .when(stateViewMock.getBalance(ArgumentMatchers.any[Address]))
      .thenReturn(ZenWeiConverter.MAX_MONEY_IN_WEI) // Has always enough balance
    Mockito.when(stateViewMock.isEoaAccount(ArgumentMatchers.any[Address])).thenReturn(true)
    Mockito.when(stateViewMock.getNextBaseFee).thenReturn(BigInteger.ZERO)

    Mockito.when(stateViewMock.getNonce(ArgumentMatchers.any[Address])).thenAnswer { answer =>
      {
        mockStateDbNonces.getOrElse(answer.getArgument(0), BigInteger.ZERO)
      }
    }

    wallet = mock[AccountWallet]
    Mockito.when(wallet.scanOffchain(ArgumentMatchers.any[SidechainTypes#SCAT])).thenReturn(wallet)
  }

  /*
  This method tests the performance related to adding new txs to the mem pool. In order to simulate a more realistic scenario,
  the txs come from different accounts. There are to types of accounts: normal and spammers.
  Normal accounts have only few txs each (5), while spammers have 100 txs each.
  The test is executed twice, the first time txs are added ordered by increasing nonce, the second time they are added
  ordered by decreasing nonce, to see the impact on the mem pool of reordering the txs.
   */
  @Test
  @Ignore
  def txModifyTest(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/txModifyTest.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("*********************************************************************\n\n")
      out.write("*                Adding transaction performance test                 \n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")


      val numOfTxs = 10000
      val numOfTxsPerSpammerAccounts = 100
      val numOfTxsPerNormalAccounts = 5
      val normalSpammerRatio = numOfTxsPerSpammerAccounts / numOfTxsPerNormalAccounts
      assertTrue(
        "Invalid test parameters",
        numOfTxs % (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts) == 0
      )
      val numOfSpammerAccount = numOfTxs / (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts)
      val numOfNormalAccount = normalSpammerRatio * numOfSpammerAccount
      out.write(s"Total number of transactions:                    $numOfTxs\n")
      out.write(s"Number of spammer accounts:                      $numOfSpammerAccount\n")
      out.write(s"Number of transactions for each spammer account: $numOfTxsPerSpammerAccounts\n")
      out.write(s"Number of normal accounts:                       $numOfNormalAccount\n")
      out.write(s"Number of transactions for each normal account:  $numOfTxsPerNormalAccounts\n")

      val mempoolSettings = AccountMempoolSettings(
        maxNonceGap = numOfTxsPerSpammerAccounts,
        maxAccountSlots = numOfTxsPerSpammerAccounts,
        maxMemPoolSlots = numOfTxs,
        maxNonExecMemPoolSlots = numOfTxs - 1
      )
      val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)

      val listOfTxs = scala.collection.mutable.ListBuffer[EthereumTransaction]()

      println(s"*************** Adding transaction performance test ***************")
      println(s"Total number of transaction: $numOfTxs")

      listOfTxs ++= createTransactions(numOfNormalAccount, numOfTxsPerNormalAccounts, orphanIdx = -1)

      listOfTxs ++= createTransactions(numOfSpammerAccount, numOfTxsPerSpammerAccounts, seed = numOfNormalAccount + 1, orphanIdx = -1)

      println("Starting test direct order")
      val numOfSnapshots = 10
      val numOfTxsPerSnapshot = numOfTxs / numOfSnapshots
      var listOfSnapshots = new scala.collection.mutable.ListBuffer[Long]()
      var startTime = System.currentTimeMillis()
      var intermediate = startTime
      listOfTxs.zipWithIndex.foreach { case (tx, idx) =>
        nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT])
        if ((idx + 1) % numOfTxsPerSnapshot == 0) {
          val updateTime = System.currentTimeMillis()
          listOfSnapshots += (updateTime - intermediate)
          intermediate = updateTime
        }
      }
      var totalTime = System.currentTimeMillis() - startTime

      assertEquals(numOfTxs, mempool.size)

      out.write(s"\n********************* Direct order test results *********************\n")

      println(s"Total time $totalTime ms")
      var timePerTx: Float = totalTime.toFloat / numOfTxs
      println(s"Average time per transaction $timePerTx ms")
      println(
        s"Average time per transactions in Snapshots ${listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).mkString(",")} "
      )
      out.write(s"Duration of the test:                      $totalTime ms\n")
      out.write(s"Average time per transaction:             $timePerTx ms\n")
      out.write(s"Average time per transaction in snapshots:\n")
      listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).zipWithIndex.foreach { case (res, idx) =>
        out.write(s"Snapshot $idx: $res ms\n")
      }
      out.write(s"Number of transactions per snapshot: $numOfTxsPerSnapshot\n")

      println("Starting test reverse order")
      // Resetting MemPool
      mempool = AccountMemoryPool.createEmptyMempool(() => state, () => state, mempoolSettings)

      val reverseList = listOfTxs.reverse
      listOfSnapshots = new scala.collection.mutable.ListBuffer[Long]()
      startTime = System.currentTimeMillis()
      intermediate = startTime
      reverseList.zipWithIndex.foreach { case (tx, idx) =>
        nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT])
        if ((idx + 1) % numOfTxsPerSnapshot == 0) {
          val updateTime = System.currentTimeMillis()
          listOfSnapshots += (updateTime - intermediate)
          intermediate = updateTime
        }
      }
      totalTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs, mempool.size)
      println(s"Total time $totalTime ms")
      timePerTx = totalTime.toFloat / numOfTxs
      println(s"Time per transactions $timePerTx ms")
      println(
        s"Average time per transactions in Snapshots ${listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot)} "
      )
      out.write(s"\n********************* Reverse order test results *********************\n")
      out.write(s"Duration of the test:                      $totalTime ms\n")
      out.write(s"Average time per transaction:             $timePerTx ms\n")
      out.write(s"Average time per transaction in snapshots:\n")
      listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).zipWithIndex.foreach { case (res, idx) =>
        out.write(s"Snapshot $idx: $res ms\n")
      }
      out.write(s"Number of transactions per snapshot: $numOfTxsPerSnapshot\n")

      println("Starting test random order")
      // Resetting MemPool
      mempool = AccountMemoryPool.createEmptyMempool(() => state, () => state, mempoolSettings)
      val randomOrderList = Random.shuffle(listOfTxs)
      listOfSnapshots = new scala.collection.mutable.ListBuffer[Long]()
      startTime = System.currentTimeMillis()
      intermediate = startTime
      randomOrderList.zipWithIndex.foreach { case (tx, idx) =>
        nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT])
        if ((idx + 1) % numOfTxsPerSnapshot == 0) {
          val updateTime = System.currentTimeMillis()
          listOfSnapshots += (updateTime - intermediate)
          intermediate = updateTime
        }
      }
      totalTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs, mempool.size)

      out.write(s"\n********************* Random order test results *********************\n")

      println(s"Total time $totalTime ms")
      timePerTx = totalTime.toFloat / numOfTxs
      println(s"Average time per transaction $timePerTx ms")
      println(
        s"Average time per transactions in Snapshots ${listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).mkString(",")} "
      )
      out.write(s"Duration of the test:                      $totalTime ms\n")
      out.write(s"Average time per transaction:             $timePerTx ms\n")
      out.write(s"Average time per transaction in snapshots:\n")
      listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).zipWithIndex.foreach { case (res, idx) =>
        out.write(s"Snapshot $idx: $res ms\n")
      }
      out.write(s"Number of transactions per snapshot: $numOfTxsPerSnapshot\n")


    } finally {
      out.write("\n\n")
      out.close()
    }
  }

  /*
    Same as txModifyTest but it takes into account the nonce gap. So the maximum number of txs for each account can be
    only 16. The other test is temporarily kept just for comparing the performances with the previous implementations
    of the mempool.
   */
  @Test
  @Ignore
  def txModifyTestDefaultNonceGap(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/txModifyTestNonceGap.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("*********************************************************************\n\n")
      out.write("*                Adding transaction performance test                 \n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")


      val numOfTxs = 10016 //this weird number is just to have an integer number of accounts
      val numOfTxsPerSpammerAccounts = 16
      val numOfTxsPerNormalAccounts = 1
      val normalSpammerRatio = numOfTxsPerSpammerAccounts / numOfTxsPerNormalAccounts
      assertTrue(
        "Invalid test parameters",
        numOfTxs % (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts) == 0
      )
      val numOfSpammerAccount = numOfTxs / (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts)
      val numOfNormalAccount = normalSpammerRatio * numOfSpammerAccount
      out.write(s"Total number of transactions:                    $numOfTxs\n")
      out.write(s"Number of spammer accounts:                      $numOfSpammerAccount\n")
      out.write(s"Number of transactions for each spammer account: $numOfTxsPerSpammerAccounts\n")
      out.write(s"Number of normal accounts:                       $numOfNormalAccount\n")
      out.write(s"Number of transactions for each normal account:  $numOfTxsPerNormalAccounts\n")

      val mempoolSettings = AccountMempoolSettings(
        maxNonceGap = numOfTxsPerSpammerAccounts,
        maxAccountSlots = numOfTxsPerSpammerAccounts,
        maxMemPoolSlots = numOfTxs,
        maxNonExecMemPoolSlots = numOfTxs - 1
      )
      val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)

      val listOfTxs = scala.collection.mutable.ListBuffer[EthereumTransaction]()

      println(s"*************** Adding transaction performance test ***************")
      println(s"Total number of transaction: $numOfTxs")

      listOfTxs ++= createTransactions(numOfNormalAccount, numOfTxsPerNormalAccounts, orphanIdx = -1)

      listOfTxs ++= createTransactions(numOfSpammerAccount, numOfTxsPerSpammerAccounts, seed = numOfNormalAccount + 1, orphanIdx = -1)

      println("Starting test direct order")
      val numOfSnapshots = 10
      val numOfTxsPerSnapshot = numOfTxs / numOfSnapshots
      var listOfSnapshots = new scala.collection.mutable.ListBuffer[Long]()
      var startTime = System.currentTimeMillis()
      var intermediate = startTime
      listOfTxs.zipWithIndex.foreach { case (tx, idx) =>
        nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT])
        if ((idx + 1) % numOfTxsPerSnapshot == 0) {
          val updateTime = System.currentTimeMillis()
          listOfSnapshots += (updateTime - intermediate)
          intermediate = updateTime
        }
      }
      var totalTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs, mempool.size)

      out.write(s"\n********************* Direct order test results *********************\n")

      println(s"Total time $totalTime ms")
      var timePerTx: Float = totalTime.toFloat / numOfTxs
      println(s"Average time per transaction $timePerTx ms")
      println(
        s"Average time per transactions in Snapshots ${listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).mkString(",")} "
      )
      out.write(s"Duration of the test:                      $totalTime ms\n")
      out.write(s"Average time per transaction:             $timePerTx ms\n")
      out.write(s"Average time per transaction in snapshots:\n")
      listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).zipWithIndex.foreach { case (res, idx) =>
        out.write(s"Snapshot $idx: $res ms\n")
      }
      out.write(s"Number of transactions per snapshot: $numOfTxsPerSnapshot\n")

      println("Starting test reverse order")
      // Resetting MemPool
      mempool = AccountMemoryPool.createEmptyMempool(() => state, () => state, mempoolSettings)

      val reverseList = listOfTxs.reverse
      listOfSnapshots = new scala.collection.mutable.ListBuffer[Long]()
      startTime = System.currentTimeMillis()
      intermediate = startTime
      reverseList.zipWithIndex.foreach { case (tx, idx) =>
        nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT])
        if ((idx + 1) % numOfTxsPerSnapshot == 0) {
          val updateTime = System.currentTimeMillis()
          listOfSnapshots += (updateTime - intermediate)
          intermediate = updateTime
        }
      }
      totalTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs, mempool.size)
      println(s"Total time $totalTime ms")
      timePerTx = totalTime.toFloat / numOfTxs
      println(s"Time per transactions $timePerTx ms")
      println(
        s"Average time per transactions in Snapshots ${listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot)} "
      )
      out.write(s"\n********************* Reverse order test results *********************\n")
      out.write(s"Duration of the test:                      $totalTime ms\n")
      out.write(s"Average time per transaction:             $timePerTx ms\n")
      out.write(s"Average time per transaction in snapshots:\n")
      listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).zipWithIndex.foreach { case (res, idx) =>
        out.write(s"Snapshot $idx: $res ms\n")
      }
      out.write(s"Number of transactions per snapshot: $numOfTxsPerSnapshot\n")

      println("Starting test random order")
      // Resetting MemPool
      mempool = AccountMemoryPool.createEmptyMempool(() => state, () => state, mempoolSettings)
      val randomOrderList = Random.shuffle(listOfTxs)
      listOfSnapshots = new scala.collection.mutable.ListBuffer[Long]()
      startTime = System.currentTimeMillis()
      intermediate = startTime
      randomOrderList.zipWithIndex.foreach { case (tx, idx) =>
        nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT])
        if ((idx + 1) % numOfTxsPerSnapshot == 0) {
          val updateTime = System.currentTimeMillis()
          listOfSnapshots += (updateTime - intermediate)
          intermediate = updateTime
        }
      }
      totalTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs, mempool.size)

      out.write(s"\n********************* Random order test results *********************\n")

      println(s"Total time $totalTime ms")
      timePerTx = totalTime.toFloat / numOfTxs
      println(s"Average time per transaction $timePerTx ms")
      println(
        s"Average time per transactions in Snapshots ${listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).mkString(",")} "
      )
      out.write(s"Duration of the test:                      $totalTime ms\n")
      out.write(s"Average time per transaction:             $timePerTx ms\n")
      out.write(s"Average time per transaction in snapshots:\n")
      listOfSnapshots.map(res => res.toFloat / numOfTxsPerSnapshot).zipWithIndex.foreach { case (res, idx) =>
        out.write(s"Snapshot $idx: $res ms\n")
      }
      out.write(s"Number of transactions per snapshot: $numOfTxsPerSnapshot\n")


    } finally {
      out.write("\n\n")
      out.close()
    }
  }

  /*
  This method tests the performance related to updating the mem pool after a new block was applied to the blockchain.
   First the mem pool is filled with txs from different accounts, normal and spammers (i.e. with few or several txs, respectively).
   Some txs are orphans. A set of these txs are then used for creating the block that will be applied.
   In the second part of the test, the same block will be "reverted" and a new one will be applied.
   */
  @Test
  @Ignore
  def updateMemPoolTest(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/updateMemPoolTest.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("\n\n")
      out.write("*********************************************************************\n\n")
      out.write("*                Updating Memory Pool performance test              *\n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")


      val numOfTxs = 10000
      val numOfTxsPerSpammerAccounts = 100
      val numOfTxsPerNormalAccounts = 5
      val normalSpammerRatio = 20
      val numOfSpammerAccount = numOfTxs / (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts)
      val numOfNormalAccount = normalSpammerRatio * numOfSpammerAccount
      val numOfTxsInBlock = 1400

      out.write(s"Total number of transactions:                    $numOfTxs\n")
      out.write(s"Number of spammer accounts:                      $numOfSpammerAccount\n")
      out.write(s"Number of transactions for each spammer account: $numOfTxsPerSpammerAccounts\n")
      out.write(s"Number of normal accounts:                       $numOfNormalAccount\n")
      out.write(s"Number of transactions for each normal account:  $numOfTxsPerNormalAccounts\n")
      out.write(s"Number of transactions for each block:           $numOfTxsInBlock\n")


      assertTrue(
        "Invalid test parameters",
        numOfTxs % (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts) == 0
      )
      println("************** Testing with one block to apply **************")

      val listOfNormalTxs = createTransactions(numOfNormalAccount, numOfTxsPerNormalAccounts, orphanIdx = 2)

      val listOfSpammerTxs = createTransactions(numOfSpammerAccount, numOfTxsPerSpammerAccounts, seed = numOfNormalAccount + 1, orphanIdx = 75)

      val mempoolSettings = AccountMempoolSettings(
        maxNonceGap = numOfTxsPerSpammerAccounts + 1, //+1 because there are orphans, so max nonce > num of txs
        maxAccountSlots = numOfTxsPerSpammerAccounts + 1,
        maxMemPoolSlots = numOfTxs
      )
      val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)

      val listOfTxs = listOfSpammerTxs ++ listOfNormalTxs
      //Adding txs to the initial mem pool
      listOfTxs.foreach(tx => nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT]))
      assertEquals(numOfTxs, mempool.size)

      //Creating the block to be applied
      val appliedBlock: AccountBlock = mock[AccountBlock]
      //Takes txs from both spammers and normal accounts. While the gas tip is not important for this test, we must ensure
      // that txs from the same account are ordered by increasing nonce in the block.
      val listOfTxsInBlock: Seq[EthereumTransaction] =
        listOfSpammerTxs.take(numOfSpammerAccount) ++ listOfNormalTxs.take(
          numOfTxsInBlock - numOfSpammerAccount
        )
      Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsInBlock.asInstanceOf[Seq[SidechainTypes#SCAT]])
      // Update the nonces in the mock state
      listOfTxsInBlock.foreach(tx =>
        mockStateDbNonces.put(tx.getFrom.address(), tx.getNonce.add(BigInteger.ONE))
      )

      println("Starting test")
      val startTime = System.currentTimeMillis()
      val newMemPool = nodeViewHolder.updateMemPool(Seq(), Seq(appliedBlock), mempool, state)
      val updateTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs - numOfTxsInBlock, newMemPool.size)
      println(s"total time $updateTime ms")
      out.write(s"\n********************* Testing with one block to apply results *********************\n")
      out.write(s"Duration of the test:                      $updateTime ms\n")

      println("************** Testing with one rollback block and one to apply **************")
      mempool = newMemPool
      val rollBackBlock = appliedBlock
      // restore the mempool so its size is again numOfTxs
      val additionalTxs = createTransactions(numOfTxsInBlock, 1, seed = 2 * numOfNormalAccount + numOfSpammerAccount + 1)
      additionalTxs.foreach(tx => nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT]))
      assertEquals(numOfTxs, mempool.size)

      // The new block to be applied will have 1000 txs of the rolledBack block and 400 from the revertedTxs, just to
      // make the test more realistic. The new txs are taken from new accounts, so we are sure to avoid gaps in the nonces.
      val appliedBlock2: AccountBlock = mock[AccountBlock]
      val listOfTxsInBlock2 = listOfTxsInBlock.take(1000) ++ additionalTxs.take(400)//TODO this should be configurable
      Mockito.when(appliedBlock2.transactions).thenReturn(listOfTxsInBlock2.asInstanceOf[Seq[SidechainTypes#SCAT]])

      // Update the nonces in the mock state.
      // First resetting the nonces (so it will restart from 0), then put the new nonces for txs in appliedBlock2
      mockStateDbNonces.clear()
      listOfTxsInBlock2.foreach(tx =>
        mockStateDbNonces.put(tx.getFrom.address(), tx.getNonce.add(BigInteger.ONE))
      )
      println("Starting test")
      val startTime2 = System.currentTimeMillis()
      val newMemPool2 = nodeViewHolder.updateMemPool(Seq(rollBackBlock), Seq(appliedBlock2), mempool, state)
      val updateTime2 = System.currentTimeMillis() - startTime2
      assertEquals(numOfTxs, newMemPool2.size)
      println(s"total time $updateTime2 ms")
      out.write(s"\n********************* Testing rollback block and one to apply results *********************\n")
      out.write(s"Duration of the test:                      $updateTime2 ms\n")

    } finally {
      out.write("\n\n")
      out.close()
    }
  }

  /*
  This method tests the performance related to updating the mem pool after a new block was applied to the blockchain.
  In this case the chain reorg causes a switch of the active chain composed by several blocks.
   */
  @Test
  @Ignore
  def updateMemPoolMultipleBlocksTest(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/updateMemPoolMultiBlocksTest.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("*********************************************************************\n\n")
      out.write("*   Updating Memory Pool with Multiple Blocks performance test       \n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")

      val numOfTxs = 10000
      val numOfTxsPerSpammerAccounts = 100
      val numOfTxsPerNormalAccounts = 5
      val normalSpammerRatio = 20
      val numOfSpammerAccount = numOfTxs / (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts)
      val numOfNormalAccount = normalSpammerRatio * numOfSpammerAccount
      val numOfTxsInBlock = 1400
      val numOfBlocks = 4

      out.write(s"Total number of transactions:                    $numOfTxs\n")
      out.write(s"Number of spammer accounts:                      $numOfSpammerAccount\n")
      out.write(s"Number of transactions for each spammer account: $numOfTxsPerSpammerAccounts\n")
      out.write(s"Number of normal accounts:                       $numOfNormalAccount\n")
      out.write(s"Number of transactions for each normal account:  $numOfTxsPerNormalAccounts\n")
      out.write(s"Number of transactions for each block:           $numOfTxsInBlock\n")
      out.write(s"Number of applied blocks:                        $numOfBlocks\n")

      assertTrue(
        "Invalid test parameters",
        numOfTxs % (numOfTxsPerSpammerAccounts + normalSpammerRatio * numOfTxsPerNormalAccounts) == 0
      )

      assertTrue(
        "Invalid number of blocks",
        numOfBlocks * numOfTxsInBlock <= numOfTxs
      )

      println(s"************** Testing with $numOfBlocks blocks to apply **************")
      //This in real life should never happen, but taking some measures could be useful
      val listOfNormalTxs = createTransactions(numOfNormalAccount, numOfTxsPerNormalAccounts, orphanIdx = 2)

      val listOfSpammerTxs = createTransactions(numOfSpammerAccount, numOfTxsPerSpammerAccounts, seed = numOfNormalAccount + 1, orphanIdx = 75)

      val mempoolSettings = AccountMempoolSettings(
        maxNonceGap = numOfTxsPerSpammerAccounts + 1, //+1 because there are orphans, so max nonce > num of txs
        maxAccountSlots = numOfTxsPerSpammerAccounts + 1,
        maxMemPoolSlots = numOfTxs
      )
      val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)

      val listOfTxs = listOfSpammerTxs ++ listOfNormalTxs
      listOfTxs.foreach(tx => nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT]))
      assertEquals(numOfTxs, mempool.size)

      //Prepare the first blocks to apply that later will become rejected. The transactions must be ordered so I'll use mem pool's take method
      val listOfExecTransactionsToApply = mempool.take(numOfBlocks * numOfTxsInBlock)

      val listOfBlocks = new scala.collection.mutable.ListBuffer[AccountBlock]

      (0 until numOfBlocks).foreach { idx =>
        val appliedBlock: AccountBlock = mock[AccountBlock]
        val listOfTxsInBlock = listOfExecTransactionsToApply.slice(idx * numOfTxsInBlock, (idx + 1 ) * numOfTxsInBlock)
        Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsInBlock.asInstanceOf[Seq[SidechainTypes#SCAT]])
        listOfBlocks.append(appliedBlock)
      }

      // Update the nonces
      listOfExecTransactionsToApply.foreach(tx =>
        mockStateDbNonces.put(tx.asInstanceOf[EthereumTransaction].getFrom.address(), tx.getNonce.add(BigInteger.ONE))
      )

      println("Starting test")
      val startTime = System.currentTimeMillis()
      val newMemPool = nodeViewHolder.updateMemPool(Seq(), listOfBlocks, mempool, state)
      val updateTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs - numOfBlocks * numOfTxsInBlock, newMemPool.size)
      println(s"total time $updateTime ms")
      out.write(s"\n********************* Testing with $numOfBlocks blocks to apply results *********************\n")
      out.write(s"Duration of the test:                      $updateTime ms\n")

      println(s"************** Testing with $numOfBlocks rejected blocks and $numOfBlocks blocks to apply **************")
      mempool = newMemPool
      val rollBackBlocks = listOfBlocks
      // restore the mempool so its size is again numOfTxs
      val additionalTxs = createTransactions(numOfBlocks * numOfTxsInBlock, 1, seed = 2 * numOfNormalAccount + numOfSpammerAccount + 1)
      additionalTxs.foreach(tx => nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT]))
      assertEquals(numOfTxs, mempool.size)

      // the blocks to be applied will have 1000 txs of the rolledBack blocks and 400 from the revertedTxs
      val listOfBlocks2 = new scala.collection.mutable.ListBuffer[AccountBlock]

      val numOfReappliedTxs = numOfTxsInBlock - 400 //TODO make it configurable

      val numOfNewTxs = numOfTxsInBlock - numOfReappliedTxs
      (0 until numOfBlocks).foreach { idx =>
        val appliedBlock: AccountBlock = mock[AccountBlock]
        val listOfTxsInBlock = listOfExecTransactionsToApply.slice(idx * numOfReappliedTxs, (idx + 1) * numOfReappliedTxs) ++
          additionalTxs.slice(idx * numOfNewTxs, (idx + 1) * numOfNewTxs)
        Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsInBlock.asInstanceOf[Seq[SidechainTypes#SCAT]])
        listOfBlocks2.append(appliedBlock)
      }


      // Update the nonces
      // First resetting the nonces in the mock state, then the ones in appliedBlock2
      mockStateDbNonces.clear()
      listOfBlocks2.foreach( block => block.transactions.foreach(tx =>
        mockStateDbNonces.put(tx.asInstanceOf[EthereumTransaction].getFrom.address(), tx.getNonce.add(BigInteger.ONE))
      ))
      println("Starting test")
      val startTime2 = System.currentTimeMillis()
      val newMemPool2 = nodeViewHolder.updateMemPool(rollBackBlocks, listOfBlocks2, mempool, state)
      val updateTime2 = System.currentTimeMillis() - startTime2
      assertEquals(numOfTxs, newMemPool2.size)
      println(s"total time $updateTime2 ms")
      out.write(s"\n********************* Testing $numOfBlocks rejected blocks and $numOfBlocks blocks results *********************\n")
      out.write(s"Duration of the test:                      $updateTime2 ms\n")

    } finally {
      out.write("\n\n")
      out.close()
    }
  }


  @Test
  @Ignore
  def updateMemPoolSingleAccountTest(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/updateMemPoolSingleAccountTest.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("\n\n")
      out.write("*********************************************************************\n\n")
      out.write("*       Updating Memory Pool with Single Account performance test   *\n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")

      val numOfTxs = 12000
      val numOfNormalAccount = 1
      val numOfTxsInBlock = 1400

      out.write(s"Total number of transactions:                    $numOfTxs\n")
      out.write(s"Number of normal accounts:                       $numOfNormalAccount\n")
      out.write(s"Number of transactions for each block:           $numOfTxsInBlock\n")

      println("************** Testing with one block to apply **************")

      val mempoolSettings = AccountMempoolSettings(
        maxNonceGap = numOfTxs,
        maxAccountSlots = numOfTxs,
        maxMemPoolSlots = numOfTxs
      )
      val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)
      val listOfTxs = createTransactions(numOfNormalAccount, numOfTxs)

      listOfTxs.foreach(tx => nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT]))
      assertEquals(numOfTxs, mempool.size)

      val appliedBlock: AccountBlock = mock[AccountBlock]
      val listOfTxsInBlock = listOfTxs.take(numOfTxsInBlock)

      Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsInBlock.asInstanceOf[Seq[SidechainTypes#SCAT]])
      // Update the nonces
      listOfTxsInBlock.foreach(tx =>
        mockStateDbNonces.put(tx.getFrom.address(), tx.getNonce.add(BigInteger.ONE))
      )

      println("Starting test")
      val startTime = System.currentTimeMillis()
      val newMemPool = nodeViewHolder.updateMemPool(Seq(), Seq(appliedBlock), mempool, state)
      val updateTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs - numOfTxsInBlock, newMemPool.size)
      println(s"total time $updateTime ms")
      out.write(s"\n********************* Testing with one block to apply results *********************\n")
      out.write(s"Duration of the test:                      $updateTime ms\n")

    } finally {
      out.write("\n\n")
      out.close()
    }
  }


  @Test
  @Ignore
  def takeTest(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/takeTest.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("*********************************************************************\n\n")
      out.write("*        Ordering executable transactions performance test          *\n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")

      val numOfTxs = 10000
      val numOfTxsPerAccount = 5
      val numOfAccounts = numOfTxs / numOfTxsPerAccount
      assertTrue("Invalid test parameters", numOfTxs % numOfTxsPerAccount == 0)
      out.write(s"Total number of transactions:            $numOfTxs\n")
      out.write(s"Number of accounts:                      $numOfAccounts\n")
      out.write(s"Number of transactions for each account: $numOfTxsPerAccount\n")

      val mempoolSettings = AccountMempoolSettings(
        maxMemPoolSlots = numOfTxs
      )
      val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)

      println(s"************** Test ordering executable transactions with $numOfTxs in mem pool **************")

      val listOfTxs = createTransactions(numOfAccounts, numOfTxsPerAccount)

      listOfTxs.foreach { tx =>
        nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT])
      }

      println("Starting test")

      val startTime = System.currentTimeMillis()
      val executablesTxs = mempool.take(mempool.size)
      val totalTime = System.currentTimeMillis() - startTime
      assertEquals(numOfTxs, executablesTxs.size)

      println(s"Total time $totalTime ms")
      out.write(s"\n********************* Test results *********************\n")
      out.write(s"Duration of the test:                      $totalTime ms\n")
    } finally {
      out.write("\n\n")
      out.close()
    }

  }


  /*
  This method tests the performance related to updating the mem pool after a mainchain fork.
  In this case, the number of sidechain blocks that will be reverted could be high, depending on how much faster is the
  sidechain forging than the mainchain. In this case the idea is to have 20 sidechain blocks for each mainchain block.
   */
  @Test
  @Ignore
  def updateMemPoolMainchainForkTest(): Unit = {
    val out = new BufferedWriter(new FileWriter("log/updateMemPoolMainchainForkTest.txt", true))

    val cal = Calendar.getInstance()
    try {
      out.write("*********************************************************************\n\n")
      out.write("*   Updating Memory Pool in case of Mainchain Fork performance test  \n\n")
      out.write("*********************************************************************\n\n")

      out.write(s"Date and time of the test: ${cal.getTime}\n\n")
      val mempoolSettings = AccountMempoolSettings() //Using defaults
      val nodeViewHolder = getMockedAccountSidechainNodeViewHolder(mempoolSettings)


      val numOfTxs = mempoolSettings.maxMemPoolSlots
      val numOfTxsPerNormalAccounts = 1
      val numOfNormalAccount = numOfTxs

      val numOfTxsInBlock = 1400
      val numOfRevertedBlocks = 20

      out.write(s"Number of transactions in the mempool:           $numOfTxs\n")
      out.write(s"Number of normal accounts:                       $numOfNormalAccount\n")
      out.write(s"Number of transactions for each normal account:  $numOfTxsPerNormalAccounts\n")
      out.write(s"Number of transactions for each block:           $numOfTxsInBlock\n")
      out.write(s"Number of reverted blocks:                       $numOfRevertedBlocks\n")

      println(s"************** Testing with $numOfRevertedBlocks blocks to be reverted **************")

      //Initialize the mem pool
      val listOfInitialTxs = createTransactions(numOfNormalAccount, numOfTxsPerNormalAccounts)

      listOfInitialTxs.foreach(tx => nodeViewHolder.txModify(tx.asInstanceOf[SidechainTypes#SCAT]))
      assertEquals(numOfTxs, mempool.size)

      //Prepare the block to apply. The transactions must be ordered so I'll use mem pool's take method
      val listOfExecTransactionsToApply = mempool.take(numOfTxsInBlock)

      val listOfBlocks = new scala.collection.mutable.ListBuffer[AccountBlock]
      val appliedBlock: AccountBlock = mock[AccountBlock]
      Mockito.when(appliedBlock.transactions).thenReturn(listOfExecTransactionsToApply.asInstanceOf[Seq[SidechainTypes#SCAT]])
      listOfBlocks.append(appliedBlock)

      // Update the nonces
      listOfExecTransactionsToApply.foreach(tx =>
        mockStateDbNonces.put(tx.asInstanceOf[EthereumTransaction].getFrom.address(), tx.getNonce.add(BigInteger.ONE))
      )

      //Prepare the blocks to be reverted.
      val rollBackBlocks = new scala.collection.mutable.ListBuffer[AccountBlock]

      (0 until numOfRevertedBlocks).foreach { idx =>
        val block: AccountBlock = mock[AccountBlock]
        val listOfTxsInBlock = createTransactions(numOfTxsInBlock, 1, seed = numOfNormalAccount + idx + 1)
        Mockito.when(block.transactions).thenReturn(listOfTxsInBlock.asInstanceOf[Seq[SidechainTypes#SCAT]])
        rollBackBlocks.append(block)
      }

      println("Starting test")
      val startTime = System.currentTimeMillis()
      val newMemPool = nodeViewHolder.updateMemPool(rollBackBlocks, listOfBlocks, mempool, state)
      val updateTime = System.currentTimeMillis() - startTime
      assertEquals(mempoolSettings.maxMemPoolSlots, newMemPool.size)

      println(s"total time $updateTime ms")
      out.write(s"\n********************* Testing $numOfRevertedBlocks rejected blocks results *********************\n")
      out.write(s"Duration of the test:                      $updateTime ms\n")

    } finally {
      out.write("\n\n")
      out.close()
    }
  }


  class MockedAccountSidechainNodeViewHolder(
      sidechainSettings: SidechainSettings,
      params: NetworkParams,
      timeProvider: NetworkTimeProvider,
      historyStorage: AccountHistoryStorage,
      consensusDataStorage: ConsensusDataStorage,
      stateMetadataStorage: AccountStateMetadataStorage,
      stateDbStorage: Database,
      customMessageProcessors: Seq[MessageProcessor],
      secretStorage: SidechainSecretStorage,
      genesisBlock: AccountBlock
  ) extends AccountSidechainNodeViewHolder(
        sidechainSettings,
        params,
        timeProvider,
        historyStorage,
        consensusDataStorage,
        stateMetadataStorage,
        stateDbStorage,
        customMessageProcessors,
        secretStorage,
        genesisBlock
      ) {
    override def txModify(tx: SidechainTypes#SCAT): Unit = super.txModify(tx)

    override def minimalState(): AccountState = state

    override def history(): AccountHistory = historyMock

    override def vault(): AccountWallet = wallet

    override def memoryPool(): AccountMemoryPool = mempool

    override protected def genesisState: (HIS, MS, VL, MP) = (history(), state, wallet, mempool)

    override def updateMemPool(
        blocksRemoved: Seq[AccountBlock],
        blocksApplied: Seq[AccountBlock],
        memPool: AccountMemoryPool,
        state: AccountState
    ): AccountMemoryPool = super.updateMemPool(blocksRemoved, blocksApplied, memPool, state)

  }

  def getMockedAccountSidechainNodeViewHolder(mempoolSettings: AccountMempoolSettings)(implicit
      actorSystem: ActorSystem
  ): MockedAccountSidechainNodeViewHolder = {
    val sidechainSettings = mock[SidechainSettings]
    val mockWalletSettings: WalletSettings = mock[WalletSettings]
    Mockito.when(mockWalletSettings.maxTxFee).thenReturn(100L)
    Mockito.when(sidechainSettings.wallet).thenReturn(mockWalletSettings)
    val params: NetworkParams = mock[NetworkParams]
    Mockito.when(params.chainId).thenReturn(1997)
    Mockito.when(params.circuitType).thenReturn(NaiveThresholdSignatureCircuit)
    val timeProvider: NetworkTimeProvider = mock[NetworkTimeProvider]

    val historyStorage: AccountHistoryStorage = mock[AccountHistoryStorage]
    val consensusDataStorage: ConsensusDataStorage = mock[ConsensusDataStorage]
    val stateMetadataStorage: AccountStateMetadataStorage = mock[AccountStateMetadataStorage]
    Mockito.when(stateMetadataStorage.isEmpty).thenReturn(true)
    val stateDbStorage: Database = mock[Database]
    val customMessageProcessors: Seq[MessageProcessor] = Seq()
    val secretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val genesisBlock: AccountBlock = mock[AccountBlock]

    val versionTag: VersionTag = VersionTag @@ BytesUtils.toHexString(getVersion.data())

    state = new AccountState(
      params,
      timeProvider,
      MockedHistoryBlockHashProvider,
      versionTag,
      stateMetadataStorage,
      stateDbStorage,
      Seq()
    ) {
      override def getView: AccountStateView = stateViewMock
    }

    mempool = AccountMemoryPool.createEmptyMempool(() => state, () => state, mempoolSettings)

    val nodeViewHolderRef: TestActorRef[MockedAccountSidechainNodeViewHolder] = TestActorRef(
      Props(
        new MockedAccountSidechainNodeViewHolder(
          sidechainSettings,
          params,
          timeProvider,
          historyStorage,
          consensusDataStorage,
          stateMetadataStorage,
          stateDbStorage,
          customMessageProcessors,
          secretStorage,
          genesisBlock
        )
      )
    )
    nodeViewHolderRef.underlyingActor
  }

}
