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
import com.horizen.utils.{BlockFeeInfo, ByteArrayWrapper, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.collection.JavaConverters._

class SidechainStateStorageTest
  extends JUnitSuite
    with BoxFixture
    with StoreFixture
    with WithdrawalEpochCertificateFixture
    with SidechainTypes
{

  val customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers)

  val withdrawalEpochInfo: WithdrawalEpochInfo = WithdrawalEpochInfo(0, 0)
  val consensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(1)
  val nextConsensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(2)

  val blockFeeInfo: BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("1234".getBytes()).publicImage())

  @Test
  def mainFlowTest() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion)

    // Verify that withdrawal epoch info and consensus info is not defined
    assertTrue("WithdrawalEpoch info expected to be undefined.", sidechainStateStorage.getWithdrawalEpochInfo.isEmpty)
    assertTrue("ConsensusEpoch info expected to be undefined.", sidechainStateStorage.getConsensusEpochNumber.isEmpty)

    val bList1: List[SidechainTypes#SCB] = getZenBoxList(5).asScala.toList
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
      sidechainStateStorage.update(version1, withdrawalEpochInfo, (bList1 ++ bList2).toSet, Set(), Seq(),
        consensusEpoch, None, blockFeeInfo, None, false).isSuccess)
    assertEquals("Version in storage must be - " + version1,
      version1, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    for (b <- bList1 ++ bList2) {
      assertEquals("Storage must contain specified box - " + b,
        b, sidechainStateStorage.getBox(b.id()).get)
    }

    assertEquals("Different consensus epoch expected.", consensusEpoch, sidechainStateStorage.getConsensusEpochNumber.get)

    // Test delete operation: first ZenBox and first CustomBox
    val boxIdsToRemoveSet: Set[ByteArrayWrapper] = Set(new ByteArrayWrapper(bList1.head.id()), new ByteArrayWrapper(bList2.head.id()))
    assertTrue("Update(delete) operation must be successful.",
      sidechainStateStorage.update(version2, withdrawalEpochInfo, Set(),
        boxIdsToRemoveSet, Seq(), consensusEpoch, None, blockFeeInfo, None, false).isSuccess)

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
  def withdrawalRequestsFlow() : Unit = {
    val rnd = new Random(90)
    val sidechainStateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion)

    // Verify that withdrawal requests info is not defined
    assertTrue("WithdrawalEpoch info expected to be undefined.", sidechainStateStorage.getWithdrawalEpochInfo.isEmpty)
    assertTrue("No withdrawal requests expected to be stored.", sidechainStateStorage.getWithdrawalRequests(withdrawalEpochInfo.epoch).isEmpty)

    val withdrawalRequestsList: List[WithdrawalRequestBox] = getWithdrawalRequestsBoxList(5).asScala.toList

    // Test append withdrawals operation (empty storage).
    val firstWithdrawalEpochNumber: Int = 0

    val mod1Version = getVersion
    val mod1WithdrawalEpochInfo = WithdrawalEpochInfo(firstWithdrawalEpochNumber, 1)
    val mod1mcBlockHashInCertificate = Option(Array.fill(32)(rnd.nextInt().toByte))
    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod1Version, mod1WithdrawalEpochInfo, Set(), Set(), withdrawalRequestsList,
        consensusEpoch, Option(generateWithdrawalEpochCertificate(mod1mcBlockHashInCertificate)), blockFeeInfo, None, false
      ).isSuccess
    )

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
        consensusEpoch, Option(generateWithdrawalEpochCertificate(mod2mcBlockHashInCertificate)), blockFeeInfo, None, false
      ).isSuccess
    )


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
        consensusEpoch, None, blockFeeInfo, None, false
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
        consensusEpoch, None, blockFeeInfo, None, false
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
  }

  @Test
  def feePaymentsFlow() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion)

    val withdrawalEpoch0: Int = 0

    // Verify that withdrawal requests info is not defined
    assertTrue(s"No fee payments for the epoch $withdrawalEpoch0 expected to be stored.",
      sidechainStateStorage.getFeePayments(withdrawalEpoch0).isEmpty)


    // Test append block fee info for given withdrawal epoch (empty storage).
    val mod1Version = getVersion
    val mod1WithdrawalEpochInfo = WithdrawalEpochInfo(withdrawalEpoch0, 1)
    val mod1BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("mod1".getBytes()).publicImage())

    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod1Version, mod1WithdrawalEpochInfo, Set(), Set(), Seq(),
        consensusEpoch, None, mod1BlockFeeInfo, None, false
      ).isSuccess
    )

    assertEquals("Version in storage must be - " + mod1Version,
      mod1Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    // Check that block fee payment info was stored correctly.
    assertEquals(s"Storage expected to have different fee payments for epoch $withdrawalEpoch0",
      Seq(mod1BlockFeeInfo), sidechainStateStorage.getFeePayments(withdrawalEpoch0))


    // Test append block fee info to existing withdrawal epoch payments
    val mod2Version = getVersion
    val mod2WithdrawalEpochInfo = WithdrawalEpochInfo(withdrawalEpoch0, 1)
    val mod2BlockFeeInfo = BlockFeeInfo(200, getPrivateKey25519("mod2".getBytes()).publicImage())

    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod2Version, mod2WithdrawalEpochInfo, Set(), Set(), Seq(),
        consensusEpoch, None, mod2BlockFeeInfo, None, false
      ).isSuccess
    )

    assertEquals("Version in storage must be - " + mod2Version,
      mod2Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 2 version.",
      2, sidechainStateStorage.rollbackVersions.size)

    // Check that block fee payment info was stored correctly.
    assertEquals(s"Storage expected to have different fee payments for epoch $withdrawalEpoch0",
      Seq(mod1BlockFeeInfo, mod2BlockFeeInfo), sidechainStateStorage.getFeePayments(withdrawalEpoch0))


    // Test clean-up of the old fee payments for the previous withdrawal epoch
    // Switch withdrawal epoch number to the epoch 1.
    val mod3Version = getVersion
    val withdrawalEpoch1: Int = 1
    val mod3WithdrawalEpochInfo = WithdrawalEpochInfo(withdrawalEpoch1, 1)
    val mod3BlockFeeInfo = BlockFeeInfo(300, getPrivateKey25519("mod3".getBytes()).publicImage())

    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod3Version, mod3WithdrawalEpochInfo, Set(), Set(), Seq(),
        consensusEpoch, None, mod3BlockFeeInfo, None, false
      ).isSuccess
    )

    // Check that block fee payment info was stored correctly.
    assertEquals(s"Storage expected to have different fee payments for epoch $withdrawalEpoch1",
      Seq(mod3BlockFeeInfo), sidechainStateStorage.getFeePayments(withdrawalEpoch1))

    // Check that fee payments for withdrawal epoch 0 were removed.
    assertEquals(s"Storage expected to have different fee payments for epoch $withdrawalEpoch0",
      Seq(), sidechainStateStorage.getFeePayments(withdrawalEpoch0))


    // Test rollback operation
    assertTrue("Rollback operation must be successful.",
      sidechainStateStorage.rollback(mod1Version).isSuccess)

    assertEquals("Version in storage must be - " + mod1Version,
      mod1Version, sidechainStateStorage.lastVersionId.get)
    assertEquals("Storage must contain 1 version.",
      1, sidechainStateStorage.rollbackVersions.size)

    // Check that block fee payment info records were restored correctly.
    assertEquals(s"Storage expected to have different fee payments for epoch $withdrawalEpoch0",
      Seq(mod1BlockFeeInfo), sidechainStateStorage.getFeePayments(withdrawalEpoch0))
    assertEquals(s"Storage expected to have different fee payments for epoch $withdrawalEpoch1",
      Seq(), sidechainStateStorage.getFeePayments(withdrawalEpoch1))
  }

  @Test
  def testUtxoMerkleTreeRootUpdate() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion)

    val withdrawalEpoch1: Int = 0

    // Verify initial value
    assertFalse(s"Storage must not have ceased flag defined.", sidechainStateStorage.getUtxoMerkleTreeRoot(withdrawalEpoch1).isDefined)



    // Test 1: append block with utxoMerkleRoot.
    val utxoMerkleRoot1: Array[Byte] = FieldElementFixture.generateFieldElement()
    val mod1Version = getVersion
    val mod1WithdrawalEpochInfo = WithdrawalEpochInfo(withdrawalEpoch1, 1)
    val mod1BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("mod1".getBytes()).publicImage())

    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod1Version, mod1WithdrawalEpochInfo, Set(), Set(), Seq(),
        consensusEpoch, None, mod1BlockFeeInfo, Some(utxoMerkleRoot1), scHasCeased = false
      ).isSuccess
    )

    // Verify utxoMerkleRoot value
    val actualRoot1Opt = sidechainStateStorage.getUtxoMerkleTreeRoot(withdrawalEpoch1)
    assertTrue(s"Storage must not have ceased flag defined.", actualRoot1Opt.isDefined)
    assertArrayEquals("Different utxoMerkleRoot value expected.", utxoMerkleRoot1, actualRoot1Opt.get)


    // Test2: append block with utxoMerkleRoot for next epoch
    val utxoMerkleRoot2: Array[Byte] = FieldElementFixture.generateFieldElement()
    val withdrawalEpoch2 = withdrawalEpoch1 + 1
    val mod2Version = getVersion
    val mod2WithdrawalEpochInfo = WithdrawalEpochInfo(withdrawalEpoch2, 2)
    val mod2BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("mod1".getBytes()).publicImage())

    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod2Version, mod2WithdrawalEpochInfo, Set(), Set(), Seq(),
        consensusEpoch, None, mod2BlockFeeInfo, Some(utxoMerkleRoot2), scHasCeased = true
      ).isSuccess
    )

    // Verify utxoMerkleRoot values
    val actualRoot2Opt = sidechainStateStorage.getUtxoMerkleTreeRoot(withdrawalEpoch2)
    assertTrue(s"Storage must not have ceased flag defined.", actualRoot2Opt.isDefined)
    assertArrayEquals("Different utxoMerkleRoot value expected.", utxoMerkleRoot2, actualRoot2Opt.get)

    // Check previous epoch value again
    assertArrayEquals("Different utxoMerkleRoot value expected.",
      utxoMerkleRoot1, sidechainStateStorage.getUtxoMerkleTreeRoot(withdrawalEpoch1).get)
  }

  @Test
  def testHasCeased() : Unit = {
    val sidechainStateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion)

    val withdrawalEpoch0: Int = 0

    // Verify initial value
    assertFalse(s"Storage must not have ceased flag defined.", sidechainStateStorage.hasCeased)


    // Test 1: append block with hasCeased=false.
    val mod1Version = getVersion
    val mod1WithdrawalEpochInfo = WithdrawalEpochInfo(withdrawalEpoch0, 1)
    val mod1BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("mod1".getBytes()).publicImage())

    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod1Version, mod1WithdrawalEpochInfo, Set(), Set(), Seq(),
        consensusEpoch, None, mod1BlockFeeInfo, None, scHasCeased = false
      ).isSuccess
    )

    // Verify hasCeased flag value
    assertFalse(s"Storage must not have ceased flag defined.", sidechainStateStorage.hasCeased)


    // Test2: append block with hasCeased=false.
    val mod2Version = getVersion
    val mod2WithdrawalEpochInfo = WithdrawalEpochInfo(withdrawalEpoch0, 2)
    val mod2BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("mod1".getBytes()).publicImage())

    assertTrue("Update(insert) must be successful.",
      sidechainStateStorage.update(mod2Version, mod2WithdrawalEpochInfo, Set(), Set(), Seq(),
        consensusEpoch, None, mod2BlockFeeInfo, None, scHasCeased = true
      ).isSuccess
    )

    // Verify hasCeased flag value
    assertTrue(s"Storage must have ceased flag defined.", sidechainStateStorage.hasCeased)
  }

  @Test
  def testExceptions(): Unit = {
    val sidechainStateStorage = new SidechainStateStorage(getStorage(), sidechainBoxesCompanion)

    val bList1 = getZenBoxList(5).asScala.toSet
    val version1 = getVersion

    //Try to rollback to non-existent version
    assertTrue("Rollback operation to non-existent version must throw exception.",
      sidechainStateStorage.rollback(version1).isFailure)

    //Try to remove non-existent item
    assertFalse("Remove operation of non-existent item must not throw exception.",
      sidechainStateStorage.update(version1, withdrawalEpochInfo, Set(), bList1.map(b => new ByteArrayWrapper(b.id())),
        Seq(), intToConsensusEpochNumber(0), None, blockFeeInfo, None, false).isFailure)
  }
}
