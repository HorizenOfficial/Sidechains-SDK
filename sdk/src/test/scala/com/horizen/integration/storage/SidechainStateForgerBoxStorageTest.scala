package com.horizen.integration.storage

import com.horizen.SidechainTypes
import com.horizen.box.ForgerBox
import com.horizen.fixtures.{BoxFixture, IODBStoreFixture}
import com.horizen.storage.{IODBStoreAdapter, SidechainStateForgerBoxStorage}
import com.horizen.utils.ByteArrayWrapper
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.junit.Assert._

import scala.collection.JavaConverters._

class SidechainStateForgerBoxStorageTest
  extends JUnitSuite
    with BoxFixture
    with IODBStoreFixture
    with SidechainTypes {

  @Test
  def mainFlowTest(): Unit = {
    val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(new IODBStoreAdapter(getStore()))

    // Verify that forger boxes seq is empty
    assertTrue("ForgerBox seq expected to be empty.", sidechainStateForgerBoxStorage.getAllForgerBoxes.isEmpty)

    val forgerBoxesSeq: Seq[ForgerBox] = getForgerBoxList(5).asScala

    val version1 = getVersion
    val version2 = getVersion


    // Test rollback versions of empty storage
    assertTrue("lastVersionId must be empty for empty storage.",
      sidechainStateForgerBoxStorage.lastVersionId.isEmpty)
    assertEquals("Storage must not contain versions.",
      0, sidechainStateForgerBoxStorage.rollbackVersions.size)


    // Test insert operation (empty storage).
    assertTrue("Update(insert) must be successful.",
      sidechainStateForgerBoxStorage.update(version1, forgerBoxesSeq, Set()).isSuccess)
    assertEquals("Version in storage must be - " + version1,
      version1, sidechainStateForgerBoxStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateForgerBoxStorage.rollbackVersions.size)

    for (b <- forgerBoxesSeq) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateForgerBoxStorage.getForgerBox(b.id()).get)
    }

    assertEquals("Storage all ForgerBox sequence list is different",
      sidechainStateForgerBoxStorage.getAllForgerBoxes.toSet, forgerBoxesSeq.toSet)


    // Test insert operation (non-empty sorage) and delete operation for the first ForgerBox
    val anotherForgerBoxesSeq: Seq[ForgerBox] = getForgerBoxList(2).asScala

    val boxIdsToRemoveSet: Set[ByteArrayWrapper] = Set(new ByteArrayWrapper(forgerBoxesSeq.head.id()))
    assertTrue("Update/delete operation must be successful.",
      sidechainStateForgerBoxStorage.update(version2, anotherForgerBoxesSeq, boxIdsToRemoveSet).isSuccess)

    assertEquals("Version in storage must be - " + version2,
      version2, sidechainStateForgerBoxStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 versions.",
      2, sidechainStateForgerBoxStorage.rollbackVersions.size)

    for (b <- forgerBoxesSeq.tail ++ anotherForgerBoxesSeq) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateForgerBoxStorage.getForgerBox(b.id()).get)
    }

    assertTrue("Storage must not contain specified box - " + forgerBoxesSeq.head,
      sidechainStateForgerBoxStorage.getForgerBox(forgerBoxesSeq.head.id()).isEmpty)

    assertEquals("Storage all ForgerBox sequence list is different",
      sidechainStateForgerBoxStorage.getAllForgerBoxes.toSet, (forgerBoxesSeq.tail ++ anotherForgerBoxesSeq).toSet)


    //Test rollback operation
    assertTrue("Rollback operation must be successful.",
      sidechainStateForgerBoxStorage.rollback(version1).isSuccess)

    assertEquals("Version in storage must be - " + version1,
      version1, sidechainStateForgerBoxStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateForgerBoxStorage.rollbackVersions.size)

    for (b <- forgerBoxesSeq) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateForgerBoxStorage.getForgerBox(b.id()).get)
    }

    for (b <- anotherForgerBoxesSeq) {
      assertTrue("Storage must not contain specified box - " + b,
        sidechainStateForgerBoxStorage.getForgerBox(b.id()).isEmpty)
    }

    assertEquals("Storage all ForgerBox sequence list is different",
      sidechainStateForgerBoxStorage.getAllForgerBoxes.toSet, forgerBoxesSeq.toSet)
  }

  @Test
  def testExceptions(): Unit = {
    val sidechainStateForgerBoxStorage = new SidechainStateForgerBoxStorage(new IODBStoreAdapter(getStore()))

    val forgerBoxesSeq: Seq[ForgerBox] = getForgerBoxList(5).asScala
    val version1 = getVersion

    //Try to rollback to non-existent version
    assertTrue("Rollback operation to non-existent version must throw exception.",
      sidechainStateForgerBoxStorage.rollback(version1).isFailure)

    //Try to remove non-existent item
    assertFalse("Remove operation of non-existent item must not throw exception.",
      sidechainStateForgerBoxStorage.update(version1, Seq(), forgerBoxesSeq.map(b => new ByteArrayWrapper(b.id())).toSet).isFailure)
  }
}
