package com.horizen

import java.util

import org.scalatestplus.junit.JUnitSuite
import org.junit.{Before, Test}
import org.junit.Assert._
import com.horizen.fixtures._
import com.horizen.transaction.RegularTransaction
import com.horizen.utils.FeeRate
import org.mockito.Mockito
import scorex.util.ModifierId
import org.scalatestplus.mockito.MockitoSugar

import scala.collection.JavaConverters._
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
    var tx2 = getCompatibleTransaction
    val txId: ModifierId = ModifierId @@ tx.id
    assertEquals("maxPoolSizeBytes must be equals to 300MB size",
      memoryPool.maxPoolSizeBytes, (300*1024*1024))

    assertEquals("Put operation must be success.", memoryPool.put(tx).isSuccess, true)
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction" + txId, memoryPool.modifierById(txId).get, tx)

    assertEquals("Put operation must be success.", memoryPool.put(tx2).isSuccess,
      true)
    assertEquals("Size must be 2.", memoryPool.size, 2)
    assertEquals("usedPoolSizeBytes must be equals to tx1+tx2 size", memoryPool.usedPoolSizeBytes,
      tx.size() + tx2.size())

    memoryPool.remove(tx)
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("usedPoolSizeBytes must be equals to tx2 size", memoryPool.usedPoolSizeBytes, tx2.size())


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

    assertEquals("usedPoolSizeBytes must be correct", memoryPool.usedPoolSizeBytes, tx.size()+txCompat.size())

    assertEquals("Take must return transaction " + tx.id, tx, memoryPool.take(1).head)
    assertEquals("Take with custom sort function must return transaction " + txCompat.id, txCompat,
      memoryPool.take( (a,b) => {
        if (a.getUnconfirmedTx().fee() < b.getUnconfirmedTx().fee())
          true
        else
          false
      }, 1).head)

    val mp = memoryPool.filter(List(tx))

    assertEquals("After applying of filter size must be 1.", memoryPool.size, 1)
    assertEquals("After applying of filter usedPoolSizeBytes must be updated correctly", memoryPool.usedPoolSizeBytes, txCompat.size())
    assertEquals("MemoryPool must contain transaction " + txCompat.id, memoryPool.modifierById(ModifierId @@ txCompat.id).get, txCompat)
    assertEquals("MemoryPool must contain transaction " + txCompat.id, memoryPool.contains(ModifierId @@ txCompat.id), true)
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
      totFee, memoryPool.usedPoolSizeBytes)

    assertEquals("Size must be 2.", memoryPool.size, 2)

    assertEquals("Put operation must be failure.", memoryPool.put(getIncompatibleTransactionList).isSuccess,
      false)
    assertEquals("Size must be 2.", memoryPool.size, 2)
    assertEquals("usedPoolSizeBytes must be the same as before",  totFee, memoryPool.usedPoolSizeBytes)
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
  def putWithLimitedPoolSize(): Unit = {
    //build a list of transactions up to 1MB size
    var totalTxSizeMax = (1024*1024)  //1MB
    val list = mutable.MutableList[RegularTransaction]()
    val lowestFeeTx = getRegularRandomTransaction(10, 1)
    list += lowestFeeTx
    var totalTxSize = lowestFeeTx.size()
    while (totalTxSize < totalTxSizeMax) {
      var newTx = getRegularRandomTransaction(30, 1)
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
    assertEquals("Put tx operation must be success.", true, memoryPool.put(list.apply(list.size - 1)).isSuccess)
    assertEquals("MemoryPool must have correct size ", list.size-1, memoryPool.size)
    assertEquals("Lowest fee transaction must not be present ", false, memoryPool.getTransactionById(lowestFeeTx.id()).isPresent)

  }

  private def getMockedMempoolSettings(maxSize: Int, minFeeRate: Long): MempoolSettings = {
    val mockedSettings: MempoolSettings = mock[MempoolSettings]
    Mockito.when(mockedSettings.maxSize).thenReturn(maxSize)
    Mockito.when(mockedSettings.minFeeRate).thenReturn(minFeeRate)
    mockedSettings
  }
}
