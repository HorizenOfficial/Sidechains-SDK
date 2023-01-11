package com.horizen.account.transaction

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.utils.EthereumTransactionDecoder
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertNull, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.util.SparkzEncoder

class EthereumTransactionJsonSerializationTest
  extends JUnitSuite
    with EthereumTransactionFixture
{

  val serializer: ApplicationJsonSerializer = ApplicationJsonSerializer.getInstance()
  serializer.setDefaultConfiguration()

  @Test
  def testLegacyTxToJson(): Unit = {
    val transaction = getEoa2EoaLegacyTransaction
    evalJsonRepr(transaction)
  }

  @Test
  def testContractDeploymentEip1559TxToJson(): Unit = {
    val transaction = getContractDeploymentEip1559Transaction
    evalJsonRepr(transaction, printOutput = true)
  }

  @Test
  def testContractCallEip155LegacyTxToJson(): Unit = {
    val transaction = getContractCallEip155LegacyTransaction
    evalJsonRepr(transaction)
  }

  @Test
  def testUnsignedLegacyTxToJson(): Unit = {
    val transaction = getUnsignedEoa2EoaLegacyTransaction
    evalJsonRepr(transaction)
  }

  @Test
  def testEoa2EoaEip1559TxToJson(): Unit = {
    val transaction = getEoa2EoaEip1559Transaction
    evalJsonRepr(transaction, printOutput = true)
  }

  @Test
  def testUnsignedEip1559TxToJson(): Unit = {
    val transaction = getUnsignedEoa2EoaEip1559Transaction
    evalJsonRepr(transaction)
  }

  @Test
  def testEip155TxToJson(): Unit = {
    val transaction = getEoa2EoaEip155LegacyTransaction
    evalJsonRepr(transaction, printOutput = true)
  }

  @Test
  def testPartiallySignedEip155TxToJson(): Unit = {
    val transaction = getPartiallySignedEip155LegacyTransaction
    assertTrue(transaction.isEIP155)
    assertNull(transaction.getFromAddressString)
    evalJsonRepr(transaction)
  }

  @Test
  def testUnsignedEip155TxToJson(): Unit = {
    val transaction = getUnsignedEip155LegacyTransaction
    evalJsonRepr(transaction)
  }

  def evalJsonRepr(transaction: EthereumTransaction, printOutput: Boolean = false): Unit = {
    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    try {
      val id = node.path("id").asText()
      assertEquals("Transaction id json value must be the same.",
        SparkzEncoder.default.encode(transaction.id), id)
    } catch {
      case _: Throwable => fail("Transaction id not found in json.")
    }

    try {
      val fromAddress = node.path("from").path("address").asText()
      val strFrom: String = if (transaction.getFrom == null) {
        ""
      } else {
        BytesUtils.toHexString(transaction.getFrom.address())
      }
      assertEquals("Transaction from address json value must be the same.",
        strFrom, fromAddress)
    } catch {
      case _: Throwable => fail("Transaction from address not found in json.")
    }

    // optional, can be empty for contract deployment
    if (!node.path("to").isMissingNode) {
      try {
        val toAddress = node.path("to").path("address").asText()
        assertEquals("Transaction to address json value must be the same.",
          SparkzEncoder.default.encode(transaction.getTo.get().address()), toAddress)
      } catch {
        case _: Throwable => fail("Transaction to address not found in json.")
      }
    }

    try {
      val data = node.path("data").asText()
      assertEquals("Transaction data json must be the same.",
        SparkzEncoder.default.encode(transaction.getData), data)
    } catch {
      case _: Throwable => fail("Transaction data not found in json.")
    }

    try {
      val value = node.path("value").bigIntegerValue()
      assertEquals("Transaction value json must be the same.",
        transaction.getValue, value)
      assertTrue(s"non negative value expected: $value", value.signum() >= 0  )
    } catch {
      case _: Throwable => fail("Transaction from address not found in json.")
    }

    try {
      val nonce = node.path("nonce").bigIntegerValue()
      assertEquals("Transaction nonce json must be the same.",
        transaction.getNonce, nonce)
      assertTrue(s"non negative nonce expected: $nonce", nonce.signum() >= 0  )

    } catch {
      case _: Throwable => fail("Transaction nonce not found in json.")
    }


    var txType: Int = 0x0
    try {
      txType = node.path("version").asInt()
      assertEquals("Transaction version json value must be the same.",
        transaction.version(), txType)
    } catch {
      case _: Throwable => fail("Transaction version not found in json.")
    }

    if (txType == 0x0) {
      try {
        val gasPrice = node.path("gasPrice").bigIntegerValue()
        assertEquals("Transaction gasPrice json must be the same.",
          transaction.getGasPrice, gasPrice)
        assertTrue(s"non negative nonce expected: $gasPrice", gasPrice.signum() >= 0  )

      } catch {
        case _: Throwable => fail("Transaction gasPrice not found in json.")
      }
      assertTrue(node.path("maxFeePerGas").isMissingNode)
      assertTrue(node.path("maxPriorityFeePerGas").isMissingNode)
    } else
    if (txType == EthereumTransactionDecoder.TransactionType.EIP1559.getRlpType) {
      assertTrue(node.path("gasPrice").isMissingNode)

      try {
        val maxFeePerGas = node.path("maxFeePerGas").bigIntegerValue()
        assertEquals("Transaction maxFeePerGas json must be the same.",
          transaction.getMaxFeePerGas, maxFeePerGas)
        assertTrue(s"non negative nonce expected: $maxFeePerGas", maxFeePerGas.signum() >= 0  )

      } catch {
        case _: Throwable => fail("Transaction maxFeePerGas not found in json.")
      }

      try {
        val maxPriorityFeePerGas = node.path("maxPriorityFeePerGas").bigIntegerValue()
        assertEquals("Transaction maxPriorityFeePerGas json must be the same.",
          transaction.getMaxPriorityFeePerGas, maxPriorityFeePerGas)
        assertTrue(s"non negative nonce expected: $maxPriorityFeePerGas", maxPriorityFeePerGas.signum() >= 0  )

      } catch {
        case _: Throwable => fail("Transaction maxPriorityFeePerGas not found in json.")
      }
    } else {
      assertTrue(s"TX version $txType not expected", false)
    }

    try {
      val gasLimit = node.path("gasLimit").bigIntegerValue()
      assertEquals("Transaction gasLimit json must be the same.",
        transaction.getGasLimit, gasLimit)
      assertTrue(s"non negative nonce expected: $gasLimit", gasLimit.signum() >= 0  )

    } catch {
      case _: Throwable => fail("Transaction gasLimit not found in json.")
    }

    try {
      val eip1559 = node.path("eip1559").asBoolean()
      assertEquals("Transaction eip1559 json value must be the same.",
        transaction.isEIP1559, eip1559)
    } catch {
      case _: Throwable => fail("Transaction eip1559 not found in json.")
    }


    // optional, can be empty for legacy non eip155 signed tx
    if (!node.path("chainId").isMissingNode) {
      try {
        val chainId = node.path("chainId").asLong()
        assertEquals("Transaction chainId json value must be the same.",
          transaction.getChainId, chainId)
      } catch {
        case _: Throwable => fail("Transaction chainId not found in json.")
      }
    }

    try {
      val signed = node.path("signed").asBoolean()
      assertEquals("Transaction signed json value must be the same.",
        transaction.isSigned, signed)
    } catch {
      case _: Throwable => fail("Transaction signed not found in json.")
    }

    if (transaction.getSignature != null) {
      try {
        val sig_v = node.path("signature").path("v").asText()
        assertEquals("Transaction signature v json value must be the same.",
          // in case of EIP155 tx signature data getV is the byte array carrying the chain id
          // (actual V value is in getSignature())
          SparkzEncoder.default.encode(transaction.getSignature.getV), sig_v)

      } catch {
        case _: Throwable => fail("Transaction signature v not found in json.")
      }

      try {
        val sig_r = node.path("signature").path("r").asText()
        assertEquals("Transaction signature r json value must be the same.",
          SparkzEncoder.default.encode(transaction.getSignature.getR), sig_r)
      } catch {
        case _: Throwable => fail("Transaction signature r not found in json.")
      }

      try {
        val sig_s = node.path("signature").path("s").asText()
        assertEquals("Transaction signature s json value must be the same.",
          SparkzEncoder.default.encode(transaction.getSignature.getS), sig_s)
      } catch {
        case _: Throwable => fail("Transaction signature s not found in json.")
      }
    }

    try {
      val str = transaction.toString
      if (printOutput)
        println(str)
    } catch {
      case _: Throwable => fail("Malformed string returned")
    }

    try {
      val jsonStr = node.toPrettyString
      if (printOutput)
        println(jsonStr)
    } catch {
      case _: Throwable => fail("Malformed json string returned")
    }
  }

}
