package com.horizen.integration.storage

import com.horizen.SidechainTypes
import com.horizen.fixtures.{CswDataFixture, StoreFixture}
import com.horizen.storage.{SidechainWalletCswDataStorage, Storage}
import com.horizen.utils.CswData
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
class SidechainWalletCswDataStorageIntegrationTest extends JUnitSuite with StoreFixture with CswDataFixture with SidechainTypes {

  @Test
  def mainFlowTest(): Unit = {
    val cswDataStorage = new SidechainWalletCswDataStorage(getStorage())
    val initialWithdrawalEpoch: Int = 0

    // Check initial withdrawal epoch for empty storage.
    assertEquals("Different withdrawal epoch number found.", initialWithdrawalEpoch, cswDataStorage.getWithdrawalEpoch)
    // Check CSW records for non-existing withdrawal epoch
    assertTrue("No CSW records expected.", cswDataStorage.getCswData(initialWithdrawalEpoch).isEmpty)

    // Test rollback versions of empty storage
    assertTrue("lastVersionId must be empty for empty storage.", cswDataStorage.lastVersionId.isEmpty)
    assertEquals("Storage must not contain versions.", 0, cswDataStorage.rollbackVersions.size)


    // Test 1: update operation (empty storage).
    val version1 = getVersion
    val cswData1: Seq[CswData] = Seq(getUtxoCswData(123L), getForwardTransferCswData(456L))
    assertTrue("Update(insert) must be successful.", cswDataStorage.update(version1, initialWithdrawalEpoch, cswData1).isSuccess)

    assertEquals("Version in storage must be - " + version1, version1, cswDataStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.", 1, cswDataStorage.rollbackVersions.size)

    // Check CSW records
    assertEquals("Different CSW data records found.", cswData1, cswDataStorage.getCswData(initialWithdrawalEpoch))


    // Test 2: update operation (non-empty storage) with the same withdrawal epoch
    val version2 = getVersion
    val cswData2: Seq[CswData] = Seq(getUtxoCswData(1111L), getUtxoCswData(2222L), getUtxoCswData(333L))
    assertTrue("Update(insert) must be successful.", cswDataStorage.update(version2, initialWithdrawalEpoch, cswData2).isSuccess)

    assertEquals("Version in storage must be - " + version2, version2, cswDataStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 versions.", 2, cswDataStorage.rollbackVersions.size)

    // Check CSW records
    assertEquals("Different CSW data records found.", cswData1 ++ cswData2, cswDataStorage.getCswData(initialWithdrawalEpoch))


    // Test 3: update operation with no CSW records
    val version3 = getVersion
    assertTrue("Update(insert) must be successful.", cswDataStorage.update(version3, initialWithdrawalEpoch, Seq()).isSuccess)

    assertEquals("Version in storage must be - " + version3, version3, cswDataStorage.lastVersionId.get)
    assertEquals("Storage must contain 3 versions.", 3, cswDataStorage.rollbackVersions.size)

    // Check CSW records, must be the same as before
    assertEquals("Different CSW data records found.", cswData1 ++ cswData2, cswDataStorage.getCswData(initialWithdrawalEpoch))


    // Test 4: update operation with another Withdrawal Epoch
    val nextWithdrawalEpoch = initialWithdrawalEpoch + 1
    val version4 = getVersion
    val cswData3: Seq[CswData] = Seq(getForwardTransferCswData(4444L))

    assertTrue("Update(insert) must be successful.", cswDataStorage.update(version4, nextWithdrawalEpoch, cswData3).isSuccess)

    assertEquals("Version in storage must be - " + version4, version4, cswDataStorage.lastVersionId.get)
    assertEquals("Storage must contain 4 versions.", 4, cswDataStorage.rollbackVersions.size)

    // Check CSW records
    assertEquals("Different CSW data records found.", cswData1 ++ cswData2, cswDataStorage.getCswData(initialWithdrawalEpoch))
    assertEquals("Different CSW data records found.", cswData3, cswDataStorage.getCswData(nextWithdrawalEpoch))


    // Test 5: update operation with better Withdrawal Epoch that leads to clean up of initial version.
    val anotherWithdrawalEpoch = initialWithdrawalEpoch + 4
    val version5 = getVersion
    assertTrue("Update(insert) must be successful.", cswDataStorage.update(version5, anotherWithdrawalEpoch, Seq()).isSuccess)

    assertEquals("Version in storage must be - " + version5, version5, cswDataStorage.lastVersionId.get)
    assertEquals("Storage must contain 5 versions.", 5, cswDataStorage.rollbackVersions.size)

    // Check CSW records
    assertEquals("Different CSW data records found.", Seq(), cswDataStorage.getCswData(initialWithdrawalEpoch))
    assertEquals("Different CSW data records found.", cswData3, cswDataStorage.getCswData(nextWithdrawalEpoch))
    assertEquals("Different CSW data records found.", Seq(), cswDataStorage.getCswData(anotherWithdrawalEpoch))


    // Test 6: rollback operation
    assertTrue("Rollback operation must be successful.", cswDataStorage.rollback(version1).isSuccess)

    assertEquals("Version in storage must be - " + version1, version1, cswDataStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.", 1, cswDataStorage.rollbackVersions.size)

    // Check CSW records
    assertEquals("Different CSW data records found.", cswData1, cswDataStorage.getCswData(initialWithdrawalEpoch))
    assertEquals("Different CSW data records found.", Seq(), cswDataStorage.getCswData(nextWithdrawalEpoch))
    assertEquals("Different CSW data records found.", Seq(), cswDataStorage.getCswData(anotherWithdrawalEpoch))

  }

  @Test
  def testExceptions(): Unit = {
    val cswDataStorage = new SidechainWalletCswDataStorage(getStorage())

    val version1 = getVersion

    //Try to rollback to non-existent version
    assertTrue("Rollback operation to non-existent version must throw exception.",
      cswDataStorage.rollback(version1).isFailure)
  }
}
