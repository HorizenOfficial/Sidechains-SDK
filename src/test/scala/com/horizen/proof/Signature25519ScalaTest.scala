package com.horizen.proof


import com.horizen.secret.PrivateKey25519Creator
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

/*    val json = pr.toJson

    assertEquals("Json must contain only 1 signature.",
      1, json.\\("signature").size)
    assertEquals("",
      ScorexEncoder.default.encode(pr._signatureBytes), json.\\("signature").head.asString.get)*/
  }
}

