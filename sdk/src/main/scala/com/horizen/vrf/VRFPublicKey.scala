package com.horizen.vrf

class VRFPublicKey {
  def verify(message: Array[Byte], proof: VRFProof): Boolean = ??? // jni call to Rust impl

  // maybe also a method for verifying VRFPublicKey
  def isValid: Boolean = ??? // jni call to Rust impl
}
