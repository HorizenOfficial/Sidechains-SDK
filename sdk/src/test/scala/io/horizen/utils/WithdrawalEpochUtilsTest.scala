package io.horizen.utils

import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.fixtures.{CompanionsFixture, MainchainBlockReferenceFixture}
import io.horizen.params.{MainNetParams, NetworkParams}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar


class WithdrawalEpochUtilsTest extends JUnitSuite with MockitoSugar with MainchainBlockReferenceFixture with CompanionsFixture {

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion
  val params: NetworkParams = mock[NetworkParams]
  val withdrawalEpochLength: Int = 100

  @Test
  def testGetWithdrawalEpochInfo(): Unit = {
    Mockito.when(params.withdrawalEpochLength).thenReturn(withdrawalEpochLength)

    // Test 1: no MainchainBlockReferenceData
    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(1, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(0, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 0),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(0, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )


    // Test 2: 1 MainchainBlockReferenceData
    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, 2),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(1, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, withdrawalEpochLength),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(1, WithdrawalEpochInfo(1, withdrawalEpochLength - 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(1, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )


    // Test 3: 2 MainchainBlockReferenceData
    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, 3),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(2, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, withdrawalEpochLength),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(2, WithdrawalEpochInfo(1, withdrawalEpochLength - 2), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(2, WithdrawalEpochInfo(1, withdrawalEpochLength - 1), params)
    )


    // Test 4: no MainchainBlockReferenceData
    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(1, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(0, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 0),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(0, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )

    // Test 5: block with maximum number of MainchainBlockReferenceData
    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(3, withdrawalEpochLength),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(withdrawalEpochLength, WithdrawalEpochInfo(2, withdrawalEpochLength), params)
    )

    // Test 6: block with maximum number of MainchainBlockReferenceData starting at index 3
    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(3, 3),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(withdrawalEpochLength, WithdrawalEpochInfo(2, 3), params)
    )

    // Test 7: block with maximum number of MainchainBlockReferenceData starting at index 3
    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(3, 3),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(withdrawalEpochLength, WithdrawalEpochInfo(2, 3), params)
    )

    // Test 8: block with an invalid number MainchainBlockReferenceData, greater than epoch length
    assertThrows[IllegalArgumentException](
      WithdrawalEpochUtils.getWithdrawalEpochInfo(withdrawalEpochLength + 1, WithdrawalEpochInfo(2, 0), params)
    )

    // Test 9: block with an invalid number MainchainBlockReferenceData, negative
    assertThrows[IllegalArgumentException](
      WithdrawalEpochUtils.getWithdrawalEpochInfo(- 1, WithdrawalEpochInfo(2, 0), params)
    )
  }

  @Test
  def testHasReachedCertificateSubmissionWindowEnd(): Unit = {
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
