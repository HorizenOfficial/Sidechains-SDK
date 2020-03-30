package com.horizen.vrf

import java.util

import com.horizen.utils.Utils
import com.horizen.vrf

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description

//@TODO Shall be changed ASAP: VRFSecret shall be reimplemented as extension of com.horizen.secret.Secret
class VRFSecretKey(val key: Array[Byte]) {
  def prove(message: Array[Byte]): VRFProof = {
    val messageWithCorrectLength: Array[Byte] = Utils.doubleSHA256Hash(message)

    new vrf.VRFProof(messageWithCorrectLength.map(byte => (byte ^ key.head).toByte))
  } // jni call to Rust impl

  def vrfHash(message: Array[Byte]): Array[Byte] = {
    prove(message).proofToVRFHash()
  }

  def bytes: Array[Byte] = key

  override def hashCode(): Int = util.Arrays.hashCode(key)

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: VRFSecretKey => this.key sameElements that.key
      case _ =>
        false
    }
  }
}


object VRFSecretKey {
  val length: Int = 32 //sha256HashLen
  def parseBytes(bytes: Array[Byte]): VRFSecretKey = new VRFSecretKey(bytes)
}
