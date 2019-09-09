package com.horizen.proposition

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

/*    val json = prop1.toJson

    assertEquals("Json must contain only 1 publicKey.",
      1, json.\\("publicKey").size)
    assertEquals("PublicKey json value must be the same.",
      ScorexEncoder.default.encode(prop1.pubKeyBytes()), json.\\("publicKey").head.asString.get)*/
  }
}
