package com.horizen.csw

import com.horizen.cryptolibprovider.CswCircuitImplZendoo
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.utils.BytesUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class CswProofTest {
  val cswCircuit = new CswCircuitImplZendoo()

  @Test
  def transformPrivateKey25519(): Unit = {
    val pk: PrivateKey25519 = PrivateKey25519Creator.getInstance().generateSecret("seed".getBytes)

    val res = cswCircuit.transformPrivateKey25519(pk)

    // Regression
    assertEquals("Different pub key", "f165e1e5f7c290e52f2edef3fbab60cbae74bfd3274f8e5ee1de3345c954a166", BytesUtils.toHexString(pk.publicImage().pubKeyBytes()))
    assertEquals("Different private key", "19b25856e1c150ca834cffc8b59b23adbd0ec0389e58eb22b3b64768098d002b", BytesUtils.toHexString(pk.privateKey()))
    assertEquals("Different transformation result", "08eb1969be10581600c812f6ef0eea3b16c432854588698d78696e8ff7a7d163", BytesUtils.toHexString(res))
  }
}
