package com.horizen.vrf

import java.util

import com.horizen.vrf.VrfFunctions.KeyType
import org.junit.Assert.{assertEquals, assertFalse, assertNotEquals, assertTrue}
import org.junit.Test

import scala.util.Random


class VrfFunctionsTest {
  val keys: util.EnumMap[KeyType, Array[Byte]] = VrfLoader.vrfFunctions.generatePublicAndSecretKeys(1.toString.getBytes())
  val secretBytes: Array[Byte] = keys.get(KeyType.SECRET)
  val publicBytes: Array[Byte] = keys.get(KeyType.PUBLIC)
  val message: Array[Byte] = "Very secret message!".getBytes
  val vrfProofBytes: Array[Byte] = VrfLoader.vrfFunctions.createVrfProof(secretBytes, publicBytes, message)
  val vrfProofCheck: Boolean = VrfLoader.vrfFunctions.verifyProof(message, publicBytes, vrfProofBytes)
  val vrfProofHashBytes: Array[Byte] = VrfLoader.vrfFunctions.vrfProofToVrfHash(publicBytes, message, vrfProofBytes)

  @Test
  def sanityCheck(): Unit = {
    assertNotEquals(vrfProofBytes.deep, vrfProofHashBytes.deep)
    assertTrue(VrfLoader.vrfFunctions.publicKeyIsValid(publicBytes))
    assertTrue(vrfProofCheck)
    assertTrue(vrfProofHashBytes.nonEmpty)
  }


  @Test
  def determinismCheck(): Unit = {
    //@TODO add seed check here as it became supported
    val rnd = new Random()

    for (i <- 1 to 10) {
      val newMessage = rnd.nextString(rnd.nextInt(128)).getBytes
      val firstVrfProofBytes = VrfLoader.vrfFunctions.createVrfProof(secretBytes, publicBytes, newMessage)
      val secondVrfProofBytes = VrfLoader.vrfFunctions.createVrfProof(secretBytes, publicBytes, newMessage)
      //assertEquals(vrfProofBytes.deep, otherVrfProofBytes.deep)

      val firstVrfProofHashBytes = VrfLoader.vrfFunctions.vrfProofToVrfHash(publicBytes, newMessage, firstVrfProofBytes)
      val secondVrfProofHashBytes = VrfLoader.vrfFunctions.vrfProofToVrfHash(publicBytes, newMessage, secondVrfProofBytes)

      assertEquals(firstVrfProofHashBytes.deep, secondVrfProofHashBytes.deep)
      println(s"iteration ${i}, for message len ${newMessage.length}")
    }
  }

  @Test()
  def tryToCorruptProof(): Unit= {
    val corruptedMessage: Array[Byte] = "Not very secret message!".getBytes
    val vrfProofCheckCorruptedMessage = VrfLoader.vrfFunctions.verifyProof(corruptedMessage, publicBytes, vrfProofBytes)
    assertFalse(vrfProofCheckCorruptedMessage)

    val corruptedProofBytes: Array[Byte] = util.Arrays.copyOf(vrfProofBytes, vrfProofBytes.length)
    corruptedProofBytes(0) = (~corruptedProofBytes(0)).toByte
    val vrfProofCheckCorruptedVrfProof = VrfLoader.vrfFunctions.verifyProof(message, publicBytes, corruptedProofBytes)
    assertFalse(vrfProofCheckCorruptedVrfProof)

    val corruptedPublicBytes: Array[Byte] = util.Arrays.copyOf(publicBytes, publicBytes.length)
    corruptedPublicBytes(0) = (~corruptedPublicBytes(0)).toByte
    val vrfProofCheckCorruptedPublicBytes = VrfLoader.vrfFunctions.verifyProof(message, corruptedPublicBytes, vrfProofBytes)
    assertFalse(vrfProofCheckCorruptedPublicBytes)
  }

}