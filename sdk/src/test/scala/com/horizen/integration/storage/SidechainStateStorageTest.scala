package com.horizen.integration.storage

import java.lang.{Byte => JByte}
import java.util.{Random, HashMap => JHashMap}

import com.horizen._
import com.horizen.block.WithdrawalEpochCertificateFixture
import com.horizen.box._
import com.horizen.companion._
import com.horizen.consensus._
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.storage._
import com.horizen.utils.{ByteArrayWrapper, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.collection.JavaConverters._

class SidechainStateStorageTest
  extends JUnitSuite
    with BoxFixture
    with IODBStoreFixture
    with WithdrawalEpochCertificateFixture
    with SidechainTypes
{

  val customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers)

  val withdrawalEpochInfo = WithdrawalEpochInfo(0, 0)
  val consensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(1)
  val nextConsensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(2)

  @Test
  def mainFlowTest() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    // Verify that withdrawal epoch info and consensus info is not defined
    assertTrue("WithdrawalEpoch info expected to be undefined.", sidechainStateStorage.getWithdrawalEpochInfo.isEmpty)
    assertTrue("ConsensusEpoch info expected to be undefined.", sidechainStateStorage.getConsensusEpochNumber.isEmpty)
    assertTrue("ForgingStakesAmount info expected to be undefined.", sidechainStateStorage.getForgingStakesAmount.isEmpty)
    assertTrue("ForgingStakesInfo info expected to be undefined.", sidechainStateStorage.getForgingStakesInfo.isEmpty)

    val bList1: List[SidechainTypes#SCB] = getRegularBoxList(5).asScala.toList
    val bList2: List[SidechainTypes#SCB] = getCustomBoxList(3).asScala.map(_.asInstanceOf[SidechainTypes#SCB]).toList

    val version1 = getVersion
    val version2 = getVersion

    // Test rollback versions of empty storage
    assertTrue("lastVersionId must be empty for empty storage.",
      sidechainStateStorage.lastVersionId.isEmpty)
    assertEquals("Storage must not contain versions.",
      0, sidechainStateStorage.rollbackVersions.size)

    // Test insert operation (empty storage).
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(version1, withdrawalEpochInfo, (bList1 ++ bList2).toSet, Set(), Seq(), Seq(), consensusEpoch, None).isSuccess)
    assertEquals("Version in storage must be - " + version1,
      version1, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    for (b <- bList1 ++ bList2) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateStorage.getBox(b.id()).get)
    }

    assertEquals("Different consensus epoch expected.", consensusEpoch, sidechainStateStorage.getConsensusEpochNumber.get)

    // Test delete operation: first RegularBox and first CustomBox
    val boxIdsToRemoveSet: Set[ByteArrayWrapper] = Set(new ByteArrayWrapper(bList1.head.id()), new ByteArrayWrapper(bList2.head.id()))
    assertTrue("Update(delete) operation must be successful.",
      sidechainStateStorage.update(version2, withdrawalEpochInfo, Set(),
        boxIdsToRemoveSet, Seq(), Seq(), consensusEpoch, None).isSuccess)

    assertEquals("Version in storage must be - " + version2,
      version2, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 versions.",
      2, sidechainStateStorage.rollbackVersions.size)

    for (b <- bList1.slice(1, bList1.size) ++ bList2.slice(1, bList2.size)) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateStorage.getBox(b.id()).get)
    }

    for (b <- bList1.slice(0, 1) ++ bList2.slice(0, 1)) {
      assertTrue("Storage must not contain specified box - " + b,
        sidechainStateStorage.getBox(b.id()).isEmpty)
    }

    //Test rollback operation
    assertTrue("Rollback operation must be successful.",
      sidechainStateStorage.rollback(version1).isSuccess)

    assertEquals("Version in storage must be - " + version1,
      version1, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    for (b <- bList1 ++ bList2) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateStorage.getBox(b.id()).get)
    }
  }

  @Test
  def forgerStakesFlow(): Unit = {
    val sidechainStateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    // Verify that consensus info is not defined
    assertTrue("ConsensusEpoch info expected to be undefined.", sidechainStateStorage.getConsensusEpochNumber.isEmpty)
    assertTrue("ForgingStakesAmount info expected to be undefined.", sidechainStateStorage.getForgingStakesAmount.isEmpty)
    assertTrue("ForgingStakesInfo info expected to be undefined.", sidechainStateStorage.getForgingStakesInfo.isEmpty)

    val forgerBoxList: List[SidechainTypes#SCB] = getForgerBoxList(5).asScala.toList
    val forgingStakesToAppendSeq = forgerBoxList.map(box => ForgingStakeInfo(box.id(), box.value()))
    val forgingStakesAmount: Long = forgerBoxList.foldLeft(0L)(_ + _.value())


    // Test insert operation (empty storage).
    val mod1Version = getVersion
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod1Version, withdrawalEpochInfo, forgerBoxList.toSet, Set(), Seq(),
        forgingStakesToAppendSeq, consensusEpoch, None
      ).isSuccess
    )

    assertEquals("Version in storage must be - " + mod1Version,
      mod1Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    for (box <- forgerBoxList) {
      assertEquals("Storage must contain specified forger box - " + box,
        box, sidechainStateStorage.getBox(box.id()).get)
    }

    assertEquals("Different consensus epoch expected.", consensusEpoch, sidechainStateStorage.getConsensusEpochNumber.get)
    assertEquals("Different forging stakes amount expected.", forgingStakesAmount, sidechainStateStorage.getForgingStakesAmount.get)
    assertEquals("Different forging stakes expected.", forgingStakesToAppendSeq, sidechainStateStorage.getForgingStakesInfo.get)


    // Test delete operation: first ForgerBox
    val mod2Version = getVersion
    val boxIdsToRemoveSet: Set[ByteArrayWrapper] = Set(new ByteArrayWrapper(forgerBoxList.head.id()))
    assertTrue("Update(delete) operation must be successful.",
      sidechainStateStorage.update(mod2Version, withdrawalEpochInfo, Set(),
        boxIdsToRemoveSet, Seq(), Seq(), consensusEpoch, None).isSuccess)

    assertEquals("Version in storage must be - " + mod2Version,
      mod2Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 versions.",
      2, sidechainStateStorage.rollbackVersions.size)

    for (box <- forgerBoxList.slice(1, forgerBoxList.size)) {
      assertEquals("Storage must contain specified box - " + box,
        box, sidechainStateStorage.getBox(box.id()).get)
    }

    for (box <- forgerBoxList.slice(0, 1)) {
      assertTrue("Storage must not contain specified box - " + box,
        sidechainStateStorage.getBox(box.id()).isEmpty)
    }

    assertEquals("Different consensus epoch expected.", consensusEpoch, sidechainStateStorage.getConsensusEpochNumber.get)
    assertEquals("Different forging stakes amount expected.", forgingStakesAmount - forgerBoxList.head.value(), sidechainStateStorage.getForgingStakesAmount.get)
    assertEquals("Different forging stakes expected.", forgingStakesToAppendSeq.tail, sidechainStateStorage.getForgingStakesInfo.get)


    // Test updating consensus epoch
    val mod3Version = getVersion
    assertTrue("Update consensus epoch must be successful.",
      sidechainStateStorage.update(mod3Version, withdrawalEpochInfo, Set(), Set(), Seq(), Seq(), nextConsensusEpoch, None).isSuccess)
    assertEquals("Different consensus epoch expected.", nextConsensusEpoch, sidechainStateStorage.getConsensusEpochNumber.get)


    // Test rollback operation
    assertTrue("Rollback operation must be successful.",
      sidechainStateStorage.rollback(mod1Version).isSuccess)

    assertEquals("Version in storage must be - " + mod1Version,
      mod1Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    assertEquals("Different consensus epoch expected.", consensusEpoch, sidechainStateStorage.getConsensusEpochNumber.get)
    assertEquals("Different forging stakes amount expected.", forgingStakesAmount, sidechainStateStorage.getForgingStakesAmount.get)
    assertEquals("Different forging stakes expected.", forgingStakesToAppendSeq, sidechainStateStorage.getForgingStakesInfo.get)

    for (box <- forgerBoxList) {
      assertEquals("Storage must contain specified box - " + box,
        box, sidechainStateStorage.getBox(box.id()).get)
    }
  }

  @Test
  def withdrawalRequestsFlow() : Unit = {
    val rnd = new Random(90)
    val sidechainStateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    // Verify that withdrawal requests info is not defined
    assertTrue("WithdrawalEpoch info expected to be undefined.", sidechainStateStorage.getWithdrawalEpochInfo.isEmpty)
    assertTrue("No withdrawal requests expected to be stored.", sidechainStateStorage.getWithdrawalRequests(withdrawalEpochInfo.epoch).isEmpty)
    assertTrue("No last withdrawal certificate previous McBlock expected to be defined", sidechainStateStorage.getLastCertificateEndEpochMcBlockHashOpt.isEmpty)

    val withdrawalRequestsList: List[WithdrawalRequestBox] = getWithdrawalRequestsBoxList(5).asScala.toList

    // Test append withdrawals operation (empty storage).
    val firstWithdrawalEpochNumber: Int = 0

    val mod1Version = getVersion
    val mod1WithdrawalEpochInfo = WithdrawalEpochInfo(firstWithdrawalEpochNumber, 1)
    val mod1mcBlockHashInCertificate = Option(Array.fill(32)(rnd.nextInt().toByte))
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod1Version, mod1WithdrawalEpochInfo, Set(), Set(), withdrawalRequestsList,
        Seq(), consensusEpoch, Option(generateWithdrawalEpochCertificate(mod1mcBlockHashInCertificate))
      ).isSuccess
    )

    assertEquals(sidechainStateStorage.getLastCertificateEndEpochMcBlockHashOpt, mod1mcBlockHashInCertificate)

    assertEquals("Version in storage must be - " + mod1Version,
      mod1Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    assertEquals("Different WithdrawalEpoch info expected to be stored.",
      mod1WithdrawalEpochInfo, sidechainStateStorage.getWithdrawalEpochInfo.get)

    for (box <- withdrawalRequestsList) {
      assertTrue("Storage must not contain specified forger box - " + box, sidechainStateStorage.getBox(box.id()).isEmpty)
    }
    assertEquals("Storage expected to have different withdrawal requests boxes applied.",
      withdrawalRequestsList, sidechainStateStorage.getWithdrawalRequests(firstWithdrawalEpochNumber))


    // Test append withdrawals to existing withdrawal epoch
    val mod2Version = getVersion
    val newWithdrawalRequestsList: List[WithdrawalRequestBox] = getWithdrawalRequestsBoxList(2).asScala.toList

    val mod2WithdrawalEpochInfo = WithdrawalEpochInfo(firstWithdrawalEpochNumber, 2)
    val mod2mcBlockHashInCertificate = Option(Array.fill(32)(rnd.nextInt().toByte))
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod2Version, mod2WithdrawalEpochInfo, Set(), Set(), newWithdrawalRequestsList,
        Seq(), consensusEpoch, Option(generateWithdrawalEpochCertificate(mod2mcBlockHashInCertificate))
      ).isSuccess
    )
    assertEquals(sidechainStateStorage.getLastCertificateEndEpochMcBlockHashOpt.get.deep, mod2mcBlockHashInCertificate.get.deep)


    assertEquals("Version in storage must be - " + mod2Version,
      mod2Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 version.",
      2, sidechainStateStorage.rollbackVersions.size)

    assertEquals("Different WithdrawalEpoch info expected to be stored.",
      mod2WithdrawalEpochInfo, sidechainStateStorage.getWithdrawalEpochInfo.get)

    for (box <- newWithdrawalRequestsList) {
      assertTrue("Storage must not contain specified forger box - " + box, sidechainStateStorage.getBox(box.id()).isEmpty)
    }
    assertEquals("Storage expected to have different withdrawal requests boxes applied.",
      withdrawalRequestsList ++ newWithdrawalRequestsList, sidechainStateStorage.getWithdrawalRequests(firstWithdrawalEpochNumber))


    // Test clean-up for the old withdrawals from 2 epoch before
    // Switch withdrawal epoch number to the second one.
    val mod3Version = getVersion
    val secondWithdrawalEpochNumber: Int = 1
    val mod3WithdrawalEpochInfo = WithdrawalEpochInfo(secondWithdrawalEpochNumber, 1)
    val secondEpochWithdrawalRequestsList: List[WithdrawalRequestBox] = getWithdrawalRequestsBoxList(2).asScala.toList
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod3Version, mod3WithdrawalEpochInfo, Set(), Set(), secondEpochWithdrawalRequestsList,
        Seq(), consensusEpoch, None
      ).isSuccess
    )
    // Check that first epoch withdrawals still exist -> no clean-up
    assertEquals("Storage expected to have different withdrawal requests boxes applied.",
      withdrawalRequestsList ++ newWithdrawalRequestsList, sidechainStateStorage.getWithdrawalRequests(firstWithdrawalEpochNumber))

    // Switch withdrawal epoch number to the third one -> first epoch withdrawals expected to be removed.
    val mod4Version = getVersion
    val thirdWithdrawalEpochNumber: Int = 2
    val mod4WithdrawalEpochInfo = WithdrawalEpochInfo(thirdWithdrawalEpochNumber, 1)
    val thirdEpochWithdrawalRequestsList: List[WithdrawalRequestBox] = getWithdrawalRequestsBoxList(3).asScala.toList
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod4Version, mod4WithdrawalEpochInfo, Set(), Set(), thirdEpochWithdrawalRequestsList,
        Seq(), consensusEpoch, None
      ).isSuccess
    )
    // Check that first epoch withdrawals were removed.
    assertEquals("Storage expected to have different withdrawal requests boxes applied.",
      Seq(), sidechainStateStorage.getWithdrawalRequests(firstWithdrawalEpochNumber))


    // Test rollback operation
    assertTrue("Rollback operation must be successful.",
      sidechainStateStorage.rollback(mod1Version).isSuccess)

    assertEquals("Version in storage must be - " + mod1Version,
      mod1Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    assertEquals("Different WithdrawalEpoch info expected to be stored.",
      mod1WithdrawalEpochInfo, sidechainStateStorage.getWithdrawalEpochInfo.get)
    assertEquals("Storage expected to have different withdrawal requests boxes applied.",
      withdrawalRequestsList, sidechainStateStorage.getWithdrawalRequests(mod1WithdrawalEpochInfo.epoch))
    assertEquals(sidechainStateStorage.getLastCertificateEndEpochMcBlockHashOpt.get.deep, mod1mcBlockHashInCertificate.get.deep)
  }

  @Test
  def unprocessedWithdrawalRequestsFlow() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    // Verify that withdrawal requests info is not defined
    assertTrue("WithdrawalEpoch info expected to be undefined.", sidechainStateStorage.getWithdrawalEpochInfo.isEmpty)
    assertTrue("No withdrawal requests expected to be stored.", sidechainStateStorage.getWithdrawalRequests(withdrawalEpochInfo.epoch).isEmpty)
    assertEquals("No unprocessedwithdrawal requests expected to be stored.",
      Some(Seq()), sidechainStateStorage.getUnprocessedWithdrawalRequests(withdrawalEpochInfo.epoch))

    val withdrawalRequestsList: List[WithdrawalRequestBox] = getWithdrawalRequestsBoxList(5).asScala.toList

    // Append withdrawals for the first epoch. No certificate for this epoch.
    val firstWithdrawalEpochNumber: Int = 0

    val mod1Version = getVersion
    val mod1WithdrawalEpochInfo = WithdrawalEpochInfo(firstWithdrawalEpochNumber, 1)
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod1Version, mod1WithdrawalEpochInfo, Set(), Set(), withdrawalRequestsList,
        Seq(), consensusEpoch, None
      ).isSuccess
    )

    // Test: storage expect to have defined unprocessed withdrawal requests for the first epoch
    assertEquals("Storage expected to have different unprocessed withdrawal requests boxes applied.",
      Some(withdrawalRequestsList), sidechainStateStorage.getUnprocessedWithdrawalRequests(firstWithdrawalEpochNumber))


    // Test: set the modifier contains certificate for the first epoch -> unprocessed withdrawal request
    // for the first epoch expected to be None.
    val secondWithdrawalEpochNumber: Int = 1
    val certificateIsPresent: Boolean = true

    val mod2Version = getVersion
    val mod2WithdrawalEpochInfo = WithdrawalEpochInfo(secondWithdrawalEpochNumber, 1)
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod2Version, mod2WithdrawalEpochInfo, Set(), Set(), Seq(),
        Seq(), consensusEpoch, Option(generateWithdrawalEpochCertificate())
      ).isSuccess
    )
  }

  @Test
  def testExceptions(): Unit = {
    val sidechainStateStorage = new SidechainStateStorage(new IODBStoreAdapter(getStore()), sidechainBoxesCompanion)

    val bList1 = getRegularBoxList(5).asScala.toSet
    val version1 = getVersion

    //Try to rollback to non-existent version
    assertTrue("Rollback operation to non-existent version must throw exception.",
      sidechainStateStorage.rollback(version1).isFailure)

    //Try to remove non-existent item
    assertFalse("Remove operation of non-existent item must not throw exception.",
      sidechainStateStorage.update(version1, withdrawalEpochInfo, Set(), bList1.map(b => new ByteArrayWrapper(b.id())),
        Seq(), Seq(), intToConsensusEpochNumber(0), None).isFailure)
  }
}
