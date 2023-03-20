package io.horizen.account.transaction

import com.fasterxml.jackson.databind.JsonNode
import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.utils.EthereumTransactionDecoder
import io.horizen.json.serializer.ApplicationJsonSerializer
import org.junit.Assert.{assertEquals, assertNull, assertTrue}
import org.junit.{Ignore, Test}
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
  def testUnsignedEip155TxToJson(): Unit = {
    val transaction = getUnsignedEip155LegacyTransaction
    evalJsonRepr(transaction)
  }

  def evalJsonRepr(transaction: EthereumTransaction, printOutput: Boolean = false): Unit = {
    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    val id = node.path("id").asText()
    assertEquals("Transaction id json value must be the same.",
      SparkzEncoder.default.encode(transaction.id), id)

    val fromAddress = node.path("from").path("address").asText()
    val strFrom: String = if (transaction.getFrom == null) {
      ""
    } else {
      transaction.getFrom.address().toStringNoPrefix
    }
    assertEquals("Transaction from address json value must be the same.",
      strFrom, fromAddress)

  // optional, can be empty for contract deployment
  if (!node.path("to").isMissingNode) {
      val toAddress = node.path("to").path("address").asText()
      assertEquals("Transaction to address json value must be the same.",
        SparkzEncoder.default.encode(transaction.getTo.get().address().toBytes), toAddress)
  }

    val data = node.path("data").asText()
    assertEquals("Transaction data json must be the same.",
      SparkzEncoder.default.encode(transaction.getData), data)

    val value = node.path("value").bigIntegerValue()
    assertEquals("Transaction value json must be the same.",
      transaction.getValue, value)
    assertTrue(s"non negative value expected: $value", value.signum() >= 0  )

    val nonce = node.path("nonce").bigIntegerValue()
    assertEquals("Transaction nonce json must be the same.",
      transaction.getNonce, nonce)
    assertTrue(s"non negative nonce expected: $nonce", nonce.signum() >= 0  )

    var txType: Int = 0x0
    txType = node.path("version").asInt()
    assertEquals("Transaction version json value must be the same.",
      transaction.version(), txType)

    if (txType == 0x0) {
      val gasPrice = node.path("gasPrice").bigIntegerValue()
      assertEquals("Transaction gasPrice json must be the same.",
        transaction.getGasPrice, gasPrice)
      assertTrue(s"non negative nonce expected: $gasPrice", gasPrice.signum() >= 0  )
      assertTrue(node.path("maxFeePerGas").isMissingNode)
      assertTrue(node.path("maxPriorityFeePerGas").isMissingNode)
    } else if (txType == EthereumTransactionDecoder.TransactionType.EIP1559.getRlpType) {
      assertTrue(node.path("gasPrice").isMissingNode)

      val maxFeePerGas = node.path("maxFeePerGas").bigIntegerValue()
      assertEquals("Transaction maxFeePerGas json must be the same.",
        transaction.getMaxFeePerGas, maxFeePerGas)
      assertTrue(s"non negative nonce expected: $maxFeePerGas", maxFeePerGas.signum() >= 0  )

      val maxPriorityFeePerGas = node.path("maxPriorityFeePerGas").bigIntegerValue()
      assertEquals("Transaction maxPriorityFeePerGas json must be the same.",
        transaction.getMaxPriorityFeePerGas, maxPriorityFeePerGas)
      assertTrue(s"non negative nonce expected: $maxPriorityFeePerGas", maxPriorityFeePerGas.signum() >= 0  )
    } else {
      fail(s"TX version $txType not expected")
    }

    val gasLimit = node.path("gasLimit").bigIntegerValue()
    assertEquals("Transaction gasLimit json must be the same.",
      transaction.getGasLimit, gasLimit)
    assertTrue(s"non negative nonce expected: $gasLimit", gasLimit.signum() >= 0  )

    val eip1559 = node.path("eip1559").asBoolean()
    assertEquals("Transaction eip1559 json value must be the same.",
      transaction.isEIP1559, eip1559)


    // optional, can be empty for legacy non eip155 signed tx
    if (!node.path("chainId").isMissingNode) {
      val chainId = node.path("chainId").asLong()
      assertEquals("Transaction chainId json value must be the same.",
        transaction.getChainId, chainId)
    }

    val signed = node.path("signed").asBoolean()
    assertEquals("Transaction signed json value must be the same.",
      transaction.isSigned, signed)

    if (transaction.getSignature != null) {
      val sig_v = node.path("signature").path("v").asText()
      assertEquals("Transaction signature v json value must be the same.",
        // in case of EIP155 tx signature data getV is the byte array carrying the chain id
        // (actual V value is in getSignature())
        SparkzEncoder.default.encode(transaction.getSignature.getV.toString(16)), sig_v)

      val sig_r = node.path("signature").path("r").asText()
      assertEquals("Transaction signature r json value must be the same.",
        SparkzEncoder.default.encode(transaction.getSignature.getR.toString(16)), sig_r)

      val sig_s = node.path("signature").path("s").asText()
      assertEquals("Transaction signature s json value must be the same.",
        SparkzEncoder.default.encode(transaction.getSignature.getS.toString(16)), sig_s)
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
