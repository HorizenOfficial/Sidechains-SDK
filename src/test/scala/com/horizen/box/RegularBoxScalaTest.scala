package com.horizen.box

import com.horizen.proposition.PublicKey25519Proposition
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder
import scorex.crypto.signatures.Curve25519

class RegularBoxScalaTest
  extends JUnitSuite
{

  @Test
  def testToJson(): Unit = {
    val seed = "12345".getBytes
    val keyPair = Curve25519.createKeyPair(seed)
    val privateKey = keyPair._1
    val publicKey = keyPair._2

    val proposition = new PublicKey25519Proposition(publicKey)
    val nonce = 12345
    val value = 10
    val box = new RegularBox(proposition, nonce, value)

    val json = box.toJson

    assertEquals("Json must contain only 1 proposition.",
      1, json.\\("proposition").size)
    assertEquals("Proposition json content must be the same.",
      proposition.toJson, json.\\("proposition").head)

    assertEquals("Json must contain only 1 id.",
      1, json.\\("id").size)
    assertTrue("Id json value must be the same.",
      box.id().sameElements(ScorexEncoder.default.decode(json.\\("id").head.asString.get).get))

    assertEquals("Json must contain only 1 nonce.",
      1, json.\\("nonce").size)
    assertEquals("Nonce json value must be the same.",
      box.nonce(), json.\\("nonce").head.asNumber.get.toLong.get)

    assertEquals("Json must contain only 1 value.",
      1, json.\\("value").size)
    assertEquals("Value json value must be the same.",
      box.value(), json.\\("value").head.asNumber.get.toLong.get)
  }
}

