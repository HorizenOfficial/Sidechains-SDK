package com.horizen

import java.util

import com.horizen.box.{ZenBox,ForgerBox}
import org.scalatestplus.junit.JUnitSuite
import org.junit.{Before, Test}
import org.junit.Assert._
import com.horizen.fixtures._
import com.horizen.mempool.{MempoolTakeFilter, MempoolTakeFilterWithMaxBoxType}
import com.horizen.transaction.RegularTransaction
import com.horizen.utils.FeeRate
import org.mockito.Mockito
import sparkz.util.ModifierId
import org.scalatestplus.mockito.MockitoSugar

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

class SidechainMemoryPoolTest
  extends JUnitSuite
  with TransactionFixture
  with SidechainTypesTestsExtension
  with MockitoSugar
{

  @Test
  def remove(): Unit = {
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0))
    val tx = getRegularTransaction
    val tx2 = getCompatibleTransaction
    val tx3 = getRegularRandomTransaction(10,1)
    val txId: ModifierId = ModifierId @@ tx.id
    assertEquals("maxPoolSizeBytes must be equals to 300MB size",
      memoryPool.maxPoolSizeBytes, (300*1024*1024))

    assertEquals("Put operation must be success.", memoryPool.put(tx).isSuccess, true)
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction" + txId, memoryPool.modifierById(txId).get, tx)

    assertEquals("Put operation must be success.", memoryPool.put(tx2).isSuccess,
      true)
    assertEquals("Size must be 2.", memoryPool.size, 2)
    assertEquals("usedPoolSizeBytes must be equals to tx1+tx2 size", memoryPool.usedSizeBytes,
      tx.size() + tx2.size())

    //remove unexisting tx
    memoryPool.remove(tx3)
    assertEquals("Size must be 2.", memoryPool.size, 2)
    assertEquals("usedPoolSizeBytes must be equals to tx1+tx2 size", memoryPool.usedSizeBytes,
      tx.size() + tx2.size())

    memoryPool.remove(tx)
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("usedPoolSizeBytes must be equals to tx2 size", memoryPool.usedSizeBytes, tx2.size())


  }

  @Test
  def put(): Unit = {
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300,0))
    val tx = getRegularTransaction
    val txCompat = getCompatibleTransaction
    val txIncompat = getIncompatibleTransaction
    val txId: ModifierId = ModifierId @@ tx.id
    assertEquals("maxPoolSizeBytes must be equals to 300MB size",
      memoryPool.maxPoolSizeBytes, (300*1024*1024))

    assertEquals("Put operation must be success.", memoryPool.put(tx).isSuccess, true)
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction " + txId, memoryPool.modifierById(txId).get, tx)
    assertEquals("MemoryPool must contain transaction " + txId, memoryPool.contains(txId), true)

    assertEquals("Put operation must be success.", memoryPool.put(txCompat).isSuccess,
          true)
    assertEquals("Size must be 2.", memoryPool.size, 2)
    assertEquals("MemoryPool must contain transaction " + txCompat.id, memoryPool.modifierById(ModifierId @@ txCompat.id).get, txCompat)
    assertEquals("MemoryPool must contain transaction " + txCompat.id, memoryPool.contains(ModifierId @@ txCompat.id), true)

    assertEquals("Put operation must be failure.", memoryPool.put(txIncompat).isSuccess,
      false)
    assertEquals("Size must be 2.", memoryPool.size, 2)
    assertEquals("MemoryPool must not contain transaction " + txIncompat.id,
      memoryPool.contains(ModifierId @@ txIncompat.id), false)
    assertEquals("MemoryPool must not contain transaction " + txIncompat.id,
      1, memoryPool.notIn(Seq(ModifierId @@ txIncompat.id)).size)

    assertEquals("usedPoolSizeBytes must be correct", memoryPool.usedSizeBytes, tx.size()+txCompat.size())

    assertEquals("Take must return transaction " + tx.id, tx, memoryPool.take(1).head)
    assertEquals("Take with custom sort function must return transaction " + txCompat.id, txCompat,
      memoryPool.take( (a,b) => {
        if (a.getUnconfirmedTx().fee() < b.getUnconfirmedTx().fee())
          true
        else
          false
      }, 1).head)

    val mp = memoryPool.filter(List(tx))

    assertEquals("After applying of filter size must be 1.", mp.size, 1)
    assertEquals("After applying of filter usedPoolSizeBytes must be updated correctly", mp.usedSizeBytes, txCompat.size())
    assertEquals("MemoryPool must contain transaction " + txCompat.id, mp.modifierById(ModifierId @@ txCompat.id).get, txCompat)
    assertEquals("MemoryPool must contain transaction " + txCompat.id, mp.contains(ModifierId @@ txCompat.id), true)
  }

  @Test
  def putSeq(): Unit = {
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0))
    val txList = getRegularTransactionList(1).asScala.toList
    val txId: ModifierId = ModifierId @@ txList.head.id

    assertEquals("maxPoolSizeBytes must be equals to 300MB size",
      memoryPool.maxPoolSizeBytes, (300*1024*1024))

    val txIncompat = txList ::: getIncompatibleTransactionList
    assertEquals("Put operation must be failure.", memoryPool.put(txIncompat).isSuccess, false)
    assertEquals("Size must be 0.", memoryPool.size, 0)

    assertEquals("Put operation must be success.", memoryPool.put(txList).isSuccess, true)
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction " + txId, memoryPool.modifierById(txId).get, txList.head)

    val compatibleList = getCompatibleTransactionList
    assertEquals("Put operation must be success.", memoryPool.put(compatibleList).isSuccess,
      true)

    var totFee : Long  = 0
    txList.foreach((tx) => totFee = totFee + tx.size())
    compatibleList.foreach((tx) => totFee = totFee + tx.size())
    assertEquals("usedPoolSizeBytes must be updated correctly",
      totFee, memoryPool.usedSizeBytes)

    assertEquals("Size must be 2.", memoryPool.size, 2)

    assertEquals("Put operation must be failure.", memoryPool.put(getIncompatibleTransactionList).isSuccess,
      false)
    assertEquals("Size must be 2.", memoryPool.size, 2)
    assertEquals("usedPoolSizeBytes must be the same as before",  totFee, memoryPool.usedSizeBytes)
  }

  @Test
  def putWithoutCheck(): Unit = {
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0))
    val txList = getRegularTransactionList(1).asScala.toList
    val txId: ModifierId = ModifierId @@ txList.head.id

    assertEquals("maxPoolSizeBytes must be equals to 300MB size",
      memoryPool.maxPoolSizeBytes, (300*1024*1024))

    val txIncompat = txList ::: getIncompatibleTransactionList
    assertEquals("Put operation must be failure.", memoryPool.put(txIncompat).isSuccess, false)
    assertEquals("Size must be 0.", memoryPool.size, 0)

    assertEquals("Put operation must be success.", memoryPool.put(txList).isSuccess, true)
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction " + txId, memoryPool.modifierById(txId).get, txList.head)

    assertEquals("Put operation must be success.", memoryPool.put(getCompatibleTransactionList).isSuccess,
      true)
    assertEquals("Size must be 2.", memoryPool.size, 2)

    assertEquals("Put operation must be failure.", memoryPool.put(getIncompatibleTransactionList).isSuccess,
      false)
    assertEquals("Size must be 2.", memoryPool.size, 2)
  }

  @Test
  def putWithMinFeeRate(): Unit = {

    val list = List(
      getRegularRandomTransaction(10,1),
      getRegularRandomTransaction(20,2),
      getRegularRandomTransaction(30,3),
      getRegularRandomTransaction(40,1)
    )
    val minFeeRate  : Long = list.map(tx => new FeeRate(tx.fee(), tx.size()).getFeeRate()).min
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, minFeeRate + 1))

    assertEquals("Put tx1 operation must be failure.", false, memoryPool.put(list.apply(0)).isSuccess)
    assertEquals("Put tx2 operation must be success.", true, memoryPool.put(list.apply(1)).isSuccess)
    assertEquals("Put tx3 operation must be success.", true,  memoryPool.put(list.apply(2)).isSuccess)
    assertEquals("Put tx4 operation must be success.", true, memoryPool.put(list.apply(3)).isSuccess)
    assertEquals("MemoryPool must  have size 3 ", 3, memoryPool.size)
  }

  @Test
  def take(): Unit = {

    val list = List(
      getRegularRandomTransaction(10,1),
      getRegularRandomTransaction(2000,2),
      getRegularRandomTransaction(3000000,3),
      getRegularRandomTransaction(400000000,1)
    )
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0))

    assertEquals("Put tx1 operation must be success.", true, memoryPool.put(list.apply(0)).isSuccess)
    assertEquals("Put tx2 operation must be success.", true, memoryPool.put(list.apply(1)).isSuccess)
    assertEquals("Put tx3 operation must be success.", true,  memoryPool.put(list.apply(2)).isSuccess)
    assertEquals("Put tx4 operation must be success.", true, memoryPool.put(list.apply(3)).isSuccess)
    assertEquals("MemoryPool must  have size 4 ", 4, memoryPool.size)
    val it = memoryPool.take(2)
    assertEquals("MemoryPool take must  have size 2", 2, it.size)
    assertEquals(400000000, it.toSeq(0).fee())
    assertEquals(3000000, it.toSeq(1).fee())
  }


  @Test
  def putWithLimitedPoolSize(): Unit = {
    //build a list of transactions up to 1MB size
    val totalTxSizeMax = (1024*1024)  //1MB
    val list = mutable.MutableList[RegularTransaction]()
    val lowestFeeTx = getRegularRandomTransaction(10, 1)
    val secondLowestFeeTx = getRegularRandomTransaction(20, 1)
    list += lowestFeeTx
    list += secondLowestFeeTx
    var totalTxSize = lowestFeeTx.size() + secondLowestFeeTx.size()
    while (totalTxSize <= totalTxSizeMax) {
      val newTx = getRegularRandomTransaction(30, 1)
      list += newTx
      totalTxSize = totalTxSize + newTx.size()
    }
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(1, 0))

    //put all transaction except the last one (so we are under 1MB limit)
    for (i <- 0 to (list.size - 2)) {
      assertEquals("Put tx operation must be success.", true, memoryPool.put(list.apply(i)).isSuccess)
    }
    assertEquals("MemoryPool must have correct size ", list.size-1, memoryPool.size)
    assertEquals("Lowest fee transaction must be present ", true, memoryPool.getTransactionById(lowestFeeTx.id()).isPresent)

    //add one more tx, causing the maxPoolSize to be reached and lowest one to be evicted
    var aNewTx = getRegularRandomTransaction(30, 1)
    while (aNewTx.size() > lowestFeeTx.size()) {
      aNewTx = getRegularRandomTransaction(30, 1)
    }
    assertEquals("Put tx operation must be success.", true, memoryPool.put(aNewTx).isSuccess)
    assertEquals("MemoryPool must have correct size ", list.size-1, memoryPool.size)
    assertEquals("Lowest fee transaction must not be present ", false, memoryPool.getTransactionById(lowestFeeTx.id()).isPresent)

    //now the tx with lowest fee is secondLowestFeeTx: we try to add another tx with the same feerate
    var expectedMempoolSize = list.size - 1
    //Check if the mempool has space for another tx, in this case fill the mempool before continue
    if (memoryPool.maxPoolSizeBytes - memoryPool.usedSizeBytes >= secondLowestFeeTx.size()) {
      var fillingTx = getRegularRandomTransaction(20, 1)
      while (fillingTx.size() != secondLowestFeeTx.size()) {
        fillingTx = getRegularRandomTransaction(20, 1)
      }
      assertEquals("Put tx operation must be success.", true, memoryPool.put(fillingTx).isSuccess)
      expectedMempoolSize += 1
    }

    aNewTx = getRegularRandomTransaction(20, 1)
    while (aNewTx.size() != secondLowestFeeTx.size()) {
      aNewTx = getRegularRandomTransaction(20, 1)
    }
    assertEquals("Put tx operation must be success.", true, memoryPool.put(aNewTx).isSuccess)
    assertEquals("MemoryPool must have correct size ", expectedMempoolSize, memoryPool.size)
    assertEquals("Old lowest fee transaction must not be present ", false, memoryPool.getTransactionById(secondLowestFeeTx.id()).isPresent)
    assertEquals("New lowest fee transaction must be present ", true, memoryPool.getTransactionById(aNewTx.id()).isPresent)
  }

  @Test
  def testwithZeroMaxSize(): Unit = {
    val tx1 = getRegularRandomTransaction(10,1)
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(0, 0))
    assertEquals("Put tx operation must not be successfull.", false, memoryPool.put(tx1).isSuccess)
  }

  @Test
  def testWithHighRateAndEmptyMempool(): Unit = {
    val tx1 = getRegularRandomTransaction(10,1)
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(1, 200))
    assertEquals("Put tx operation must not be successfull.", false, memoryPool.put(tx1).isSuccess)
  }

  @Test
  def takeWithLimit(): Unit = {
    val tx1 = getRegularRandomTransaction(10,1)
    val tx2 = getRegularRandomTransaction(10,2)
    val tx3 = getRegularRandomTransaction(10,3)
    val memoryPool = SidechainMemoryPool.createEmptyMempool(getMockedMempoolSettings(300, 0))
    assertEquals("Put tx operation must  be successfull.", true, memoryPool.put(tx1).isSuccess)
    assertEquals("Put tx operation must  be successfull.", true, memoryPool.put(tx2).isSuccess)
    assertEquals("Put tx operation must  be successfull.", true, memoryPool.put(tx3).isSuccess)

    val take1 =  memoryPool.takeWithFilterLimit(Seq[MempoolTakeFilter](new MempoolTakeFilterWithMaxBoxType[ZenBox](classOf[ZenBox], 3)))
    assertEquals("Unexpected number of transaction taken", 2, take1.size)

    val take2 =  memoryPool.takeWithFilterLimit(Seq[MempoolTakeFilter](new MempoolTakeFilterWithMaxBoxType[ForgerBox](classOf[ForgerBox], 3)))
    assertEquals("Unexpected number of transaction taken", 3, take2.size)

    val take3 =  memoryPool.takeWithFilterLimit(Seq[MempoolTakeFilter](
      new MempoolTakeFilterWithMaxBoxType[ZenBox](classOf[ZenBox], 3),
      new MempoolTakeFilterWithMaxBoxType[ForgerBox](classOf[ForgerBox], 3)))
    assertEquals("Unexpected number of transaction taken", 2, take3.size)
  }

  private def getMockedMempoolSettings(maxSize: Int, minFeeRate: Long): MempoolSettings = {
    val mockedSettings: MempoolSettings = mock[MempoolSettings]
    Mockito.when(mockedSettings.maxSize).thenReturn(maxSize)
    Mockito.when(mockedSettings.minFeeRate).thenReturn(minFeeRate)
    mockedSettings
  }
}
