package com.horizen.block

import com.horizen.utils.BytesUtils
import org.junit.{Ignore, Test}
import org.scalatest.junit.JUnitSuite
import org.junit.Assert.{assertEquals, assertTrue}

import scala.io.Source

class WithdrawalEpochCertificateTest extends JUnitSuite {

  @Ignore
  @Test
  def testParseEmptyCertificate(): Unit = {

    val mcCertificateHex = Source.fromResource("backward_transfer_certificate_empty").getLines().next()
    val mcCertificateBytes = BytesUtils.fromHexString(mcCertificateHex)
    val certificate = WithdrawalEpochCertificate.parse(mcCertificateBytes, 0)

    assertEquals("Version is wrong.", -5, certificate.version)
    assertEquals("Sidechain Id is wrong.",
      "00000000000000000000000000000000000000000000000000000000deadbeeb",
      BytesUtils.toHexString(certificate.sidechainId))
    assertEquals("Epoch number is wrong.", 0, certificate.epochNumber)
    assertEquals("Quality is wrong.", 1, certificate.quality)
    assertEquals("EndEpochBlockHash is wrong.",
      "0e82e63bf7fb0e8106bee55e3201deadc0644e9190e5ca00df846171bbd5f43b",
      BytesUtils.toHexString(certificate.endEpochBlockHash))

    assertEquals("Count of transaction inputs is wrong.", 1, certificate.transactionInputs.size)
    assertEquals("Transaction input prev tx hash is wrong.",
      "497fe2a0b6e7fa0c2ee164601bcdeaec54df91a414f088b3f5e6cc62123dd221",
      BytesUtils.toHexString(certificate.transactionInputs(0).prevTxHash))
    assertEquals("Transaction input prev tx output index is wrong.", 1, certificate.transactionInputs.size)
    assertEquals("Transaction output tx script is wrong.",
      "473044022057441734816a17ca2c510c3721cf89b3d99d986c9c86d9de46c445c135ff6e2302201f7a1ce607932da3b62299165146f20eebb07a4afc2d64243476920eca261d760121020825ced4b8fe22e316bac3676f8cdbe94fb2142e7ac72d445b6b77d239129da0",
      BytesUtils.toHexString(certificate.transactionInputs(0).txScript))
    assertEquals("Transaction input sequence is wrong.", 1, certificate.transactionInputs.size)

    assertEquals("Count of transaction outputs is wrong.", 1, certificate.transactionOutputs.size)
    assertEquals("Transaction output value is wrong.", 99998501, certificate.transactionOutputs(0).value)
    assertEquals("Transaction output script is wrong.",
      "b4000da5ee723b7923feb580518541c6f098206330dbc711a6678922c11f2ccf1abb20ac8806a8b533cd2c3dddb54f81211f6899a434d1b0e814a976",
      BytesUtils.toHexString(certificate.transactionOutputs(0).script))

    assertEquals("Count of backward transfer outputs is wrong.", 0, certificate.backwardTransferOutputs.size)
  }

  @Ignore
  @Test
  def testParseNonEmptyCertificate(): Unit = {

    val mcCertificateHex = Source.fromResource("backward_transfer_certificate_non_empty").getLines().next()
    val mcCertificateBytes = BytesUtils.fromHexString(mcCertificateHex)
    val certificate = WithdrawalEpochCertificate.parse(mcCertificateBytes, 0)

    assertEquals("Version is wrong.", -5, certificate.version)
    assertEquals("Sidechain Id is wrong.",
      "00000000000000000000000000000000000000000000000000000000deadbeeb",
      BytesUtils.toHexString(certificate.sidechainId))
    assertEquals("Epoch number is wrong.", 0, certificate.epochNumber)
    assertEquals("Quality is wrong.", 1, certificate.quality)
    assertEquals("EndEpochBlockHash is wrong.",
      "00fdfacc318deeef7464fecc39914e458b4477411d6ba6dc37b2da07c50e033c",
      BytesUtils.toHexString(certificate.endEpochBlockHash))

    assertEquals("Count of transaction inputs is wrong.", 1, certificate.transactionInputs.size)
    assertEquals("Transaction input prev tx hash is wrong.",
      "1633df9afacd87aa642a583e38aeb796b3575d9c2eb203e1250ab2643f42d9ce",
      BytesUtils.toHexString(certificate.transactionInputs(0).prevTxHash))
    assertEquals("Transaction input prev tx output index is wrong.", 1, certificate.transactionInputs.size)
    assertEquals("Transaction output tx script is wrong.",
      "483045022100db0457fb8f47d9488e98acc110871cdc739ebeefc2d6fcc43d64ebb184f438d302200815f669661521b21fcc7baa8893b5745ffc4e07892a5200781f27387e1241cb0121031a0cf92961bf96c0508c4cb52e862f6daca11e3d0feffae06bfd904323d04f56",
      BytesUtils.toHexString(certificate.transactionInputs(0).txScript))
    assertEquals("Transaction input sequence is wrong.", 1, certificate.transactionInputs.size)

    assertEquals("Count of transaction outputs is wrong.", 1, certificate.transactionOutputs.size)
    assertEquals("Transaction output value is wrong.", 99998501, certificate.transactionOutputs(0).value)
    assertEquals("Transaction output script is wrong.",
      "b4000da5ee723b7923feb580518541c6f098206330dbc711a6678922c11f2ccf1abb20ac8829eb8941ea9b3bdf9a107070c4a5942bfc68ef5414a976",
      BytesUtils.toHexString(certificate.transactionOutputs(0).script))

    assertEquals("Count of backward transfer outputs is wrong.", 1, certificate.backwardTransferOutputs.size)
    assertEquals("Backward transfer output amount is wrong.", 550000000, certificate.backwardTransferOutputs(0).amount)
    assertEquals("Transaction output script is wrong.",
      "7df167f74f58eb8975ae69e70456865a9848fc8a",
      BytesUtils.toHexString(certificate.backwardTransferOutputs(0).pubKeyHash))

  }
}
