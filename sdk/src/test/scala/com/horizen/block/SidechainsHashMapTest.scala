package com.horizen.block

import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class SidechainsHashMapTest extends JUnitSuite {

  @Test
  def testaddTransactionOutputs(): Unit = {
    val shm = new SidechainsHashMap()

    val scId1 = new ByteArrayWrapper(BytesUtils
      .fromHexString("00000000000000000000000000000000000000000000000000000000deadbeeb"))
    val scId2 = new ByteArrayWrapper(BytesUtils
      .fromHexString("00000000000000000000000000000000000000000000000000000000deadbeed"))
    val scId3 = new ByteArrayWrapper(BytesUtils
      .fromHexString("00000000000000000000000000000000000000000000000000000000deadbeef"))

    shm.addTransactionOutputs(scId1, Seq())
    shm.addTransactionOutputs(scId3, Seq())
    shm.addTransactionOutputs(scId2, Seq())

    val scids = shm.sidechainsHashMap.toSeq.sortWith(_._1 < _._1)

    //val mt = shm.getMerkleTree

  }


}
