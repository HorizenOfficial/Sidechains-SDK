package com.horizen.account

import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.horizen.account.serialization.EthJsonMapper
import io.horizen.evm.Address
import org.junit.Assert.assertEquals

import java.math.BigInteger
import scala.jdk.CollectionConverters.asScalaIteratorConverter
import scala.language.implicitConversions
import scala.util.Random

trait AccountFixture {
  // simplifies using BigIntegers within the tests
  implicit def longToBigInteger(x: Long): BigInteger = BigInteger.valueOf(x)

  def randomBytes(n: Int): Array[Byte] = {
    val bytes = new Array[Byte](n)
    val rand = new Random()
    rand.nextBytes(bytes)
    bytes
  }

  def randomU256: BigInteger = new BigInteger(randomBytes(32))

  def randomHash: Array[Byte] = randomBytes(32)

  def randomAddress: Address = new Address(randomBytes(Address.LENGTH))

  def assertJsonEquals(expected: String, actual: Object): Unit = {
    val mapper = new ObjectMapper()
    val actualJson = EthJsonMapper.serialize(actual)
    val actualTree = mapper.readTree(actualJson)
    val expectedTree = mapper.readTree(expected)
    // try to produce are more precise error message than the one below
    if (!expectedTree.equals(actualTree)) {
      throwFirstJsonDifference(expectedTree, actualTree)
    }
    // JsonNode.equals() implements a full deep-equality check
    assertEquals("JSON should be equal", expectedTree, actualTree)
  }

  def throwFirstJsonDifference(expected: JsonNode, actual: JsonNode): Unit = {
    expected.getNodeType match {
      case JsonNodeType.OBJECT =>
        // need to iterate over both expected and actual to catch elements only existing in one of them
        for (name <- expected.fieldNames().asScala ++ actual.fieldNames().asScala) {
          assertEquals(s"object field should match: $name", expected.get(name), actual.get(name))
        }
      case JsonNodeType.ARRAY =>
        for (i <- 0 until expected.size().max(actual.size())) {
          assertEquals(s"array item should match: $i", expected.get(i), actual.get(i))
        }
      case JsonNodeType.STRING => assertEquals("string value should match", expected.textValue(), actual.textValue())
      case JsonNodeType.NUMBER =>
        assertEquals("number value should match", expected.numberValue(), actual.numberValue())
      case _ => assertEquals("json should match", expected, actual)
    }
  }
}
