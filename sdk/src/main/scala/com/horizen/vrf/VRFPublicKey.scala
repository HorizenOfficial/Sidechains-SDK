package com.horizen.vrf

class VRFPublicKey(val key: Array[Byte]) {
  require(key.length == VRFPublicKey.length)
  def verify(message: Array[Byte], proof: VRFProof): Boolean = ??? // jni call to Rust impl

  // maybe also a method for verifying VRFPublicKey
  def isValid: Boolean = ??? // jni call to Rust impl

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
