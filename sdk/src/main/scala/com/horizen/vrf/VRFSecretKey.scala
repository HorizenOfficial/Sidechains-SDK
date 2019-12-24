package com.horizen.vrf

class VRFSecretKey {
  def prove(message: Array[Byte]): VRFProof = ??? // jni call to Rust impl

  def vrfHash(message: Array[Byte]): Array[Byte] = ??? // if need // jni call to Rust impl
}
