package com.horizen.vrf

import java.util

import com.horizen.secret.{Secret, SecretsIdsEnum}

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description

class VRFSecretKey(val secret: Array[Byte], publicKey: Array[Byte]) extends Secret{
  override def secretTypeId(): Byte = SecretsIdsEnum.VrfPrivateKey.id()

  def prove(slotNumber: Int, nonceBytes: Array[Byte]): VRFProof = {
    val vrfProofBytes = VrfLoader.vrfFunctions.messageToVrfProofBytes(secret, slotNumber, nonceBytes)
    //val messageWithCorrectLength: Array[Byte] = Utils.doubleSHA256Hash(message)
    //new vrf.VRFProof(messageWithCorrectLength.map(byte => (byte ^ key.head).toByte))
    new VRFProof(vrfProofBytes)
  } // jni call to Rust impl

  def vrfHash(slotNumber: Int, nonceBytes: Array[Byte]): Array[Byte] = {
    VrfLoader.vrfFunctions.proofBytesToVrfHashBytes(prove(slotNumber, nonceBytes).bytes)
  }

  override def bytes: Array[Byte] = secret

  override def hashCode(): Int = util.Arrays.hashCode(secret)

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: VRFSecretKey => this.secret sameElements that.secret
      case _ =>
        false
    }
  }
}

object VRFSecretKey {
  val length: Int = 32 //sha256HashLen
  def parseBytes(bytes: Array[Byte]): VRFSecretKey = new VRFSecretKey(bytes)
}
