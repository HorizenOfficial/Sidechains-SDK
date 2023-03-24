package io.horizen.utils

import java.time.Instant
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.fixtures.{CompanionsFixture, ForgerBoxFixture, MainchainBlockReferenceFixture, MerkleTreeFixture, VrfGenerator}
import io.horizen.params.{MainNetParams, NetworkParams}
import io.horizen.utxo.block.SidechainBlock
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.util.bytesToId


class WithdrawalEpochUtilsTest extends JUnitSuite with MockitoSugar with MainchainBlockReferenceFixture with CompanionsFixture {

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val params: NetworkParams = mock[NetworkParams]
  val withdrawalEpochLength: Int = 100

  @Test
  def getWithdrawalEpochInfo(): Unit = {
    Mockito.when(params.withdrawalEpochLength).thenReturn(withdrawalEpochLength)

    // Test 1: block with no MainchainBlockReferenceData
    val (forgerBox1, forgerMeta1) = ForgerBoxFixture.generateForgerBox(32)
    var block: SidechainBlock = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(), // no MainchainBlockReferenceData
      Seq(),
      Seq(),
      Seq(),
      forgerMeta1.blockSignSecret,
      forgerMeta1.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      new Array[Byte](32),
      sidechainTransactionsCompanion
    ).get

    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(1, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 0),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )


    // Test 2: block with 1 MainchainBlockReferenceData
    val (forgerBox2, forgerMeta2) = ForgerBoxFixture.generateForgerBox(322)

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(generateMainchainBlockReference().data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta2.blockSignSecret,
      forgerMeta2.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      new Array[Byte](32),
      sidechainTransactionsCompanion
    ).get

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, 2),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, withdrawalEpochLength),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, withdrawalEpochLength - 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )


    // Test 3: block with 2 MainchainBlockReferenceData
    val (forgerBox3, forgerMeta3) = ForgerBoxFixture.generateForgerBox(332)

    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(generateMainchainBlockReference().data, generateMainchainBlockReference().data),
      Seq(),
      Seq(),
      Seq(),
      forgerMeta3.blockSignSecret,
      forgerMeta3.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      new Array[Byte](32),
      sidechainTransactionsCompanion
    ).get

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, 3),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, withdrawalEpochLength),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, withdrawalEpochLength - 2), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, withdrawalEpochLength - 1), params)
    )


    // Test 4: block with no MainchainBlockReferenceData, but with 1 MainchainHeader
    val (forgerBox4, forgerMeta4) = ForgerBoxFixture.generateForgerBox(328)
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      SidechainBlock.BLOCK_VERSION,
      Instant.now.getEpochSecond - 10000,
      Seq(), // no MainchainBlockReferenceData
      Seq(),
      Seq(generateMainchainBlockReference().header), // MainchainHeader has no impact on WithdrawalEpochInfo
      Seq(),
      forgerMeta4.blockSignSecret,
      forgerMeta4.forgingStakeInfo,
      VrfGenerator.generateProof(456L),
      MerkleTreeFixture.generateRandomMerklePath(456L),
      new Array[Byte](32),
      sidechainTransactionsCompanion
    ).get

    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(1, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 0),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )
  }

  @Test
  def hasReachedCertificateSubmissionWindowEnd(): Unit = {
    val withdrawalEpochLength: Int = 200
    val submissionWindowLength: Int = 40
    val params: NetworkParams = MainNetParams(withdrawalEpochLength = withdrawalEpochLength)

    // Check window length
    assertEquals("Different submission window length found.",
      submissionWindowLength, WithdrawalEpochUtils.certificateSubmissionWindowLength(params))

    // Negative cases

    // Test 1: parent is at the beginning, current is in the middle of the submission window
    var parent = WithdrawalEpochInfo(1, 0)
    var current = WithdrawalEpochInfo(1, 1)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 2: parent is at the beginning, current is in the middle of the submission window
    parent = WithdrawalEpochInfo(1, 5)
    current = WithdrawalEpochInfo(1, 11)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))

    // Test 3: both parent and current are in the end of the window
    parent = WithdrawalEpochInfo(1, submissionWindowLength)
    current = WithdrawalEpochInfo(1, submissionWindowLength)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))

    // Test 4: parent is in the end of the window and current is out of the submission window
    parent = WithdrawalEpochInfo(1, submissionWindowLength)
    current = WithdrawalEpochInfo(1, submissionWindowLength + 1)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 5: parent and current are out of the submission window
    parent = WithdrawalEpochInfo(1, submissionWindowLength + 1)
    current = WithdrawalEpochInfo(1, submissionWindowLength + 2)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 6: parent is in previous epoch and current one is in the middle of submission window
    parent = WithdrawalEpochInfo(1, withdrawalEpochLength)
    current = WithdrawalEpochInfo(2, 10)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 7: first epoch specific case - no submission window
    parent = WithdrawalEpochInfo(0, 0)
    current = WithdrawalEpochInfo(0, submissionWindowLength)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))
    parent = WithdrawalEpochInfo(0, submissionWindowLength / 2)
    assertFalse("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Positive cases

    // Test 8: parent is at the beginning, current is in the end of the submission window
    parent = WithdrawalEpochInfo(1, 0)
    current = WithdrawalEpochInfo(1, submissionWindowLength)
    assertTrue("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 9: parent is at the beginning, current is out of the submission window
    parent = WithdrawalEpochInfo(1, 0)
    current = WithdrawalEpochInfo(1, submissionWindowLength + 1)
    assertTrue("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 10: parent is the middle of the window, current is in the end of the submission window
    parent = WithdrawalEpochInfo(1, submissionWindowLength / 2)
    current = WithdrawalEpochInfo(1, submissionWindowLength)
    assertTrue("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 11: parent is in the middle, current is out of the submission window
    parent = WithdrawalEpochInfo(1, submissionWindowLength / 2)
    current = WithdrawalEpochInfo(1, submissionWindowLength + 1)
    assertTrue("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 12: parent is in the end of previous epoch, current is in the end of new epoch submission window
    parent = WithdrawalEpochInfo(1, withdrawalEpochLength)
    current = WithdrawalEpochInfo(2, submissionWindowLength)
    assertTrue("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))


    // Test 13: parent is in the end of previous epoch, current after new epoch submission window
    parent = WithdrawalEpochInfo(1, withdrawalEpochLength)
    current = WithdrawalEpochInfo(2, submissionWindowLength + 1)
    assertTrue("Should not reach the end of the window.",
      WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(current, parent, params))
  }
}
