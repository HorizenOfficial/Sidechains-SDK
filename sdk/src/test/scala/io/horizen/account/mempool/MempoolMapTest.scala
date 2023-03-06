package io.horizen.account.mempool

import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.mempool.exception.{AccountMemPoolOutOfBoundException, NonceGapTooWideException, TransactionReplaceUnderpricedException, TxOversizedException}
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import io.horizen.account.state.{AccountStateReader, AccountStateReaderProvider, BaseStateReaderProvider}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.state.BaseStateReader
import io.horizen.evm.Address
import io.horizen.{AccountMempoolSettings, SidechainTypes}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.util.ModifierId

import java.math.BigInteger
import java.nio.charset.StandardCharsets
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

  val account1KeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest1".getBytes(StandardCharsets.UTF_8)))
  val account2KeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest2".getBytes(StandardCharsets.UTF_8)))
  val account3KeyOpt: Option[PrivateKeySecp256k1] = Some(PrivateKeySecp256k1Creator.getInstance().generateSecret("mempoolmaptest3".getBytes(StandardCharsets.UTF_8)))
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
    assertFalse(mempoolMap.canPayHigherFee(legacyTxHigherPrice, eip1559TxHFeeLTip))
    assertFalse(mempoolMap.canPayHigherFee(legacyTxHigherPrice, eip1559TxHFeeLTip))
    assertTrue(mempoolMap.canPayHigherFee(legacyTxHigherPrice, eip1559TxLFeeLTip))
    assertTrue(mempoolMap.canPayHigherFee(eip1559TxHFeeHTip, legacyTxLowerPrice))
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
    assertEquals("Wrong mem pool size in slots", 0, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

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
    assertEquals("Wrong mem pool size in slots", 1, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

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
    assertEquals("Wrong mem pool size in slots", 2, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

    val account2InitialStateNonce = BigInteger.valueOf(4576)
    val account2ExecTransaction0 = setupMockSizeInSlotsToTx(createEIP1559Transaction(
      value,
      account2InitialStateNonce,
      account2KeyOpt
    ),
      2
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
    assertEquals("Wrong mem pool size in slots", 4, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

  }

  @Test
  def testAddNonExecutableTx(): Unit = {

    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    var expectedNumOfTxs = 0
    var expectedNumOfExecutableTxs = 0

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
    assertEquals("Wrong mem pool size in slots", 1, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(false).size)


    res = mempoolMap.add(account1NonExecTransaction0)
    assertTrue("Adding twice the same tx should not fail", res.isSuccess)
    assertEquals("Wrong mem pool size in slots", 1, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(false).size)

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
    assertEquals("Wrong mem pool size in slots", 2, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(false).size)

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
    assertEquals("Wrong mem pool size in slots", 3, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)


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
    assertEquals("Wrong mem pool size in slots", 4, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)


    val account1NonExecTransaction2 = setupMockSizeInSlotsToTx(createEIP1559Transaction(
      value,
      account1NonExecTransaction0.getNonce.add(BigInteger.TEN),
      account1KeyOpt
    ),
      3
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
    assertEquals("Wrong mem pool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfExecutableTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)

  }

  @Test
  def testAddSameNonce(): Unit = {

    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings(maxNonceGap = 20000, maxAccountSlots = 20)) //For this test I don't care about maxNonceGap

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
    assertEquals("Wrong mempool size in slots", 6, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 6, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 6, mempoolMap.mempoolTransactions(false).size)

    val account1NonExecTransactionSameNonceLowerFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      nonExecNonce,
      account1KeyOpt,
      BigInteger.ONE,
      BigInteger.ONE
    )
    res = mempoolMap.add(account1NonExecTransactionSameNonceLowerFee)

    res match {
      case Success(_) => fail("Adding underpriced transaction should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[TransactionReplaceUnderpricedException])
    }

    assertFalse(
      "Mempool contains transaction with lower gas fee",
      mempoolMap.contains(
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
    res match {
      case Success(_) => fail("Adding underpriced transaction should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[TransactionReplaceUnderpricedException])
    }
    assertFalse(
      "Mempool contains transaction with same gas fee",
      mempoolMap.contains(
        ModifierId @@ account1NonExecTransactionSameNonceSameFee.id
      )
    )

    val higherFee = account1NonExecTransaction0.getGasPrice.add(BigInteger.ONE)
    val account1NonExecTransactionSameNonceHigherFee = setupMockSizeInSlotsToTx(createEIP1559Transaction(
      BigInteger.valueOf(123),
      nonExecNonce,
      account1KeyOpt,
      higherFee,
      higherFee
    ),
      4)
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
    assertEquals("Wrong mempool size in slots", 9, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 9, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 6, mempoolMap.mempoolTransactions(false).size)

    val account1ExecTransaction0 = setupMockSizeInSlotsToTx(createEIP1559Transaction(
      value,
      account1InitialStateNonce,
      account1KeyOpt,
      BigInteger.valueOf(100),
      BigInteger.valueOf(80)
    ),
      3)
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
    assertEquals("Wrong mempool size in slots", 17, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 9, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 6, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 6, mempoolMap.mempoolTransactions(false).size)


    val account1ExecTransactionSameNonceLowerFee = createEIP1559Transaction(
      BigInteger.valueOf(123),
      account1InitialStateNonce,
      account1KeyOpt,
      BigInteger.ONE,
      BigInteger.ONE
    )
    res = mempoolMap.add(account1ExecTransactionSameNonceLowerFee)
    res match {
      case Success(_) => fail("Adding underpriced transaction should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[TransactionReplaceUnderpricedException])
    }

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
    res match {
      case Success(_) => fail("Adding underpriced transaction should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[TransactionReplaceUnderpricedException])
    }
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
    assertEquals("Wrong mempool size in slots", 15, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 9, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 6, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 6, mempoolMap.mempoolTransactions(false).size)

  }

  @Test
  def testRemove(): Unit = {
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())

    val account1InitialStateNonce = BigInteger.ZERO
    val value = BigInteger.TEN
    val account1NonExecTransaction0 =
      createEIP1559Transaction(value, BigInteger.TWO, account1KeyOpt)

    var res = mempoolMap.remove(account1NonExecTransaction0)
    assertTrue("Removing a not existing transaction failed", res.isSuccess)
    assertEquals("Wrong mem pool size in slots", 0, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)


    mempoolMap = res.get

    var expectedNumOfTxs = 0
    mempoolMap.add(account1NonExecTransaction0)
    res = mempoolMap.remove(account1NonExecTransaction0)
    assertTrue("Removing transaction failed", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mem pool size in slots", 0, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
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
    assertEquals("Wrong mem pool size in slots", 0, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", expectedNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)
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
      var tx = createEIP1559Transaction(value, nonce, account1KeyOpt)
      if (i == 3) {
        tx = setupMockSizeInSlotsToTx(tx, 2)
        txToRemove = tx
      }
      val res = mempoolMap.add(tx)
      assertTrue("Adding transaction failed", res.isSuccess)
      mempoolMap = res.get
    })
    expectedNumOfTxs = 5
    res = mempoolMap.remove(txToRemove)
    assertTrue(s"Removing transaction failed $res", res.isSuccess)
    mempoolMap = res.get
    assertEquals("Wrong mem pool size", expectedNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mem pool size in slots", 5, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 2, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 3, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)

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
    assertEquals("Wrong mem pool size", 6, mempoolMap.size)
    assertEquals("Wrong mem pool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 6, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

  }

  @Test
  def testTakeExecutableTxs(): Unit = {

    val initialStateNonce = BigInteger.ZERO
    Mockito.when(baseStateViewMock.getNextBaseFee).thenReturn(BigInteger.TEN)
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings(maxNonceGap = 2000)) //For this test I don't care about nonce gap

    var listOfExecTxs = mempoolMap.takeExecutableTxs()
    assertTrue(
      "Wrong tx list size ",
      listOfExecTxs.isEmpty
    )

    var iter = listOfExecTxs.iterator
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
  def testTxSizeInSlot(): Unit = {

    val invalidNegativeSize = -1L
    assertThrows[IllegalArgumentException]("Negative size values are not allowed", MempoolMap.sizeToSlot(invalidNegativeSize))

    var size: Long = 0
    assertEquals("Wrong number of slots", 0, MempoolMap.sizeToSlot(size))

    size = 1
    assertEquals("Wrong number of slots", 1, MempoolMap.sizeToSlot(size))

    size = MempoolMap.TxSlotSize
    assertEquals("Wrong number of slots", 1, MempoolMap.sizeToSlot(size))

    size = MempoolMap.TxSlotSize + 1
    assertEquals("Wrong number of slots", 2, MempoolMap.sizeToSlot(size))

    size = 2 * MempoolMap.TxSlotSize + 1
    assertEquals("Wrong number of slots", 3, MempoolMap.sizeToSlot(size))


    val tx = mock[EthereumTransaction]
    Mockito.when(tx.size()).thenReturn(MempoolMap.TxSlotSize / 2)
    assertEquals("Wrong number of slots for transaction", 1, MempoolMap.txSizeInSlot(tx))

    val expectedNumOfSlots = 3
    Mockito.when(tx.size()).thenReturn(expectedNumOfSlots * MempoolMap.TxSlotSize - 1)
    assertEquals("Wrong number of slots for transaction", expectedNumOfSlots, MempoolMap.txSizeInSlot(tx))

  }

  @Test
  def testAddTooBigTx(): Unit = {

    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, AccountMempoolSettings())
    val normalTx = createMockTxWithSize(size = MempoolMap.MaxTxSize)

    val res = mempoolMap.add(normalTx)
    assertTrue(s"Adding transaction failed $res", res.isSuccess)

    mempoolMap = res.get

    val tooBigTx = createMockTxWithSize(size = MempoolMap.MaxTxSize + 1)
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


  @Test
  def testAddAccountSizeCheck(): Unit = {

    val MaxSlotsPerAccount = 10
    val mempoolSettings: AccountMempoolSettings = AccountMempoolSettings(maxAccountSlots = MaxSlotsPerAccount)
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)

    //Test 1: fill an account with exec txs of 1 slot each. Verify that adding an additional exec tx fails
    (0 until MaxSlotsPerAccount).foreach(nonce => assertTrue("Adding transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt)).isSuccess))
    assertEquals("Wrong number of txs in mempool", MaxSlotsPerAccount, mempoolMap.size)

    var exceedingTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(MaxSlotsPerAccount), keyOpt = account1KeyOpt)
    assertEquals("Wrong account size in slots", MaxSlotsPerAccount, mempoolMap.getAccountSlots(exceedingTx.getFrom))

    mempoolMap.add(exceedingTx) match {
      case Success(_) => fail("Adding exec transaction to a full account should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[AccountMemPoolOutOfBoundException])
    }
    assertFalse("Rejected tx was added to the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id))

    //Test 2: same as test 1 but with a non exec tx
    exceedingTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(MaxSlotsPerAccount + 1), keyOpt = account1KeyOpt)

    mempoolMap.add(exceedingTx) match {
      case Success(_) => fail("Adding non exec transaction to a full account should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[AccountMemPoolOutOfBoundException])
    }
    assertFalse("Rejected tx was added to the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id))

    //Test 3: Create 2 non exec txs of 4 slots each. Verify that adding an additional exec tx of 4 slots fails
    //Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    (1 until 3).foreach(nonce =>
      assertTrue(
        "Adding transaction failed",
        mempoolMap.add(
          setupMockSizeInSlotsToTx(
            createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt),
            4
          )
        ).isSuccess
      )
    )
    assertEquals("Wrong number of txs in mempool", 2, mempoolMap.size)
    assertEquals("Wrong account size in slots", 8, mempoolMap.getAccountSlots(exceedingTx.getFrom))

    exceedingTx = setupMockSizeInSlotsToTx(
      createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account1KeyOpt),
      4
    )

    mempoolMap.add(exceedingTx) match {
      case Success(_) => fail("Adding transaction to a full account should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[AccountMemPoolOutOfBoundException])
    }
    assertFalse("Rejected tx was added to the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id))


    //Test 4: fill an account with exec and non exec txs of 1 slot each. Verify that trying to replace an existing tx with
    // a bigger one fails and the old tx remains in the mempool.
    //Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    var txToReplace = createEIP1559Transaction(value = BigInteger.ONE,
      nonce = BigInteger.ZERO,
      keyOpt = account1KeyOpt,
      gasFee = BigInteger.valueOf(20000),
      priorityGasFee = BigInteger.valueOf(20000),
    )
    assertTrue("Adding exec transaction failed", mempoolMap.add(txToReplace).isSuccess)

    (1 until 4).foreach(nonce => assertTrue("Adding exec transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt)).isSuccess))
    (5 to MaxSlotsPerAccount).foreach(nonce => assertTrue("Adding non exec transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt)).isSuccess))
    assertEquals("Wrong number of txs in mempool", MaxSlotsPerAccount, mempoolMap.size)
    assertEquals("Wrong account size in slots", MaxSlotsPerAccount, mempoolMap.getAccountSlots(exceedingTx.getFrom))


    //First create a tx with the same nonce of an existing one but with greater gas fee, tip (for allowing replacing) and
    // same size. Verify that it replaces the old one

    exceedingTx = createEIP1559Transaction(value = BigInteger.TWO,
      nonce = txToReplace.getNonce,
      keyOpt = account1KeyOpt,
      gasFee = txToReplace.getMaxFeePerGas.add(BigInteger.TEN),
      priorityGasFee = txToReplace.getMaxPriorityFeePerGas.add(BigInteger.TEN),
    )

    assertTrue("Replacing transaction failed", mempoolMap.add(exceedingTx).isSuccess)

    assertEquals("Wrong number of txs in the mempool", MaxSlotsPerAccount, mempoolMap.size)
    assertFalse("Old tx is still in the mempool", mempoolMap.contains(ModifierId @@ txToReplace.id()))
    assertTrue("Exceeding tx is not in the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id()))


    txToReplace = exceedingTx

    //Create a tx with the same nonce of an existing one but with greater gas fee, tip (for allowing replacing) and size
    // (so it should be rejected)

    exceedingTx = setupMockSizeInSlotsToTx(
      createEIP1559Transaction(value = BigInteger.TWO,
        nonce = txToReplace.getNonce,
        keyOpt = account1KeyOpt,
        gasFee = txToReplace.getMaxFeePerGas.add(BigInteger.TEN),
        priorityGasFee = txToReplace.getMaxPriorityFeePerGas.add(BigInteger.TEN),
      ),
      4
    )

    mempoolMap.add(exceedingTx) match {
      case Success(_) => fail("Replacing a transaction with a bigger one to a full account should have failed")
      case Failure(e) => assertTrue(s"Wrong exception type: ${e.getClass}", e.isInstanceOf[AccountMemPoolOutOfBoundException])
    }
    assertTrue("Old tx is no more in the mempool", mempoolMap.contains(ModifierId @@ txToReplace.id()))
    assertFalse("Exceeding tx is in the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id()))

  }


  @Test
  def testAddMempoolSizeCheck(): Unit = {

    val MaxMempoolSlots = 10
    val mempoolSettings: AccountMempoolSettings = AccountMempoolSettings(maxAccountSlots = MaxMempoolSlots,
                                                                        maxMemPoolSlots = MaxMempoolSlots,
                                                                        maxNonExecMemPoolSlots = MaxMempoolSlots - 1)
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    assertEquals("Wrong mempool size in slots", 0, mempoolMap.getMempoolSizeInSlots)

    //Test 1: fill an account with exec txs of 1 slot each from 2 accounts. Verify that adding an additional exec tx
    // will evict the oldest already present
    val totalNumOfTxs = MaxMempoolSlots
    val listOfTxsAccount1 = (0 until 5).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt))
    listOfTxsAccount1.foreach(tx => assertTrue("Adding transaction failed", mempoolMap.add(tx).isSuccess))
    val numOfTxsAccount3 = totalNumOfTxs - listOfTxsAccount1.size
    val listOfTxsAccount3 = (0 until numOfTxsAccount3).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account3KeyOpt))
    listOfTxsAccount3.foreach(tx => assertTrue("Adding transaction failed", mempoolMap.add(tx).isSuccess))

    assertEquals("Wrong number of txs in mempool", totalNumOfTxs, mempoolMap.size)
    assertEquals("Wrong account 1 size in slots", listOfTxsAccount1.size, mempoolMap.getAccountSlots(account1KeyOpt.get.publicImage()))
    assertEquals("Wrong account 3 size in slots", listOfTxsAccount3.size, mempoolMap.getAccountSlots(account3KeyOpt.get.publicImage()))
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong number of exec txs", totalNumOfTxs, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 0, mempoolMap.mempoolTransactions(false).size)

    var oldestTx = listOfTxsAccount1.head
    var exceedingTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account2KeyOpt)

    mempoolMap = mempoolMap.add(exceedingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction to a full mempool failed with exception $e", e)
    }
    assertEquals("Wrong number of txs in mempool", totalNumOfTxs, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertTrue("Exceeding tx wasn't added to the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id))
    assertFalse("Oldest tx wasn't removed from the mempool", mempoolMap.contains(ModifierId @@ oldestTx.id))
    //Check that after having evicted the oldest tx, all the remaining ones of account 1 have become non executable
    assertEquals("Wrong number of exec txs", listOfTxsAccount3.size + 1, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", listOfTxsAccount1.size - 1, mempoolMap.mempoolTransactions(false).size)

    //Test 2: same as test 1 but with exceeding tx with a size corresponding to 4 slots =>
    // 4 txs will be evicted
    exceedingTx = setupMockSizeInSlotsToTx(
      createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.TWO, keyOpt = account2KeyOpt),
      4
    )

    mempoolMap = mempoolMap.add(exceedingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction to a full mempool failed with exception $e", e)
    }
    assertEquals("Wrong number of txs in mempool", totalNumOfTxs - 3, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertTrue("Rejected tx wasn't added to the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id))
    listOfTxsAccount1.slice(1, 5).foreach(tx =>
      assertFalse("Oldest tx wasn't removed from the mempool", mempoolMap.contains(ModifierId @@ tx.id))
    )


    //Test 3: Create 2 txs of 4 slots each and 2 txs of 1 slot. Verify that adding an additional tx of 1 slot will
    //evict the first 4 slots tx.
    //Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    oldestTx = setupMockSizeInSlotsToTx(
      createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account1KeyOpt),
      4
    )
    assertTrue("Adding transaction failed",
      mempoolMap.add(oldestTx).isSuccess
    )
    assertTrue("Adding transaction failed",
      mempoolMap.add(
        setupMockSizeInSlotsToTx(
          createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ONE, keyOpt = account2KeyOpt),
          4
        )
      ).isSuccess
    )
    assertTrue("Adding transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account3KeyOpt)).isSuccess
    )
    assertTrue("Adding transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.TWO, keyOpt = account3KeyOpt)).isSuccess
    )

    assertEquals("Wrong number of txs in mempool", 4, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 10, mempoolMap.getMempoolSizeInSlots)

    exceedingTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ONE, keyOpt = account3KeyOpt)

    mempoolMap = mempoolMap.add(exceedingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction to a full mempool failed with exception $e", e)
    }
    assertEquals("Wrong number of txs in mempool", 4, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 7, mempoolMap.getMempoolSizeInSlots)
    assertTrue("Rejected tx wasn't added to the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id))
    assertFalse("Oldest tx wasn't removed from the mempool", mempoolMap.contains(ModifierId @@ oldestTx.id))

  }

  @Test
  def testReplaceTxMempoolSizeCheck(): Unit = {

    // Test 1: fill the mempool with exec and non exec txs. Verify that trying to replace an existing tx with
    // a bigger one will evict oldest txs.
    val MaxMempoolSlots = 10
    val mempoolSettings: AccountMempoolSettings = AccountMempoolSettings(maxAccountSlots = MaxMempoolSlots,
                                                                          maxMemPoolSlots = MaxMempoolSlots, maxNonExecMemPoolSlots = MaxMempoolSlots - 2)
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    val txToReplace1 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.ONE,
      nonce = BigInteger.ZERO,
      keyOpt = account1KeyOpt,
      gasFee = BigInteger.valueOf(20000),
      priorityGasFee = BigInteger.valueOf(20000),
    ),
      2
    )
    assertTrue("Adding exec transaction failed", mempoolMap.add(txToReplace1).isSuccess)

    val oldestTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(1), keyOpt = account1KeyOpt)
    assertTrue("Adding exec transaction failed", mempoolMap.add(oldestTx).isSuccess)
    (0 to 1).foreach(nonce => assertTrue("Adding exec transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account3KeyOpt)).isSuccess))

    val txToReplace2 = createEIP1559Transaction(value = BigInteger.ONE,
      nonce = BigInteger.valueOf(5),
      keyOpt = account2KeyOpt,
      gasFee = BigInteger.valueOf(20000),
      priorityGasFee = BigInteger.valueOf(20000),
    )
    assertTrue("Adding transaction failed", mempoolMap.add(txToReplace2).isSuccess)

    (6 until MaxMempoolSlots).foreach(nonce => assertTrue("Adding non exec transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account2KeyOpt)).isSuccess))
    assertEquals("Wrong number of txs in mempool", MaxMempoolSlots - 1, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", MaxMempoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 5, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 4, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 5, mempoolMap.mempoolTransactions(false).size)

    //In the mempool now we have the following txs (in chronological order): A1_0 (of 2 slots), A1_1, A3_0, A3_1, A2_5, A2_6, A2_7,
    //A2_8, A2_9
    //First create a tx with the same nonce of an existing one but with same gas fee and tip (so it can't replace the old one)
    // but greater size. Verify that it is rejected and no txs are evicted.

    var exceedingTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TWO,
      nonce = txToReplace1.getNonce,
      keyOpt = account1KeyOpt,
      gasFee = txToReplace1.getMaxFeePerGas,
      priorityGasFee = txToReplace1.getMaxPriorityFeePerGas,
    ),
      3
    )

    assertTrue("Transaction should have been rejected", mempoolMap.add(exceedingTx).isFailure)
    assertEquals("Wrong number of txs in the mempool", MaxMempoolSlots - 1, mempoolMap.size)
    assertTrue("Old tx is no more in the mempool", mempoolMap.contains(ModifierId @@ txToReplace1.id()))
    assertFalse("Exceeding tx is in the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id()))

    //Create a tx with the same nonce of an existing one but with greater gas fee, tip (for allowing replacing) and
    // smaller size. Verify that it replaces the old one and no txs are evicted.

    exceedingTx = createEIP1559Transaction(value = BigInteger.TWO,
      nonce = txToReplace1.getNonce,
      keyOpt = account1KeyOpt,
      gasFee = txToReplace1.getMaxFeePerGas.add(BigInteger.TEN),
      priorityGasFee = txToReplace1.getMaxPriorityFeePerGas.add(BigInteger.TEN),
    )

    assertTrue("Replacing transaction failed", mempoolMap.add(exceedingTx).isSuccess)
    assertEquals("Wrong number of txs in the mempool", MaxMempoolSlots - 1, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", MaxMempoolSlots - 1, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 5, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 4, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 5, mempoolMap.mempoolTransactions(false).size)
    assertFalse("Old tx is still in the mempool", mempoolMap.contains(ModifierId @@ txToReplace1.id()))
    assertTrue("Exceeding tx is not in the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id()))


    //After first replace, in the mempool we have the following txs (in chronological order):
    // A1_1, A3_0, A3_1, A2_5, A2_6, A2_7, A2_8, A2_9, A1_0. All are of 1 slot.
    //Create a tx with the same nonce of an existing one (A2_5) but with greater gas fee, tip (for allowing replacing) and size.
    //Verify that enough txs to make room for the new one are evicted.

    exceedingTx = setupMockSizeInSlotsToTx(
      createEIP1559Transaction(value = BigInteger.TWO,
        nonce = txToReplace2.getNonce,
        keyOpt = account2KeyOpt,
        gasFee = txToReplace2.getMaxFeePerGas.add(BigInteger.TEN),
        priorityGasFee = txToReplace2.getMaxPriorityFeePerGas.add(BigInteger.TEN),
      ),
      3
    )


    mempoolMap = mempoolMap.add(exceedingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction to a full mempool failed with exception $e", e)
    }
    assertEquals("Wrong number of txs in mempool", 8, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 10, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 7, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 3, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 5, mempoolMap.mempoolTransactions(false).size)

    assertTrue("Exceeding tx wasn't added to the mempool", mempoolMap.contains(ModifierId @@ exceedingTx.id))
    assertFalse("Oldest tx wasn't removed from the mempool", mempoolMap.contains(ModifierId @@ oldestTx.id))


    //Corner case: try to replace an existing tx with another one with bigger size and mempool full. The existing tx
    // is the oldest so it is a candidate to be evicted. Actually, for the current implementation this is not a problem,
    // because first the tx is replaced (so the old one is no more in the mempool) then the mempool is freed. This test
    // is kept as a safe guard in case the implementation is changed in the future

    // Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)

    //Fill the mempool with exec txs from the same account
    val listOfTxs = (0 until MaxMempoolSlots - 1).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt))
    listOfTxs.foreach(tx => assertTrue("Adding transaction failed", mempoolMap.add(tx).isSuccess))
    assertTrue("Adding transaction failed", mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account2KeyOpt)).isSuccess)

    assertEquals("Wrong number of txs in mempool", MaxMempoolSlots, mempoolMap.size)
    assertEquals("Wrong account size in slots", MaxMempoolSlots - 1, mempoolMap.getAccountSlots(account1KeyOpt.get.publicImage()))
    assertEquals("Wrong mempool size in slots", MaxMempoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong number of executable txs", MaxMempoolSlots, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non executable txs", 0, mempoolMap.mempoolTransactions(false).size)


    val txToReplace = listOfTxs.head
    val replacingTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.ONE,
      nonce = txToReplace.getNonce,
      gasFee = txToReplace.getMaxFeePerGas.add(BigInteger.TEN),
      priorityGasFee = txToReplace.getMaxPriorityFeePerGas.add(BigInteger.TEN),
      keyOpt = account1KeyOpt),
      2 //2 slots => 2 txs to be removed
    )

    mempoolMap = mempoolMap.add(replacingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction to a full mempool failed with exception $e", e)
    }
    assertEquals("Wrong number of txs in mempool", MaxMempoolSlots - 1, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", MaxMempoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertTrue("Replacing tx wasn't added to the mempool", mempoolMap.contains(ModifierId @@ replacingTx.id))
    assertFalse("Tx to be replaced wasn't removed from the mempool", mempoolMap.contains(ModifierId @@ txToReplace.id))
    assertFalse("Second oldest tx wasn't removed from the mempool", mempoolMap.contains(ModifierId @@ listOfTxs(1).id))

    //Check that after having evicted the oldest 2 txs, all the remaining ones have become non executable
    assertEquals("Replacing tx should be executable", 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Remaining txs should be non executable", MaxMempoolSlots - 3, mempoolMap.mempoolTransactions(false).size)

  }


  @Test
  def testNonExecSizeCheck(): Unit = {
    val mempoolSettings = AccountMempoolSettings(maxAccountSlots = 10, maxMemPoolSlots = 10, maxNonExecMemPoolSlots = 4)
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)

    // Test 1: add one executable and 3 non executable transactions and check that when the third
    //tx is added, the oldest non exec tx is removed
    val execTx0 = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account1KeyOpt)
    assertTrue("Adding transaction failed", mempoolMap.add(execTx0).isSuccess)
    val nonExecTx2 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.TWO, keyOpt = account1KeyOpt), 2)
    assertTrue("Adding non exec transaction failed", mempoolMap.add(nonExecTx2).isSuccess)
    val nonExecTx3 = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(3), keyOpt = account1KeyOpt), 2)
    assertTrue("Adding non exec transaction failed", mempoolMap.add(nonExecTx3).isSuccess)

    assertEquals("Wrong mempool size in slots", 5, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertTrue("nonExecTx2 was removed from the mempool", mempoolMap.contains(ModifierId @@ nonExecTx2.id))
    assertEquals("Wrong number of exec txs", 1, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)

    //Add one additional non exec tx to the mempool => nonExecTx2 should be evicted
    var additionalTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ONE, keyOpt = account2KeyOpt)
    mempoolMap = mempoolMap.add(additionalTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding non exec transaction failed with exception $e", e)
    }

    assertEquals("Wrong mempool size in slots", 4, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertTrue("additionalTx was not added to the mempool", mempoolMap.contains(ModifierId @@ additionalTx.id))
    assertFalse("nonExecTx2 was not removed from the mempool", mempoolMap.contains(ModifierId @@ nonExecTx2.id))
    assertEquals("Wrong number of exec txs", 1, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)

    //Test 2: Add one additional exec tx to the mempool => no tx should be evicted
    additionalTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account3KeyOpt)
    mempoolMap = mempoolMap.add(additionalTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction failed with exception $e", e)
    }
    assertEquals("Wrong mempool size in slots", 5, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertTrue("additionalTx was not added to the mempool", mempoolMap.contains(ModifierId @@ additionalTx.id))
    assertEquals("Wrong number of exec txs", 2, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)

    // Test 3: fill both non exec subpool and whole mempool.
    // a) Add 1 non exec tx => verify that the oldest non exec tx is evicted
    // b) Add 1 exec tx => verify that the oldest tx is evicted
    additionalTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.TEN, keyOpt = account3KeyOpt)
    assertTrue("Adding non exec transaction failed", mempoolMap.add(additionalTx).isSuccess)

    (1 to 4).foreach(nonce => assertTrue("Adding exec transaction failed",
      mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account3KeyOpt)).isSuccess))

    assertEquals("Wrong mempool size in slots", 10, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 6, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 3, mempoolMap.mempoolTransactions(false).size)


    // a) Add 1 non exec tx => verify that nonExecTx3 (the oldest non exec tx) is evicted.
    additionalTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.TEN, keyOpt = account1KeyOpt)
    mempoolMap = mempoolMap.add(additionalTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding non exec transaction failed with exception $e", e)
    }
    assertFalse("nonExecTx3 was not removed from the mempool", mempoolMap.contains(ModifierId @@ nonExecTx3.id))
    assertEquals("Wrong mempool size in slots", 9, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 6, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 3, mempoolMap.mempoolTransactions(false).size)

    // b) Add 1 exec tx => verify that execTx0 (the oldest tx) is evicted
    additionalTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(5), keyOpt = account3KeyOpt), 2)
    mempoolMap = mempoolMap.add(additionalTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction failed with exception $e", e)
    }
    assertFalse("execTx0 was not removed from the mempool", mempoolMap.contains(ModifierId @@ execTx0.id))
    assertEquals("Wrong mempool size in slots", 10, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 6, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 3, mempoolMap.mempoolTransactions(false).size)

    //Test 4: transform exec txs in non-exec ones and evict oldest non exec, including some of the txs that caused the eviction.
    //Reset mempool
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider,
      AccountMempoolSettings(maxAccountSlots = 10, maxMemPoolSlots = 10, maxNonExecMemPoolSlots = 5))

    //Create enough exec txs to fill the mempool
    val listOfExecTxs = (0 until mempoolMap.MaxMemPoolSlots - 1).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt))
    listOfExecTxs.foreach(tx => assertTrue("Adding transaction failed", mempoolMap.add(tx).isSuccess))
    assertTrue("Adding transaction failed", mempoolMap.add(createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.ZERO, keyOpt = account2KeyOpt)).isSuccess)

    assertEquals("Wrong number of txs in mempool", mempoolMap.MaxMemPoolSlots, mempoolMap.size)
    assertEquals("Wrong number of exec txs in mempool", mempoolMap.MaxMemPoolSlots, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs in mempool", 0, mempoolMap.mempoolTransactions(false).size)
    assertEquals("Wrong mempool size in slots", mempoolMap.MaxMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 0, mempoolMap.getNonExecSubpoolSizeInSlots)

    //Now a new exec tx from the same account will be added to the mempool. This will evict the first exec tx and so
    // all the remaining txs will become non exec. The number of non exec txs will in turn be bigger than the maximum allowed
    // so an additional number of txs will be evicted. To summarize:
    //1) Add in the mempool enough txs to use all the mempool slots (total num of txs = 10)
    //2) Add another tx. The mempool size exceeds its maximum and the first exec tx is evicted (total num of txs = 10)
    //3) The remaining txs become all non exec (total num of non exec txs = 9)
    //4) the maximum allowed number of non exec txs is 5 => 4 txs will be evicted
    //5) Resulting mempool size = 6, resulting non exec size = 5, resulting exec size = 1
    additionalTx = createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(mempoolMap.MaxMemPoolSlots), keyOpt = account1KeyOpt)

    mempoolMap = mempoolMap.add(additionalTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding exec transaction to a full mempool failed with exception $e", e)
    }
    assertEquals("Wrong number of txs in mempool", 6, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 6, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", mempoolMap.MaxNonExecSubPoolSlots, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals(1, mempoolMap.mempoolTransactions(true).size)
    assertEquals(mempoolMap.MaxNonExecSubPoolSlots, mempoolMap.mempoolTransactions(false).size)

  }

  @Test
  def testReplaceTxNonExecSubpoolSizeCheck(): Unit = {

    // Test 1: fill the mempool with non exec txs. Verify that trying to replace an existing tx with
    // a bigger one will evict oldest txs. In this case txToReplace1 will be replaced while txs with nonces 3 and 4 will
    // be evicted because the replacing tx has size 3 instead of 1
    var mempoolSettings: AccountMempoolSettings = AccountMempoolSettings(maxNonExecMemPoolSlots = 5)
    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    var txToReplace = createEIP1559Transaction(value = BigInteger.ONE,
      nonce = BigInteger.TWO,
      keyOpt = account1KeyOpt,
      gasFee = BigInteger.valueOf(20000),
      priorityGasFee = BigInteger.valueOf(20000),
    )
    assertTrue("Adding exec transaction failed", mempoolMap.add(txToReplace).isSuccess)
    val listOfNonExecTxs = (3 to 6).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account1KeyOpt))
    listOfNonExecTxs.foreach(tx => assertTrue("Adding transaction failed", mempoolMap.add(tx).isSuccess))

    //Check the current situation
    assertEquals("Wrong number of txs in mempool", 5, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxNonExecMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", mempoolSettings.maxNonExecMemPoolSlots, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 5, mempoolMap.mempoolTransactions(false).size)

    var replacingTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TWO,
      nonce = txToReplace.getNonce,
      keyOpt = account1KeyOpt,
      gasFee = txToReplace.getMaxFeePerGas.add(BigInteger.ONE),
      priorityGasFee = txToReplace.getMaxPriorityFeePerGas.add(BigInteger.ONE),
    ),
      3
    )

    mempoolMap = mempoolMap.add(replacingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding non exec transaction failed with exception $e", e)
    }

    assertEquals("Wrong number of txs in mempool", 3, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxNonExecMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", mempoolSettings.maxNonExecMemPoolSlots, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertTrue("replacingTx was not added to the mempool", mempoolMap.contains(ModifierId @@ replacingTx.id))
    assertFalse("txToReplace1 was not removed from the mempool", mempoolMap.contains(ModifierId @@ txToReplace.id))
    assertFalse("Oldest tx was not removed from the mempool", mempoolMap.contains(ModifierId @@ listOfNonExecTxs(0).id))
    assertFalse("Oldest tx was not removed from the mempool", mempoolMap.contains(ModifierId @@ listOfNonExecTxs(1).id))
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 3, mempoolMap.mempoolTransactions(false).size)

    // Now in the mempool there are 2 tx of 1 slot each (nonces 5 and 6), and 1 of 3 slots (nonce 2). Try to replace tx
    // nonce 5 with a tx of 3 slots. Verify it will remain the only one in the mempool

    txToReplace = listOfNonExecTxs(2)
    replacingTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TWO,
      nonce = txToReplace.getNonce,
      keyOpt = account1KeyOpt,
      gasFee = txToReplace.getMaxFeePerGas.add(BigInteger.ONE),
      priorityGasFee = txToReplace.getMaxPriorityFeePerGas.add(BigInteger.ONE),
    ),
      3
    )

    mempoolMap = mempoolMap.add(replacingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding non exec transaction failed with exception $e", e)
    }

    assertEquals("Wrong number of txs in mempool", 1, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 3, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertTrue("replacingTx was not added to the mempool", mempoolMap.contains(ModifierId @@ replacingTx.id))
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)

    // Add another tx of 2 slots to fill again the non exec mempool. Try to replace tx with 3 slots (nonce 5) with a smaller one.
    // Verify that no tx is evicted.
    txToReplace = replacingTx

    val tx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.ONE,
      nonce = BigInteger.valueOf(8),
      keyOpt = account1KeyOpt,
      gasFee = BigInteger.valueOf(20000),
      priorityGasFee = BigInteger.valueOf(20000),
    ),
      2
    )
    assertTrue("Adding exec transaction failed", mempoolMap.add(tx).isSuccess)
    //Check the current situation
    assertEquals("Wrong number of txs in mempool", 2, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxNonExecMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", mempoolSettings.maxNonExecMemPoolSlots, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)

    replacingTx = createEIP1559Transaction(value = BigInteger.TWO,
      nonce = txToReplace.getNonce,
      keyOpt = account1KeyOpt,
      gasFee = txToReplace.getMaxFeePerGas.add(BigInteger.ONE),
      priorityGasFee = txToReplace.getMaxPriorityFeePerGas.add(BigInteger.ONE),
    )

    mempoolMap = mempoolMap.add(replacingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding non exec transaction failed with exception $e", e)
    }

    assertEquals("Wrong number of txs in mempool", 2, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 3, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 3, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertTrue("replacingTx was not added to the mempool", mempoolMap.contains(ModifierId @@ replacingTx.id))
    assertFalse("txToReplace was not removed from the mempool", mempoolMap.contains(ModifierId @@ txToReplace.id))
    assertTrue("additional tx was removed from the mempool", mempoolMap.contains(ModifierId @@ tx.id))
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 2, mempoolMap.mempoolTransactions(false).size)

    //Corner case: cascade deletion. The mempool has nonexec max size = 4 and max size 5. In it there will be 1 non exec
    // tx and 4 exec txs, each of 1 slot => the mempool is full but the subpool is not. The non exec tx is replaced by 1
    // tx of 2 slots => the subpool is still under the max size but the mempool is not, so the oldest tx will be evicted.
    // In this case the oldest is the first exec, its eviction causes the other exec txs to become non exec. So in the end
    // there will be 4 non exec txs, the 3 former exec and the replacing one. Now the subpool is too big (2 + 3*1 = 5 > 4)
    // 1 additional tx needs to be evicted=> in the end there will be 3 non exec txs of 4 slots in total

    //Reset mempool

    mempoolSettings = AccountMempoolSettings(maxAccountSlots = 5, maxMemPoolSlots = 5, maxNonExecMemPoolSlots = 4)
    mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    txToReplace = createEIP1559Transaction(value = BigInteger.ONE,
      nonce = BigInteger.TWO,
      keyOpt = account1KeyOpt,
      gasFee = BigInteger.valueOf(20000),
      priorityGasFee = BigInteger.valueOf(20000),
    )
    assertTrue("Adding exec transaction failed", mempoolMap.add(txToReplace).isSuccess)
    val listOfExecTxs = (0 to 3).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account2KeyOpt))
    listOfExecTxs.foreach(tx => assertTrue("Adding transaction failed", mempoolMap.add(tx).isSuccess))

    //Check the current situation
    assertEquals("Wrong number of txs in mempool", 5, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", listOfExecTxs.size, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)

    replacingTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TWO,
      nonce = txToReplace.getNonce,
      keyOpt = account1KeyOpt,
      gasFee = txToReplace.getMaxFeePerGas.add(BigInteger.ONE),
      priorityGasFee = txToReplace.getMaxPriorityFeePerGas.add(BigInteger.ONE),
    ), 2)

    mempoolMap = mempoolMap.add(replacingTx) match {
      case Success(m) => m
      case Failure(e) => fail(s"Adding non exec transaction failed with exception $e", e)
    }

    assertEquals("Wrong number of txs in mempool", 3, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 4, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertTrue("replacingTx was not added to the mempool", mempoolMap.contains(ModifierId @@ replacingTx.id))
    assertFalse("txToReplace was not removed from the mempool", mempoolMap.contains(ModifierId @@ txToReplace.id))
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 3, mempoolMap.mempoolTransactions(false).size)
  }

  @Test
  def testRemoveFromMempool(): Unit = {
    //The mempool has nonexec max size = 4 and max size 6. In it there will be 1 non exec
    // tx and 5 exec txs, each of 1 slot.
    // Try to remove the first exec, its deletion causes the other exec txs to become non exec. So in the end
    // there will be 5 non exec txs, the 4 former exec and the non exec one. Now the subpool is too big (1 + 4*1 = 5 > 4)
    // 1 tx needs to be evicted => in the end there will be 4 non exec txs of 4 slots in total

    val mempoolSettings = AccountMempoolSettings(maxAccountSlots = 6, maxMemPoolSlots = 6, maxNonExecMemPoolSlots = 4)

    var mempoolMap = new MempoolMap(accountStateProvider, baseStateProvider, mempoolSettings)
    val txToReplace = createEIP1559Transaction(value = BigInteger.ONE,
      nonce = BigInteger.TWO,
      keyOpt = account1KeyOpt,
      gasFee = BigInteger.valueOf(20000),
      priorityGasFee = BigInteger.valueOf(20000),
    )
    assertTrue("Adding exec transaction failed", mempoolMap.add(txToReplace).isSuccess)
    val listOfExecTxs = (0 to 4).map(nonce => createEIP1559Transaction(value = BigInteger.ONE, nonce = BigInteger.valueOf(nonce), keyOpt = account2KeyOpt))
    listOfExecTxs.foreach(tx => assertTrue("Adding transaction failed", mempoolMap.add(tx).isSuccess))

    //Check the current situation
    assertEquals("Wrong number of txs in mempool", 6, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", mempoolSettings.maxMemPoolSlots, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 1, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertEquals("Wrong number of exec txs", listOfExecTxs.size, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 1, mempoolMap.mempoolTransactions(false).size)

    mempoolMap = mempoolMap.removeFromMempool(listOfExecTxs.head) match {
      case Success(m) => m
      case Failure(e) => fail(s"Removing transaction failed with exception $e", e)
    }

    assertEquals("Wrong number of txs in mempool", 4, mempoolMap.size)
    assertEquals("Wrong mempool size in slots", 4, mempoolMap.getMempoolSizeInSlots)
    assertEquals("Wrong non exec mempool size in slots", 4, mempoolMap.getNonExecSubpoolSizeInSlots)
    assertFalse("Exec tx was not removed from the mempool", mempoolMap.contains(ModifierId @@ listOfExecTxs.head.id))
    assertEquals("Wrong number of exec txs", 0, mempoolMap.mempoolTransactions(true).size)
    assertEquals("Wrong number of non exec txs", 4, mempoolMap.mempoolTransactions(false).size)
  }


private def createMockTxWithSize(size: Long): EthereumTransaction = {
  val dummyTx = createEIP1559Transaction(value = BigInteger.ONE)
  addMockSizeToTx(dummyTx, size)
}
}
