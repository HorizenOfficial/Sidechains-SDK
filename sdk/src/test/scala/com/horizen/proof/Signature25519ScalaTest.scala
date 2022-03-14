package com.horizen.proof


import com.fasterxml.jackson.databind.JsonNode
import com.horizen.secret.PrivateKey25519Creator
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertFalse}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder

class Signature25519ScalaTest
  extends JUnitSuite
{

  @Test
  def testToJson(): Unit = {
    val testMessage: Array[Byte] = "Test string message to sign/verify.".getBytes
    val seed = "12345".getBytes
    val key = PrivateKey25519Creator.getInstance.generateSecret(seed)
    val pr = key.sign(testMessage)

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    // Signature test
    val jsonStr = serializer.serialize(pr)

    val node : JsonNode = serializer.getObjectMapper().readTree(jsonStr)


    assertEquals("Json must contain only 1 signature.",
      1, node.findValues("signature").size())
    assertEquals("",
      ScorexEncoder.default.encode(pr.signatureBytes), node.path("signature").asText())

    // Test key
    val jsonKeyStr = serializer.serialize(key)

    val keyNode : JsonNode = serializer.getObjectMapper().readTree(jsonKeyStr)

    assertFalse("Secret should be not custom", keyNode.get("isCustom").asBoolean())
    assertEquals("The type name of the secret should be PrivateKey25519", keyNode.get("typeName").asText, "PrivateKey25519")
    assertEquals("Block parentId json value must be the same.",
      keyNode.get("bytes").asText(), BytesUtils.toHexString(key.bytes()))
  }
}

