package com.horizen.account.mempool

import com.horizen.{AccountMempoolSettings, SidechainTypes}
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.mempool.exception.{NonceGapTooWideException, TxOversizedException}
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.account.state.{AccountStateReader, AccountStateReaderProvider, BaseStateReaderProvider}
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.evm.utils.Address
import com.horizen.state.BaseStateReader
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.util.ModifierId
import java.math.BigInteger
import scala.util.{Failure, Random, Success}

class MempoolMapTest
    extends JUnitSuite
    with EthereumTransactionFixture
    with SidechainTypes
    with MockitoSugar {

  val accountStateViewMock: AccountStateReader = mock[AccountStateReader]
  val accountStateProvider: AccountStateReaderProvider = () => accountStateViewMock
  val baseStateViewMock: BaseStateReader = mock[BaseStateReader]
  val baseStateProvider: BaseStateReaderProvider = () => baseStateViewMock

  val account1KeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest1".getBytes()))
  val account2KeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest2".getBytes()))
  val account3KeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest3".getBytes()))
  
  @Before
  def setUp(): Unit = {
    Mockito.when(baseStateViewMock.getNextBaseFee).thenReturn(BigInteger.ZERO)

    Mockito
      .when(accountStateViewMock.getNonce(ArgumentMatchers.any[Address]))
      .thenReturn(BigInteger.ZERO)

  }

  @Test
  def testCanPayHigherFee(): Unit = {
    val mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    val nonce = BigInteger.ZERO
    val value = BigInteger.TEN
    val lowerGasPrice = BigInteger.valueOf(53)
    val higherGasPrice = lowerGasPrice.add(BigInteger.TEN)


    // Legacy tx with legacy tx
    val legacyTxHigherPrice = createLegacyTransaction(value, nonce, Option.empty, higherGasPrice)
    val legacyTxLowerPrice = createLegacyTransaction(value, nonce, Option.empty, lowerGasPrice)
    assertTrue(mempoolMap.canPayHigherFee(legacyTxHigherPrice, legacyTxLowerPrice))
    assertFalse(mempoolMap.canPayHigherFee(legacyTxHigherPrice, legacyTxHigherPrice))
    assertFalse(mempoolMap.canPayHigherFee(legacyTxLowerPrice, legacyTxHigherPrice))
    val lowerGasFee = lowerGasPrice
    val higherGasFee = higherGasPrice
    val lowerGasTip = BigInteger.valueOf(54)
    val higherGasTip = lowerGasTip.add(BigInteger.TEN)
    // EIP1559 tx with EIP1559 tx
    val eip1559TxHFeeLTip = createEIP1559Transaction(value, nonce, Option.empty, higherGasFee, lowerGasTip)
    val eip1559TxHFeeHTip = createEIP1559Transaction(value, nonce, Option.empty, higherGasFee, higherGasTip)
    val eip1559TxLFeeLTip = createEIP1559Transaction(value, nonce, Option.empty, lowerGasFee, lowerGasTip)
    val eip1559TxLFeeHTip = createEIP1559Transaction(value, nonce, Option.empty, lowerGasFee, higherGasTip)
    assertFalse(mempoolMap.canPayHigherFee(eip1559TxHFeeLTip, eip1559TxHFeeLTip))
    assertFalse(mempoolMap.canPayHigherFee(eip1559TxHFeeLTip, eip1559TxLFeeHTip))
    assertFalse(mempoolMap.canPayHigherFee(eip1559TxLFeeHTip, eip1559TxHFeeLTip))
    assertFalse(mempoolMap.canPayHigherFee(eip1559TxLFeeHTip, eip1559TxLFeeLTip))
    assertFalse(mempoolMap.canPayHigherFee(eip1559TxHFeeHTip, eip1559TxHFeeLTip))
    assertFalse(mempoolMap.canPayHigherFee(eip1559TxHFeeHTip, eip1559TxLFeeHTip))
    assertTrue(mempoolMap.canPayHigherFee(eip1559TxHFeeHTip, eip1559TxLFeeLTip))
    //Mixed tx
    assertFalse(mempoolMap.canPayHigherFee(eip1559TxHFeeHTip, legacyTxHigherPrice))
    assertFalse(mempoolMap.canPayHigherFee(legacyTxHigherPrice,eip1559TxHFeeLTip))
    assertFalse(mempoolMap.canPayHigherFee(legacyTxHigherPrice,eip1559TxHFeeLTip))
    assertTrue(mempoolMap.canPayHigherFee(legacyTxHigherPrice,eip1559TxLFeeLTip))
    assertTrue(mempoolMap.canPayHigherFee(eip1559TxHFeeHTip,legacyTxLowerPrice))
  }


  @Test
  def testAddExecutableTx(): Unit = {
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    var expectedNumOfTxs = 0
    assertEquals(
      "Wrong number of transactions",
      expectedNumOfTxs,
      mempoolMap.size
    )

    val account1InitialStateNonce = BigInteger.ZERO
    val value = BigInteger.TEN
    
    val account1ExecTransaction0 = createEIP1559Transaction(
      value,
      account1InitialStateNonce,
      account1KeyOpt
    )

    expectedNumOfTxs += 1
    var res = mempoolMap.add(account1ExecTransaction0)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account1ExecTransaction0.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account1ExecTransaction0.id)
        .nonEmpty
    )

    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfTxs,
      executableTxs.size
    )
    assertEquals(
      "Added transaction is not executable",
      account1ExecTransaction0.id(),
      executableTxs.head.id
    )

    res = mempoolMap.add(account1ExecTransaction0)
    assertTrue("Adding twice the same tx should not fail", res.isSuccess)
    mempoolMap = res.get
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.size
    )

    val account1ExecTransaction1 = createEIP1559Transaction(
      value,
      account1InitialStateNonce.add(BigInteger.ONE),
      account1KeyOpt
    )

    expectedNumOfTxs += 1

    res = mempoolMap.add(account1ExecTransaction1)
    assertTrue(
      "Adding second transaction to same account failed",
      res.isSuccess
    )
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account1ExecTransaction1.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account1ExecTransaction1.id)
        .nonEmpty
    )

    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfTxs,
      executableTxs.size
    )

    assertEquals(
      "Added transaction is not executable",
      account1ExecTransaction1.id(),
      executableTxs.last.id
    )
    
    val account2InitialStateNonce = BigInteger.valueOf(4576)
    val account2ExecTransaction0 = createEIP1559Transaction(
      value,
      account2InitialStateNonce,
      account2KeyOpt
    )

    Mockito
      .when(accountStateViewMock.getNonce(account2ExecTransaction0.getFrom.address()))
      .thenReturn(account2InitialStateNonce)
    expectedNumOfTxs += 1
    res = mempoolMap.add(account2ExecTransaction0)

    assertTrue("Adding transaction to account 2 failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account2ExecTransaction0.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account2ExecTransaction0.id)
        .nonEmpty
    )

    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfTxs,
      executableTxs.size
    )
    assertTrue(
      "Added transaction is not executable",
      executableTxs.exists(t => t.id == account1ExecTransaction1.id)
    )
  }

  @Test
  def testAddNonExecutableTx(): Unit = {

    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    var expectedNumOfTxs = 0
    var expectedNumOfExecutableTxs = 0
    assertEquals(
      "Wrong number of transactions",
      expectedNumOfTxs,
      mempoolMap.size
    )

    val account1InitialStateNonce = BigInteger.ZERO
    val value = BigInteger.TEN
    
    val account1NonExecTransaction0 = createEIP1559Transaction(value, BigInteger.TWO, account1KeyOpt)

    expectedNumOfTxs += 1
    var res = mempoolMap.add(account1NonExecTransaction0)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account1NonExecTransaction0.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account1NonExecTransaction0.id)
        .nonEmpty
    )

    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfExecutableTxs,
      executableTxs.size
    )

    res = mempoolMap.add(account1NonExecTransaction0)
    assertTrue("Adding twice the same tx should not fail", res.isSuccess)
    mempoolMap = res.get

    val account1NonExecTransaction1 = createEIP1559Transaction(value, BigInteger.ONE, account1KeyOpt)
    expectedNumOfTxs += 1

    res = mempoolMap.add(account1NonExecTransaction1)
    assertTrue(
      "Adding second transaction to same account failed",
      res.isSuccess
    )
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account1NonExecTransaction1.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account1NonExecTransaction1.id)
        .nonEmpty
    )
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfExecutableTxs,
      executableTxs.size
    )

    val account1ExecTransaction0 = createEIP1559Transaction(
      value,
      account1InitialStateNonce,
      account1KeyOpt
    )
    expectedNumOfTxs += 1
    expectedNumOfExecutableTxs = expectedNumOfTxs

    res = mempoolMap.add(account1ExecTransaction0)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account1ExecTransaction0.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account1ExecTransaction0.id)
        .nonEmpty
    )

    executableTxs = mempoolMap.takeExecutableTxs()

    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfExecutableTxs,
      executableTxs.size
    )

    val iter = executableTxs.iterator
    assertEquals(
      "Wrong first tx",
      account1ExecTransaction0.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong second tx",
      account1NonExecTransaction1.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong third tx",
      account1NonExecTransaction0.id(),
      iter.next().id
    )

    val account1ExecTransaction1 = createEIP1559Transaction(
      value,
      account1NonExecTransaction0.getNonce.add(BigInteger.ONE),
      account1KeyOpt
    )
    expectedNumOfTxs += 1
    expectedNumOfExecutableTxs += 1

    res = mempoolMap.add(account1ExecTransaction1)
    assertTrue("Adding third transaction to same account failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account1ExecTransaction0.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account1ExecTransaction0.id)
        .nonEmpty
    )

    executableTxs = mempoolMap.takeExecutableTxs()

    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfExecutableTxs,
      executableTxs.size
    )

    val account1NonExecTransaction2 = createEIP1559Transaction(
      value,
      account1NonExecTransaction0.getNonce.add(BigInteger.TEN),
      account1KeyOpt
    )
    expectedNumOfTxs += 1

    res = mempoolMap.add(account1NonExecTransaction2)
    assertTrue("Adding transaction to same account failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap.contains(ModifierId @@ account1ExecTransaction0.id)
    )
    assertTrue(
      "Mempool doesn't contain transaction",
      mempoolMap
        .getTransaction(ModifierId @@ account1ExecTransaction0.id)
        .nonEmpty
    )

    executableTxs = mempoolMap.takeExecutableTxs()

    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfExecutableTxs,
      executableTxs.size
    )

  }

  @Test
  def testAddSameNonce(): Unit = {

    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings(maxNonceGap = 20000))//For this test I don't care about maxNonceGap

    val account1InitialStateNonce = BigInteger.ZERO
    val value = BigInteger.TEN

    val nonExecNonce = BigInteger.TEN
    val nonExecGasFeeCap = BigInteger.valueOf(100)
    val nonExecGasTipCap = BigInteger.valueOf(80)
    val account1NonExecTransaction0 = createEIP1559Transaction(
      value,
      nonExecNonce,
      account1KeyOpt,
      nonExecGasFeeCap,
      nonExecGasTipCap
    )

    var res = mempoolMap.add(account1NonExecTransaction0)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get

    //Create some additional non exec txs
    (1 to 5).foreach(_ => {
      val nonce =
        BigInteger.valueOf(Random.nextInt(10000) + nonExecNonce.intValue() + 1)
      val tx = createEIP1559Transaction(value, nonce, account1KeyOpt)
      val res = mempoolMap.add(tx)
      assertTrue("Adding transaction failed", res.isSuccess)
      mempoolMap = res.get
    })

    val account1NonExecTransactionSameNonceLowerFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      nonExecNonce,
      account1KeyOpt,
      BigInteger.ONE,
      BigInteger.ONE
    )
    res = mempoolMap.add(account1NonExecTransactionSameNonceLowerFee)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertFalse(
      "Mempool contains transaction with lower gas fee",
      res.get.contains(
        ModifierId @@ account1NonExecTransactionSameNonceLowerFee.id
      )
    )

    val account1NonExecTransactionSameNonceSameFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      nonExecNonce,
      account1KeyOpt,
      account1NonExecTransaction0.getGasPrice,
      BigInteger.ONE
    )
    res = mempoolMap.add(account1NonExecTransactionSameNonceSameFee)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertFalse(
      "Mempool contains transaction with same gas fee",
      mempoolMap.contains(
        ModifierId @@ account1NonExecTransactionSameNonceSameFee.id
      )
    )

    val higherFee = account1NonExecTransaction0.getGasPrice.add(BigInteger.ONE)
    val account1NonExecTransactionSameNonceHigherFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      nonExecNonce,
      account1KeyOpt,
      higherFee,
      higherFee
    )
    res = mempoolMap.add(account1NonExecTransactionSameNonceHigherFee)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertTrue(
      "Mempool doesn't contain transaction with higher gas fee",
      mempoolMap.contains(
        ModifierId @@ account1NonExecTransactionSameNonceHigherFee.id
      )
    )
    assertFalse(
      "Mempool still contains old transaction with lower gas fee",
      mempoolMap.contains(ModifierId @@ account1NonExecTransaction0.id)
    )

    val account1ExecTransaction0 = createEIP1559Transaction(
      value,
      account1InitialStateNonce,
      account1KeyOpt,
      BigInteger.valueOf(100),
      BigInteger.valueOf(80)
    )
    res = mempoolMap.add(account1ExecTransaction0)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    //Create some additional exec txs
    (1 to 5).foreach(i => {
      val nonce = account1InitialStateNonce.add(BigInteger.valueOf(i))
      val tx = createEIP1559Transaction(value, nonce, account1KeyOpt)
      val res = mempoolMap.add(tx)
      assertTrue("Adding transaction failed", res.isSuccess)
      mempoolMap = res.get
    })

    val account1ExecTransactionSameNonceLowerFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      account1InitialStateNonce,
      account1KeyOpt,
      BigInteger.ONE,
      BigInteger.ONE
    )
    res = mempoolMap.add(account1ExecTransactionSameNonceLowerFee)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertFalse(
      "Mempool contains exec transaction with lower gas fee",
      mempoolMap.contains(
        ModifierId @@ account1ExecTransactionSameNonceLowerFee.id
      )
    )

    val account1ExecTransactionSameNonceSameFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      account1InitialStateNonce,
      account1KeyOpt,
      account1ExecTransaction0.getGasPrice,
      account1ExecTransaction0.getGasPrice
    )
    res = mempoolMap.add(account1ExecTransactionSameNonceSameFee)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertFalse(
      "Mempool contains transaction with same gas fee",
      mempoolMap.contains(
        ModifierId @@ account1ExecTransactionSameNonceSameFee.id
      )
    )

    val execTxHigherFee =
      account1ExecTransaction0.getGasPrice.add(BigInteger.ONE)
    val account1ExecTransactionSameNonceHigherFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      account1InitialStateNonce,
      account1KeyOpt,
      execTxHigherFee,
      execTxHigherFee
    )
    res = mempoolMap.add(account1ExecTransactionSameNonceHigherFee)
    assertTrue("Adding transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertTrue(
      "Mempool doesn't contain transaction with higher gas fee",
      mempoolMap.contains(
        ModifierId @@ account1ExecTransactionSameNonceHigherFee.id
      )
    )
    assertFalse(
      "Mempool still contains old transaction with lower gas fee",
      mempoolMap.contains(ModifierId @@ account1ExecTransaction0.id)
    )
  }

  @Test
  def testRemove(): Unit = {
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    val account1InitialStateNonce = BigInteger.ZERO
    val value = BigInteger.TEN
    val account1NonExecTransaction0 =
      createEIP1559Transaction(value, BigInteger.TWO, account1KeyOpt)

    var res = mempoolMap.remove(account1NonExecTransaction0)

    res = mempoolMap.remove(account1NonExecTransaction0)
    assertTrue("Removing a not existing transaction failed", res.isSuccess)
    mempoolMap = res.get

    var expectedNumOfTxs = 0
    mempoolMap.add(account1NonExecTransaction0)
    res = mempoolMap.remove(account1NonExecTransaction0)
    assertTrue("Removing transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )

    assertFalse(
      "Transaction is still in the mempool",
      mempoolMap.contains(ModifierId @@ account1NonExecTransaction0.id)
    )
    var executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfTxs,
      executableTxs.size
    )

    val account1ExecTransaction0 = createEIP1559Transaction(
      value,
      account1InitialStateNonce,
      account1KeyOpt
    )

    mempoolMap.add(account1ExecTransaction0)
    res = mempoolMap.remove(account1ExecTransaction0)
    assertTrue("Removing transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )

    assertFalse(
      "Transaction is still in the mempool",
      mempoolMap.contains(ModifierId @@ account1ExecTransaction0.id)
    )
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      expectedNumOfTxs,
      executableTxs.size
    )

    //Create some additional exec txs
    var txToRemove: EthereumTransaction = null
    (0 to 5).foreach(i => {
      val nonce = account1InitialStateNonce.add(BigInteger.valueOf(i))
      val tx = createEIP1559Transaction(value, nonce, account1KeyOpt)
      if (i == 3)
        txToRemove = tx
      val res = mempoolMap.add(tx)
      assertTrue("Adding transaction failed", res.isSuccess)
      mempoolMap = res.get
    })
    expectedNumOfTxs = 5
    res = mempoolMap.remove(txToRemove)
    assertTrue(s"Removing transaction failed $res", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals(
      "Wrong number of total transactions",
      expectedNumOfTxs,
      mempoolMap.values.size
    )

    assertFalse(
      "Transaction is still in the mempool",
      mempoolMap.contains(ModifierId @@ txToRemove.id)
    )
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      3,
      executableTxs.size
    )

    res = mempoolMap.add(txToRemove)
    executableTxs = mempoolMap.takeExecutableTxs()
    assertEquals(
      "Wrong number of executable transactions",
      6,
      executableTxs.size
    )

  }

  @Test
  def testTakeExecutableTxs(): Unit = {

    val initialStateNonce = BigInteger.ZERO
    Mockito.when(baseStateViewMock.getNextBaseFee).thenReturn(BigInteger.TEN)
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings(maxNonceGap = 2000))//For this test I don't care about nonce gap

    var listOfExecTxs = mempoolMap.takeExecutableTxs()
    assertTrue(
      "Wrong tx list size ",
      listOfExecTxs.isEmpty
    )

    var iter =  listOfExecTxs.iterator
    assertFalse(iter.hasNext)
    assertThrows[NoSuchElementException](iter.peek)
    assertThrows[NoSuchElementException](iter.removeAndSkipAccount())
    assertThrows[NoSuchElementException](iter.next())

    //Adding some txs in the mempool

    val value = BigInteger.TEN

    val account1ExecTransaction0 = createEIP1559Transaction(
      value,
      initialStateNonce,
      account1KeyOpt,
      gasFee = BigInteger.valueOf(13),
      priorityGasFee = BigInteger.valueOf(4)
    )
    var res = mempoolMap.add(account1ExecTransaction0)
    assertTrue(res.isSuccess)
    mempoolMap = res.get

    listOfExecTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong tx list size ", 1, listOfExecTxs.size)
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      listOfExecTxs.head.id
    )

    iter =  listOfExecTxs.iterator
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      iter.peek.id
    )
    assertEquals(
      "Peek should return always the same element ",
      account1ExecTransaction0.id(),
      iter.peek.id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      iter.removeAndSkipAccount().id
    )
    assertThrows[NoSuchElementException]("Pop should modify the iterator", iter.removeAndSkipAccount())

    val account1NonExecTransaction0 = createEIP1559Transaction(
      value,
      BigInteger.valueOf(1000),
      account1KeyOpt,
      gasFee = BigInteger.ONE,
      priorityGasFee = BigInteger.ONE
    )
    res = mempoolMap.add(account1NonExecTransaction0)
    assertTrue(res.isSuccess)
    mempoolMap = res.get
    listOfExecTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong tx list size ", 1, listOfExecTxs.size)
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      listOfExecTxs.head.id
    )

    iter = listOfExecTxs.iterator
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      iter.peek.id
    )
    assertEquals(
      "Peek should return always the same element ",
      account1ExecTransaction0.id(),
      iter.peek.id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      iter.removeAndSkipAccount().id
    )
    assertThrows[NoSuchElementException]("Pop should modify the iterator", iter.removeAndSkipAccount())

    //Adding other Txs to the same account and verify they are returned ordered by nonce and not by gas price

    val account1ExecTransaction1 = createEIP1559Transaction(
      value,
      account1ExecTransaction0.getNonce.add(BigInteger.ONE),
      account1KeyOpt,
      gasFee = BigInteger.valueOf(20),
      priorityGasFee = BigInteger.ONE
    )
    res = mempoolMap.add(account1ExecTransaction1)
    assertTrue(res.isSuccess)
    mempoolMap = res.get
    val account1ExecTransaction2 = createEIP1559Transaction(
      value,
      account1ExecTransaction1.getNonce.add(BigInteger.ONE),
      account1KeyOpt,
      gasFee = BigInteger.valueOf(1100),
      priorityGasFee = BigInteger.valueOf(110)
    )
    res = mempoolMap.add(account1ExecTransaction2)
    assertTrue(res.isSuccess)
    mempoolMap = res.get

    listOfExecTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong tx list size ", 3, listOfExecTxs.size)
    iter = listOfExecTxs.iterator
    assertEquals(
      "Wrong tx by peek ",
      account1ExecTransaction0.id(),
      iter.peek.id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx by peek ",
      account1ExecTransaction1.id(),
      iter.peek.id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction1.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx by peek ",
      account1ExecTransaction2.id(),
      iter.peek.id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction2.id(),
      iter.next().id
    )
    assertFalse("Iterator still finds txs", iter.hasNext)

    iter = listOfExecTxs.iterator
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id,
      iter.removeAndSkipAccount().id
    )
    assertThrows[NoSuchElementException]("Pop should skip all txs from the same account", iter.removeAndSkipAccount())


    //Create txs for other accounts and verify that the list is ordered by nonce and gas price
    //The expected order is: tx3_0, tx3_1, tx3_2, tx2_0, tx1_0, tx2_1, tx2_2, tx1_1, tx1_2

    val account2ExecTransaction0 = createLegacyTransaction(
      value,
      initialStateNonce,
      account2KeyOpt,
      gasPrice = BigInteger.valueOf(15)
    )

    val account2ExecTransaction1 = createEIP1559Transaction(
      value,
      account2ExecTransaction0.getNonce.add(BigInteger.ONE),
      account2KeyOpt,
      gasFee = BigInteger.valueOf(12),
      priorityGasFee = BigInteger.valueOf(12)
    )
    val account2ExecTransaction2 = createEIP1559Transaction(
      value,
      account2ExecTransaction1.getNonce.add(BigInteger.ONE),
      account2KeyOpt,
      gasFee = BigInteger.valueOf(203),
      priorityGasFee = BigInteger.valueOf(190)
    )
    res = mempoolMap.add(account2ExecTransaction1)
    assertTrue(res.isSuccess)
    mempoolMap = res.get
    res = mempoolMap.add(account2ExecTransaction2)
    assertTrue(res.isSuccess)
    mempoolMap = res.get
    res = mempoolMap.add(account2ExecTransaction0)
    assertTrue(res.isSuccess)
    mempoolMap = res.get

    val account3ExecTransaction0 = createLegacyTransaction(
      value,
      initialStateNonce,
      account3KeyOpt,
      gasPrice = BigInteger.valueOf(20)
    )


    val account3ExecTransaction1 = createEIP1559Transaction(
      value,
      account3ExecTransaction0.getNonce.add(BigInteger.ONE),
      account3KeyOpt,
      gasFee = BigInteger.valueOf(1200),
      priorityGasFee = BigInteger.valueOf(200)
    )
    val account3ExecTransaction2 = createEIP1559Transaction(
      value,
      account3ExecTransaction1.getNonce.add(BigInteger.ONE),
      account3KeyOpt,
      gasFee = BigInteger.valueOf(16),
      priorityGasFee = BigInteger.valueOf(6)
    )
    res = mempoolMap.add(account3ExecTransaction0)
    assertTrue(res.isSuccess)
    mempoolMap = res.get
    res = mempoolMap.add(account3ExecTransaction2)
    assertTrue(res.isSuccess)
    mempoolMap = res.get
    res = mempoolMap.add(account3ExecTransaction1)
    assertTrue(res.isSuccess)
    mempoolMap = res.get

    listOfExecTxs = mempoolMap.takeExecutableTxs()
    assertEquals("Wrong tx list size ", 9, listOfExecTxs.size)
    iter = listOfExecTxs.iterator
    assertEquals(
      "Wrong tx ",
      account3ExecTransaction0.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account3ExecTransaction1.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account3ExecTransaction2.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account2ExecTransaction0.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction0.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account2ExecTransaction1.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account2ExecTransaction2.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction1.id(),
      iter.next().id
    )
    assertEquals(
      "Wrong tx ",
      account1ExecTransaction2.id(),
      iter.next().id
    )

    assertFalse(iter.hasNext)


    iter = listOfExecTxs.iterator
    assertEquals(
      "Wrong tx ",
      account3ExecTransaction0.id(),
      iter.removeAndSkipAccount().id
    )

    assertEquals(
      "Wrong tx by peek after pop",
      account2ExecTransaction0.id(),
      iter.peek.id
    )
    assertEquals(
      "Wrong tx ",
      account2ExecTransaction0.id(),
      iter.next.id
    )
    assertEquals(
      "Wrong tx by peek after next",
      account1ExecTransaction0.id(),
      iter.peek.id
    )
    assertEquals(
      "Wrong tx",
      account1ExecTransaction0.id(),
      iter.removeAndSkipAccount().id
    )
    assertEquals(
      "Wrong tx ",
      account2ExecTransaction1.id(),
      iter.removeAndSkipAccount().id
    )
    assertFalse(iter.hasNext)
    assertThrows[NoSuchElementException]("Pop should skip all txs from the same account", iter.removeAndSkipAccount())

  }

  @Test
  def txSizeInSlotTest(): Unit = {

    val invalidNegativeSize = -1L
    assertThrows[IllegalArgumentException]("Negative size values are not allowed", MempoolMap.sizeToSlot(invalidNegativeSize))

    var size: Long = 0
    assertEquals("Wrong number of slots", 0,  MempoolMap.sizeToSlot(size))

    size = 1
    assertEquals("Wrong number of slots", 1,  MempoolMap.sizeToSlot(size))

    size = MempoolMap.TxSlotSize
    assertEquals("Wrong number of slots", 1,  MempoolMap.sizeToSlot(size))

    size = MempoolMap.TxSlotSize + 1
    assertEquals("Wrong number of slots", 2,  MempoolMap.sizeToSlot(size))

    size = 2 * MempoolMap.TxSlotSize + 1
    assertEquals("Wrong number of slots", 3, MempoolMap.sizeToSlot(size))


    val tx = mock[EthereumTransaction]
    Mockito.when(tx.size()).thenReturn(MempoolMap.TxSlotSize / 2)
    assertEquals("Wrong number of slots for transaction", 1,  MempoolMap.txSizeInSlot(tx))

    val expectedNumOfSlots = 3
    Mockito.when(tx.size()).thenReturn( expectedNumOfSlots * MempoolMap.TxSlotSize - 1)
    assertEquals("Wrong number of slots for transaction", expectedNumOfSlots, MempoolMap.txSizeInSlot(tx))

  }

  @Test
  def testAddTooBigTx(): Unit = {

    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    val normalTx = createMockTxWithSize(MempoolMap.MaxTxSize)

    val res = mempoolMap.add(normalTx)
    assertTrue(s"Adding transaction failed $res", res.isSuccess)

    mempoolMap = res.get

    val tooBigTx = createMockTxWithSize(MempoolMap.MaxTxSize + 1)
    mempoolMap.add(tooBigTx) match {
      case Success(_) => fail("Adding transaction too big should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[TxOversizedException])
    }

  }

  @Test
  def testAddTxWithNonceGapTooBig(): Unit = {
    val MaxNonceGap = 11
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings(maxNonceGap = MaxNonceGap))

    val stateNonce = BigInteger.valueOf(153)
    val validTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = stateNonce.add(BigInteger.valueOf(MaxNonceGap - 1)))
    when(accountStateViewMock.getNonce(validTx.getFrom.address())).thenReturn(stateNonce)

    val res = mempoolMap.add(validTx)
    assertTrue(s"Adding transaction failed $res", res.isSuccess)

    mempoolMap = res.get

    val nonceGapTooBigTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = stateNonce.add(BigInteger.valueOf(MaxNonceGap)))
    when(accountStateViewMock.getNonce(nonceGapTooBigTx.getFrom.address())).thenReturn(stateNonce)

    assertThrows[NonceGapTooWideException]("Adding transaction with nonce gap too big should have thrown an NonceGapTooWideException", mempoolMap.add(nonceGapTooBigTx).get)
  }


  private def createMockTxWithSize(size: Long): EthereumTransaction = {
    val dummyTx = createEIP1559Transaction(BigInteger.ONE)
    val tx = Mockito.spy[EthereumTransaction](dummyTx)
    Mockito.when(tx.size()).thenReturn(size)
    tx
  }
}
