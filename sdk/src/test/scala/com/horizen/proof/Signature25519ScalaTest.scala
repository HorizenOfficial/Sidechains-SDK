package com.horizen.proof


import com.fasterxml.jackson.databind.JsonNode
import com.horizen.secret.PrivateKey25519Creator
import com.horizen.serialization.ApplicationJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder

class Signature25519ScalaTest
  extends JUnitSuite
{

  @Test
  def testToJson(): Unit = {
    val testMessage: Array[Byte] = "Test string message to sign/verify.".getBytes
    val seed = "12345".getBytes
    val key = PrivateKey25519Creator.getInstance.generateSecret(seed)
    val prp = key.publicImage
    val pr = key.sign(testMessage)

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(pr)

    val node : JsonNode = serializer.getObjectMapper().readTree(jsonStr)

    assertEquals("Json must contain only 1 signature.",
      1, node.findValues("signature").size())
    assertEquals("",
      ScorexEncoder.default.encode(pr.signatureBytes), node.path("signature").asText())
  }
}

