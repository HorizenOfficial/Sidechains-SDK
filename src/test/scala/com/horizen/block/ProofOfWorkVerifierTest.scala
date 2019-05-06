package com.horizen.block

import java.math.BigInteger

import com.google.common.primitives.UnsignedInts
import com.horizen.fixtures.MainchainHeaderFixture
import com.horizen.params.MainNetParams
import com.horizen.utils.{BytesUtils, Utils}
import org.junit.Assert.{assertEquals, assertTrue, assertFalse}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class ProofOfWorkVerifierTest extends JUnitSuite with MainchainHeaderFixture {

  @Test
  def ProofOfWorkVerifierTest_CheckPoW(): Unit = {
    var hash: Array[Byte] = null
    var bits: Int = 0

    // Test 1: Test valid Horizen block #498971
    hash = BytesUtils.fromHexString("00000000117c360186cfea085c6d15c176118a7778ed56733084084133790fe7")
    bits = 0x1c21c09e
    assertTrue("Proof of Work expected to be Valid.", ProofOfWorkVerifier.checkProofOfWork(getHeaderWithPoW(bits, hash), MainNetParams))


    // Test 2: Test valid Horizen block #238971
    hash = BytesUtils.fromHexString("00000000c1dfa2e554343822f6a6fb70b622ecbd2ae766eb4d61e792f37d46bd")
    bits = 0x1d012dc4
    assertTrue("Proof of Work expected to be Valid.", ProofOfWorkVerifier.checkProofOfWork(getHeaderWithPoW(bits, hash), MainNetParams))


    // Test 3: Test invalid PoW: bits (target) is lower than hash target
    hash = BytesUtils.fromHexString("00000000c1dfa2e554343822f6a6fb70b622ecbd2ae766eb4d61e792f37d46bd")
    bits = Utils.encodeCompactBits(new BigInteger(1, hash)).toInt // it will cut some part of data, so value will be less than hash target
    assertFalse("Proof of Work expected to be Invalid.", ProofOfWorkVerifier.checkProofOfWork(getHeaderWithPoW(bits, hash), MainNetParams))


    // Test 4: Test invalid PoW: bits (target) is greater than powLimit
    hash = BytesUtils.fromHexString("00000000c1dfa2e554343822f6a6fb70b622ecbd2ae766eb4d61e792f37d46bd")
    bits = Utils.encodeCompactBits(MainNetParams.powLimit.add(BigInteger.ONE)).toInt
    assertFalse("Proof of Work expected to be Invalid.", ProofOfWorkVerifier.checkProofOfWork(getHeaderWithPoW(bits, hash), MainNetParams))
  }

  @Test
  def ProofOfWorkVerifierTest_CalculateNextWorkRequired(): Unit = {
    var nLastRetargetTime: Int = 0
    var nThisTime: Int = 0
    var bitsAvg: BigInteger = null

    var expectedWork: Int = 0
    var calculatedWork: Int = 0

    // Test 1: Test calculation of next difficulty target with no constraints applying
    nLastRetargetTime = 1262149169 // NOTE: Not an actual block time
    nThisTime = 1262152739 // Block #32255 of Bitcoin
    bitsAvg = Utils.decodeCompactBits(UnsignedInts.toLong(0x1d00ffff))
    expectedWork = 0x1d011998

    calculatedWork = ProofOfWorkVerifier.calculateNextWorkRequired(bitsAvg, nThisTime, nLastRetargetTime, MainNetParams)
    assertEquals("Calculated PoW bits should be equal to expected one.", expectedWork, calculatedWork)


    // Test 2: Test the constraint on the upper bound for next work
    nLastRetargetTime = 1231006505 // Block #0 of Bitcoin
    nThisTime = 1233061996 // Block #2015 of Bitcoin
    bitsAvg = Utils.decodeCompactBits(UnsignedInts.toLong(0x1f07ffff))
    expectedWork = 0x1f07ffff

    calculatedWork = ProofOfWorkVerifier.calculateNextWorkRequired(bitsAvg, nThisTime, nLastRetargetTime, MainNetParams)
    assertEquals("Calculated PoW bits should be equal to expected one.", expectedWork, calculatedWork)


    // Test 3: Test the constraint on the lower bound for actual time taken
    nLastRetargetTime = 1279296753 // NOTE: Not an actual block time
    nThisTime = 1279297671 // Block #68543 of Bitcoin
    bitsAvg = Utils.decodeCompactBits(UnsignedInts.toLong(0x1c05a3f4))
    expectedWork = 0x1c04bceb

    calculatedWork = ProofOfWorkVerifier.calculateNextWorkRequired(bitsAvg, nThisTime, nLastRetargetTime, MainNetParams)
    assertEquals("Calculated PoW bits should be equal to expected one.", expectedWork, calculatedWork)


    // Test 4: Test the constraint on the upper bound for actual time taken
    nLastRetargetTime = 1269205629 // NOTE: Not an actual block time
    nThisTime = 1269211443 // Block #46367  of Bitcoin
    bitsAvg = Utils.decodeCompactBits(UnsignedInts.toLong(0x1c387f6f))
    expectedWork = 0x1c4a93bb

    calculatedWork = ProofOfWorkVerifier.calculateNextWorkRequired(bitsAvg, nThisTime, nLastRetargetTime, MainNetParams)
    assertEquals("Calculated PoW bits should be equal to expected one.", expectedWork, calculatedWork)
  }
}