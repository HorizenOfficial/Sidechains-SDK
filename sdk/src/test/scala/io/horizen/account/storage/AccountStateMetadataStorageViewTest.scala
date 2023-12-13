package io.horizen.account.storage

import com.google.common.primitives.Ints
import io.horizen.SidechainTypes
import io.horizen.account.state.receipt.{EthereumReceipt, ReceiptFixture}
import io.horizen.account.storage.AccountStateMetadataStorageView.DEFAULT_ACCOUNT_STATE_ROOT
import io.horizen.account.utils.AccountBlockFeeInfo
import io.horizen.block.{WithdrawalEpochCertificate, WithdrawalEpochCertificateFixture}
import io.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import io.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import io.horizen.storage.Storage
import io.horizen.utils.{ByteArrayWrapper, BytesUtils, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito.when
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core._

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.Optional
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.io.Source
import scala.util.Random

class AccountStateMetadataStorageViewTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypes
    with WithdrawalEpochCertificateFixture
    with ReceiptFixture {

  val stateMetadataStorage = new AccountStateMetadataStorage(getStorage())


  @Test
  def testUpdate(): Unit = {
    val storageView: AccountStateMetadataStorageView = stateMetadataStorage.getView

    assertFalse("Sidechain is ceased in view", storageView.hasCeased)
    assertFalse("Sidechain is ceased in storage", stateMetadataStorage.hasCeased)

    assertEquals("Initial height should be 0 in view", 0, storageView.getHeight)
    assertEquals("Initial height should be 0 in storage", 0, stateMetadataStorage.getHeight)

    assertTrue("Only default epoch info should be present in view", storageView.getWithdrawalEpochInfo.epoch == 0)
    assertTrue("Only default epoch info should be present in view", storageView.getWithdrawalEpochInfo.lastEpochIndex == 0)
    assertTrue("Only default epoch info should be present in storage", stateMetadataStorage.getWithdrawalEpochInfo.epoch == 0)
    assertTrue("Only default epoch info should be present in storage", stateMetadataStorage.getWithdrawalEpochInfo.lastEpochIndex == 0)

    assertTrue("Block fee info should be empty in view", storageView.getFeePayments(0).isEmpty)
    assertTrue("Block fee info should be empty in storage", stateMetadataStorage.getFeePayments(0).isEmpty)

    assertTrue("Consensus epoch number should be empty in view", storageView.getConsensusEpochNumber.isEmpty)
    assertTrue("Consensus epoch number should be empty in storage", stateMetadataStorage.getConsensusEpochNumber.isEmpty)

    assertTrue("Last certificate referenced epoch number should be empty in view", storageView.lastCertificateReferencedEpoch.isEmpty)
    assertTrue("Last certificate referenced epoch number should be empty in storage", stateMetadataStorage.lastCertificateReferencedEpoch.isEmpty)

    //Starting modification of the view and checking that view and storage are no more aligned

    val currentEpoch: Int = 1
    storageView.setCeased()
    assertTrue("Sidechain is not ceased in view", storageView.hasCeased)
    assertFalse("Sidechain is ceased in storage", stateMetadataStorage.hasCeased)

    storageView.updateWithdrawalEpochInfo(WithdrawalEpochInfo(currentEpoch, 1))
    assertTrue("epoch info should be present in view", storageView.getWithdrawalEpochInfo.epoch == currentEpoch)
    assertTrue("epoch info should be present in view", storageView.getWithdrawalEpochInfo.lastEpochIndex == 1)
    assertTrue("Only default epoch info should be present in storage", stateMetadataStorage.getWithdrawalEpochInfo.epoch == 0)
    assertTrue("Only default epoch info should be present in storage", stateMetadataStorage.getWithdrawalEpochInfo.lastEpochIndex == 0)

    storageView.updateFeePaymentInfo(AccountBlockFeeInfo(BigInteger.valueOf(100), BigInteger.valueOf(50), getPrivateKeySecp256k1(8333).publicImage()))
    assertEquals("Block fee is not present in view", 1, storageView.getFeePayments(currentEpoch).size)
    assertEquals("Block fee is present in storage", 0, stateMetadataStorage.getFeePayments(currentEpoch).size)

    storageView.updateTopQualityCertificate(generateCertificateWithEpochNumber(currentEpoch))
    assertFalse("Certificate is not present in view", storageView.getTopQualityCertificate(currentEpoch).isEmpty)
    assertTrue("Certificate is present in storage", stateMetadataStorage.getTopQualityCertificate(currentEpoch).isEmpty)

    // Check state root of empty storage and view
    assertArrayEquals("Non-default account state was set in the view", DEFAULT_ACCOUNT_STATE_ROOT, storageView.getAccountStateRoot)
    val root = getRandomAccountStateRoot
    storageView.updateAccountStateRoot(root)
    assertArrayEquals("Default account state was set in the view", root, storageView.getAccountStateRoot)
    assertArrayEquals("Non-default account state was set in the storage", DEFAULT_ACCOUNT_STATE_ROOT, stateMetadataStorage.getAccountStateRoot)

    val consensusEpochNum: ConsensusEpochNumber = intToConsensusEpochNumber(3)
    storageView.updateConsensusEpochNumber(consensusEpochNum)
    assertTrue("Consensus epoch number should be defined in view", storageView.getConsensusEpochNumber.isDefined)
    assertTrue("Consensus epoch number should be empty in storage", stateMetadataStorage.getConsensusEpochNumber.isEmpty)

    val lastCertificateReferencedEpoch: Int = 4
    storageView.updateLastCertificateReferencedEpoch(lastCertificateReferencedEpoch)
    assertTrue("Last certificate referenced epoch number should be defined in view", storageView.lastCertificateReferencedEpoch.isDefined)
    assertEquals("Last certificate referenced epoch number should be correct", lastCertificateReferencedEpoch,storageView.lastCertificateReferencedEpoch.get)
    assertTrue("Last certificate referenced epoch number should be empty in storage", stateMetadataStorage.lastCertificateReferencedEpoch.isEmpty)

    val receipts = new ListBuffer[EthereumReceipt]()
    val receipt1 = createTestEthereumReceipt(0)
    val receipt2 = createTestEthereumReceipt(1)

    receipts += receipt1
    receipts += receipt2
    storageView.updateTransactionReceipts(receipts)
    assertTrue("receipts should be defined in view", storageView.getTransactionReceipt(receipt1.transactionHash).isDefined)
    assertTrue("receipts should not be in storage", stateMetadataStorage.getTransactionReceipt(receipt1.transactionHash).isEmpty)

    storageView.commit(bytesToVersion(getVersion.data()))

    assertEquals("Sidechain ceased state is different in view and in storage after a commit", storageView.hasCeased, stateMetadataStorage.hasCeased)
    assertEquals("Withdrawal epoch info is different in view and in storage after a commit", storageView.getWithdrawalEpochInfo, stateMetadataStorage.getWithdrawalEpochInfo)
    assertEquals("Block fee info is different in view and in storage after a commit", storageView.getFeePayments(currentEpoch).size,
      stateMetadataStorage.getFeePayments(currentEpoch).size)
    assertArrayEquals("Account State root is different in view and in storage after a commit", storageView.getAccountStateRoot,
      stateMetadataStorage.getAccountStateRoot)
    assertEquals("Certificate is different in view and in storage after a commit", storageView.getTopQualityCertificate(currentEpoch).get.epochNumber,
      stateMetadataStorage.getTopQualityCertificate(currentEpoch).get.epochNumber)

    assertEquals("Height after commit should be 1 in view", 1, storageView.getHeight)
    assertEquals("Height after commit should be 1 in storage", 1, stateMetadataStorage.getHeight)

    assertEquals("Wrong Consensus epoch number in view after commit", consensusEpochNum, storageView.getConsensusEpochNumber.get)
    assertEquals("Wrong Consensus epoch number in storage after commit", consensusEpochNum, stateMetadataStorage.getConsensusEpochNumber.get)

    assertEquals("Wrong last certificate referenced epoch number value in view after commit", lastCertificateReferencedEpoch, storageView.lastCertificateReferencedEpoch.get)
    assertEquals("Wrong last certificate referenced epoch number value in storage after commit", lastCertificateReferencedEpoch, stateMetadataStorage.lastCertificateReferencedEpoch.get)

    assertEquals("Wrong receipts in view after commit", receipt1.blockNumber, storageView.getTransactionReceipt(receipt1.transactionHash).get.blockNumber)
    assertEquals("Wrong receipts in storage after commit", receipt1.blockNumber, stateMetadataStorage.getTransactionReceipt(receipt1.transactionHash).get.blockNumber)

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
      storageView.updateFeePaymentInfo(AccountBlockFeeInfo(BigInteger.valueOf(100), BigInteger.valueOf(50), getPrivateKeySecp256k1(8333).publicImage()))
      storageView.updateLastCertificateReferencedEpoch((epochNumber))
      storageView.updateAccountStateRoot(getRandomAccountStateRoot)
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

  @Test
  def doNotRemoveOldCertificateIfAreStillNeededAsPreviousCertificate(): Unit = {
    // Arrange
    val storageMock = mock[Storage]
    val metadataStorageView = new AccountStateMetadataStorageView(storageMock)
    val storageCertKey = new ByteArrayWrapper(BytesUtils.fromHexString("fecbe6fcc71e68d667c602c6112bec5e365c77f0fcb829d2502afb4e0608eaba"))

    when(storageMock.get(storageCertKey)).thenAnswer(_ => Optional.empty())

    // Act
    val oldCertToBeRemoved = metadataStorageView.getOldTopCertificatesToBeRemoved(WithdrawalEpochInfo(5, 5))

    // Assert
    assertTrue(oldCertToBeRemoved.isEmpty)
  }

  @Test
  def removeOldCertificateIfCertificateWasAlreadyUsedAsPreviousCertificate(): Unit = {
    // Arrange
    val storageMock = mock[Storage]
    val metadataStorageView = new AccountStateMetadataStorageView(storageMock)
    val storageCertKey = new ByteArrayWrapper(BytesUtils.fromHexString("fecbe6fcc71e68d667c602c6112bec5e365c77f0fcb829d2502afb4e0608eaba"))

    when(storageMock.get(storageCertKey)).thenAnswer(_ => Optional.of(Some(2)))

    // Act
    val oldCertToBeRemoved = metadataStorageView.getOldTopCertificatesToBeRemoved(WithdrawalEpochInfo(5, 5))

    // Assert
    assertTrue(oldCertToBeRemoved.nonEmpty)
  }

  def generateCertificateWithEpochNumber(epochNum: Int): WithdrawalEpochCertificate = {
    val sourceCertHex: String = Source.fromResource("cert_no_bts").getLines().next()
    val newCertBytes: ArrayBuffer[Byte] = ArrayBuffer[Byte]()
    newCertBytes.appendAll(BytesUtils.fromHexString(sourceCertHex))

    val newEpochBytes: Array[Byte] = Ints.toByteArray(epochNum)
    (0 to 3).foreach(idx => newCertBytes(36 + idx) = newEpochBytes(3 - idx))
    WithdrawalEpochCertificate.parse(newCertBytes.toArray, 0)
  }


  def getRandomAccountStateRoot: Array[Byte] = {
    val value = new Array[Byte](32)
    Random.nextBytes(value)
    value
  }

}
