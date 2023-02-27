package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.fixtures.EthereumTransactionFixture
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.util.ModifierId

import java.math.BigInteger

class TxCacheTest
  extends JUnitSuite
    with EthereumTransactionFixture
    with SidechainTypes
    with MockitoSugar {

  @Test
  def testAdd(): Unit = {

    val txCache = new TxCache
    assertTrue("Oldest is already initialized", txCache.getOldestTransaction().isEmpty)
    assertTrue("Youngest is already initialized", txCache.getYoungestTransaction().isEmpty)
    assertEquals("wrong size", 0, txCache.size)
    assertEquals("wrong size in slots", 0, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 0, txCache.getNonExecSizeInSlots)

    val firstTx = createEIP1559Transaction(value = BigInteger.ONE)
    assertFalse( txCache.contains(ModifierId @@ firstTx.id))
    assertThrows[NoSuchElementException](txCache(ModifierId @@ firstTx.id))

    txCache.add(firstTx, isNonExec = false)
    assertEquals("wrong size", 1, txCache.size)
    assertEquals("wrong size in slots", 1, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 0, txCache.getNonExecSizeInSlots)
    assertTrue("Oldest is not initialized", txCache.getOldestTransaction().isDefined)
    assertTrue("Youngest is not initialized", txCache.getYoungestTransaction().isDefined)
    assertEquals(txCache.getOldestTransaction().get, txCache.getYoungestTransaction().get)
    assertEquals(firstTx, txCache(ModifierId @@ firstTx.id))
    assertTrue( txCache.contains(ModifierId @@ firstTx.id))

    val secondTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TWO), 2 )
    txCache.add(secondTx, isNonExec = true)
    assertEquals("wrong size", 2, txCache.size)
    assertEquals("wrong size in slots", 3, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 2, txCache.getNonExecSizeInSlots)
    assertTrue("Oldest is not initialized", txCache.getOldestTransaction().isDefined)
    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertTrue("Youngest is not initialized", txCache.getYoungestTransaction().isDefined)
    assertEquals(secondTx, txCache.getYoungestTransaction().get)

    // Let's do the same but in different order. Nothing should change except for youngest/oldest

    val txCache2 = new TxCache
    txCache2.add(secondTx, isNonExec = true)
    assertEquals("wrong size", 1, txCache2.size)
    assertEquals("wrong size in slot", 2, txCache2.getSizeInSlots)
    assertEquals("wrong non exec size in slot", 2, txCache2.getNonExecSizeInSlots)
    assertTrue("Oldest is not initialized", txCache2.getOldestTransaction().isDefined)
    assertTrue("Youngest is not initialized", txCache2.getYoungestTransaction().isDefined)
    assertEquals(txCache2.getOldestTransaction().get, txCache2.getYoungestTransaction().get)
    assertEquals(secondTx, txCache2(ModifierId @@ secondTx.id))
    assertTrue(txCache2.contains(ModifierId @@ secondTx.id))

    txCache2.add(firstTx, isNonExec = false)
    assertEquals("wrong size", 2, txCache2.size)
    assertEquals("wrong size in slot", 3, txCache2.getSizeInSlots)
    assertEquals("wrong non exec size in slot", 2, txCache2.getNonExecSizeInSlots)
    assertTrue("Oldest is not initialized", txCache2.getOldestTransaction().isDefined)
    assertEquals(secondTx, txCache2.getOldestTransaction().get)
    assertTrue("Youngest is not initialized", txCache2.getYoungestTransaction().isDefined)
    assertEquals(firstTx, txCache2.getYoungestTransaction().get)


  }

  @Test
  def testRemove(): Unit = {
    val firstTx = createEIP1559Transaction(value = BigInteger.ONE)
    val secondTx = setupMockSizeInSlotsToTx(createEIP1559Transaction(value = BigInteger.TWO), 3 )//3 slots
    val thirdTx = createEIP1559Transaction(value = BigInteger.valueOf(3))

    var txCache = new TxCache
    txCache.add(firstTx, isNonExec = false)
    txCache.add(secondTx, isNonExec = true)
    txCache.add(thirdTx, isNonExec = true)

    assertEquals("wrong size", 3, txCache.size)
    assertEquals("wrong size in slots", 5, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 4, txCache.getNonExecSizeInSlots)
    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertEquals(thirdTx, txCache.getYoungestTransaction().get)

    txCache.remove(ModifierId @@ firstTx.id)
    assertEquals("wrong size", 2, txCache.size)
    assertEquals("wrong size in slots", 4, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 4, txCache.getNonExecSizeInSlots)
    assertEquals(secondTx, txCache.getOldestTransaction().get)
    assertEquals(thirdTx, txCache.getYoungestTransaction().get)
    assertFalse( txCache.contains(ModifierId @@ firstTx.id))

    txCache.add(firstTx, isNonExec = false)
    assertEquals("wrong size", 3, txCache.size)
    assertEquals("wrong size in slots", 5, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 4, txCache.getNonExecSizeInSlots)
    assertEquals(secondTx, txCache.getOldestTransaction().get)
    assertEquals(firstTx, txCache.getYoungestTransaction().get)


    txCache = new TxCache
    txCache.add(firstTx, isNonExec = false)
    txCache.add(secondTx, isNonExec = true)
    txCache.add(thirdTx, isNonExec = true)

    txCache.remove(ModifierId @@ secondTx.id)
    assertEquals("wrong size", 2, txCache.size)
    assertEquals("wrong size in slots", 2, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 1, txCache.getNonExecSizeInSlots)
    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertEquals(thirdTx, txCache.getYoungestTransaction().get)
    assertFalse(txCache.contains(ModifierId @@ secondTx.id))

    txCache = new TxCache
    txCache.add(firstTx, isNonExec = false)
    txCache.add(secondTx, isNonExec = true)
    txCache.add(thirdTx, isNonExec = true)

    txCache.remove(ModifierId @@ thirdTx.id)
    assertEquals("wrong size", 2, txCache.size)
    assertEquals("wrong size in slots", 4, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 3, txCache.getNonExecSizeInSlots)
    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertEquals(secondTx, txCache.getYoungestTransaction().get)
    assertFalse(txCache.contains(ModifierId @@ thirdTx.id))

    txCache.remove(ModifierId @@ firstTx.id)
    assertEquals("wrong size", 1, txCache.size)
    assertEquals("wrong size in slots", 3, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 3, txCache.getNonExecSizeInSlots)
    assertEquals(secondTx, txCache.getOldestTransaction().get)
    assertEquals(secondTx, txCache.getYoungestTransaction().get)
    assertFalse(txCache.contains(ModifierId @@ firstTx.id))

    txCache.remove(ModifierId @@ secondTx.id)
    assertEquals("wrong size", 0, txCache.size)
    assertEquals("wrong size in slots", 0, txCache.getSizeInSlots)
    assertEquals("wrong non exec size in slots", 0, txCache.getNonExecSizeInSlots)
    assertTrue("Oldest is initialized", txCache.getOldestTransaction().isEmpty)
    assertTrue("Youngest is initialized", txCache.getYoungestTransaction().isEmpty)
    assertFalse(txCache.contains(ModifierId @@ secondTx.id))

  }

  @Test
  def testNonExecIterator(): Unit = {
    // Test 1: empty cache
    var txCache = new TxCache
    var iter = txCache.getNonExecIterator()

    assertFalse(iter.hasNext)
    assertThrows[NoSuchElementException](iter.next)

    //Test 2: only exec txs in cache
    val firstTx = createEIP1559Transaction(value = BigInteger.ONE)
    val secondTx = createEIP1559Transaction(value = BigInteger.TWO)
    val thirdTx = createEIP1559Transaction(value = BigInteger.valueOf(3))
    txCache.add(firstTx, isNonExec = false)
    txCache.add(secondTx, isNonExec = false)
    txCache.add(thirdTx, isNonExec = false)

    iter = txCache.getNonExecIterator()

    assertFalse(iter.hasNext)
    assertThrows[NoSuchElementException](iter.next)

    //Test 3: Some non exec txs in cache in cache
    txCache = new TxCache
    txCache.add(firstTx, isNonExec = true)
    txCache.add(secondTx, isNonExec = false)
    txCache.add(thirdTx, isNonExec = true)

    iter = txCache.getNonExecIterator()
    assertTrue(iter.hasNext)
    assertEquals(firstTx, iter.next)

    assertTrue(iter.hasNext)
    assertEquals(thirdTx, iter.next)

    assertFalse(iter.hasNext)
    assertThrows[NoSuchElementException](iter.next)

    //Test 4: Check the iterator is not reusable
    val nonExecTx = createEIP1559Transaction(value = BigInteger.ONE)
    txCache.add(createEIP1559Transaction(value = BigInteger.ONE), isNonExec = true)

    assertFalse(iter.hasNext)
    assertThrows[NoSuchElementException](iter.next)

    txCache = new TxCache
    (1 to 10000).foreach(_ =>txCache.add(createEIP1559Transaction(value = BigInteger.ONE), isNonExec = false))
    txCache.add(nonExecTx, isNonExec = true)
    iter = txCache.getNonExecIterator()

    val startTime = System.currentTimeMillis()
    assertTrue(iter.hasNext)
    assertEquals(nonExecTx, iter.next)

    val totalTime = System.currentTimeMillis() - startTime
    println(s"Total time $totalTime")
  }

}
