package com.horizen.proposition

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.serialization.ApplicationJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder
import scorex.crypto.signatures.Curve25519

class PublicKey25519PropositionScalaTest
  extends JUnitSuite
{

  @Test
  def testToJson(): Unit = {
    val seed = "12345".getBytes
    val keyPair = Curve25519.createKeyPair(seed)
    val privateKey = keyPair._1
    val publicKey = keyPair._2

    val prop1 = new PublicKey25519Proposition(publicKey)

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(prop1)

    val node : JsonNode = serializer.getObjectMapper().readTree(jsonStr)

    assertEquals("Json must contain only 1 publicKey.",
      1, node.findValues("publicKey").size())
    assertEquals("PublicKey json value must be the same.",
      ScorexEncoder.default.encode(prop1.pubKeyBytes()), node.path("publicKey").asText())
  }
}
