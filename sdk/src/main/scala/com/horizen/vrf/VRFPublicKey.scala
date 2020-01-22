package com.horizen.vrf

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description

class VRFPublicKey(val key: Array[Byte]) {
  require(key.length == VRFPublicKey.length)

  def verify(message: Array[Byte], proof: VRFProof): Boolean = {
    val messageWithCorrectLength: Array[Byte] = new Array[Byte](32)
    Array.copy(message, 0, messageWithCorrectLength, 0, message.length)

    val decoded = proof.bytes.map(byte => (byte ^ key.head).toByte)
    messageWithCorrectLength.sameElements(decoded)
  } // jni call to Rust impl

  // maybe also a method for verifying VRFPublicKey
  def isValid: Boolean = true // jni call to Rust impl

  def bytes: Array[Byte] = key

  override def hashCode(): Int = key.head.toInt

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: VRFPublicKey => this.key sameElements that.key
      case _ =>
        false
    }
  }
}

object VRFPublicKey {
  val length: Int = 32 //just dummy number
  def parseBytes(bytes: Array[Byte]): VRFPublicKey = new VRFPublicKey(bytes)
}
