package com.horizen.account.mempool

import com.horizen.{AccountMempoolSettings, SidechainTypes}
import com.horizen.account.block.AccountBlock
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.account.state.{AccountStateReader, AccountStateReaderProvider, BaseStateReaderProvider}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.utils.Address
import com.horizen.state.BaseStateReader
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger

class MempoolMapUpdateTest extends JUnitSuite with EthereumTransactionFixture with SidechainTypes with MockitoSugar {

  val accountStateViewMock: AccountStateReader = mock[AccountStateReader]
  val baseStateViewMock: BaseStateReader = mock[BaseStateReader]

  val accountStateProvider: AccountStateReaderProvider = () => accountStateViewMock
  val baseStateProvider: BaseStateReaderProvider = () => baseStateViewMock

  val rejectedBlock: AccountBlock = mock[AccountBlock]
  val appliedBlock: AccountBlock = mock[AccountBlock]

  val listOfRejectedBlocks = Seq(rejectedBlock)
  val listOfAppliedBlocks = Seq(appliedBlock)

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
    val listOfTxs = createTransactionsForAccount(accountKeyOpt.get, expectedNumOfTxs).toSeq

    // Try with only txs from reverted blocks
    var listOfTxsToReAdd = listOfTxs
    var listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]

    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)

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
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 0, mempoolMap.size)

    // Try with txs from applied and reverted blocks
    // Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    listOfTxsToReAdd = listOfTxs.take(3)
    listOfTxsToRemove = listOfTxs
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 0, mempoolMap.size)

    // Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    listOfTxsToReAdd = listOfTxs
    listOfTxsToRemove = listOfTxs.take(4)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(4))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", listOfTxsToReAdd.size - listOfTxsToRemove.size, mempoolMap.size)
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
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    assertTrue(invalidTx.maxCost().compareTo(listOfTxsToReAdd.head.maxCost()) > 0)
    Mockito
      .when(accountStateViewMock.getBalance(invalidTx.getFrom.address()))
      .thenReturn(listOfTxsToReAdd.head.maxCost())

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", listOfTxsToReAdd.size - listOfTxsToRemove.size - 1, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", listOfTxsToReAdd.size - listOfTxsToRemove.size - 2, executableTxs.size)
  }

  @Test
  def testWithTxsInMemPool(): Unit = {
    val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    val expectedNumOfTxs = 5
    val expectedNumOfExecutableTxs = 3
    val listOfTxs = createTransactionsForAccount(accountKeyOpt.get, expectedNumOfTxs, expectedNumOfExecutableTxs).toSeq

    //initialize mem pool
    listOfTxs.foreach(tx => mempoolMap.add(tx))
    assertEquals(expectedNumOfTxs, mempoolMap.size)
    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    // Try with only txs from applied blocks
    var listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    var listOfTxsToRemove = listOfTxs.take(expectedNumOfExecutableTxs - 1)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfExecutableTxs))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", expectedNumOfExecutableTxs, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 1, executableTxs.size)

    // Try with only txs from reverted blocks
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.ZERO)


    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)

    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)


    // Try with txs from applied and reverted blocks
    listOfTxsToReAdd = listOfTxs.take(1)
    listOfTxsToRemove = listOfTxs.take(2)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(2))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs - 2, mempoolMap.size)

    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs - 2, executableTxs.size)

    // Reset mempool to initial situation
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.ZERO)
    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    //Apply enough txs so that the non executable txs become executable
    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = createTransactionsForAccount(accountKeyOpt.get, expectedNumOfExecutableTxs + 1).toSeq //creates expectedNumOfExecutableTxs + 1 consecutive txs
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfExecutableTxs + 1))

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
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

    val listOfTxs = scala.collection.mutable.ListBuffer[SidechainTypes#SCAT](tx0, tx1, tx2, tx3, tx5, tx6).toSeq

    //initialize mem pool
    listOfTxs.foreach(tx => assertTrue(s"Error while adding tx $tx", mempoolMap.add(tx).isSuccess))
    assertEquals(expectedNumOfTxs, mempoolMap.size)
    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    // Try with only txs from applied blocks
    var listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    var listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx0, tx1)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
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
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 0, executableTxs.size)


    // Try to revert tx0 and tx1 but tx1 is not inserted because the balance is too low now
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(tx1.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 3, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 1, executableTxs.size)

    //Apply txs with nonce 0, 1 and 2 => tx3 becomes executable
    val newTx1 = createEIP1559Transaction(BigInteger.valueOf(500), BigInteger.valueOf(1),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)
    val newTx2 = createEIP1559Transaction(BigInteger.valueOf(500), BigInteger.valueOf(2),
      accountKeyOpt, gasLimit = limitOfGas, gasFee = maxGasFee)

    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx0, newTx1, newTx2)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(tx3.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
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

    //Prepare blocks
    var listOfTxsToReAdd =  Seq[SidechainTypes#SCAT](tx0, tx1)
    var listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
     Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx0.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 3, mempoolMap.size)
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
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])
    //Update the nonce in the state db
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(tx2.getNonce)

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 4, mempoolMap.size)
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
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    var stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 12, after the update txs from tx12 to tx16 are exec, tx18 is
    // non exec and tx19 and tx20 are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 6, mempoolMap.size)
    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 5, executableTxs.size)

    //Test 1.2 exec txs exceed max nonce gap after update
    //Prepare blocks

    //Reset mempool
    mempoolMap = initMempool()

    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 9, after the update txs from tx9 to tx15 are exec, tx16 to tx18
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)


    //Test 1.3 re-injected txs exceed max nonce gap after update
    //Reset mempool
    mempoolMap = initMempool()

    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx6, tx7, tx8, tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 6, after the update txs from tx6 to t12 are exec, tx13 onwards
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)

    // Test 2: Try with txs from reverted and applied blocks
    //Reset mempool
    mempoolMap = initMempool()

    //Test 2.1 non-exec txs exceed max nonce gap after update
    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx11, tx12, tx13)
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx11)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    stateNonce = tx12.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 12, after the update txs from tx12 to tx16 are exec, tx18 is
    // non exec and tx19 and tx20 are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 6, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 5, executableTxs.size)

    //Test 2.2 exec txs exceed max nonce gap after update
    mempoolMap = initMempool()

    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx8, tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx8)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    stateNonce = tx9.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 9, after the update txs from tx9 to tx15 are exec, tx16 and tx18
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)

    //Test 2.3 re-injected txs exceed max nonce gap after update
    //Reset mempool
    mempoolMap = initMempool()

    //Prepare blocks
    listOfTxsToReAdd = Seq[SidechainTypes#SCAT](tx5, tx6, tx7, tx8, tx9, tx10, tx11, tx12, tx13)
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx5)
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    stateNonce = tx6.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //With maxNonceGap = 7 and stateNonce = 6, after the update txs from tx6 to t12 are exec, tx13 onwards
    // are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 7, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 7, executableTxs.size)
  }

  @Test
  def testWithTxsInvalidForAccountSize(): Unit = {

    def createMockTxWithSize(txToMock: EthereumTransaction, size: Long): EthereumTransaction = {
      val tx = Mockito.spy[EthereumTransaction](txToMock)
      Mockito.when(tx.size()).thenReturn(size)
      tx
    }
    val tx11 = createMockTxWithSize(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(11), keyOpt = accountKeyOpt), MempoolMap.MaxTxSize)
    val tx12 = createMockTxWithSize(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(12), keyOpt = accountKeyOpt), MempoolMap.MaxTxSize)
    val tx13 = createMockTxWithSize(createEIP1559Transaction(value = BigInteger.TEN, nonce = BigInteger.valueOf(13), keyOpt = accountKeyOpt), MempoolMap.MaxTxSize)
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
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    //Update the nonce in the state db
    val stateNonce = listOfTxsToReAdd.head.getNonce
    Mockito
      .when(accountStateViewMock.getNonce(address))
      .thenReturn(stateNonce)

    //After the update tx11 and tx12 are exec, tx14 is non-exec and the others are dropped from the mempool

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)

    assertEquals("Wrong number of txs in the mempool", 3, mempoolMap.size)
    val executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong number of executable transactions", 2, executableTxs.size)
    assertEquals("Wrong account size in slots", mempoolSettings.maxAccountSlots, mempoolMap.getAccountSlots(tx14.getFrom))
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
