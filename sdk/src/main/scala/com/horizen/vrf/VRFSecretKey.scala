package com.horizen.vrf

import com.horizen.vrf

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description

class VRFSecretKey(val key: Array[Byte]) {
  def prove(message: Array[Byte]): VRFProof = {
    val messageWithCorrectLength: Array[Byte] = new Array[Byte](32)
    Array.copy(message, 0, messageWithCorrectLength, 0, message.length)

    new vrf.VRFProof(messageWithCorrectLength.map(byte => (byte ^ key.head).toByte))
  } // jni call to Rust impl

  def vrfHash(message: Array[Byte]): Array[Byte] = {
    prove(message).proofToVRFHash()
  }

  def bytes: Array[Byte] = key

  override def hashCode(): Int = key.head.toInt

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: VRFSecretKey => this.key sameElements that.key
      case _ =>
        false
    }
  }
}


object VRFSecretKey {
  val length: Int = 32 //just dummy number
  def parseBytes(bytes: Array[Byte]): VRFSecretKey = new VRFSecretKey(bytes)
}
