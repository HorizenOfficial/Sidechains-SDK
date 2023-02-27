package com.horizen.utils

import com.horizen.fixtures.TransactionFixture
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import com.horizen.{SidechainTypes, utxo}
import com.horizen.utxo.SidechainMemoryPoolEntry
import org.junit.Assert._

class MempoolMapTest extends JUnitSuite
  with TransactionFixture {

  val tx1 = getRegularRandomTransaction(10,1)
  val tx2 = getRegularRandomTransaction(2000000,1)
  val tx3 = getRegularRandomTransaction(3000,1)


  @Test
  def takeLowest(): Unit = {
    val map = new MempoolMap(List())
    map.add(SidechainMemoryPoolEntry(tx1))
    map.add(SidechainMemoryPoolEntry(tx2))
    map.add(SidechainMemoryPoolEntry(tx3))
    val ret = map.takeLowest(2)
    assertEquals(2, ret.size)
    assertEquals(tx1.id(), ret(0).getUnconfirmedTx().id())
    assertEquals(tx3.id(), ret(1).getUnconfirmedTx().id())
  }

  @Test
  def takeLowestWithInitialiList(): Unit = {
    val map = new MempoolMap(List(
      SidechainMemoryPoolEntry(tx1),
      SidechainMemoryPoolEntry(tx2),
      SidechainMemoryPoolEntry(tx3))
    )
    val ret = map.takeLowest(2)
    assertEquals(2, ret.size)
    assertEquals(tx1.id(), ret(0).getUnconfirmedTx().id())
    assertEquals(tx3.id(), ret(1).getUnconfirmedTx().id())
  }

  @Test
  def takeHighest(): Unit = {
    val map = new MempoolMap(List())
    map.add(SidechainMemoryPoolEntry(tx1))
    map.add(SidechainMemoryPoolEntry(tx2))
    map.add(SidechainMemoryPoolEntry(tx3))
    val ret2 = map.takeHighest(2).toArray
    assertEquals(2, ret2.size)
    assertEquals(tx2.id(), ret2(0).getUnconfirmedTx().id())
    assertEquals(tx3.id(), ret2(1).getUnconfirmedTx().id())
  }

  @Test
  def headOption(): Unit = {
    val map = new MempoolMap(List())
    map.add(SidechainMemoryPoolEntry(tx1))
    map.add(SidechainMemoryPoolEntry(tx2))
    map.add(SidechainMemoryPoolEntry(tx3))
    val ret = map.headOption()
    assertEquals(true, ret.isDefined)
    assertEquals(tx1.id(), ret.get.getUnconfirmedTx().id())
  }

  @Test
  def remove(): Unit = {
    val map = new MempoolMap(List())
    map.add(SidechainMemoryPoolEntry(tx1))
    map.add(SidechainMemoryPoolEntry(tx2))
    map.add(SidechainMemoryPoolEntry(tx3))
    assertTrue(map.contains(tx2.id()))
    map.remove(tx2.id())
    assertEquals(2, map.size)
    assertFalse(map.contains(tx2.id()))
    val ret2 = map.takeHighest(2).toArray
    assertEquals(2, ret2.size)
    assertEquals(tx3.id(), ret2(0).getUnconfirmedTx().id())
    assertEquals(tx1.id(), ret2(1).getUnconfirmedTx().id())
  }


  @Test
  def txWithSameSizeAndFee(): Unit = {
    val txSame1 = getRegularRandomTransaction(10,1)
    var txSame2 = getRegularRandomTransaction(10,1)
    while (txSame2.size() != txSame1.size()){ //the size could be slighty different due to nounce lenght
      txSame2 = getRegularRandomTransaction(10,1)
    }
    var txSame3 = getRegularRandomTransaction(10,1)
    while (txSame3.size() != txSame3.size()){ //the size could be slighty different due to nounce lenght
      txSame3 = getRegularRandomTransaction(10,1)
    }

    val map = new MempoolMap(List())
    map.add(SidechainMemoryPoolEntry(txSame1))
    map.add(SidechainMemoryPoolEntry(txSame2))
    map.add(SidechainMemoryPoolEntry(txSame3))
    assertEquals(3, map.size)

    val ret = map.takeLowest(2)
    assertEquals(2, ret.size)
  }



}
