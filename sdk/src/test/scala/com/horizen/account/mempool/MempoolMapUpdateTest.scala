package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.AccountStateReader
import com.horizen.account.utils.ZenWeiConverter
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.crypto.{ECKeyPair, Keys}

import java.math.BigInteger

class MempoolMapUpdateTest extends JUnitSuite with EthereumTransactionFixture with SidechainTypes with MockitoSugar {

  val stateViewMock: AccountStateReader = mock[AccountStateReader]
  val stateProvider: AccountStateReaderProvider = () => stateViewMock

  @Before
  def setUp(): Unit = {
    Mockito
      .when(stateViewMock.getBalance(ArgumentMatchers.any[Array[Byte]]))
      .thenReturn(ZenWeiConverter.MAX_MONEY_IN_WEI) // Has always enough balance

    Mockito
      .when(stateViewMock.getNonce(ArgumentMatchers.any[Array[Byte]]))
      .thenReturn(BigInteger.ZERO)

  }

  @Test
  def testEmptyMemPool(): Unit = {
    var mempoolMap = new MempoolMap(stateProvider)

    val account = Keys.createEcKeyPair()

    val expectedNumOfTxs = 7
    val listOfTxs = createTransactionsForAccount(account, expectedNumOfTxs).toSeq

    // Try with only txs from reverted blocks
    var listOfTxsToReAdd = listOfTxs
    var listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)

    var executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", expectedNumOfTxs, executableTxs.size)

    // Try with only txs from applied blocks
    // Reset mempool
    mempoolMap = new MempoolMap(stateProvider)
    //Update the nonce in the state db
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfTxs))

    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = listOfTxs

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", 0, mempoolMap.size)

    // Try with txs from applied and reverted blocks
    // Reset mempool
    mempoolMap = new MempoolMap(stateProvider)
    listOfTxsToReAdd = listOfTxs.take(3)
    listOfTxsToRemove = listOfTxs

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", 0, mempoolMap.size)

    // Reset mempool
    mempoolMap = new MempoolMap(stateProvider)
    listOfTxsToReAdd = listOfTxs
    listOfTxsToRemove = listOfTxs.take(4)
    //Update the nonce in the state db
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(4))

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", listOfTxsToReAdd.size - listOfTxsToRemove.size, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", listOfTxsToReAdd.size - listOfTxsToRemove.size, executableTxs.size)

    //With orphans for balance
    mempoolMap = new MempoolMap(stateProvider)
    val invalidTx = createEIP1559Transaction(BigInteger.valueOf(20000), nonce = BigInteger.valueOf(expectedNumOfTxs),
      Some(account), gasLimit = BigInteger.valueOf(1000000), gasFee = BigInteger.valueOf(1000000))
    val validTx = createEIP1559Transaction(BigInteger.valueOf(12), nonce = BigInteger.valueOf(expectedNumOfTxs + 1),
      Some(account))
    listOfTxsToReAdd = listOfTxsToReAdd :+ invalidTx.asInstanceOf[SidechainTypes#SCAT]
    listOfTxsToReAdd = listOfTxsToReAdd :+ validTx.asInstanceOf[SidechainTypes#SCAT]
    assertTrue(invalidTx.maxCost().compareTo(listOfTxsToReAdd.head.maxCost()) > 0)
    Mockito
      .when(stateViewMock.getBalance(invalidTx.getFrom.address()))
      .thenReturn(listOfTxsToReAdd.head.maxCost())

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", listOfTxsToReAdd.size - listOfTxsToRemove.size - 1, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", listOfTxsToReAdd.size - listOfTxsToRemove.size - 2, executableTxs.size)
  }

  @Test
  def testWithTxsInMemPool(): Unit = {
    val mempoolMap = new MempoolMap(stateProvider)

    val account = Keys.createEcKeyPair()

    val expectedNumOfTxs = 5
    val expectedNumOfExecutableTxs = 3
    val listOfTxs = createTransactionsForAccount(account, expectedNumOfTxs, expectedNumOfExecutableTxs).toSeq

    //initialize mem pool
    listOfTxs.foreach(tx => mempoolMap.add(tx))
    assertEquals(expectedNumOfTxs, mempoolMap.size)
    var executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    // Try with only txs from applied blocks
    var listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    var listOfTxsToRemove = listOfTxs.take(expectedNumOfExecutableTxs - 1)
    //Update the nonce in the state db
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfExecutableTxs))

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfExecutableTxs, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", 1, executableTxs.size)

    // Try with only txs from reverted blocks
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    //Update the nonce in the state db
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.ZERO)


    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)

    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)


    // Try with txs from applied and reverted blocks
    listOfTxsToReAdd = listOfTxs.take(1)
    listOfTxsToRemove = listOfTxs.take(2)
    //Update the nonce in the state db
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(2))

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs - 2, mempoolMap.size)

    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs - 2, executableTxs.size)

    // Reset mempool to initial situation
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.ZERO)
    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", expectedNumOfTxs, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    //Apply enough txs so that the non executable txs become executable
    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = createTransactionsForAccount(account, expectedNumOfExecutableTxs + 1).toSeq //creates expectedNumOfExecutableTxs + 1 consecutive txs
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.valueOf(expectedNumOfExecutableTxs + 1))

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", 2, executableTxs.size)

  }

  @Test
  def testWithTxsInvalidForBalance(): Unit = {
    val mempoolMap = new MempoolMap(stateProvider)

    val account = Keys.createEcKeyPair()
    val limitOfGas = BigInteger.valueOf(1000000)
    val maxGasFee = BigInteger.valueOf(1000000)
    val tx0 = createEIP1559Transaction(BigInteger.valueOf(20000), BigInteger.valueOf(0),
      Some(account), gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx1 = createEIP1559Transaction(BigInteger.valueOf(50000), BigInteger.valueOf(1),
      Some(account), gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx2 = createEIP1559Transaction(BigInteger.valueOf(200), BigInteger.valueOf(2),
      Some(account), gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx4 = createEIP1559Transaction(BigInteger.valueOf(20000), BigInteger.valueOf(4),
      Some(account), gasLimit = limitOfGas, gasFee = maxGasFee)
    val tx5 = createEIP1559Transaction(BigInteger.valueOf(10), BigInteger.valueOf(5),
      Some(account), gasLimit = limitOfGas, gasFee = maxGasFee)


    val expectedNumOfTxs = 5
    val expectedNumOfExecutableTxs = 3

    val listOfTxs = scala.collection.mutable.ListBuffer[SidechainTypes#SCAT](tx0, tx1, tx2, tx4, tx5).toSeq

    //initialize mem pool
    listOfTxs.foreach(tx => mempoolMap.add(tx))
    assertEquals(expectedNumOfTxs, mempoolMap.size)
    var executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", expectedNumOfExecutableTxs, executableTxs.size)

    // Try with only txs from applied blocks
    var listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    var listOfTxsToRemove = Seq[SidechainTypes#SCAT](tx0)
    //Update the nonce in the state db
    val address = listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()
    Mockito
      .when(stateViewMock.getNonce(address))
      .thenReturn(tx1.getNonce)

    //Reduce the balance so tx1 is and tx4 are no valid anymore and the txs in the mempool are all non exec
    Mockito
      .when(stateViewMock.getBalance(address))
      .thenReturn(tx0.maxCost().subtract(BigInteger.ONE))


    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", 0, executableTxs.size)


    // Try to revert tx0 but it is not inserted because the balance is too low now
    listOfTxsToReAdd = listOfTxsToRemove
    listOfTxsToRemove = Seq.empty[SidechainTypes#SCAT]
    //Update the nonce in the state db
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(BigInteger.ZERO)

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", 0, executableTxs.size)

    //Apply tx with nonce 1 => tx2 becomes executable
    val newTx1 = createEIP1559Transaction(BigInteger.valueOf(500), BigInteger.valueOf(1),
      Some(account), gasLimit = limitOfGas, gasFee = maxGasFee)

    listOfTxsToReAdd = Seq.empty[SidechainTypes#SCAT]
    listOfTxsToRemove = Seq[SidechainTypes#SCAT](newTx1)
    //Update the nonce in the state db
    Mockito
      .when(stateViewMock.getNonce(listOfTxs.head.getFrom.asInstanceOf[AddressProposition].address()))
      .thenReturn(tx2.getNonce)

    mempoolMap.updateMemPool(listOfTxsToReAdd, listOfTxsToRemove)
    assertEquals("Wrong number of txs in the mempool", 2, mempoolMap.size)
    executableTxs = mempoolMap.takeExecutableTxs(10).toSeq
    assertEquals("Wrong number of executable transactions", 1, executableTxs.size)

  }



  private def createTransactionsForAccount(
      keys: ECKeyPair,
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
          pairOpt = Some(keys),
          to = toAddr
        )
      } else
        listOfTxs += createEIP1559Transaction(value, nonce = currentNonce, pairOpt = Some(keys), to = toAddr)
    })
    listOfTxs
  }


}
