package com.horizen

import org.scalatest.junit.JUnitSuite
import org.junit.{Before, Test}
import org.junit.Assert._

import com.horizen.fixtures._
import scorex.util.ModifierId

class SidechainMemoryPoolTest extends JUnitSuite
  with SidechainMemoryPoolFixture
  with TransactionFixture
{

  @Test def remove(): Unit = {
    val memoryPool = getSidechainMemoryPool()
    val tx = getTransaction().asInstanceOf[SidechainTypes#BT]
    val txId : ModifierId = ModifierId(tx.id())

    assertEquals("Put operation must be success.", memoryPool.put(tx).isSuccess, true);
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction" + txId, memoryPool.modifierById(txId).get, tx)

    assertEquals("Put operation must be success.", memoryPool.put(getCompatibleTransaction().asInstanceOf[SidechainTypes#BT]).isSuccess,
      true)
    assertEquals("Size must be 2.", memoryPool.size, 2)

    memoryPool.remove(tx)
    //val h = memoryPool.notIn(Seq(txId))
    assertEquals("Size must be 1.", memoryPool.size, 1)
    //assertEquals("", memoryPool.notIn())
    //assertEquals("MemoryPool must not contain transaction" + txId, memoryPool.modifierById(txId).get., tx)

  }

  @Test def put(): Unit = {
    val memoryPool = getSidechainMemoryPool()
    val tx = getTransaction().asInstanceOf[SidechainTypes#BT]
    val txId : ModifierId = ModifierId(tx.id())

    assertEquals("Put operation must be success.", memoryPool.put(tx).isSuccess, true);
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction" + txId, memoryPool.modifierById(txId).get, tx)
    assertEquals("MemoryPool must contain transaction" + txId, memoryPool.contains(txId), true)

    assertEquals("Put operation must be success.", memoryPool.put(getCompatibleTransaction().asInstanceOf[SidechainTypes#BT]).isSuccess,
          true)
    assertEquals("Size must be 2.", memoryPool.size, 2)

    assertEquals("Put operation must be failure.", memoryPool.put(getIncompatibleTransaction().asInstanceOf[SidechainTypes#BT]).isSuccess,
      false)
    assertEquals("Size must be 2.", memoryPool.size, 2)
  }

  @Test def putSeq(): Unit = {
    val memoryPool = getSidechainMemoryPool()
    val txLst = getTransactionList().asInstanceOf[List[SidechainTypes#BT]]
    val txId : ModifierId = ModifierId(txLst.head.id())

    val txIncompat = txLst ::: getIncompatibleTransactionList().asInstanceOf[List[SidechainTypes#BT]]
    assertEquals("Put operation must be failure.", memoryPool.put(txIncompat).isSuccess, false);
    assertEquals("Size must be 0.", memoryPool.size, 0)

    assertEquals("Put operation must be success.", memoryPool.put(txLst).isSuccess, true);
    assertEquals("Size must be 1.", memoryPool.size, 1)
    assertEquals("MemoryPool must contain transaction" + txId, memoryPool.modifierById(txId).get, txLst.head)

    assertEquals("Put operation must be success.", memoryPool.put(getCompatibleTransactionList().asInstanceOf[List[SidechainTypes#BT]]).isSuccess,
      true)
    assertEquals("Size must be 2.", memoryPool.size, 2)

    assertEquals("Put operation must be failure.", memoryPool.put(getIncompatibleTransactionList().asInstanceOf[List[SidechainTypes#BT]]).isSuccess,
      false)
    assertEquals("Size must be 2.", memoryPool.size, 2)
  }

  @Test def putWithoutCheck(): Unit = {
    val memoryPool = getSidechainMemoryPool()
    val txLst = getTransactionList().asInstanceOf[List[SidechainTypes#BT]]
    var et : Boolean = false

    try {
      memoryPool.putWithoutCheck(txLst)
    }
    catch {
      case e : UnsupportedOperationException => et = true
      case unknown => fail("Wrong exception type.", unknown)
    }

    assertEquals("Exception must be thrown.", et, true)
  }
}
