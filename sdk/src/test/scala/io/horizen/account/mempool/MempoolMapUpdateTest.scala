package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.{AccountStateReader, AccountStateReaderProvider, BaseStateReaderProvider}
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.state.BaseStateReader
import io.horizen.evm.Address
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger
import java.nio.charset.StandardCharsets

class MempoolMapUpdateTest extends JUnitSuite with EthereumTransactionFixture with SidechainTypes with MockitoSugar {

  val accountStateViewMock: AccountStateReader = mock[AccountStateReader]
  val baseStateViewMock: BaseStateReader = mock[BaseStateReader]

  val accountStateProvider: AccountStateReaderProvider = () => accountStateViewMock
  val baseStateProvider: BaseStateReaderProvider = () => baseStateViewMock

  val rejectedBlock: AccountBlock = mock[AccountBlock]
  val appliedBlock: AccountBlock = mock[AccountBlock]

  val listOfRejectedBlocks = Seq(rejectedBlock)
  val listOfAppliedBlocks = Seq(appliedBlock)

  val accountKeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest1".getBytes(StandardCharsets.UTF_8)))

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
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider)

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
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider)
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
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider)
    listOfTxsToReAdd = listOfTxs.take(3)
    listOfTxsToRemove = listOfTxs
    Mockito.when(rejectedBlock.transactions).thenReturn(listOfTxsToReAdd.asInstanceOf[Seq[SidechainTypes#SCAT]])
    Mockito.when(appliedBlock.transactions).thenReturn(listOfTxsToRemove.asInstanceOf[Seq[SidechainTypes#SCAT]])

    mempoolMap.updateMemPool(listOfRejectedBlocks, listOfAppliedBlocks)
    assertEquals("Wrong number of txs in the mempool", 0, mempoolMap.size)

    // Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider)
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
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider)
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
    val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider)

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
    val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider)

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
    listOfTxs.foreach(tx => mempoolMap.add(tx))
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



  private def createTransactionsForAccount(
      key: PrivateKeySecp256k1,
      numOfTxsPerAccount: Int,
      orphanIdx: Int = -1
  ): scala.collection.mutable.ListBuffer[SidechainTypes#SCAT] = {
    val toAddr = "0x00112233445566778899AABBCCDDEEFF01020304"
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
