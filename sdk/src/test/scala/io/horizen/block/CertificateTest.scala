package com.horizen.block

import com.horizen.utils.BytesUtils
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.junit.Assert._

import scala.io.Source

class CertificateTest extends JUnitSuite {

  @Test
  def certificateWithoutBTs(): Unit = {
    val emptyCertHex : String = Source.fromResource("cert_no_bts").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(emptyCertHex)
    val emptyCertHash: String = "ebe46fa0b4d88e6ea892c6ff9ebb8d94d926bc016b6e619dc9bccf4715f8dc45"
    val sidechainIdHex: String = "367fd1fb8f092fb78c80f059c34e0fafa64522fd297dfd5b8ffd990e003f5412"

    val cert: WithdrawalEpochCertificate = WithdrawalEpochCertificate.parse(bytes, 0)

    assertEquals("Certificate epoch number is different.", 0, cert.epochNumber)
    assertEquals("Certificate sidechain id is wrong", sidechainIdHex, BytesUtils.toHexString(BytesUtils.reverseBytes(cert.sidechainId)))
    assertEquals("Version is wrong", -5, cert.version)
    assertEquals("Quality is wrong", 7, cert.quality)
    assertEquals("btrFee is wrong", 0, cert.btrFee)
    assertEquals("ftMinAmount is wrong", 0, cert.ftMinAmount)
    assertEquals("Transaction input size is wrong", 1, cert.transactionInputs.size)
    assertEquals("Transaction output size is wrong", 1, cert.transactionOutputs.size)
    assertEquals("Certificate backward transfers number is different.", 0, cert.backwardTransferOutputs.size)
    assertEquals("Certificate hash is different", emptyCertHash, BytesUtils.toHexString(cert.hash))
  }

  @Test
  def certificateWithBTs(): Unit = {
    val certHex : String = Source.fromResource("cert_with_bts").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(certHex)
    val certHash: String = "1042d4737e2c5ca16b3ef96466e2cfb7ea92c5a6986c84a4ffc1b347f874994a"
    val sidechainIdHex: String = "367fd1fb8f092fb78c80f059c34e0fafa64522fd297dfd5b8ffd990e003f5412"

    val cert: WithdrawalEpochCertificate = WithdrawalEpochCertificate.parse(bytes, 0)

    assertEquals("Certificate epoch number is different.", 1, cert.epochNumber)
    assertEquals("Certificate sidechain id is wrong", sidechainIdHex, BytesUtils.toHexString(BytesUtils.reverseBytes(cert.sidechainId)))
    assertEquals("Quality is wrong", 7, cert.quality)
    assertEquals("Version is wrong", -5, cert.version)
    assertEquals("btrFee is wrong", 0, cert.btrFee)
    assertEquals("ftMinAmount is wrong", 0, cert.ftMinAmount)

    assertEquals("Transaction input size is wrong", 1, cert.transactionInputs.size)
    assertEquals("Transaction output size is wrong", 1, cert.transactionOutputs.size)

    assertEquals("Certificate backward transfer amount is different.", 2, cert.backwardTransferOutputs.size)
    assertEquals("First backward transfer amount is wrong", 700000000, cert.backwardTransferOutputs.head.amount)
    assertEquals("First backward transfer pubKeyHash is wrong", "206f75deb9df73ec7216c1a3c1533dce3525e4c5",
      BytesUtils.toHexString(BytesUtils.reverseBytes(cert.backwardTransferOutputs.head.pubKeyHash)))
    assertEquals("Second backward transfer amount is wrong", 300000000, cert.backwardTransferOutputs(1).amount)
    assertEquals("Second backward transfer pubKeyHash is wrong", "06d2169cabb3fe2d1322fdaae497cd65ac73dafe",
      BytesUtils.toHexString(BytesUtils.reverseBytes(cert.backwardTransferOutputs(1).pubKeyHash)))
    assertEquals("Certificate hash is different", certHash, BytesUtils.toHexString(cert.hash))
  }
}
