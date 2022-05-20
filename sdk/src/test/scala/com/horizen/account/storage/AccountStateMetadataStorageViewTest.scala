package com.horizen.account.storage

import com.horizen.SidechainTypes
import com.horizen.block.WithdrawalEpochCertificateFixture
import com.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import com.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core._

import scala.util.Random

class AccountStateMetadataStorageViewTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypes
    with WithdrawalEpochCertificateFixture {

  val stateMetadataStorage = new AccountStateMetadataStorage(getStorage())


  @Test
  def testUpdate(): Unit = {
    val storageView: AccountStateMetadataStorageView = stateMetadataStorage.getView

    assertFalse("Sidechain is ceased in view", storageView.hasCeased)
    assertFalse("Sidechain is ceased in storage", stateMetadataStorage.hasCeased)

    assertEquals("Initial height should be 0 in view",0, storageView.getHeight )
    assertEquals("Initial height should be 0 in storage",0, stateMetadataStorage.getHeight )

    assertTrue("No withdrawal epoch info should be present in view", storageView.getWithdrawalEpochInfo.isEmpty)
    assertTrue("No withdrawal epoch info should be present in storage", stateMetadataStorage.getWithdrawalEpochInfo.isEmpty)

    assertTrue("Block fee info should be empty in view", storageView.getFeePayments(0).isEmpty)
    assertTrue("Block fee info should be empty in storage", stateMetadataStorage.getFeePayments(0).isEmpty)

    assertTrue("Consensus epoch number should be empty in view", storageView.getConsensusEpochNumber.isEmpty)
    assertTrue("Consensus epoch number should be empty in storage", stateMetadataStorage.getConsensusEpochNumber.isEmpty)


    //Starting modification of the view and checking that view and storage are no more aligned

    val currentEpoch: Int = 1
    storageView.setCeased()
    assertTrue("Sidechain is not ceased in view", storageView.hasCeased)
    assertFalse("Sidechain is ceased in storage", stateMetadataStorage.hasCeased)

    storageView.updateWithdrawalEpochInfo(WithdrawalEpochInfo(currentEpoch, 1))
    assertFalse("No withdrawal epoch info is present in view", storageView.getWithdrawalEpochInfo.isEmpty)
    assertTrue("No withdrawal epoch info should be present in storage yet", stateMetadataStorage.getWithdrawalEpochInfo.isEmpty)

    storageView.addFeePayment(BlockFeeInfo(100, getPrivateKey25519("8333".getBytes()).publicImage()))
    assertEquals("Block fee not present in view", 1, storageView.getFeePayments(currentEpoch).size)
    assertEquals("Block fee is present in storage", 0, stateMetadataStorage.getFeePayments(currentEpoch).size)

    storageView.updateAccountStateRoot(getAccountStateRoot)
    assertFalse("Account State not present in view", storageView.getAccountStateRoot.isEmpty)
    assertTrue("Account State present in storage", stateMetadataStorage.getAccountStateRoot.isEmpty)

    val consensusEpochNum: ConsensusEpochNumber = intToConsensusEpochNumber(3)
    storageView.updateConsensusEpochNumber(consensusEpochNum)
    assertTrue("Consensus epoch number should be defined in view", storageView.getConsensusEpochNumber.isDefined)
    assertTrue("Consensus epoch number should be empty in storage", stateMetadataStorage.getConsensusEpochNumber.isEmpty)


    storageView.commit(bytesToVersion(getVersion.data()))

    assertEquals("Sidechain ceased state is different in view and in storage after a commit", storageView.hasCeased, stateMetadataStorage.hasCeased)
    assertEquals("Withdrawal epoch info is different in view and in storage after a commit", storageView.getWithdrawalEpochInfo, stateMetadataStorage.getWithdrawalEpochInfo)
    assertEquals("Block fee info is different in view and in storage after a commit", storageView.getFeePayments(currentEpoch).size,
      stateMetadataStorage.getFeePayments(currentEpoch).size)
    assertTrue("Account State root is different in view and in storage after a commit", java.util.Arrays.compare(storageView.getAccountStateRoot.get,
      stateMetadataStorage.getAccountStateRoot.get) == 0)

    assertEquals("Height after commit should be 1 in view",1, storageView.getHeight )
    assertEquals("Height after commit should be 1 in storage",1, stateMetadataStorage.getHeight )

    assertEquals("Wrong Consensus epoch number in view after commit", consensusEpochNum, storageView.getConsensusEpochNumber.get)
    assertEquals("Wrong Consensus epoch number in storage after commit", consensusEpochNum,stateMetadataStorage.getConsensusEpochNumber.get)

  }

  @Test
  def testDeleteOldData(): Unit = {
    val storageView: AccountStateMetadataStorageView = stateMetadataStorage.getView

    (100 to 104).foreach(epochNumber => {
      storageView.updateWithdrawalEpochInfo(WithdrawalEpochInfo(epochNumber, 1))
      val certificate = generateWithdrawalEpochCertificate(epoch = epochNumber)
      storageView.updateTopQualityCertificate(certificate)
      storageView.addFeePayment(BlockFeeInfo(100, getPrivateKey25519("8333".getBytes()).publicImage()))
      storageView.updateAccountStateRoot(getAccountStateRoot)
      storageView.commit(bytesToVersion(getVersion.data()))
      assertTrue(storageView.getFeePayments(epochNumber).size == 1)
      if(epochNumber>100){
        assertTrue(storageView.getFeePayments(epochNumber - 1).isEmpty)
      }
    })

  }

  def getAccountStateRoot: Array[Byte] = {
    val value = new Array[Byte](valueSize)
    Random.nextBytes(value)
    value
  }

}
