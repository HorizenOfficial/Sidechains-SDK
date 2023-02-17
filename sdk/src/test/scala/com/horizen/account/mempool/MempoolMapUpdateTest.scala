package com.horizen.account.mempool

import com.horizen.account.block.AccountBlock
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.account.state.{AccountStateReader, AccountStateReaderProvider, BaseStateReaderProvider}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.utils.Address
import com.horizen.state.BaseStateReader
import com.horizen.{AccountMempoolSettings, SidechainTypes}
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.util.ModifierId

import java.math.BigInteger

class MempoolMapUpdateTest extends JUnitSuite with EthereumTransactionFixture with SidechainTypes with MockitoSugar {

  val accountStateViewMock: AccountStateReader = mock[AccountStateReader]
  val baseStateViewMock: BaseStateReader = mock[BaseStateReader]

  val accountStateProvider: AccountStateReaderProvider = () => accountStateViewMock
  val baseStateProvider: BaseStateReaderProvider = () => baseStateViewMock

  val rejectedBlock: AccountBlock = mock[AccountBlock]
  val appliedBlock: AccountBlock = mock[AccountBlock]

  val listOfRejectedBlocks: Seq[AccountBlock] = Seq(rejectedBlock)
  val listOfAppliedBlocks: Seq[AccountBlock] = Seq(appliedBlock)

  val accountKeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest1".getBytes()))

  @Before
  def setUp(): Unit = {
    Mockito
      .when(accountStateViewMock.getBalance(ArgumentMatchers.any[Address]))
      .thenReturn(ZenWeiConverter.MAX_MONEY_IN_WEI) // Has always enough balance

    Mockito
      .when(accountStateViewMock.getNonce(ArgumentMatchers.any[Address]))
      .thenReturn(BigInteger.ZERO)
    Mockito.reset(rejectedBlock)
    Mockito.reset(appliedBlock)

  }

  @Test
  def testEmptyMemPool(): Unit = {
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    val expectedNumOfTxs = 7
    val listOfTxs: Seq[SidechainTypes#SCAT] = createTransactionsForAccount(accountKeyOpt.get, expectedNumOfTxs)

    // Try with only txs from reverted blocks
    var listOfTxsToReAdd = listOfTxs
    var listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]

    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", expectedNumOfTxs, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfTxs, executableTxs.size)


    // Try with only txs from applied blocks
    // Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfTxs))

    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = listOfTxs
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 0, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 0, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

    // Try with txs from applied and reverted blocks
    // Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    listOfTxsToReAdd = listOfTxs.take(3)
    listOfTxsToRemove = listOfTxs
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 0, mempoolMap.size)

    // Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    listOfTxsToReAdd = listOfTxs
    listOfTxsToRemove = listOfTxs.take(4)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(4))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", listOfTxsToReAdd.size - listOfTxsToRemove.size, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", listOfTxsToReAdd.size - listOfTxsToRemove.size, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", listOfTxsToReAdd.size - listOfTxsToRemove.size, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", listOfTxsToReAdd.size - listOfTxsToRemove.size, executableTxs.size)

    //With orphans for balance
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    val invalidTx = createEIP1559Transaction(BigInteger.valueOf(20000), nonce = BigInteger.valueOf(expectedNumOfTxs),
      accountKeyOpt, gasLimit = BigInteger.valueOf(1000000), gasFee = BigInteger.valueOf(1000000))
    val validTx = createEIP1559Transaction(BigInteger.valueOf(12), nonce = BigInteger.valueOf(expectedNumOfTxs + 1),
      accountKeyOpt)
    listOfTxsToReAdd = listOfTxsToReAdd :+ invalidTx.asInstanceOf[SidechainTypes#SCAT]
    listOfTxsToReAdd = listOfTxsToReAdd :+ validTx.asInstanceOf[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    assertTrue(invalidTx.maxCost().compareTo(listOfTxsToReAdd.head.maxCost()) > 0)
    Mockito
      .when(accountStateViewMock.getBalance(invalidTx.getFrom.address()))
      .thenReturn(listOfTxsToReAdd.head.maxCost())

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", listOfTxsToReAdd.size - listOfTxsToRemove.size - 1, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", listOfTxsToReAdd.size - listOfTxsToRemove.size - 1, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", listOfTxsToReAdd.size - listOfTxsToRemove.size - 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", listOfTxsToReAdd.size - listOfTxsToRemove.size - 2, executableTxs.size)
  }

  @Test
  def testWithTxsInMemPool(): Unit = {
    val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    val expectedNumOfTxs = 5
    val expectedNumOfExecutableTxs = 3
    //In listOfTxs there will be 3 exec txs and 2 non exec txs
    val listOfTxs: Seq[SidechainTypes#SCAT] = createTransactionsForAccount(accountKeyOpt.get, expectedNumOfTxs, expectedNumOfExecutableTxs)

    //initialize mem pool
    listOfTxs.foreach(tx => mempoolMap.add(tx))
    //Initial check on current situation
    assertEquals(expectedNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", expectedNumOfTxs, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", expectedNumOfTxs - expectedNumOfExecutableTxs, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", expectedNumOfTxs - expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(false).size)

    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    // Try with only txs from applied blocks
    var listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    var listOfTxsToRemove = listOfTxs.take(expectedNumOfExecutableTxs - 1)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfExecutableTxs))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", expectedNumOfExecutableTxs, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 3, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 1, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 1, executableTxs.size)

    // Try with only txs from reverted blocks
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.ZERO)


    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", expectedNumOfTxs, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", expectedNumOfTxs - expectedNumOfExecutableTxs, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", expectedNumOfTxs - expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(false).size)

    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)


    // Try with txs from applied and reverted blocks
    listOfTxsToReAdd = listOfTxs.take(1)
    listOfTxsToRemove = listOfTxs.take(2)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(2))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs - 2, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", expectedNumOfTxs - 2, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs - 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)

    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs - 2, executableTxs.size)

    // Reset mempool to initial situation
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.ZERO)
    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", expectedNumOfTxs, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", expectedNumOfTxs - expectedNumOfExecutableTxs, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", expectedNumOfTxs - expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    //Apply enough txs so that the non executable txs become executable
    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = createTransactionsForAccount(accountKeyOpt.get, expectedNumOfExecutableTxs + 1) //creates expectedNumOfExecutableTxs + 1 consecutive txs
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfExecutableTxs + 1))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 2, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 2, executableTxs.size)

  }

  @Test
  def testWithTxsInvalidForBalance(): Unit = {
    val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    val limitOfGas = BigInteger.valueOf(1000000)
    val maxGasFee = BigInteger.valueOf(1000000)
    val tx0 = createEIP1559Transaction(BigInteger.valueOf(10000), BigInteger.valueOf(0),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx1 = createEIP1559Transaction(BigInteger.valueOf(20000), BigInteger.valueOf(1),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx2 = createEIP1559Transaction(BigInteger.valueOf(50000), BigInteger.valueOf(2),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx3 = createEIP1559Transaction(BigInteger.valueOf(200), BigInteger.valueOf(3),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx5 = createEIP1559Transaction(BigInteger.valueOf(30000), BigInteger.valueOf(5),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx6 = createEIP1559Transaction(BigInteger.valueOf(10), BigInteger.valueOf(6),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)


    val expectedNumOfTxs = 6
    val expectedNumOfExecutableTxs = 4

    val listOfTxs = scala.collection.mutable.ListBuffer[SidechainTypes#SCAT](tx0, tx1, tx2, tx3, tx5, tx6)

    //initialize mem pool
    listOfTxs.foreach(tx => assertTrue(s"Error while adding tx $tx", mempoolMap.add(tx).isSuccess))
    //Initial check
    assertEquals(expectedNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", expectedNumOfTxs, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)
    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    // Try with only txs from applied blocks
    var listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    var listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx0, tx1)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    val address = listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx2.getNonce)

    //Reduce the balance so tx2 and tx5 are no valid anymore and the txs in the mempool are all non exec
    Mockito
      .when(accountStateViewMock.getBalance(address))
      .thenReturn(tx1.maxCost().subtract(BigInteger.ONE))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 2, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 0, executableTxs.size)


    // Try to revert tx0 and tx1 but tx1 is not inserted because the balance is too low now
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(tx1.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 3, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 3, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 1, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 1, executableTxs.size)

    //Apply txs with nonce 0, 1 and 2 => tx3 becomes executable
    val newTx1 = createEIP1559Transaction(BigInteger.valueOf(500), BigInteger.valueOf(1),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val newTx2 = createEIP1559Transaction(BigInteger.valueOf(500), BigInteger.valueOf(2),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)

    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx0, newTx1, newTx2)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(tx3.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 2, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 1, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 1, executableTxs.size)


  }

  @Test
  def testWithTxsInvalidForSize(): Unit = {

    val tx0 = createTransactionWithDataSize(MempoolMap.MaxTxSize, BigInteger.valueOf(0))
    val tx1 = createTransactionWithDataSize(0, BigInteger.valueOf(1))
    val tx2 = createTransactionWithDataSize(0, BigInteger.valueOf(2))
    val tx3 = createTransactionWithDataSize(0, BigInteger.valueOf(3))
    val tx4 = createTransactionWithDataSize(MempoolMap.MaxTxSize, BigInteger.valueOf(4))
    val tx5 = createTransactionWithDataSize(0, BigInteger.valueOf(5))
    val tx6 = createTransactionWithDataSize(0, BigInteger.valueOf(6))

    // Test 1: Try with only txs from reverted blocks

    //Initialize mempool
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    //Update the nonce in the state db
    val address = tx2.getFrom.address()
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx2.getNonce)

    //Add txs to the mem pool
    mempoolMap.add(tx2)
    mempoolMap.add(tx3)
    assertEquals(2, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 2, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

    //Prepare blocks
    var listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx0, tx1)
    var listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx0.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 3, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 3, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 3, mempoolMap.mempoolTransactions(false).size)
    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 0, executableTxs.size)

    // Test 2: Try with txs from reverted and applied blocks
    //Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx5.getNonce)

    //Add txs to the mem pool
    mempoolMap.add(tx5)
    mempoolMap.add(tx6)
    assertEquals(2, mempoolMap.size)

    val tx0Norm = createTransactionWithDataSize(0, BigInteger.valueOf(0))
    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx0, tx1, tx2, tx3, tx4)
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx0Norm, tx1)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx2.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 4, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 4, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 2, executableTxs.size)
    val iter = executableTxs.iterator
    assertEquals("Wrong executable transaction", tx2, iter.next())
    assertEquals("Wrong executable transaction", tx3, iter.next())

  }

  @Test
  def testWithTxsInvalidForNonceGap(): Unit = {

    val tx5 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(5), keyOpt = accountKeyOpt)
    val tx6 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(6), keyOpt = accountKeyOpt)
    val tx7 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(7), keyOpt = accountKeyOpt)
    val tx8 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(8), keyOpt = accountKeyOpt)
    val tx9 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(9), keyOpt = accountKeyOpt)
    val tx10 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(10), keyOpt = accountKeyOpt)
    val tx11 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(11), keyOpt = accountKeyOpt)
    val tx12 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(12), keyOpt = accountKeyOpt)
    val tx13 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(13), keyOpt = accountKeyOpt)
    val tx14 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(14), keyOpt = accountKeyOpt)
    val tx15 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(15), keyOpt = accountKeyOpt)
    val tx16 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(16), keyOpt = accountKeyOpt)
    val tx18 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(18), keyOpt = accountKeyOpt)
    val tx19 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(19), keyOpt = accountKeyOpt)
    val tx20 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(20), keyOpt = accountKeyOpt)

    // Test 1: Try with only txs from reverted blocks

    //Initialize mempool
    val address = tx14.getFrom.address()

    def initMempool(): MempoolMap = {
      val mempoolSettings = AccountMempoolSettings(maxNonceGap = 7)
      val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)

      //Update the nonce in the state db
      Mockito
        .when(accountStateViewMock.getNonce(address))
        .thenReturn(tx14.getNonce)

      //Add txs to the mem pool
      assertTrue(mempoolMap.add(tx14).isSuccess) //exec
      assertTrue(mempoolMap.add(tx15).isSuccess) //exec
      assertTrue(mempoolMap.add(tx16).isSuccess) //exec
      assertTrue(mempoolMap.add(tx18).isSuccess) //non exec
      assertTrue(mempoolMap.add(tx19).isSuccess) //non exec
      assertTrue(mempoolMap.add(tx20).isSuccess) //non exec
      assertEquals(6, mempoolMap.size)
      mempoolMap
    }

    var mempoolMap = initMempool()

    //Test 1.1 non-exec txs exceed max nonce gap after update
    //Prepare blocks
    var listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx12, tx13)
    var listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    var stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 12, after the update txs from tx12 to tx16 are exec, tx18 is
    // non exec and tx19 and tx20 are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 6, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 6, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 5, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)
    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 5, executableTxs.size)

    //Test 1.2 exec txs exceed max nonce gap after update
    //Prepare blocks

    //Reset mempool
    mempoolMap = initMempool()

    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 9, after the update txs from tx9 to tx15 are exec, tx16 to tx18
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 7, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)


    //Test 1.3 re-injected txs exceed max nonce gap after update
    //Reset mempool
    mempoolMap = initMempool()

    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx6, tx7, tx8, tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 6, after the update txs from tx6 to t12 are exec, tx13 onwards
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 7, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)

    // Test 2: Try with txs from reverted and applied blocks
    //Reset mempool
    mempoolMap = initMempool()

    //Test 2.1 non-exec txs exceed max nonce gap after update
    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx11, tx12, tx13)
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx11)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    stateNonce = tx12.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 12, after the update txs from tx12 to tx16 are exec, tx18 is
    // non exec and tx19 and tx20 are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 6, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 6, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 5, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 5, executableTxs.size)

    //Test 2.2 exec txs exceed max nonce gap after update
    mempoolMap = initMempool()

    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx8, tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx8)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    stateNonce = tx9.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 9, after the update txs from tx9 to tx15 are exec, tx16 and tx18
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 7, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)

    //Test 2.3 re-injected txs exceed max nonce gap after update
    //Reset mempool
    mempoolMap = initMempool()

    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx5, tx6, tx7, tx8, tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx5)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    stateNonce = tx6.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 6, after the update txs from tx6 to t12 are exec, tx13 onwards
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 7, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)
  }

  @Test
  def testWithTxsInvalidForAccountSize(): Unit = {

    val tx11 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(11), keyOpt = accountKeyOpt), 4)
    val tx12 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(12), keyOpt = accountKeyOpt), 4)
    val tx13 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(13), keyOpt = accountKeyOpt), 4)
    val tx14 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(14), keyOpt = accountKeyOpt)
    val tx15 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(15), keyOpt = accountKeyOpt)
    val tx16 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(16), keyOpt = accountKeyOpt)
    val tx18 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(18), keyOpt = accountKeyOpt)
    val tx19 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(19), keyOpt = accountKeyOpt)
    val tx20 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(20), keyOpt = accountKeyOpt)

    // Test 1: Txs from reverted blocks exceed account size

    //Initialize mempool
    val mempoolSettings = AccountMempoolSettings(maxAccountSlots = 9)
    val address = tx14.getFrom.address()

    def initMempool(): MempoolMap = {
      val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)

      //Update the nonce in the state db
      Mockito
        .when(accountStateViewMock.getNonce(address))
        .thenReturn(tx14.getNonce)

      //Add txs to the mem pool
      assertTrue(mempoolMap.add(tx14).isSuccess) //exec
      assertTrue(mempoolMap.add(tx15).isSuccess) //exec
      assertTrue(mempoolMap.add(tx16).isSuccess) //exec
      assertTrue(mempoolMap.add(tx18).isSuccess) //non exec
      assertTrue(mempoolMap.add(tx19).isSuccess) //non exec
      assertTrue(mempoolMap.add(tx20).isSuccess) //non exec
      assertEquals(6, mempoolMap.size)
      mempoolMap
    }

    val mempoolMap = initMempool()

    //Prepare blocks
    val listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx11, tx12, tx13)
    val listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    val stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //After the update tx11 and tx12 are exec, tx14 is non-exec and the others are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 3, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 9, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)
    val executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 2, executableTxs.size)
    assertEquals("Wrong account size in slots", mempoolSettings.maxAccountSlots, mempoolMap.getAccountSizeInSlots(tx14.getFrom))
  }


  @Test
  def testUpdateWithMempoolFull(): Unit = {

    val addressA = accountKeyOpt.get.publicImage().address()
    val txA0 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(0), keyOpt = accountKeyOpt), 4)
    val txA1 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(1), keyOpt = accountKeyOpt)
    val txA2 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(2), keyOpt = accountKeyOpt)
    val txA3 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(3), keyOpt = accountKeyOpt), 4)
    val txA4 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(4), keyOpt = accountKeyOpt)
    val txA5 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(5), keyOpt = accountKeyOpt), 2)

    val accountKeyBOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest2".getBytes()))
    val addressB = accountKeyBOpt.get.publicImage().address()

    val txB0 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(0), keyOpt = accountKeyBOpt)
    val txB1 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(1), keyOpt = accountKeyBOpt)
    val txB2 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(2), keyOpt = accountKeyBOpt)
    val txB3 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(3), keyOpt = accountKeyBOpt)
    val txB4 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(4), keyOpt = accountKeyBOpt)
    val txB5 = createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(5), keyOpt = accountKeyBOpt)

    // Test 1: Txs from reverted blocks exceed mempool size. Verify that oldest txs are evicted

    //Initialize mempool
    val mempoolMap = new MempoolMap(accountStateProvider,
      baseStateProvider,
      AccountMempoolSettings(maxMemPoolSlots = 8,
        maxNonExecMemPoolSlots = 7))
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(addressA))
      .thenReturn(BigInteger.valueOf(3))
    Mockito
      .when(accountStateViewMock.getNonce(addressB))
      .thenReturn(BigInteger.valueOf(5))

    //Add txs to the mem pool
    assertTrue(mempoolMap.add(txA3).isSuccess) //exec
    assertTrue(mempoolMap.add(txA4).isSuccess) //exec
    assertTrue(mempoolMap.add(txB5).isSuccess) //exec
    assertEquals("Wrong account size in slots", 6, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong number of txs in the mempool", 3, mempoolMap.size)


    //Prepare blocks. The rejected txs occupy 4 slots, there are already 6 slots occupied => total size 10 > maxMemPoolSlots (8)
    // txA3, that occupies 4 slots and it is the oldest, should be evicted.
    var listOfTxsToReAdd = Seq[SidechainTypes#SCAT](txB1, txB2, txB3, txB4)
    val listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(addressB))
      .thenReturn(BigInteger.valueOf(1))

    //After the update txA4, txB1, txB2, txB3, txB4 and txB5 will be in the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong mempool size in slots", 6, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 5, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)
    assertEquals("Wrong number of txs in the mempool", 6, mempoolMap.size)
    assertTrue(mempoolMap.contains(ModifierId @@ txB1.id))
    assertTrue(mempoolMap.contains(ModifierId @@ txB2.id))
    assertTrue(mempoolMap.contains(ModifierId @@ txA4.id))
    assertTrue(mempoolMap.contains(ModifierId @@ txB3.id))
    assertTrue(mempoolMap.contains(ModifierId @@ txB4.id))
    assertTrue(mempoolMap.contains(ModifierId @@ txB5.id))

    //Verify that the rejected txs are "younger" than the txs that were already in the mempool.
    //Fill again the mempool and check that the eviction order is the same as the expected age order.
    //First will be evicted the txs that were in the mempool, then the txs reinjected, in reverse order respect the nonce order
    assertTrue(mempoolMap.add(txA5).isSuccess) //Add 2 slots
    val orderedTxs: Array[ModifierId] = Array(ModifierId @@ txA4.id, ModifierId @@ txB5.id, ModifierId @@ txB4.id, ModifierId @@ txB3.id, ModifierId @@ txB2.id, ModifierId @@ txB1.id)
    val accountKeyCOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest3".getBytes()))
    (0 to 5).foreach { idx =>
      assertTrue(mempoolMap.contains(ModifierId @@ orderedTxs(idx)))
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(idx), keyOpt = accountKeyCOpt))
      assertFalse(s"Transaction $idx is still in mempool", mempoolMap.contains(ModifierId @@ orderedTxs(idx)))
    }
  }


  @Test
  def testUpdateWithNonExecSubpoolFull(): Unit = {
    //Test 1: in the mempool there are only exec txs. Reverting 1 block causes the first tx to become invalid for balance
    // and so the subsequent txs will become non exec. The non exec subpool is too big so some txs will be evicted.

    val limitOfGas = BigInteger.valueOf(1000000)
    val maxGasFee = BigInteger.valueOf(1000000)
    val tx0 = createEIP1559Transaction(BigInteger.valueOf(100), BigInteger.valueOf(0),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx1 = createEIP1559Transaction(BigInteger.valueOf(200), BigInteger.valueOf(1),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx2 = createEIP1559Transaction(BigInteger.valueOf(150), BigInteger.valueOf(2),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx3 = createEIP1559Transaction(BigInteger.valueOf(100), BigInteger.valueOf(3),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx4 = createEIP1559Transaction(BigInteger.valueOf(100), BigInteger.valueOf(4),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx5 = createEIP1559Transaction(BigInteger.valueOf(100), BigInteger.valueOf(5),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx6 = createEIP1559Transaction(BigInteger.valueOf(10), BigInteger.valueOf(6),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx7 = createEIP1559Transaction(BigInteger.valueOf(10), BigInteger.valueOf(7),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx8 = createEIP1559Transaction(BigInteger.valueOf(10), BigInteger.valueOf(8),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)

    var mempoolMap = new MempoolMap(accountStateProvider,
      baseStateProvider,
      AccountMempoolSettings(maxMemPoolSlots = 10,
        maxNonExecMemPoolSlots = 4))
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(tx1.getFrom.address()))
      .thenReturn(BigInteger.valueOf(1))

    val listOfTxs = scala.collection.mutable.ListBuffer[SidechainTypes#SCAT](tx1, tx2, tx3, tx5, tx6, tx7, tx8)

    //initialize mem pool
    listOfTxs.foreach(tx => assertTrue(s"Error while adding tx $tx", mempoolMap.add(tx).isSuccess))
    assertEquals("Wrong mempool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 3, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 4, mempoolMap.mempoolTransactions(false).size)

    var listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    var listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx0, tx1)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)
    //Update the nonce in the state db
    val address = listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx2.getNonce)

    //Reduce the balance so tx2 is no valid anymore and the txs in the mempool are all non exec
    Mockito
      .when(accountStateViewMock.getBalance(address))
      .thenReturn(tx2.maxCost().subtract(BigInteger.ONE))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 4, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 4, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 4, mempoolMap.mempoolTransactions(false).size)


    //Test 2: same as before but now after the uodate, bith non exec sub pool and the whole mempool are too big for 1 slot.
    // Verify that just 1 non exec tx needs to be evicted.
    //Reset mempool

    mempoolMap = new MempoolMap(accountStateProvider,
      baseStateProvider,
      AccountMempoolSettings(maxMemPoolSlots = 10,
        maxNonExecMemPoolSlots = 4))
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(tx1.getFrom.address()))
      .thenReturn(BigInteger.valueOf(1))

    val accountKeyBOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest2".getBytes()))
    val oldestTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = accountKeyBOpt)
    assertTrue("Adding transaction failed", mempoolMap.add(oldestTx).isSuccess)

    //Note to my future self: I don't need to reset state nonce and balance because they are not checked in the add function
    //initialize mem pool
    listOfTxs.foreach(tx => assertTrue(s"Error while adding tx $tx", mempoolMap.add(tx).isSuccess))
    assertEquals("Wrong mempool size in slots", 8, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 4, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 4, mempoolMap.mempoolTransactions(false).size)

    //Reinsert some additional exec txs in order to reach the maximum size limit
    val accountKeyCOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest2".getBytes()))

    val listOfTxsAccountC = (0 to 4).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = accountKeyCOpt)).toSeq

    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsAccountC.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 10, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 10, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 6, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 4, mempoolMap.mempoolTransactions(false).size)
    assertTrue("Oldest tx was removed from the mempool", mempoolMap.contains(ModifierId @@ oldestTx.id))

    // Test 3: after updating the mempool, the mempool size is too big but not the non exec sub pool. Oldest txs are removed
    // that cause some exec txs to become non exec. After that, also the non exec sub pool has too many txs and some need
    // to be removed as well

    mempoolMap = new MempoolMap(accountStateProvider,
      baseStateProvider,
      AccountMempoolSettings(maxMemPoolSlots = 10,
        maxNonExecMemPoolSlots = 4))
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(tx1.getFrom.address()))
      .thenReturn(BigInteger.valueOf(0))
    //Update the balance
    Mockito
      .when(accountStateViewMock.getBalance(tx1.getFrom.address()))
      .thenReturn(ZenWeiConverter.MAX_MONEY_IN_WEI) // Has always enough balance

    //initialize mem pool
    Seq(tx0, tx1, tx2, tx3, tx4, tx5, tx6, tx7, tx8).foreach(tx => assertTrue(s"Error while adding tx $tx", mempoolMap.add(tx).isSuccess))
    assertEquals("Wrong number of txs in the mempool", 9, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 9, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 9, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

    //Now 2 additional exec txs are reinjected. The total number of txs will be 11, so the oldest tx will be evicted.
    // This eviction will created 8 non exec txs => 4 non exec txs will be removed as well

    listOfTxsToReAdd = Seq(listOfTxsAccountC(0), listOfTxsAccountC(1))
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd)
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 6, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 6, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 4, mempoolMap.mempoolTransactions(false).size)

  }

  def createTransactionWithDataSize(dataSize: Int, nonce: BigInteger): EthereumTransaction = {
    val randomData = Array.fill(dataSize)((scala.util.Random.nextInt(256) - 128).toByte)
    createEIP1559Transaction(value = BigInteger.ONE, nonce, keyOpt = accountKeyOpt, data = randomData)
  }


  private def createTransactionsForAccount(
                                            key: PrivateKeySecp256k1,
                                            numOfTxsPerAccount: Int,
                                            orphanIdx: Int = -1
                                          ): scala.collection.mutable.ListBuffer[SidechainTypes#SCAT] = {
    val value = BigInteger.valueOf(12)
    val listOfTxs = new scala.collection.mutable.ListBuffer[SidechainTypes#SCAT]

    (0 until numOfTxsPerAccount).foreach(nonceTx => {
      val currentNonce = BigInteger.valueOf(nonceTx)
      if (orphanIdx >= 0 && nonceTx >= orphanIdx) { // Create orphans
        listOfTxs += createEIP1559Transaction(
          value,
          nonce = BigInteger.valueOf(nonceTx + 1),
          keyOpt = Some(key)
        )
      } else
        listOfTxs += createEIP1559Transaction(value, nonce = currentNonce, keyOpt = Some(key))
    })
    listOfTxs
  }


}
