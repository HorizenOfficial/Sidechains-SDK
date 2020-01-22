package com.horizen.utils

import java.time.Instant

import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.secret.PrivateKey25519Creator
import org.junit.Test
import org.mockito.Mockito
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import org.junit.Assert.assertEquals
import scorex.util.bytesToId
import java.util.{HashMap => JHashMap}

import com.horizen.fixtures.{ForgerBoxFixture, MainchainBlockReferenceFixture}
import com.horizen.vrf.VrfGenerator


class WithdrawalEpochUtilsTest extends JUnitSuite with MockitoSugar with MainchainBlockReferenceFixture {

  val sidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap())
  val params: NetworkParams = mock[NetworkParams]
  val withdrawalEpochLength: Int = 100

  @Test
  def getWithdrawalEpochInfo(): Unit = {
    Mockito.when(params.withdrawalEpochLength).thenReturn(withdrawalEpochLength)

    // Test 1: block with no mc block references
    var block: SidechainBlock = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(), // no mc block refs
      Seq(),
      PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(111).getBytes),
      ForgerBoxFixture.generateForgerBox, VrfGenerator.generateProof(456L), MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    assertEquals("Epoch info expected to be the same as previous.",
      WithdrawalEpochInfo(1, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 0),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )


    // Test 2: block with 1 mc block ref
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(generateMainchainBlockReference()),
      Seq(),
      PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(111).getBytes),
      ForgerBoxFixture.generateForgerBox, VrfGenerator.generateProof(456L), MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, 2),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, withdrawalEpochLength),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, withdrawalEpochLength - 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, withdrawalEpochLength), params)
    )


    // Test 3: block with 2 mc block ref
    block = SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(generateMainchainBlockReference(), generateMainchainBlockReference()),
      Seq(),
      PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(111).getBytes),
      ForgerBoxFixture.generateForgerBox, VrfGenerator.generateProof(456L), MerkleTreeFixture.generateRandomMerklePath(456L),
      sidechainTransactionsCompanion,
      null
    ).get

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, 3),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, 1), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch index should increase.",
      WithdrawalEpochInfo(1, withdrawalEpochLength),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, withdrawalEpochLength - 2), params)
    )

    assertEquals("Epoch info expected to be the changed: epoch switch expected.",
      WithdrawalEpochInfo(2, 1),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, WithdrawalEpochInfo(1, withdrawalEpochLength - 1), params)
    )
  }

}
