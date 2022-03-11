package com.horizen.proposition

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.params.{MainNetParams, NetworkParams, RegTestParams}
import com.horizen.serialization.{ApplicationJsonSerializer, JsonHorizenPublicKeyHashSerializer}
import com.horizen.utils.{BytesUtils, Ed25519}
import org.junit.Assert.{assertArrayEquals, assertEquals, assertTrue}
import org.junit.{Assert, Test}
import org.scalatestplus.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder

import java.util.Random

class MCPublicKeyHashPropositionJsonTest
  extends JUnitSuite
{

  @Test
  def testToJson(): Unit = {
    val rnd = new Random(11111)
    val mcPubKeyHashBytes = new Array[Byte](MCPublicKeyHashProposition.KEY_LENGTH)
    rnd.nextBytes(mcPubKeyHashBytes)

    val prop1 = new MCPublicKeyHashProposition(mcPubKeyHashBytes)

    val params: NetworkParams = MainNetParams()
    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()
    JsonHorizenPublicKeyHashSerializer.setNetworkType(params)

    val jsonStr = serializer.serialize(prop1)

    val node : JsonNode = serializer.getObjectMapper().readTree(jsonStr)

    assertEquals("Json must contain only 1 mainchainAddress.",
      1, node.findValues("mainchainAddress").size())
    val pubKey: String = node.path("mainchainAddress").asText()
    var parsedPubKeyHashBytes: Array[Byte] = null
    try {
      parsedPubKeyHashBytes = BytesUtils.fromHorizenPublicKeyAddress(pubKey, params)
    } catch {
      case e: Exception => Assert.fail("PublicKey json value must be a Horizen address. Instead: error" + e.getMessage)
    }

    assertArrayEquals("Different pub key hash bytes found.", mcPubKeyHashBytes, parsedPubKeyHashBytes)

    var exceptionOccurred = false
    try {
      BytesUtils.fromHorizenPublicKeyAddress(pubKey, RegTestParams())
    } catch {
      case _: Exception => exceptionOccurred = true // expected
    }
    assertTrue("Horizen address parsing with inconsistent network type must fail.", exceptionOccurred)
  }
}
