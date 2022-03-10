package com.horizen.block

import com.horizen.fixtures.MainchainTxCrosschainOutputFixture
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.secret.PrivateKey25519Creator
import com.horizen.utils.{BytesUtils, Utils}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.util.Random
import scala.util.Try

class MainchainTxForwardTransferCrosschainOutputTest extends JUnitSuite with MainchainTxCrosschainOutputFixture {

  @Test
  def creation(): Unit = {
    val amount: Long = 100L
    val proposition: PublicKey25519Proposition = PrivateKey25519Creator.getInstance().generateSecret("test1".getBytes()).publicImage()
    val sidechainId: Array[Byte] = new Array[Byte](32)
    Random.nextBytes(sidechainId)
    val mcReturnAddress: Array[Byte] = new Array[Byte](20)
    Random.nextBytes(mcReturnAddress)

    val bytes: Array[Byte] = generateMainchainTxForwardTransferCrosschainOutputBytes(amount, proposition, sidechainId, mcReturnAddress)
    val hash: String = BytesUtils.toHexString(Utils.doubleSHA256Hash(bytes))


    // Test 1: successful creation
    var output: Try[MainchainTxForwardTransferCrosschainOutput] = MainchainTxForwardTransferCrosschainOutput.create(bytes, 0)

    assertTrue("Forward Transfer crosschain output expected to be parsed.", output.isSuccess)
    assertEquals("Output Hash is different.", hash, BytesUtils.toHexString(output.get.hash))
    assertEquals("Output amount is different.", amount, output.get.amount)
    assertEquals("Output proposition bytes are different.", proposition, new PublicKey25519Proposition(output.get.propositionBytes))
    assertEquals("Output sidechainId is different.", BytesUtils.toHexString(sidechainId), BytesUtils.toHexString(output.get.sidechainId))


    // Test 2: broken bytes: length is too small
    val brokenBytes = bytes.slice(0, bytes.length - 1)

    output = MainchainTxForwardTransferCrosschainOutput.create(brokenBytes, 0)
    assertTrue("Forward Transfer crosschain output expected to be NOT parsed.", output.isFailure)

  }
}
