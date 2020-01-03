package com.horizen.vrf

object VRFKeyGenerator {

  def generate(seed: Array[Byte]): (VRFSecretKey, VRFPublicKey) = (new VRFSecretKey(Array.fill(32)(0x00)), new VRFPublicKey(Array.fill(32)(0x01))) // jni call to Rust impl
}
