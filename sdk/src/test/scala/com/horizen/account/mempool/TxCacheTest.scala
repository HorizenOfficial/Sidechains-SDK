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

    val firstTx = createEIP1559Transaction(value = BigInteger.ONE)
    assertFalse( txCache.contains(ModifierId @@ firstTx.id))
    assertThrows[NoSuchElementException](txCache(ModifierId @@ firstTx.id))

    txCache.add(firstTx)
    assertEquals("wrong size", 1, txCache.size)
    assertTrue("Oldest is not initialized", txCache.getOldestTransaction().isDefined)
    assertTrue("Youngest is not initialized", txCache.getYoungestTransaction().isDefined)
    assertEquals(txCache.getOldestTransaction().get, txCache.getYoungestTransaction().get)
    assertEquals(firstTx, txCache(ModifierId @@ firstTx.id))
    assertTrue( txCache.contains(ModifierId @@ firstTx.id))

    val secondTx = createEIP1559Transaction(value = BigInteger.TWO)
    txCache.add(secondTx)
    assertEquals("wrong size", 2, txCache.size)
    assertTrue("Oldest is not initialized", txCache.getOldestTransaction().isDefined)
    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertTrue("Youngest is not initialized", txCache.getYoungestTransaction().isDefined)
    assertEquals(secondTx, txCache.getYoungestTransaction().get)

  }

  @Test
  def testRemove(): Unit = {
    val firstTx = createEIP1559Transaction(value = BigInteger.ONE)
    val secondTx = createEIP1559Transaction(value = BigInteger.TWO)
    val thirdTx = createEIP1559Transaction(value = BigInteger.valueOf(3))

    var txCache = new TxCache
    txCache.add(firstTx)
    txCache.add(secondTx)
    txCache.add(thirdTx)

    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertEquals(thirdTx, txCache.getYoungestTransaction().get)

    txCache.remove(ModifierId @@ firstTx.id)
    assertEquals("wrong size", 2, txCache.size)
    assertEquals(secondTx, txCache.getOldestTransaction().get)
    assertEquals(thirdTx, txCache.getYoungestTransaction().get)
    assertFalse( txCache.contains(ModifierId @@ firstTx.id))

    txCache.add(firstTx)
    assertEquals("wrong size", 3, txCache.size)
    assertEquals(secondTx, txCache.getOldestTransaction().get)
    assertEquals(firstTx, txCache.getYoungestTransaction().get)


    txCache = new TxCache
    txCache.add(firstTx)
    txCache.add(secondTx)
    txCache.add(thirdTx)

    txCache.remove(ModifierId @@ secondTx.id)
    assertEquals("wrong size", 2, txCache.size)
    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertEquals(thirdTx, txCache.getYoungestTransaction().get)
    assertFalse(txCache.contains(ModifierId @@ secondTx.id))

    txCache = new TxCache
    txCache.add(firstTx)
    txCache.add(secondTx)
    txCache.add(thirdTx)

    txCache.remove(ModifierId @@ thirdTx.id)
    assertEquals("wrong size", 2, txCache.size)
    assertEquals(firstTx, txCache.getOldestTransaction().get)
    assertEquals(secondTx, txCache.getYoungestTransaction().get)
    assertFalse(txCache.contains(ModifierId @@ thirdTx.id))

    txCache.remove(ModifierId @@ firstTx.id)
    assertEquals("wrong size", 1, txCache.size)
    assertEquals(secondTx, txCache.getOldestTransaction().get)
    assertEquals(secondTx, txCache.getYoungestTransaction().get)
    assertFalse(txCache.contains(ModifierId @@ firstTx.id))

    txCache.remove(ModifierId @@ secondTx.id)
    assertEquals("wrong size", 0, txCache.size)
    assertTrue("Oldest is initialized", txCache.getOldestTransaction().isEmpty)
    assertTrue("Youngest is initialized", txCache.getYoungestTransaction().isEmpty)
    assertFalse(txCache.contains(ModifierId @@ secondTx.id))


  }
}
