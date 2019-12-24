package com.horizen.vrf

object VRFKeyGenerator {

  def generate(seed: Array[Byte]): (VRFSecretKey, VRFPublicKey) = ??? // jni call to Rust impl
}
