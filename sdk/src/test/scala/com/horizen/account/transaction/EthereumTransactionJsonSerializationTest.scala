package com.horizen.account.transaction

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.utils.BytesUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import org.web3j.crypto.Sign
import sparkz.core.utils.SparkzEncoder
import java.math.BigInteger

class EthereumTransactionJsonSerializationTest extends JUnitSuite
{

  val serializer: ApplicationJsonSerializer = ApplicationJsonSerializer.getInstance()
  serializer.setDefaultConfiguration()

  @Test
  def testLegacyTxToJson(): Unit = {

    val transaction = new EthereumTransaction(
      "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new BigInteger("12000000000002539"),
      "",
      new Sign.SignatureData(
        28.toByte,
        BytesUtils.fromHexString("2a4afbdd7e8d99c3df9dfd9e4ecd0afe018d8dec0b8b5fe1a44d5f30e7d0a5c5"),
        BytesUtils.fromHexString("7ca554a8317ff86eb6b23d06fa210d23e551bed58f58f803a87e5950aa47a9e9")))

    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    evalJsonRepr(node, transaction)
  }

  @Test
  def testUnsignedLegacyTxToJson(): Unit = {

    val transaction = new EthereumTransaction(
      "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      new BigInteger("12000000000002539"),
      "",
      null)

    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    evalJsonRepr(node, transaction)
  }

  @Test
  def testEip1559TxToJson(): Unit = {

    val transaction = new EthereumTransaction(
      31337,
      "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
      BigInteger.valueOf(33L), // nonce
      BigInteger.valueOf(21000),  // gasLimit
      BigInteger.valueOf(19).multiply(BigInteger.TEN.pow(9)),  // maxPriorityFeePerGas
      BigInteger.valueOf(44).multiply(BigInteger.TEN.pow(9)),  // maxFeePerGas
      BigInteger.TEN.pow(18), // value
      "",
      new Sign.SignatureData(
        BytesUtils.fromHexString("1B"),
        BytesUtils.fromHexString("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023"),
        BytesUtils.fromHexString("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d"))
    )
    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    evalJsonRepr(node, transaction)
  }

  @Test
  def testUnsignedEip1559TxToJson(): Unit = {

    val transaction = new EthereumTransaction(
      31337,
      "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
      BigInteger.valueOf(33L), // nonce
      BigInteger.valueOf(21000),  // gasLimit
      BigInteger.valueOf(19).multiply(BigInteger.TEN.pow(9)),  // maxPriorityFeePerGas
      BigInteger.valueOf(44).multiply(BigInteger.TEN.pow(9)),  // maxFeePerGas
      BigInteger.TEN.pow(18), // value
      "",
      null
    )
    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    evalJsonRepr(node, transaction)
  }


  @Test
  def testEip155TxToJson(): Unit = {
    val transaction = new EthereumTransaction(
      "0x3535353535353535353535353535353535353535",
      BigInteger.valueOf(9L), // nonce
      BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)), // gasPrice
      BigInteger.valueOf(21000), // gasLimit
      BigInteger.TEN.pow(18), // value
      "",
      new Sign.SignatureData(
        37.toByte,
        BytesUtils.fromHexString("28EF61340BD939BC2195FE537567866003E1A15D3C71FF63E1590620AA636276"),
        BytesUtils.fromHexString("67CBE9D8997F761AECB703304B3800CCF555C9F3DC64214B297FB1966A3B6D83")
      )
    )
    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    evalJsonRepr(node, transaction)
  }


  @Test
  def testPartiallySignedEip155TxToJson(): Unit = {

    val transaction = new EthereumTransaction(
      "0x3535353535353535353535353535353535353535",
      BigInteger.valueOf(9L),
      BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)),
      BigInteger.valueOf(21000),
      BigInteger.TEN.pow(18),
      "",
      new Sign.SignatureData(Array[Byte](1), Array[Byte](0), Array[Byte](0))
    )

    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    evalJsonRepr(node, transaction)
  }

  @Test
  def testUnsignedEip155TxToJson(): Unit = {
    val transaction = new EthereumTransaction(
      "0x3535353535353535353535353535353535353535",
      BigInteger.valueOf(9L),
      BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(9)),
      BigInteger.valueOf(21000),
      BigInteger.TEN.pow(18),
      "",
      null
    )

    val jsonStr = serializer.serialize(transaction)
    val node: JsonNode = serializer.getObjectMapper.readTree(jsonStr)

    evalJsonRepr(node, transaction)
  }

  def evalJsonRepr(node: JsonNode, transaction: EthereumTransaction): Unit = {

    try {
      val id = node.path("id").asText()
      assertEquals("Transaction id json value must be the same.",
        SparkzEncoder.default.encode(transaction.id), id)
    } catch {
      case _: Throwable => fail("Transaction id not found in json.")
    }

    try {
      val fromAddress = node.path("from").path("address").asText()
      val strFrom : String = if (transaction.getFrom == null)  {
        ""
      } else {
        BytesUtils.toHexString(transaction.getFrom.address())
      }
      assertEquals("Transaction from address json value must be the same.",
        strFrom, fromAddress)
    } catch {
      case _: Throwable => fail("Transaction from address not found in json.")
    }

    try {
      val toAddress = node.path("to").path("address").asText()
      assertEquals("Transaction to address json value must be the same.",
        SparkzEncoder.default.encode(transaction.getTo.address()), toAddress)
    } catch {
      case _: Throwable => fail("Transaction to address not found in json.")
    }

    try {
      val value = node.path("value").bigIntegerValue()
      assertEquals("Transaction value json must be the same.",
        transaction.getValue, value)
    } catch {
      case _: Throwable => fail("Transaction from address not found in json.")
    }

    try {
      val nonce = node.path("nonce").bigIntegerValue()
      assertEquals("Transaction nonce json must be the same.",
        transaction.getNonce, nonce)
    } catch {
      case _: Throwable => fail("Transaction nonce not found in json.")
    }

    try {
      val data = node.path("data").asText()
      assertEquals("Transaction data json must be the same.",
        SparkzEncoder.default.encode(transaction.getData), data)
    } catch {
      case _: Throwable => fail("Transaction data not found in json.")
    }

    try {
      val gasPrice = node.path("gasPrice").bigIntegerValue()
      assertEquals("Transaction gasPrice json must be the same.",
        transaction.getGasPrice, gasPrice)
    } catch {
      case _: Throwable => fail("Transaction gasPrice not found in json.")
    }

    try {
      val gasLimit = node.path("gasLimit").bigIntegerValue()
      assertEquals("Transaction gasLimit json must be the same.",
        transaction.getGasLimit, gasLimit)
    } catch {
      case _: Throwable => fail("Transaction gasLimit not found in json.")
    }

    try {
      val maxFeePerGas = node.path("maxFeePerGas").bigIntegerValue()
      assertEquals("Transaction maxFeePerGas json must be the same.",
        transaction.getMaxFeePerGas, maxFeePerGas)
    } catch {
      case _: Throwable => fail("Transaction maxFeePerGas not found in json.")
    }

    try {
      val maxPriorityFeePerGas = node.path("maxPriorityFeePerGas").bigIntegerValue()
      assertEquals("Transaction maxPriorityFeePerGas json must be the same.",
        transaction.getMaxPriorityFeePerGas, maxPriorityFeePerGas)
    } catch {
      case _: Throwable => fail("Transaction maxPriorityFeePerGas not found in json.")
    }

    try {
      val eip1559 = node.path("eip1559").asBoolean()
      assertEquals("Transaction eip1559 json value must be the same.",
        transaction.isEIP1559, eip1559)
    } catch {
      case _: Throwable => fail("Transaction eip1559 not found in json.")
    }

    try {
      val txType = node.path("type").asInt()
      assertEquals("Transaction type json value must be the same.",
        transaction.version(), txType)
    } catch {
      case _: Throwable => fail("Transaction type not found in json.")
    }

    // optional, can be empty for legacy non eip155 signed tx
    if (!node.path("chainId").isEmpty) {
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
          // in case of EIP155 tx signature data getV is the byte arrayncarrying the chain id
          // (actual V value is in getSignature())
          SparkzEncoder.default.encode(transaction.getSignature.getV), sig_v)

      } catch {
        case _: Throwable => fail("Transaction signature v not found in json.")
      }

      try {
        val sig_r = node.path("signature").path("r").asText()
        assertEquals("Transaction signature r json value must be the same.",
          SparkzEncoder.default.encode(transaction.getR), sig_r)
      } catch {
        case _: Throwable => fail("Transaction signature r not found in json.")
      }

      try {
        val sig_s = node.path("signature").path("s").asText()
        assertEquals("Transaction signature s json value must be the same.",
          SparkzEncoder.default.encode(transaction.getS), sig_s)
      } catch {
        case _: Throwable => fail("Transaction signature s not found in json.")
      }
    }

    try {
      val str = transaction.toString
      //println(str)
    } catch {
      case _: Throwable => fail("Malformed string returned")
    }

    try {
      val jsonStr = node.toPrettyString
      //println(node.toPrettyString)
    } catch {
      case _: Throwable => fail("Malformed json string returned")
    }
  }

}
