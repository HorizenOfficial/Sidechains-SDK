package com.horizen.vrf

import java.util

import com.horizen.cryptolibprovider.VrfFunctions.{KeyType, ProofType}
import com.horizen.cryptolibprovider.{FieldElementUtils, CryptoLibProvider}
import org.junit.Assert.{assertEquals, assertFalse, assertNotEquals, assertTrue}
import org.junit.Test

import scala.util.Random


class VrfFunctionsTest {
  val keys: util.EnumMap[KeyType, Array[Byte]] = CryptoLibProvider.vrfFunctions.generatePublicAndSecretKeys(1.toString.getBytes())
  val secretBytes: Array[Byte] = keys.get(KeyType.SECRET)
  val publicBytes: Array[Byte] = keys.get(KeyType.PUBLIC)
  val message: Array[Byte] = "Very secret message!".getBytes
  val vrfProofBytes: Array[Byte] = CryptoLibProvider.vrfFunctions.createProof(secretBytes, publicBytes, message).get(ProofType.VRF_PROOF)
  val vrfProofCheck: Boolean = CryptoLibProvider.vrfFunctions.verifyProof(message, publicBytes, vrfProofBytes)
  val vrfOutputBytes: Array[Byte] = CryptoLibProvider.vrfFunctions.proofToOutput(publicBytes, message, vrfProofBytes).get()

  @Test
  def sanityCheck(): Unit = {
    assertNotEquals(vrfProofBytes.deep, vrfOutputBytes.deep)
    assertTrue(CryptoLibProvider.vrfFunctions.publicKeyIsValid(publicBytes))
    assertTrue(vrfProofCheck)
    assertTrue(vrfOutputBytes.nonEmpty)
  }


  @Test
  def determinismCheck(): Unit = {
    //@TODO add seed check here as it became supported
    val rnd = new Random()

    for (i <- 1 to 10) {
      val messageLen = rnd.nextInt(128) % FieldElementUtils.fieldElementLength()
      val newMessage = rnd.nextString(rnd.nextInt(128)).getBytes.take(messageLen)
      val firstVrfProofBytes = CryptoLibProvider.vrfFunctions.createProof(secretBytes, publicBytes, newMessage).get(ProofType.VRF_PROOF)
      val secondVrfProofBytes = CryptoLibProvider.vrfFunctions.createProof(secretBytes, publicBytes, newMessage).get(ProofType.VRF_PROOF)
      //@TODO uncomment this ASAP after proof generation became deterministic
      //assertEquals(vrfProofBytes.deep, otherVrfProofBytes.deep)

      val firstVrfOutputBytes = CryptoLibProvider.vrfFunctions.proofToOutput(publicBytes, newMessage, firstVrfProofBytes).get
      val secondVrfOutputBytes = CryptoLibProvider.vrfFunctions.proofToOutput(publicBytes, newMessage, secondVrfProofBytes).get

      assertEquals(firstVrfOutputBytes.deep, secondVrfOutputBytes.deep)
      println(s"Vrf output determinism check: iteration ${i}, for message len ${newMessage.length}")
    }
  }

  @Test()
  def tryToCorruptProof(): Unit= {
    val corruptedMessage: Array[Byte] = "Not very secret message!".getBytes
    val vrfProofCheckCorruptedMessage = CryptoLibProvider.vrfFunctions.verifyProof(corruptedMessage, publicBytes, vrfProofBytes)
    assertFalse(vrfProofCheckCorruptedMessage)

    val corruptedProofBytes: Array[Byte] = util.Arrays.copyOf(vrfProofBytes, vrfProofBytes.length)
    corruptedProofBytes(0) = (~corruptedProofBytes(0)).toByte
    val vrfProofCheckCorruptedVrfProof = CryptoLibProvider.vrfFunctions.verifyProof(message, publicBytes, corruptedProofBytes)
    assertFalse(vrfProofCheckCorruptedVrfProof)

    val corruptedPublicBytes: Array[Byte] = util.Arrays.copyOf(publicBytes, publicBytes.length)
    corruptedPublicBytes(0) = (~corruptedPublicBytes(0)).toByte
    val vrfProofCheckCorruptedPublicBytes = CryptoLibProvider.vrfFunctions.verifyProof(message, corruptedPublicBytes, vrfProofBytes)
    assertFalse(vrfProofCheckCorruptedPublicBytes)
  }

}