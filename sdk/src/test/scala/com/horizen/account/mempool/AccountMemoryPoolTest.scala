package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.account.state.AccountStateReader
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger
import java.nio.charset.StandardCharsets

class AccountMemoryPoolTest
  extends JUnitSuite
    with EthereumTransactionFixture
    with SidechainTypes
    with MockitoSugar {

  @Before
  def setUp(): Unit = {
  }

  @Test
  def testTakeExecutableTxs(): Unit = {

    val initialStateNonce = BigInteger.ZERO
    val stateViewMock = mock[AccountStateReader]
    Mockito.when(stateViewMock.nextBaseFee).thenReturn(BigInteger.ZERO)
    Mockito.when(stateViewMock.getNonce(ArgumentMatchers.any[Array[Byte]])).thenReturn(initialStateNonce)

    val accountMemoryPool = AccountMemoryPool.createEmptyMempool(() => stateViewMock)

    assertTrue("Wrong tx list size ", accountMemoryPool.takeExecutableTxs.isEmpty)

    //Adding some txs in the mempool

    val value = BigInteger.TEN
    val account1Key: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("mempooltest1".getBytes(StandardCharsets.UTF_8))


    val account1ExecTransaction0 = createEIP1559Transaction(value, initialStateNonce, Option(account1Key), gasFee = BigInteger.valueOf(3), priorityGasFee = BigInteger.valueOf(3))
    assertTrue(accountMemoryPool.put(account1ExecTransaction0).isSuccess)

    var listOfExecTxs = accountMemoryPool.takeExecutableTxs
    assertEquals("Wrong tx list size ", 1, listOfExecTxs.size)
    assertEquals("Wrong tx ", account1ExecTransaction0.id(), listOfExecTxs.head.id)

    val account1NonExecTransaction0 = createEIP1559Transaction(value, BigInteger.valueOf(1000), Option(account1Key))
    assertTrue(accountMemoryPool.put(account1NonExecTransaction0).isSuccess)
    listOfExecTxs = accountMemoryPool.takeExecutableTxs
    assertEquals("Wrong tx list size ", 1, listOfExecTxs.size)
    assertEquals("Wrong tx ", account1ExecTransaction0.id(), listOfExecTxs.head.id)

    //Adding other Txs to the same account and verify they are returned ordered by nonce and not by gas price

    val account1ExecTransaction1 = createEIP1559Transaction(value, account1ExecTransaction0.getNonce.add(BigInteger.ONE), Option(account1Key), gasFee = BigInteger.valueOf(1))
    assertTrue(accountMemoryPool.put(account1ExecTransaction1).isSuccess)
    val account1ExecTransaction2 = createEIP1559Transaction(value, account1ExecTransaction1.getNonce.add(BigInteger.ONE), Option(account1Key), gasFee = BigInteger.valueOf(1000), priorityGasFee = BigInteger.valueOf(110))
    assertTrue(accountMemoryPool.put(account1ExecTransaction2).isSuccess)

    listOfExecTxs = accountMemoryPool.takeExecutableTxs
    assertEquals("Wrong tx list size ", 3, listOfExecTxs.size)
    var iter = listOfExecTxs.iterator
    assertEquals("Wrong tx ", account1ExecTransaction0.id(), iter.next().id)
    assertEquals("Wrong tx ", account1ExecTransaction1.id(), iter.next().id)
    assertEquals("Wrong tx ", account1ExecTransaction2.id(), iter.next().id)

    var subListOfExecTxs = accountMemoryPool.take(2).toList
    assertEquals("Wrong tx list size ", 2, subListOfExecTxs.size)
    assertEquals("Wrong tx ", account1ExecTransaction0.id(), subListOfExecTxs(0).id)
    assertEquals("Wrong tx ", account1ExecTransaction1.id(), subListOfExecTxs(1).id)


    //Create txs for other accounts and verify that the list is ordered by nonce and gas price
    //The expected order is: tx3_0, tx3_1, tx3_2, tx2_0, tx1_0, tx2_1, tx2_2, tx1_1, tx1_2
    val account2Key = PrivateKeySecp256k1Creator.getInstance().generateSecret("mempooltest2".getBytes(StandardCharsets.UTF_8))
    val account2ExecTransaction0 = createEIP1559Transaction(value, initialStateNonce, Option(account2Key), gasFee = BigInteger.valueOf(25), priorityGasFee = BigInteger.valueOf(5))
    val account2ExecTransaction1 = createEIP1559Transaction(value, account2ExecTransaction0.getNonce.add(BigInteger.ONE), Option(account2Key), gasFee = BigInteger.valueOf(2), priorityGasFee = BigInteger.valueOf(2))
    val account2ExecTransaction2 = createEIP1559Transaction(value, account2ExecTransaction1.getNonce.add(BigInteger.ONE), Option(account2Key), gasFee = BigInteger.valueOf(990), priorityGasFee = BigInteger.valueOf(2))
    assertTrue(accountMemoryPool.put(account2ExecTransaction1).isSuccess)
    assertTrue(accountMemoryPool.put(account2ExecTransaction2).isSuccess)
    assertTrue(accountMemoryPool.put(account2ExecTransaction0).isSuccess)

    val account3Key = PrivateKeySecp256k1Creator.getInstance().generateSecret("mempooltest3".getBytes(StandardCharsets.UTF_8))
    val account3ExecTransaction0 = createEIP1559Transaction(value, initialStateNonce, Option(account3Key), gasFee = BigInteger.valueOf(10), priorityGasFee = BigInteger.valueOf(10))
    val account3ExecTransaction1 = createEIP1559Transaction(value, account3ExecTransaction0.getNonce.add(BigInteger.ONE), Option(account3Key), gasFee = BigInteger.valueOf(2200), priorityGasFee = BigInteger.valueOf(200))
    val account3ExecTransaction2 = createEIP1559Transaction(value, account3ExecTransaction1.getNonce.add(BigInteger.ONE), Option(account3Key), gasFee = BigInteger.valueOf(60), priorityGasFee = BigInteger.valueOf(6))
    assertTrue(accountMemoryPool.put(account3ExecTransaction0).isSuccess)
    assertTrue(accountMemoryPool.put(account3ExecTransaction2).isSuccess)
    assertTrue(accountMemoryPool.put(account3ExecTransaction1).isSuccess)

    listOfExecTxs = accountMemoryPool.takeExecutableTxs
    assertEquals("Wrong tx list size ", 9, listOfExecTxs.size)
    iter = listOfExecTxs.iterator
    assertEquals("Wrong tx ", account3ExecTransaction0.id(), iter.next().id)
    assertEquals("Wrong tx ", account3ExecTransaction1.id(), iter.next().id)
    assertEquals("Wrong tx ", account3ExecTransaction2.id(), iter.next().id)
    assertEquals("Wrong tx ", account2ExecTransaction0.id(), iter.next().id)
    assertEquals("Wrong tx ", account1ExecTransaction0.id(), iter.next().id)
    assertEquals("Wrong tx ", account2ExecTransaction1.id(), iter.next().id)
    assertEquals("Wrong tx ", account2ExecTransaction2.id(), iter.next().id)
    assertEquals("Wrong tx ", account1ExecTransaction1.id(), iter.next().id)
    assertEquals("Wrong tx ", account1ExecTransaction2.id(), iter.next().id)

    subListOfExecTxs = accountMemoryPool.take(4).toList
    assertEquals("Wrong tx list size ", 4, subListOfExecTxs.size)
    assertEquals("Wrong tx ", account3ExecTransaction0.id(), subListOfExecTxs(0).id)
    assertEquals("Wrong tx ", account3ExecTransaction1.id(), subListOfExecTxs(1).id)
    assertEquals("Wrong tx ", account3ExecTransaction2.id(), subListOfExecTxs(2).id)
    assertEquals("Wrong tx ", account2ExecTransaction0.id(), subListOfExecTxs(3).id)
  }
}
