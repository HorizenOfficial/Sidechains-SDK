package com.horizen.block

import com.horizen.utils.BytesUtils
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.junit.Assert._

// TODO: extend this test with real cases for: certificate with no withdrawals, with 1, with multiple.
class CertificateTest extends JUnitSuite {
  val certHex: String = "fbffffff01000000000000000000000000000000000000000000000000000000000000000000000083ea236a1d767478b46b8a9529b23c67f189cfe29ae7ea5e2b6bb4610c0d7b0d018e47d3bd4dd6c97ab883aa808aead140892b458c53daa3b86d832b4c9654e8f0000000006b483045022100b9d650b1b7e5eff0ba5bc905e4a4570f8116e72f9bcd0ce619545f765e01556002205ebd6aad231d7813f91c060c1cfefaac83aadf8da01216c08c57a55d7c51933a012102f3d4aa2a3ee4984f964f2c7763e99e917e8bcd1a73183739fe403817ad9ef646ffffffff01c96d7d01000000003c76a914b851350f1e36b963756594a163d174f4b0d2a99588ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b400"

  @Test
  def emptyCertificate(): Unit = {
    val bytes: Array[Byte] = BytesUtils.fromHexString(certHex)

    val cert: WithdrawalEpochCertificate = WithdrawalEpochCertificate.parse(bytes, 0)

    assertEquals("Certificate epoch number is different.", 0, cert.epochNumber)
    assertEquals("Certificate end block hash is different.", "0d7b0d0c61b46b2b5eeae79ae2cf89f1673cb229958a6bb47874761d6a23ea83",
      BytesUtils.toHexString(cert.endEpochBlockHash))
    assertEquals("Certificate backward transfer amount is different.", 0, cert.backwardTransferOutputs.size)
  }
}
