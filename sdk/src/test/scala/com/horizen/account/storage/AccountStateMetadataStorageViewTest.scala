package com.horizen.account.storage

import com.google.common.primitives.Ints
import com.horizen.SidechainTypes
import com.horizen.block.{WithdrawalEpochCertificate, WithdrawalEpochCertificateFixture}
import com.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import com.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import com.horizen.utils.{BlockFeeInfo, BytesUtils, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.core._

import scala.collection.mutable.ArrayBuffer
import scala.io.Source
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

    assertEquals("Initial height should be 0 in view", 0, storageView.getHeight)
    assertEquals("Initial height should be 0 in storage", 0, stateMetadataStorage.getHeight)

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
    assertEquals("Block fee is not present in view", 1, storageView.getFeePayments(currentEpoch).size)
    assertEquals("Block fee is present in storage", 0, stateMetadataStorage.getFeePayments(currentEpoch).size)

    storageView.updateTopQualityCertificate(generateCertificateWithEpochNumber(currentEpoch))
    assertFalse("Certificate is not present in view", storageView.getTopQualityCertificate(currentEpoch).isEmpty)
    assertTrue("Certificate is present in storage", stateMetadataStorage.getTopQualityCertificate(currentEpoch).isEmpty)

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
    assertEquals("Certificate is different in view and in storage after a commit", storageView.getTopQualityCertificate(currentEpoch).get.epochNumber,
      stateMetadataStorage.getTopQualityCertificate(currentEpoch).get.epochNumber)

    assertEquals("Height after commit should be 1 in view", 1, storageView.getHeight)
    assertEquals("Height after commit should be 1 in storage", 1, stateMetadataStorage.getHeight)

    assertEquals("Wrong Consensus epoch number in view after commit", consensusEpochNum, storageView.getConsensusEpochNumber.get)
    assertEquals("Wrong Consensus epoch number in storage after commit", consensusEpochNum, stateMetadataStorage.getConsensusEpochNumber.get)

  }

  @Test
  def testDeleteOldData(): Unit = {
    val storageView: AccountStateMetadataStorageView = stateMetadataStorage.getView
    val maxNumOfCertificateInStorage = 4

    val firstEpochToProcess = 100
    val lastEpochToProcess = firstEpochToProcess + maxNumOfCertificateInStorage + 2
    (firstEpochToProcess to lastEpochToProcess).foreach(epochNumber => {
      storageView.updateWithdrawalEpochInfo(WithdrawalEpochInfo(epochNumber, 1))
      storageView.updateTopQualityCertificate(generateCertificateWithEpochNumber(epochNumber))
      storageView.addFeePayment(BlockFeeInfo(230, getPrivateKey25519("8333".getBytes()).publicImage()))
      storageView.updateAccountStateRoot(getAccountStateRoot)
      storageView.commit(bytesToVersion(getVersion.data()))

      assertTrue(storageView.getFeePayments(epochNumber).size == 1)

      (epochNumber - 1 to firstEpochToProcess by -1).foreach(previousEpoch => {
        assertTrue("Old fee payment is still present", storageView.getFeePayments(previousEpoch).isEmpty)
        if (epochNumber - previousEpoch < maxNumOfCertificateInStorage)
          assertTrue(s"Certificate not older than $maxNumOfCertificateInStorage epoch should still be present - Current epoch: $epochNumber. old epoch: $previousEpoch",
            storageView.getTopQualityCertificate(previousEpoch).isDefined)
        else
          assertTrue(s"Certificate older than $maxNumOfCertificateInStorage epoch should not still be present - Current epoch: $epochNumber. old epoch: $previousEpoch",
            storageView.getTopQualityCertificate(previousEpoch).isEmpty)

      }
      )
    }
    )
  }


  def generateCertificateWithEpochNumber(epochNum: Int): WithdrawalEpochCertificate = {
    val sourceCertHex: String = Source.fromResource("cert_no_bts").getLines().next()
    val newCertBytes: ArrayBuffer[Byte] = ArrayBuffer[Byte]()
    newCertBytes.appendAll(BytesUtils.fromHexString(sourceCertHex))

    val newEpochBytes: Array[Byte] = Ints.toByteArray(epochNum)
    (0 to 3).foreach(idx => newCertBytes(36 + idx) = newEpochBytes(3 - idx))
    WithdrawalEpochCertificate.parse(newCertBytes.toArray, 0)
  }


  def getAccountStateRoot: Array[Byte] = {
    val value = new Array[Byte](valueSize)
    Random.nextBytes(value)
    value
  }

}
